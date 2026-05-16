package es.schsebastian.foodrats.feature.crew.data.firebase

import es.schsebastian.foodrats.feature.crew.domain.model.CrewCode
import kotlin.random.Random

class CrewCodeGenerator(private val random: Random = Random.Default) {
    fun generate(): String = buildString(CrewCode.LENGTH) {
        repeat(CrewCode.LENGTH) { append(CrewCode.ALPHABET[random.nextInt(CrewCode.ALPHABET.length)]) }
    }
}
