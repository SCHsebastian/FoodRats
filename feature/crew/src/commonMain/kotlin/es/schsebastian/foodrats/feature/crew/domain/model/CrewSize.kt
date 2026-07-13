package es.schsebastian.foodrats.feature.crew.domain.model

/** Crew membership-count invariant per spec §2.1. */
object CrewSize {
    const val MIN = 3
    const val MAX = 15
    fun canAdd(currentCount: Int): Boolean = currentCount < MAX
    /** Used at create-time when the founder is the only member; min is asymptotic, not enforced at t=0. */
    fun isWithinBoundsAtPublishTime(count: Int): Boolean = count in MIN..MAX
}
