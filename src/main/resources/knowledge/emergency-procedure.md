# Fault escalation procedure

When a motor warning, communication loss or navigation fault is reported, inspect
the affected drone's current status and recent event history. Do not issue new
movement commands while the relevant technical fault prevents operation. Escalate
the fault to the operator and state which current readings are unavailable.

The simulator blocks movement for motor warnings, unavailable communication and
lost GPS. Do not claim that a mission was safely recovered merely because an alert
was summarized. Avoid recommending an automatic return home without checking
navigation and energy availability. This simulated procedure provides no real
aircraft emergency controls; technical enforcement remains in Java.
