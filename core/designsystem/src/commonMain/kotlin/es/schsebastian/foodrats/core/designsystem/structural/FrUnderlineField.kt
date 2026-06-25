package es.schsebastian.foodrats.core.designsystem.structural

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrFontFamily
import es.schsebastian.foodrats.core.designsystem.tokens.Motion

/**
 * The structural zero-box text field — **underline only**, no container, no fill, no border. Extreme
 * contrast: a 10sp uppercase wide-tracked label sits over an oversized 18sp SemiBold input that writes
 * straight onto the media floor. The sole edge is a 1.5dp bottom line that lights up to olive
 * (`colorScheme.primary`) on focus and recedes to a 45%-foreground hairline at rest.
 *
 * ## Why the editing buffer is a [androidx.compose.foundation.text.input.TextFieldState], not a String
 *
 * This is built on the **state-based** text API (`BasicTextField(state = …)`, stable since Compose
 * Foundation 1.8 / present on this project's CMP 1.11). It is deliberately NOT the legacy
 * `value: String/TextFieldValue` + `onValueChange` overload.
 *
 * The legacy overload is fully *controlled*: every keystroke must round-trip IME → `onValueChange` →
 * hoisted state → recompose → new value handed back, all within the next frame. A predictive soft
 * keyboard (Gboard, Samsung HoneyBoard) types via `setComposingText` — an in-progress *composing
 * region* — and when the hoisted state is an MVI `StateFlow` (async, conflated) the echo lands a frame
 * late, so the field's internal controller tears the composing region against a stale value. The
 * visible result is the classic "letters reorder / earlier text reappears / the cursor jumps to the
 * start" — i.e. text typing backwards. (Paste / `adb input text` use `commitText`, never compose,
 * which is why only the on-screen keyboard hit it.)
 *
 * With [androidx.compose.foundation.text.input.TextFieldState] the field's own buffer IS the single
 * source of truth and is mutated synchronously *inside* the IME transaction; nothing pushes an
 * external value back in on every frame, so the composing region is never torn. We keep the
 * `String value` + `onValueChange` public API (so no ViewModel has to change) by bridging: seed the
 * state once, flow edits UP via `snapshotFlow`, and adopt a genuine external change (async prefill,
 * clear-on-submit) only while the field is **unfocused** — never yanking the buffer out from under an
 * actively-typing user.
 *
 * @param obfuscate render as a password ([BasicSecureTextField] with reveal-last-typed) instead of a
 *   plain field — replaces the old `PasswordVisualTransformation`, and brings the platform's secure
 *   input handling (no autofill leak, no clipboard exposure). A show/hide toggle is just this flag
 *   driven from state (`obfuscate = !show`) — flip it and pass the eye as [trailingIcon].
 * @param trailingIcon optional affordance pinned to the end of the input line, vertically centred over
 *   the underline — e.g. a password show/hide eye toggle. The field stays domain-free: the caller owns
 *   the composable (icon, click, content description).
 * @param onMedia set when the field writes directly over a real photo / `dish*` media floor (e.g. the
 *   composer or the delete-gate), which stays dark-scrimmed in BOTH themes — forces white content so
 *   the input/label/placeholder stay legible in light mode instead of flipping to dark ink.
 * @param accessibilityLabel the field's accessible name read by TalkBack/VoiceOver (WCAG 4.1.2 / 3.3.2).
 *   Defaults to [label]; pass a natural-case string when [label] is a visual all-caps eyebrow so screen
 *   readers don't spell it out. The visible label is hidden from the a11y tree to avoid a double read.
 * @param errorMessage when [isError] is set, the message announced as the field's error state (WCAG
 *   3.3.1). Drives only semantics — the visible error cue is still the caller's banner/underline color.
 */
@Composable
fun FrUnderlineField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    isError: Boolean = false,
    onMedia: Boolean = false,
    obfuscate: Boolean = false,
    accessibilityLabel: String? = label,
    errorMessage: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val contentColor = if (onMedia) StructuralColors.onMedia else StructuralColors.foreground

    // The field's own buffer is the source of truth (see KDoc). Seeded ONCE from the initial value —
    // re-seeding on every `value` change is exactly the mid-typing rollback we are eliminating.
    val state = rememberTextFieldState(value)
    val latestValue by rememberUpdatedState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)

    // Flow user edits UP, read straight off the buffer (off the recomposition hot path). This only
    // ever moves the parent TOWARD the buffer, never the buffer toward a stale parent, so it cannot
    // roll typing back. The initial seed emission equals `value`, so it self-skips.
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { text ->
            if (text != latestValue) latestOnValueChange(text)
        }
    }
    // Flush the buffer UP the instant the field loses focus — BEFORE any Save/Send handler reads the
    // hoisted value. At blur the buffer is authoritative (the user just finished typing), so the final
    // segment — including any trailing punctuation — is guaranteed to have reached the parent.
    LaunchedEffect(focused) {
        if (!focused) {
            val text = state.text.toString()
            if (text != latestValue) latestOnValueChange(text)
        }
    }
    // Adopt a GENUINE external change (async prefill of a name/bio, clear-on-submit) — keyed on `value`
    // ALONE, never on `focused`. Keying the adoption on `focused` was the data-loss bug: the blur that a
    // Save/Send tap causes re-ran this effect and overwrote the just-typed buffer with the lagging
    // hoisted echo, dropping the trailing word and its punctuation. We only pull an external value in
    // while UNFOCUSED, so an actively-typing user is never interrupted; a blur no longer triggers it.
    LaunchedEffect(value) {
        if (!focused && value != state.text.toString()) {
            state.setTextAndPlaceCursorAtEnd(value)
        }
    }

    val underlineColor by animateColorAsState(
        targetValue = when {
            isError -> scheme.error
            focused -> scheme.primary
            else -> contentColor.copy(alpha = 0.45f)
        },
        animationSpec = tween(Motion.quick, easing = Motion.Standard),
        label = "underlineColor",
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            FrText(
                text = label,
                color = when {
                    isError -> scheme.error
                    onMedia -> contentColor.copy(alpha = 0.7f)
                    else -> scheme.onSurfaceVariant
                },
                style = TextStyle(
                    fontFamily = LocalFrFontFamily.current,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    letterSpacing = 0.18.em,
                ),
                // The label is mirrored into the field's accessible name below; hide the visual node from
                // the a11y tree so TalkBack doesn't read the label twice (WCAG 4.1.2).
                modifier = Modifier.clearAndSetSemantics {},
            )
        }

        val textStyle = TextStyle(
            color = contentColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = LocalFrFontFamily.current,
        )
        val cursorBrush = SolidColor(scheme.primary)
        // Shared chrome: placeholder (shown only while the buffer is empty) over the input, with the
        // 1.5dp underline beneath. Reads `state.text` so the placeholder toggles instantly, not a
        // frame behind a hoisted echo.
        val decorator = TextFieldDecorator { innerTextField ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        if (state.text.isEmpty() && placeholder != null) {
                            FrText(
                                text = placeholder,
                                color = contentColor.copy(alpha = 0.4f),
                                style = TextStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = LocalFrFontFamily.current,
                                ),
                            )
                        }
                        innerTextField()
                    }
                    if (trailingIcon != null) {
                        Spacer(Modifier.width(8.dp))
                        trailingIcon()
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.5.dp)
                        .background(underlineColor),
                )
            }
        }

        // Name the field for screen readers (WCAG 4.1.2 / 3.3.2) and surface its error state (3.3.1).
        // Without this the structural field reads as an unnamed "Edit box".
        val fieldModifier = Modifier
            .fillMaxWidth()
            .semantics {
                if (accessibilityLabel != null) contentDescription = accessibilityLabel
                if (isError && errorMessage != null) error(errorMessage)
            }

        if (obfuscate) {
            BasicSecureTextField(
                state = state,
                modifier = fieldModifier,
                enabled = enabled,
                textStyle = textStyle,
                keyboardOptions = keyboardOptions,
                cursorBrush = cursorBrush,
                interactionSource = interaction,
                textObfuscationMode = TextObfuscationMode.RevealLastTyped,
                decorator = decorator,
            )
        } else {
            BasicTextField(
                state = state,
                modifier = fieldModifier,
                enabled = enabled,
                textStyle = textStyle,
                lineLimits = if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.MultiLine(),
                keyboardOptions = keyboardOptions,
                cursorBrush = cursorBrush,
                interactionSource = interaction,
                decorator = decorator,
            )
        }
    }
}

@FrPreview
@Composable
private fun FrUnderlineFieldPreview() {
    FoodRatsTheme(darkTheme = true) {
        Box(Modifier.background(StructuralColors.stageFloor).padding(24.dp)) {
            var value by remember { mutableStateOf("Saturday Brunch") }
            FrUnderlineField(
                value = value,
                onValueChange = { value = it },
                label = "CREW NAME",
            )
        }
    }
}
