package es.schsebastian.foodrats.feature.crew.data.firebase

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.model.CrewCode
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CrewCodeGeneratorTest {
    @Test fun generates_a_valid_CrewCode() {
        val gen = CrewCodeGenerator(random = Random(seed = 42))
        repeat(1000) {
            val raw = gen.generate()
            assertEquals(6, raw.length)
            assertIs<Result.Ok<CrewCode>>(CrewCode.of(raw))
        }
    }
}
