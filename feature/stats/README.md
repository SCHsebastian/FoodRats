# :feature:stats

Bounded context: group analytics (streaks, top dish, variety scores).
Consumes MealReadPort from :core:domain. Server-side aggregation is deferred
(see spec §17); MVP derives stats client-side.
