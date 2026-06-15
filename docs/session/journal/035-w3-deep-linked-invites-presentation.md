# 035 · w3-deep-linked-invites-presentation

**Status:** done — **WAVE 3 COMPLETE**

**Summary (≤6 lines):**
- Crew invites upgraded to a shareable deep link + QR + in-app accept/preview flow. New `…/invite/{code}` route (self-sufficient join), classified `Protected` so pre-auth taps survive sign-in via intercept-then-resume. Pure-Kotlin Compose-Canvas QR encoder (`FrQrCode`, zero dependency).
- Files: `shared/.../navigation/{DeepLink,Route,NavGraph}.kt` (+DeepLinkParserTest); `core/domain/.../analytics/AnalyticsDimensions.kt` (+`JoinMethod.INVITE_LINK`); `core/designsystem/.../atoms/qr/*` + `FrQrCode.kt` (+test, +catalog); `feature/crew/.../{domain,data,presentation/invite,presentation/settings,i18n,di}` + fakes + 2 tests + verify; `human.md`.
- Decisions: zero-dep Canvas QR encoder; in-app rich preview built, web OG unfurl flagged USER step; reused `CrewInviteShared` analytics.
- Blockers: none. MANUAL: host assetlinks/AASA for the invite host + web unfurl (custom-scheme fallback already works) + on-device invite/QR smoke (human.md).

**Verify (quoted):**
```
:shared:testAndroidHostTest :feature:crew:testAndroidHostTest → BUILD SUCCESSFUL in 8s (DeepLinkParser 16/16, AcceptInvite 6/6, ResolveCrewByCode 3/3)
:core:domain + :core:designsystem testAndroidHostTest → BUILD SUCCESSFUL in 17s (QrCode 8/8)
:androidApp + :catalogApp assembleDebug + :shared iOS compile → all BUILD SUCCESSFUL
```

Report: `docs/session/reports/w3-deep-linked-invites-presentation.md`
