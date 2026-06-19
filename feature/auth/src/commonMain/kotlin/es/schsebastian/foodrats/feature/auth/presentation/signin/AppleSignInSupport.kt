package es.schsebastian.foodrats.feature.auth.presentation.signin

/**
 * Whether native Sign-in-with-Apple is offered on this platform.
 *
 * `true` on iOS (real `ASAuthorization` → `OAuthProvider("apple.com")` flow); `false` on Android,
 * where GitLive firebase-auth 2.1.0 wraps no web/redirect OAuth, so the button is hidden rather than
 * shown as a dead "coming soon". Flip the Android actual to `true` once `AppleAuthClient.android`
 * implements the web-OAuth redirect (see `feature/auth/CLAUDE.md`).
 */
internal expect val platformSupportsAppleSignIn: Boolean
