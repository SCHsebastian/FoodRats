package es.schsebastian.foodrats.feature.stats.domain.model

import kotlin.jvm.JvmInline

@JvmInline
value class Streak(val days: Int) {
    init { require(days >= 0) { "Streak days >= 0" } }
}
