package es.schsebastian.foodrats.feature.crew.domain.error

import es.schsebastian.foodrats.core.domain.session.SessionError

/**
 * Maps a [SessionError] (raised when a crew use case can't resolve the signed-in account) to the
 * crew context's typed [CrewError]. Replaces the prior blanket collapse of *every* session failure
 * into [CrewError.Backend.Unavailable], which rendered an expired/absent session as a generic
 * "something went wrong" with no path back to sign-in.
 *
 *  - [SessionError.NotSignedIn]        → [CrewError.Session.NotSignedIn] (route to sign-in)
 *  - [SessionError.TokenExpired]       → [CrewError.Session.Expired] (re-auth required)
 *  - [SessionError.AccountDisabled]    → [CrewError.Session.Expired] (re-auth required)
 *  - [SessionError.ProviderUnavailable]→ [CrewError.Backend.Unavailable] (genuinely a backend/outage)
 */
fun SessionError.toCrewError(): CrewError = when (this) {
    SessionError.NotSignedIn -> CrewError.Session.NotSignedIn
    SessionError.TokenExpired -> CrewError.Session.Expired
    SessionError.AccountDisabled -> CrewError.Session.Expired
    SessionError.ProviderUnavailable -> CrewError.Backend.Unavailable
}
