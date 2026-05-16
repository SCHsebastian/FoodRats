# :feature:feed

Bounded context: a crew's day-window of meals to view. Consumes `MealReadPort`
from `:core:domain` (bound by `:feature:meal`'s `mealModule`). No direct Gradle
dependency on `:feature:meal`.

This module is currently a scaffold. Fill in following the `:feature:meal` template.
