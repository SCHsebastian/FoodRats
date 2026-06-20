package es.schsebastian.foodrats.core.domain.legal

import es.schsebastian.foodrats.core.domain.preferences.CURRENT_EULA_VERSION
import es.schsebastian.foodrats.core.domain.preferences.needsEulaAcceptance
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Locks the EULA gate predicate (UGC compliance §6): never-accepted and stale-version states require
 * (re-)acceptance; a current-version acceptance is settled.
 */
class NeedsEulaAcceptanceTest {

    private val current = CURRENT_EULA_VERSION

    @Test
    fun never_accepted_needs_acceptance() {
        assertTrue(needsEulaAcceptance(current = current, accepted = null))
    }

    @Test
    fun stale_acceptance_needs_reacceptance() {
        assertTrue(needsEulaAcceptance(current = current + 1, accepted = current))
    }

    @Test
    fun current_acceptance_is_settled() {
        assertFalse(needsEulaAcceptance(current = current, accepted = current))
    }

    @Test
    fun newer_than_current_is_settled() {
        assertFalse(needsEulaAcceptance(current = current, accepted = current + 1))
    }
}
