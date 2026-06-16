# 004 · w0-consent-ui-domain

**Status:** done

**Summary (≤6 lines):**
- Verified the consent domain/port + data layer already shipped by the analytics base; filled one gap.
- Files: `core/domain/.../analytics/Consent.kt` (added `needsDecision`), `core/domain/.../analytics/ConsentDecisionTest.kt` (new), `core/data/.../preferences/ConsentRepositoryTest.kt` (new).
- Decisions: `ConsentPort` (grant/deny/revoke + `decision` Flow), `ConsentDecision`, `ConsentRepository`, Koin binding, version handling ALL pre-existing — no rebuild. Added `needsDecision` extension as the single trusted "show consent screen?" signal (current-version `Denied` = settled, no re-prompt) + missing tests.
- Blockers: none.

**Verify (quoted):**
```
> Task :core:domain:testAndroidHostTest
BUILD SUCCESSFUL in 20s
(new classes 5/5 + 4/4, 0 fail; Konsist green)
```

Report: `docs/session/reports/w0-consent-ui-domain.md` · Handoff: `docs/session/handoffs/w0-consent-ui-domain.md`
