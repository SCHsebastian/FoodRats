package es.schsebastian.foodrats.core.domain.telemetry

interface CrashReporter {
    fun recordNonFatal(throwable: Throwable, tag: String? = null)
    fun log(message: String)
}

object NoopCrashReporter : CrashReporter {
    override fun recordNonFatal(throwable: Throwable, tag: String?) = Unit
    override fun log(message: String) = Unit
}
