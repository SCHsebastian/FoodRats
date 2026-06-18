# Minotaur mode — workflow-ready implementation prompt (v3 final)

Hidden cosmetic easter egg: a 3-finger long-press (≥3 fingers held ~1.5s, anywhere) toggles
"Minotaur mode", which grows a furry edge on `Fr*` atoms app-wide. Off by default. Entirely
inside `:core:designsystem` (no feature/data changes; ephemeral by default — resets on relaunch).

7 independent single-file tasks (A–G) + one verify task (V). A–G have NO ordering dependency
(distinct files; the final compile in V resolves cross-references) → fan out in parallel with
Sonnet agents.

---

## PREAMBLE — applies to EVERY agent (A–G)

- Touch EXACTLY the one file named in your task. Do not open, read, or search any other file.
- For an EDIT task: `Read` your file ONCE, then apply the exact edit(s) given. The old-strings
  below are verbatim — match them exactly.
- For a CREATE task: `Write` the full file body given. Do not read anything.
- Do not run builds, tests, or git. Do not explain your work. Your entire final message is the
  literal string `DONE <relative/path>`. Spend tokens only on the edit itself.
- Package for all designsystem files is `es.schsebastian.foodrats.core.designsystem.*` as shown.

Repo root: `/Users/sebastiancardonahenao/AndroidStudioProjects/FoodRats`

---

## TASK A — CREATE `core/designsystem/src/commonMain/kotlin/es/schsebastian/foodrats/core/designsystem/theme/Fur.kt`

`Write` this exact content:

```kotlin
package es.schsebastian.foodrats.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap

/** When true, Fr* atoms grow a furry edge (the hidden "Minotaur mode" easter egg). */
val LocalMinotaurMode = staticCompositionLocalOf { false }

/**
 * Draws a ring of short hair-like strokes just outside the content bounds when [enabled].
 * Strokes are seeded deterministically by index, so the fur is stable across recompositions
 * (never read a random source in a draw lambda). Color is [FrSemanticColors.fur].
 */
@Composable
fun Modifier.fur(enabled: Boolean): Modifier {
    if (!enabled) return this
    val furColor = LocalFrSemanticColors.current.fur
    return this.drawWithContent {
        drawContent()
        val step = 9f
        val base = 7f
        val w = size.width
        val h = size.height
        var i = 0
        var x = 0f
        while (x <= w) {
            val jitter = ((i * 53) % 7) - 3
            val len = base + ((i * 31) % 5)
            drawLine(furColor, Offset(x, 0f), Offset(x + jitter, -len), strokeWidth = 2.5f, cap = StrokeCap.Round)
            drawLine(furColor, Offset(x, h), Offset(x + jitter, h + len), strokeWidth = 2.5f, cap = StrokeCap.Round)
            x += step; i++
        }
        var y = 0f
        while (y <= h) {
            val jitter = ((i * 53) % 7) - 3
            val len = base + ((i * 31) % 5)
            drawLine(furColor, Offset(0f, y), Offset(-len, y + jitter), strokeWidth = 2.5f, cap = StrokeCap.Round)
            drawLine(furColor, Offset(w, y), Offset(w + len, y + jitter), strokeWidth = 2.5f, cap = StrokeCap.Round)
            y += step; i++
        }
    }
}
```

---

## TASK B — CREATE `core/designsystem/src/commonMain/kotlin/es/schsebastian/foodrats/core/designsystem/theme/MinotaurUnlock.kt`

`Write` this exact content:

```kotlin
package es.schsebastian.foodrats.core.designsystem.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Secret unlock for Minotaur mode: hold THREE fingers down together for ~1.5s anywhere.
 * Observes pointer events on the Initial pass and never consumes them, so normal taps,
 * scrolls and clicks still reach children. Dropping below three fingers cancels the hold.
 */
fun Modifier.minotaurUnlockGesture(onUnlock: () -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 3) {
                val released = withTimeoutOrNull(1500L) {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        if (e.changes.count { it.pressed } < 3) return@withTimeoutOrNull true
                    }
                    @Suppress("UNREACHABLE_CODE") true
                }
                if (released == null) {
                    onUnlock()
                    do {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                    } while (e.changes.any { it.pressed })
                }
            }
        }
    }
}
```

---

## TASK C — EDIT `core/designsystem/src/commonMain/kotlin/es/schsebastian/foodrats/core/designsystem/theme/SemanticColors.kt`

Three edits.

**C1** — add the role to the data class:

OLD:
```kotlin
    val streakHot: Color,
    val onStreakHot: Color,
    /** Black scrim for the protection gradient under white-on-photo text. Theme-independent. */
```
NEW:
```kotlin
    val streakHot: Color,
    val onStreakHot: Color,
    /** Minotaur-mode fur tint (hidden cosmetic easter egg). */
    val fur: Color,
    val onFur: Color,
    /** Black scrim for the protection gradient under white-on-photo text. Theme-independent. */
```

**C2** — light instance:

OLD:
```kotlin
    streakHot     = Color(0xFFD45A14),   // forge orange
    onStreakHot   = Color(0xFFFFFFFF),
    scrim         = Color(0xFF000000),
```
NEW:
```kotlin
    streakHot     = Color(0xFFD45A14),   // forge orange
    onStreakHot   = Color(0xFFFFFFFF),
    fur           = Color(0xFF6E4B2A),   // minotaur brown
    onFur         = Color(0xFFE8D9C0),   // cream
    scrim         = Color(0xFF000000),
```

**C3** — dark instance:

OLD:
```kotlin
    streakHot     = Color(0xFFFB923C),   // forge ember
    onStreakHot   = Color(0xFF3A1A00),
    scrim         = Color(0xFF000000),
```
NEW:
```kotlin
    streakHot     = Color(0xFFFB923C),   // forge ember
    onStreakHot   = Color(0xFF3A1A00),
    fur           = Color(0xFF8A6238),   // lighter minotaur brown
    onFur         = Color(0xFF2A1B0C),
    scrim         = Color(0xFF000000),
```

---

## TASK D — REWRITE `core/designsystem/src/commonMain/kotlin/es/schsebastian/foodrats/core/designsystem/theme/FoodRatsTheme.kt`

`Read` the file once (Write of an existing file requires it), then `Write` this exact content:

```kotlin
package es.schsebastian.foodrats.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun FoodRatsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    minotaur: Boolean = false,
    onMinotaurToggle: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) FoodRatsDarkColors else FoodRatsLightColors
    val semantic = if (darkTheme) FoodRatsDarkSemanticColors else FoodRatsLightSemanticColors
    val fontFamily = rememberFrFontFamily()
    val typography = rememberFoodRatsTypography(fontFamily)
    var minotaurOn by rememberSaveable(minotaur) { mutableStateOf(minotaur) }
    CompositionLocalProvider(
        LocalFrSemanticColors provides semantic,
        LocalFrFontFamily provides fontFamily,
        LocalMinotaurMode provides minotaurOn,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = typography,
            shapes = FoodRatsShapes,
        ) {
            Box(
                modifier = Modifier.minotaurUnlockGesture {
                    minotaurOn = !minotaurOn
                    onMinotaurToggle(minotaurOn)
                },
            ) {
                content()
            }
        }
    }
}
```

---

## TASK E — EDIT `core/designsystem/src/commonMain/kotlin/es/schsebastian/foodrats/core/designsystem/atoms/FrCard.kt`

Two edits.

**E1** — imports. OLD:
```kotlin
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
```
NEW:
```kotlin
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import es.schsebastian.foodrats.core.designsystem.theme.LocalMinotaurMode
import es.schsebastian.foodrats.core.designsystem.theme.fur
```

**E2** — apply fur. OLD:
```kotlin
    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(clickModifier),
```
NEW:
```kotlin
    val furModifier = Modifier.fur(LocalMinotaurMode.current)
    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(furModifier)
            .then(clickModifier),
```

---

## TASK F — EDIT `core/designsystem/src/commonMain/kotlin/es/schsebastian/foodrats/core/designsystem/atoms/FrButton.kt`

Two edits.

**F1** — imports. OLD:
```kotlin
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
```
NEW:
```kotlin
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.theme.LocalMinotaurMode
import es.schsebastian.foodrats.core.designsystem.theme.fur
```

**F2** — apply fur. OLD:
```kotlin
    val scaled = modifier.pressScale(interactionSource)
```
NEW:
```kotlin
    val scaled = modifier.pressScale(interactionSource).fur(LocalMinotaurMode.current)
```

---

## TASK G — EDIT `catalogApp/src/main/kotlin/es/schsebastian/foodrats/catalog/stories/AtomStories.kt`

Three edits.

**G1** — imports. OLD:
```kotlin
import androidx.compose.runtime.Composable
```
NEW:
```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
```
Then add this import anywhere in the import block (place it next to the other designsystem.theme imports):
```kotlin
import es.schsebastian.foodrats.core.designsystem.theme.LocalMinotaurMode
```

**G2** — register the entry. OLD:
```kotlin
    CatalogEntry("atom.card",          CatalogGroup.ATOMS, "FrCard",          "Rounded surface container — static or clickable with press lift") { CardStory() },
```
NEW:
```kotlin
    CatalogEntry("atom.card",          CatalogGroup.ATOMS, "FrCard",          "Rounded surface container — static or clickable with press lift") { CardStory() },
    CatalogEntry("atom.card.fur",      CatalogGroup.ATOMS, "FrCard (Minotaur)", "Hidden Minotaur mode — furry edge via LocalMinotaurMode") { MinotaurCardStory() },
```

**G3** — append this composable at the END of the file (after the last existing story function):
```kotlin
@Composable
private fun MinotaurCardStory() {
    CompositionLocalProvider(LocalMinotaurMode provides true) {
        CatalogScene(label = "Minotaur mode ON — furry edge") {
            FrCard(modifier = Modifier.fillMaxWidth()) {
                FrText("Furry card", style = MaterialTheme.typography.titleMedium)
                FrText("3-finger long-press unlocks this app-wide", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
```

---

## TASK V — VERIFY (run AFTER A–G complete; one agent, Bash only)

Run from repo root, in order; quote the last ~8 lines of each. Fix nothing — report pass/fail.
```
./gradlew :core:designsystem:testAndroidHostTest
./gradlew :androidApp:assembleDebug
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```
Catalog device check is manual (not in this gate):
`./gradlew :catalogApp:installDebug` → open entry `atom.card.fur` (fur visible) and do the
3-finger long-press in the running app to confirm the global toggle.

---

## Optional add-on (NOT in the parallel fan-out) — persist the unlock

Default is ephemeral. To survive relaunch, after A–G: add a `StoreKey<Boolean> minotaurMode`
(mirror an existing boolean in `core/data/.../datastore/`), then in the shared root call
`FoodRatsTheme(minotaur = flagState, onMinotaurToggle = { scope.launch { prefs.set(key, it) } })`.
This is the only change that reaches outside `:core:designsystem`.
