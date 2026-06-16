package es.schsebastian.foodrats.core.domain.analytics

import es.schsebastian.foodrats.core.domain.model.AccountId

/**
 * In-memory [AnalyticsPort] for tests — records every call so assertions can be made by value
 * equality (`assertEquals(AnalyticsEvent.MealPublished(...), recorder.events.single())`). Lives in
 * commonMain (not commonTest) by the same precedent as `FixedClock`, so feature modules can inject
 * it from their own tests without a test-fixtures dependency.
 *
 * It records UNCONDITIONALLY — it is the *real* tracker in a test, not the gated decorator. Test the
 * consent gate separately against `ConsentGatedAnalytics`.
 */
class RecordingAnalyticsTracker : AnalyticsPort {
    val events: MutableList<AnalyticsEvent> = mutableListOf()
    val userIds: MutableList<AccountId?> = mutableListOf()
    val userProperties: MutableList<Pair<UserProperty, String>> = mutableListOf()
    val consentApplications: MutableList<Boolean> = mutableListOf()
    var resetCount: Int = 0
        private set

    override fun track(event: AnalyticsEvent) { events += event }
    override fun setUserId(accountId: AccountId?) { userIds += accountId }
    override fun setUserProperty(property: UserProperty, value: String) { userProperties += (property to value) }
    override fun applyConsent(granted: Boolean) { consentApplications += granted }
    override fun resetData() { resetCount++ }

    /** Names of every recorded event, in order — convenient for terse assertions. */
    fun eventNames(): List<String> = events.map { it.name }

    fun clear() {
        events.clear()
        userIds.clear()
        userProperties.clear()
        consentApplications.clear()
        resetCount = 0
    }
}
