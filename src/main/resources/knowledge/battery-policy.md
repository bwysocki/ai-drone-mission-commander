# Battery readiness policy

This is guidance for the fictional drone simulator, not real aircraft certification.
Before dispatch, read the drone's current battery percentage and calculate the
complete route, including the return leg when requested. Compare available energy
with movement cost plus mission activity cost. Inspection consumes three additional
battery percentage points; patrol consumes two. A route estimate covers movement
only and must be recalculated after weather or position changes.

If the battery is insufficient for the complete operation, do not dispatch.
Recharge or shorten the proposed mission and obtain a fresh estimate. Do not use
a previous conversation's battery reading as current telemetry. The simulator
enforces complete-operation energy availability in Java; additional reserve
policies belong to the later MissionSafetyValidator milestone. Retrieving this
document does not authorize a flight.
