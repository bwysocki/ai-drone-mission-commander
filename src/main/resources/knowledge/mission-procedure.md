# Mission preparation procedure

Identify the drone, mission type, target sector and whether return to BASE is
required. Resolve ambiguous identifiers before planning. The simulator knows
SECTOR_A, SECTOR_B and SECTOR_C; Bravo is a drone name, not a sector alias.

Read current drone status, weather and sector coordinates, then quote the route.
A created mission is queued as CREATED and does not move the drone. Execution
through the Simulation API recalculates the route and total energy requirement,
then produces COMPLETED or FAILED. A completed or failed mission cannot be
executed again. Keep planning, approval and execution distinct: the AI's read-only
tools cannot create or execute missions.
