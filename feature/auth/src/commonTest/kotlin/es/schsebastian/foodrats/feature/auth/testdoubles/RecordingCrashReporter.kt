package es.schsebastian.foodrats.feature.auth.testdoubles

import es.schsebastian.foodrats.core.domain.telemetry.CrashReporter

class RecordingCrashReporter : CrashReporter {
    data class NonFatal(val throwable: Throwable, val tag: String?)

    val nonFatals: MutableList<NonFatal> = mutableListOf()
    val logs: MutableList<String> = mutableListOf()

    override fun recordNonFatal(throwable: Throwable, tag: String?) {
        nonFatals += NonFatal(throwable, tag)
    }

    override fun log(message: String) {
        logs += message
    }
}
