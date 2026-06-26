package es.schsebastian.foodrats.core.domain

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertFalse
import kotlin.test.Test

class KonsistRulesTest {
    @Test fun core_domain_does_not_import_android_or_firebase() {
        scope
            .files
            .withPackage("es.schsebastian.foodrats.core.domain..")
            .assertFalse { file ->
                file.imports.any { imp ->
                    imp.name.startsWith("android.") ||
                    imp.name.startsWith("androidx.") ||
                    imp.name.startsWith("com.google.firebase") ||
                    imp.name.startsWith("dev.gitlive.firebase") ||
                    imp.name.startsWith("org.jetbrains.compose") ||
                    imp.name.startsWith("androidx.compose") ||
                    imp.name.startsWith("app.cash.sqldelight")
                }
            }
    }

    private companion object {
        /**
         * Module scope built ONCE and reused across this class's assertion(s). `scopeFromModule`
         * re-parses `:core:domain` on every call; `lazy` defers the parse to first use.
         */
        val scope by lazy { Konsist.scopeFromModule("core/domain") }
    }
}
