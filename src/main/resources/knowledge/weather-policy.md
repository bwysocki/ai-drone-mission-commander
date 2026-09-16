# Weather readiness policy

Use the current simulated weather before estimating a mission. Read wind speed,
rain and visibility; do not infer conditions from an earlier answer. In this
simulator, stronger wind increases movement energy consumption. Recalculate the
route when weather changes, including the return leg.

Poor visibility and rain require operator review of whether useful inspection
observations can be obtained. A strong-wind event is a reason to reconsider the
plan, not proof that the old route quote remains adequate. This reference does
not define a real-world wind limit. Numeric mission-policy thresholds will be
implemented in Java in a later safety milestone. Search results are guidance,
not deterministic mission approval.
