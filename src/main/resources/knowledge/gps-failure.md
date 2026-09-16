# GPS and navigation failure procedure

If positioning is lost, stop issuing movement commands and inspect the current
GPS flag, position, communication availability and mission state. Do not assume
a drone can navigate home without position information. The simulator rejects
movement when GPS is LOST.

A degraded GPS event must not be interpreted as recovery from a lost fix.
The simulator does not automatically repair LOST positioning by injecting
GPS_DEGRADED. Report the navigation fault and require operator intervention.
A world reset restores the demo fixtures but is not a real aircraft recovery
procedure. Keep historical alerts separate from the current GPS status.
