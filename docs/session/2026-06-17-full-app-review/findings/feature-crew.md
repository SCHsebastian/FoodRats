# feature-crew code review

## Health summary

The `:feature:crew` module is well-structured and follows most DDD/Clean-Architecture rules: sealed error hierarchy, typed `Result<T,E>` throughout, no cross-feature imports, no raw `Color(0x…)`, all strings through `CrewStringKey`. The main issues are in the IO-dispatch boundary, which is inconsistently applied across the two-layer data stack, and a few minor correctness gaps.

---

## Findings

### crew-01 — ARCHITECTURE/HIGH: Duplicate IO boundary — repository and data source both call `withContext(dispatchers.io)`

`FirebaseCrewRepository.create`, `joinByCode`, `findByCode`, and `leave` each wrap their entire body in `withContext(dispatchers.io)`. Those methods then call into `CrewFirestoreDataSource` methods (`createCrew`, `joinByCode`, `fetchByCode`) that do NOT themselves have `withContext`. Correct so far.

BUT `removeMember` in the **data source** (`CrewFirestoreDataSource:144`) wraps itself in `withContext(dispatchers.io)`, and then `FirebaseCrewRepository.removeMember` calls it WITHOUT its own `withContext`. The `renameCrew`, `deleteCrew`, and `setBlindVoting` data-source methods also each have `withContext(dispatchers.io)` — and the repository methods for those three call `dataSource.fetchOnce()` (no dispatcher) followed by the data-source write (which has `withContext`). So:

- `removeMember`: IO boundary is in the wrong layer (data source only; repo has none).
- `renameCrew` / `deleteCrew` / `setBlindVoting`: IO boundary is only on the data-source write; the `fetchOnce` read before it runs on whatever the caller's dispatcher is (typically `Main` from a ViewModel `viewModelScope`).

The rule is: **one `withContext(dispatchers.io)` per public repository method; zero in the data source**.

**Fix:** Remove `withContext(dispatchers.io)` from all `CrewFirestoreDataSource` methods. Add a single `withContext(dispatchers.io)` wrapping the full body of `FirebaseCrewRepository.removeMember`, `renameCrew`, `deleteCrew`, and `setBlindVoting`.

---

### crew-02 — CORRECTNESS/MEDIUM: `renameCrew`, `deleteCrew`, `setBlindVoting` have no IO dispatcher — potential `NetworkOnMainThreadException` on Android

As a direct consequence of crew-01: these three `FirebaseCrewRepository` methods call `dataSource.fetchOnce()` (which is a Firestore network call via `observeCrew().first()`) and then the data-source write, all without any `withContext`. The `fetchOnce` path specifically does a `.first()` on a `SharedFlow` backed by `obsScope` (`dispatchers.default`), which avoids `NetworkOnMainThreadException` but still runs mapping on Default rather than IO. The subsequent data-source writes do switch to IO internally, but the repository's contract is to own this boundary end-to-end.

**Fix:** Same as crew-01 — wrap the entire repository method bodies in `withContext(dispatchers.io)` and strip `withContext` from the data source.

---

### crew-03 — CORRECTNESS/MEDIUM: `RemoveMemberUseCase` reads `repository.observeCrew().first()` — use case performing an IO-adjacent operation

`RemoveMemberUseCase:31` calls `repository.observeCrew(crewId).first()` to read the current crew state for pre-flight authorization checks (is requester the owner? is target a member?). Use cases must not contain IO boundaries or block waiting for I/O (rule: "Zero `withContext` in use cases"). Calling `.first()` on a live Firestore-backed flow from a use case is an implicit IO suspension in the wrong layer. Additionally, `FirebaseCrewRepository.removeMember` also does `dataSource.fetchOnce()` for the same purpose (another TOCTOU read-before-write) — so the ownership check is duplicated between `RemoveMemberUseCase` and `FirebaseCrewRepository.removeMember`, and the use case read is redundant if the repository read is authoritative.

**Fix:** Remove the ownership/membership pre-flight from `RemoveMemberUseCase`; leave it solely in `FirebaseCrewRepository.removeMember` (which already does `dataSource.fetchOnce()` for this purpose). The use case becomes a thin delegation: resolve session → `repository.removeMember(crewId, requestedBy, target)`. The repository already handles `NotOwner`, `CannotRemoveSelf`, and `MemberNotFound`.

---

### crew-04 — ARCHITECTURE/LOW: `CrewFirestoreDataSource` is `public`, not `internal`

`class CrewFirestoreDataSource(…)` (line 29) has default (`public`) visibility. It is an infrastructure adapter that should be invisible outside `feature:crew`. Being `public` means other modules that happen to add `feature:crew` as a dep could reference it directly, bypassing the `CrewDataSource` port and the `CrewRepository` abstraction boundary.

**Fix:** Change to `internal class CrewFirestoreDataSource`.

autoFixable: true, risk: low.

---

### crew-05 — CORRECTNESS/LOW: `observeMyCrews` silently drops malformed crew DTOs without surfacing an error

`FirebaseCrewRepository.observeMyCrews:112`:
```kotlin
Result.success(dtos.mapNotNull { (it.toDomain() as? Result.Ok)?.value })
```
If a crew doc fails `toDomain()` (bad Firestore data, e.g. missing `code`), it is silently dropped from the list. The result is `Ok([partial crew list])` rather than any signal to the UI. For a user who has exactly one crew and that crew's doc is malformed, the picker will render an empty list with no error — they appear crew-less and cannot switch.

Pre-launch and small user base, the risk is low, but silent data loss is a correctness issue. The behavior is intentional only for already-logged-in multi-crew flows where one bad crew should not block the others; but it currently also masks initialization failures.

**Fix:** Log the parse failure via `FrLog.w`. Consider surfacing `CrewError.Backend.Unavailable` if ALL crews fail to parse (total failure vs partial). At minimum log each dropped DTO so it's visible in crash reporting.

autoFixable: false, risk: low.

---

### crew-06 — CORRECTNESS/LOW: `SectionEyebrow` calls `.uppercase()` on a translated string without a `Locale`

`CrewSettingsScreen:527`:
```kotlin
text = text.uppercase()
```
Called with strings that come from `resolve(CrewStringKey.*)`. Using `uppercase()` without a `Locale` uses the default locale, which on Turkish (and some other locales) will corrupt ASCII letters (e.g. `"i"` → `"İ"` instead of `"I"`). The string keys used here are English section headers, so the practical impact is low. However, the fix is trivial.

**Fix:** Use `text.uppercase(Locale.ROOT)` (or pass the `Locale` through `resolve()`). In KMP `commonMain`, use `kotlin.text.uppercase(Locale.current)` or pre-uppercase in the string resource.

autoFixable: true, risk: low.
