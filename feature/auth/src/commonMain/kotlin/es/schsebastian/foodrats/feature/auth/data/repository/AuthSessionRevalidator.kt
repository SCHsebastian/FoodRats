package es.schsebastian.foodrats.feature.auth.data.repository

import es.schsebastian.foodrats.core.domain.session.SessionRevalidator
import es.schsebastian.foodrats.feature.auth.data.firebase.FirebaseAuthDataSource

/**
 * [SessionRevalidator] over Firebase Auth. A thin adapter so the vendor SDK stays in `data/firebase/`
 * and the domain port stays vendor-free — the revoked-vs-transient decision and the sign-out live in
 * [FirebaseAuthDataSource.revalidateSession].
 */
internal class AuthSessionRevalidator(
    private val firebase: FirebaseAuthDataSource,
) : SessionRevalidator {
    override suspend fun revalidate() = firebase.revalidateSession()
}
