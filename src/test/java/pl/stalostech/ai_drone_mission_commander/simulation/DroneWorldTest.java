package pl.stalostech.ai_drone_mission_commander.simulation;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import pl.stalostech.ai_drone_mission_commander.domain.*;
import pl.stalostech.ai_drone_mission_commander.simulation.exception.*;

import static org.assertj.core.api.Assertions.*;

class DroneWorldTest {
    private final DroneWorld world = new DroneWorld();
    private final RouteService routes = new RouteService(world);
    private final DroneSimulationService drones = new DroneSimulationService(world, routes);
    private final SectorService sectors = new SectorService(world);
    private final WeatherService weather = new WeatherService(world);
    private final MissionSimulationService missions = new MissionSimulationService(world, routes, drones, sectors);
    private final SimulationEventService events = new SimulationEventService(world);

    private MissionIntent inspection(String sector, boolean home) {
        return new MissionIntent("alpha", MissionType.INSPECTION, sector, home);
    }

    @Test
    void startsWithDeterministicFixturesAndImmutableSnapshots() {
        var snapshot = world.snapshot();
        assertThat(snapshot).isEqualTo(new DroneWorld().snapshot());
        assertThat(drones.list()).extracting(DroneStatus::batteryPercent).containsExactly(82, 41, 67);
        assertThat(drones.status(" ALPHA ").position()).isEqualTo(Position.BASE);
        assertThat(drones.status("bravo").position()).isEqualTo(sectors.get("sector_a").position());
        assertThat(drones.status("bravo").state()).isEqualTo(DroneState.IDLE);
        assertThat(weather.current()).isEqualTo(new Weather(12, false, Visibility.GOOD));
        assertThatThrownBy(() -> snapshot.drones().clear()).isInstanceOf(UnsupportedOperationException.class);
        drones.consumeBattery("alpha", 5);
        assertThat(snapshot.drones().getFirst().batteryPercent()).isEqualTo(82);
        assertThat(new DroneWorld().drone("alpha").batteryPercent()).isEqualTo(82);
    }

    @Test
    void calculatesEuclideanRoutesAndRoundsConsumptionPerLeg() {
        var route = routes.calculate("alpha", "SECTOR_B", true);
        assertThat(route.waypoints()).containsExactly(Position.BASE, new Position(0, 3), Position.BASE);
        assertThat(route.distanceKm()).isEqualTo(6);
        assertThat(route.estimatedBatteryUsage()).isEqualTo(20);
        assertThat(routes.calculate("alpha", "SECTOR_B", false).estimatedBatteryUsage()).isEqualTo(10);
        assertThat(routes.calculate("bravo", "SECTOR_B", true).estimatedBatteryUsage()).isEqualTo(22);
        assertThat(routes.calculate("bravo", "SECTOR_A", false).estimatedBatteryUsage()).isZero();
        assertThatThrownBy(() -> route.waypoints().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void movesConsumesBatteryAndReturnsHome() {
        var moved = drones.move("alpha", "SECTOR_A");
        assertThat(moved.batteryPercent()).isEqualTo(75);
        assertThat(moved.position()).isEqualTo(new Position(2, 0));
        assertThat(moved.state()).isEqualTo(DroneState.IDLE);
        assertThat(drones.move("alpha", "SECTOR_A")).isEqualTo(moved);
        var returned = drones.returnHome("alpha");
        assertThat(returned.position()).isEqualTo(Position.BASE);
        assertThat(returned.batteryPercent()).isEqualTo(68);
        assertThat(returned.state()).isEqualTo(DroneState.READY);
        assertThat(drones.returnHome("alpha")).isEqualTo(returned);
    }

    @Test
    void executesInspectionOnceAndPreservesCreationSnapshot() {
        var created = missions.create(inspection("SECTOR_B", true));
        assertThat(created.id()).isEqualTo("mission-1");
        assertThat(created.state()).isEqualTo(MissionState.CREATED);
        assertThat(drones.status("alpha").batteryPercent()).isEqualTo(82);
        var result = missions.execute(created.id());
        assertThat(result.state()).isEqualTo(MissionState.COMPLETED);
        assertThat(result.inspectionResult().anomaly()).isFalse();
        assertThat(drones.status("alpha").batteryPercent()).isEqualTo(59);
        assertThat(drones.status("alpha").position()).isEqualTo(Position.BASE);
        assertThat(created.state()).isEqualTo(MissionState.CREATED);
        var snapshot = world.snapshot();
        assertThatThrownBy(() -> missions.execute(created.id())).isInstanceOf(SimulationConflictException.class);
        assertThat(world.snapshot()).isEqualTo(snapshot);
    }

    @Test
    void distinguishesPatrolAndInspectionAndUsesDeterministicFindings() {
        var patrol = missions.create(new MissionIntent("charlie", MissionType.PATROL, "SECTOR_C", false));
        var result = missions.execute(patrol.id());
        assertThat(result.inspectionResult()).isNull();
        assertThat(result.state()).isEqualTo(MissionState.COMPLETED);
        assertThat(drones.status("charlie").batteryPercent()).isEqualTo(49);
        assertThat(drones.status("charlie").position()).isEqualTo(new Position(4, 3));
        assertThat(drones.status("charlie").state()).isEqualTo(DroneState.IDLE);
        var inspection = missions.execute(missions.create(inspection("SECTOR_C", true)).id());
        assertThat(inspection.inspectionResult().anomaly()).isTrue();
        assertThat(inspection.inspectionResult()).isEqualTo(sectors.inspect("SECTOR_C"));
        assertThat(drones.status("alpha").batteryPercent()).isEqualTo(47);
    }

    @Test
    void recalculatesCreatedMissionAfterWeatherAndPositionChange() {
        var created = missions.create(inspection("SECTOR_B", true));
        drones.move("alpha", "SECTOR_A"); // 75% remaining
        events.inject(SimulationEventType.STRONG_WIND, null, null);
        var currentQuote = routes.calculate("alpha", "SECTOR_B", true);
        assertThat(currentQuote.estimatedBatteryUsage()).isEqualTo(44); // ceil(sqrt(13)*6.5) + ceil(3*6.5)
        var result = missions.execute(created.id());
        assertThat(result.route()).isEqualTo(currentQuote);
        assertThat(result.state()).isEqualTo(MissionState.COMPLETED);
        assertThat(drones.status("alpha").batteryPercent()).isEqualTo(28);
        assertThat(created.route().estimatedBatteryUsage()).isEqualTo(20);
    }

    @Test
    void failsBeforeMovementWhenBatteryCannotCoverActivityAndReturn() {
        var mission = missions.create(inspection("SECTOR_B", true));
        events.inject(SimulationEventType.BATTERY_DROP, "alpha", 60); // 22, route needs 20 + activity 3
        var before = drones.status("alpha");
        var failed = missions.execute(mission.id());
        assertThat(failed.state()).isEqualTo(MissionState.FAILED);
        assertThat(failed.failureReason()).contains("Insufficient battery");
        assertThat(failed.inspectionResult()).isNull();
        assertThat(drones.status("alpha")).isEqualTo(before);
        assertThatThrownBy(() -> missions.execute(mission.id())).isInstanceOf(SimulationConflictException.class);
    }

    @ParameterizedTest
    @EnumSource(value = SimulationEventType.class, names = {"GPS_LOST", "MOTOR_WARNING", "COMMUNICATION_LOST"})
    void hardwareEventsPreventDirectMovementAndMissionExecution(SimulationEventType type) {
        var mission = missions.create(inspection("SECTOR_A", true));
        events.inject(type, "alpha", null);
        var before = drones.status("alpha");
        assertThatThrownBy(() -> drones.move("alpha", "SECTOR_A")).isInstanceOf(SimulationConflictException.class);
        assertThat(missions.execute(mission.id()).state()).isEqualTo(MissionState.FAILED);
        assertThat(drones.status("alpha")).isEqualTo(before);
    }

    @Test
    void degradedGpsDoesNotRepairLostGpsAndBatteryDropClampsAtZero() {
        assertThat(events.inject(SimulationEventType.GPS_DEGRADED, "ALPHA", null).sequence()).isEqualTo(1);
        assertThat(drones.status("alpha").gps()).isEqualTo(GpsStatus.DEGRADED);
        drones.move("alpha", "SECTOR_A");
        events.inject(SimulationEventType.GPS_LOST, "alpha", null);
        events.inject(SimulationEventType.GPS_DEGRADED, "alpha", null);
        assertThat(drones.status("alpha").gps()).isEqualTo(GpsStatus.LOST);
        var drop = events.inject(SimulationEventType.BATTERY_DROP, "alpha", 100);
        assertThat(drop.value()).isEqualTo(75);
        assertThat(drop.sequence()).isEqualTo(4);
        assertThat(drones.status("alpha").batteryPercent()).isZero();
    }

    @Test
    void resetRestoresWorldWithoutReusingMissionIdentifiers() {
        var initial = world.snapshot();
        missions.execute(missions.create(inspection("SECTOR_C", true)).id());
        events.inject(SimulationEventType.STRONG_WIND, null, 60);
        events.inject(SimulationEventType.MOTOR_WARNING, "alpha", null);
        assertThat(world.reset()).isEqualTo(initial);
        assertThat(missions.create(inspection("SECTOR_A", false)).id()).isEqualTo("mission-2");
        assertThat(events.inject(SimulationEventType.BATTERY_DROP, "alpha", null).sequence()).isEqualTo(1);
    }

    @Test
    void oldMissionIdCannotExecuteANewMissionAfterReset() {
        var old = missions.create(inspection("SECTOR_A", true));
        world.reset();
        var newer = missions.create(new MissionIntent("charlie", MissionType.PATROL, "SECTOR_C", false));
        assertThat(newer.id()).isNotEqualTo(old.id());
        var before = world.snapshot();
        assertThatThrownBy(() -> missions.execute(old.id())).isInstanceOf(SimulationNotFoundException.class);
        assertThatThrownBy(() -> missions.get(old.id())).isInstanceOf(SimulationNotFoundException.class);
        assertThat(world.snapshot()).isEqualTo(before);
        assertThat(missions.execute(newer.id()).state()).isEqualTo(MissionState.COMPLETED);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 83})
    void rejectsInvalidConsumptionWithoutChangingState(int amount) {
        var before = world.snapshot();
        assertThatThrownBy(() -> drones.consumeBattery("alpha", amount)).isInstanceOf(RuntimeException.class);
        assertThat(world.snapshot()).isEqualTo(before);
    }

    @Test
    void rejectsUnknownIdsAndInvalidEventsWithoutPartialMutation() {
        var before = world.snapshot();
        assertThatThrownBy(() -> missions.create(inspection("MISSING", true))).isInstanceOf(SimulationNotFoundException.class);
        assertThatThrownBy(() -> drones.move("missing", "SECTOR_A")).isInstanceOf(SimulationNotFoundException.class);
        assertThatThrownBy(() -> missions.execute("missing")).isInstanceOf(SimulationNotFoundException.class);
        assertThatThrownBy(() -> events.inject(null, "alpha", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> events.inject(SimulationEventType.STRONG_WIND, "alpha", 40)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> events.inject(SimulationEventType.STRONG_WIND, null, 201)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> events.inject(SimulationEventType.BATTERY_DROP, "alpha", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> events.inject(SimulationEventType.GPS_LOST, "alpha", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(world.snapshot()).isEqualTo(before);
        assertThat(missions.create(inspection("SECTOR_A", false)).id()).isEqualTo("mission-1");
    }

    @Test
    void concurrentExecutionCannotChargeTheSameMissionTwice() throws Exception {
        var mission = missions.create(inspection("SECTOR_B", true));
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<Boolean>> calls = IntStream.range(0, 20).mapToObj(i -> (Callable<Boolean>) () -> {
                try { return missions.execute(mission.id()).state() == MissionState.COMPLETED; }
                catch (SimulationConflictException expected) { return false; }
            }).toList();
            var results = executor.invokeAll(calls);
            int completed = 0;
            for (var result : results) if (result.get()) completed++;
            assertThat(completed).isEqualTo(1);
        }
        assertThat(drones.status("alpha").batteryPercent()).isEqualTo(59);
        assertThat(missions.list()).hasSize(1);
    }

    @Test
    void concurrentBatteryConsumptionDoesNotLoseUpdates() throws Exception {
        try (var executor = Executors.newFixedThreadPool(8)) {
            var calls = IntStream.range(0, 30).mapToObj(i -> (Callable<DroneStatus>) () -> drones.consumeBattery("alpha", 1)).toList();
            for (var result : executor.invokeAll(calls)) result.get();
        }
        assertThat(drones.status("alpha").batteryPercent()).isEqualTo(52);
    }

    @Test
    void validatesPhysicalValuesAndMissionStateContracts() {
        assertThatThrownBy(() -> new Position(Double.NaN, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Position(0, Double.POSITIVE_INFINITY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Weather(-1, false, Visibility.GOOD)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Weather(201, false, Visibility.GOOD)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> drones.status("alpha").at(Position.BASE, 101, DroneState.READY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Route(List.of(Position.BASE), 0, 0)).isInstanceOf(IllegalArgumentException.class);
        var route = routes.calculate("alpha", "SECTOR_A", false);
        assertThatThrownBy(() -> new Mission("x", inspection("SECTOR_A", false), MissionState.FAILED, route, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
