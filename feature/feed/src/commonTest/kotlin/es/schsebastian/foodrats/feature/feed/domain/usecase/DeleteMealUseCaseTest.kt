package es.schsebastian.foodrats.feature.feed.domain.usecase

import es.schsebastian.foodrats.core.domain.meal.MealDeleteError
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeleteMealUseCaseTest {
    private val crew = (CrewId.of("crew-1") as Result.Ok).value
    private val mealId = (MealId.of("meal-1") as Result.Ok).value

    @Test fun delegates_to_port_with_crew_and_meal_id() = runTest {
        val port = FakeMealDeletePort()
        val useCase = DeleteMealUseCase(port)

        val result = useCase(crew, mealId)

        assertTrue(result is Result.Ok)
        assertEquals(1, port.calls.size)
        val call = port.calls.first()
        assertEquals("crew-1", call.crewId)
        assertEquals("meal-1", call.mealId)
    }

    @Test fun surfaces_port_error() = runTest {
        val port = FakeMealDeletePort().apply {
            nextResult = Result.failure(MealDeleteError.NotAuthorOrOwner)
        }
        val useCase = DeleteMealUseCase(port)

        val result = useCase(crew, mealId)

        assertEquals(Result.failure(MealDeleteError.NotAuthorOrOwner), result)
    }
}
