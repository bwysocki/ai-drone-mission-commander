# Milestone 4 — Drone World Simulator

## Problem

An AI mission commander needs an environment it can inspect and affect. Real drone
hardware would make the learning project harder to run and test, so this milestone
introduces a deterministic Java world. Identical fixtures and operations produce
identical outcomes without model calls, clocks, random IDs or background workers.

The simulator is the foundation for later tool calling. No Spring AI tool annotations
or automatic connection from intent extraction to execution are introduced here.

## State and services

`DroneWorld` owns drone status, sectors, weather, missions and injected-event history.
Public reads return immutable records and collection snapshots. Its initial drones
are Alpha (82%, BASE, READY), Bravo (41%, SECTOR_A, IDLE) and Charlie (67%, BASE, READY).
Weather starts at 12 km/h wind, no rain and GOOD visibility.

`Drone` is identity; `DroneStatus` holds changing battery, position, activity state
and fault flags. `Position` stores flat-map coordinates in kilometres. `Mission`,
`Route` and `InspectionResult` capture execution state and deterministic output.
The existing `MissionType` supports INSPECTION and PATROL.

The services have small responsibilities:

| Service | Responsibility |
|---|---|
| DroneWorld | State ownership, initial fixtures and reset |
| DroneSimulationService | Status reads, movement, battery consumption and return home |
| MissionSimulationService | Mission creation, current-state checks and execution |
| RouteService | Straight-line routes and movement energy estimates |
| WeatherService | Current weather |
| SectorService | Sector reads and fixed inspection findings |
| SimulationEventService | Fault injection and sequenced history |

Constructors can be called directly in ordinary Java tests. Spring's component
annotations only provide application wiring; the simulator has no AI dependency.

## Deterministic routes and energy

The map has BASE `(0,0)`, SECTOR_A `(2,0)`, SECTOR_B `(0,3)` and SECTOR_C `(4,3)`.
Each flight leg uses Euclidean distance and consumes percentage points:

```java
double cost = Math.ceil(distance * 2 * (1 + weather.windKmh() / 20.0));
```

The route total sums individually rounded legs. An inspection costs another 3
points, and a patrol costs 2. Thus Alpha inspecting SECTOR_B and returning home in
the initial weather consumes `10 + 3 + 10`, leaving 59% battery.

This is a reproducible energy model for examples, not a physical model of a drone.
Rain and visibility are exposed as state but do not currently affect consumption.
Inspection results are fixtures: SECTOR_C contains a simulated vehicle; the other
sectors have no anomalies. Patrols do not produce inspection results.

## Mission lifecycle and consistency

A mission is first CREATED with a route quote. Creation does not reserve the drone
or consume battery. At execution the service recalculates the route using the
current weather and position, then checks the complete budget, including return.

```text
CREATED
  -> validate current technical state and total energy
  -> RUNNING -> COMPLETED
  -> FAILED if a precondition cannot be met
```

GPS loss, a motor warning, communication loss or an insufficient total battery
budget prevent execution. A failed precondition records the failure without moving
or charging the drone. A completed or failed mission cannot be executed again.
Create a new mission to retry after resetting or correcting the world.

Services synchronize complete operations on the same `DroneWorld` instance:

```java
synchronized (world) {
    var mission = world.mission(id);
    // Check transition, recompute route, validate, move, consume and save result.
}
```

This makes state changes atomic relative to other service operations. Concurrent
attempts to execute the same mission charge the battery once. A read obtains a
consistent snapshot; it cannot mutate stored records or see half a mission.
RUNNING and IN_MISSION are internal transition states because execution is instant
and synchronous. Events apply before or after a mission, not mid-flight. Time-step
simulation and fault recovery during flight are not implemented in this milestone.

## Events

The simulator supports BATTERY_DROP, GPS_DEGRADED, GPS_LOST, STRONG_WIND,
MOTOR_WARNING and COMMUNICATION_LOST. Drone events require a known drone ID;
STRONG_WIND changes global weather and must not specify a drone.

Battery drop defaults to 20 points and clamps at zero. Its history entry stores the
actual drop. Strong wind defaults to 45 km/h. Degrading an already lost GPS signal
does not recover it. Invalid events are rejected before changing state or history.
Event sequence numbers and mission IDs restart on reset, allowing repeatable scenarios.

## API and standalone mode

`SimulationController` exposes state reads, route quotes, mission creation and
execution, movement, return home, events and reset under `/api/simulation`.
Swagger's Simulation group can exercise these operations. They are explicit simulator
commands, separate from the existing AI intent-extraction endpoint.

Run without an API key:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=simulator
```

`application-simulator.properties` disables model autoconfiguration, and the
`simulator` profile excludes AI services and controllers. Normal startup retains
both AI and simulation endpoints. All world state is in memory and lost on restart.

Input errors return 400, unknown objects 404 and operation conflicts 409. Execution
returns a mission with state COMPLETED or FAILED under HTTP 200; clients must inspect
that state. Full curl examples and exact fixture values are in the README.

One surprising test failure involved JSON enum coercion: numeric `0` was accepted
as the first event type. `ApiJsonConfiguration` now rejects numeric enum ordinals,
null primitive values, scalar coercion and duplicate JSON keys in API input. Required
mission fields are enforced through the existing record contract. This prevents
malformed input from silently becoming a valid mutation command.

## Verification and lessons

`DroneWorldTest` uses real Java services without Spring or mocks. It covers initial
state, immutable snapshots, routes, leg rounding, activity costs, inspections,
patrols, return home, all event types, reset, failures and concurrent state updates.

`SimulationControllerTest` boots the simulator profile with blank credentials and
real services. It checks API scenarios, Swagger, invalid input and the absence of
AI model/service beans. Existing AI tests continue using their localhost provider.

The main lesson is that reproducible state and atomic operations make later agent
behavior testable. A route quote is not an execution guarantee: current state must
be checked again before applying changes. These technical checks enforce simulator
invariants; battery reserves, mission weather policy and authorization still belong
to the later deterministic safety validator. No real drone hardware is involved.
