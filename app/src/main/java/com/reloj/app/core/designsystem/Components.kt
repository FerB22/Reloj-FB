package com.reloj.app.core.designsystem

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reloj.app.R

@Composable
fun PrimaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconRes: Int = R.drawable.ic_add,
) {
    val colors = AppTheme.colors
    val view = LocalView.current
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            },
        shape = RoundedCornerShape(14.dp),
        color = colors.accent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = text,
                style = AppTypography.label,
                color = colors.onAccent,
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    iconRes: Int,
    title: String,
    description: String,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline),
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(36.dp),
            )
            Text(
                text = title,
                style = AppTypography.title,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = description,
                style = AppTypography.body,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            PrimaryPillButton(
                text = actionText,
                onClick = onAction,
            )
        }
    }
}

@Composable
fun NumberStepper(
    title: String,
    value: Int,
    maxValue: Int,
    onValueChange: (Int) -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    val colors = AppTheme.colors
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val view = LocalView.current

    var isFocused by remember { mutableStateOf(false) }
    var fieldValue by remember {
        val initial = value.toString().padStart(2, '0')
        mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
    }

    LaunchedEffect(value, isFocused) {
        if (!isFocused) {
            val formatted = value.toString().padStart(2, '0')
            if (fieldValue.text != formatted) {
                fieldValue = TextFieldValue(formatted, TextRange(formatted.length))
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = AppTypography.label,
            color = colors.textSecondary,
        )

        StepperButton(
            text = "+",
            onClick = {
                focusManager.clearFocus()
                onIncrement()
            },
        )

        Column(
            modifier = Modifier
                .width(64.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    focusRequester.requestFocus()
                }
                .padding(vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicTextField(
                value = fieldValue,
                onValueChange = { incoming ->
                    val digits = incoming.text.filter { it.isDigit() }.take(2)
                    if (digits.isEmpty()) {
                        fieldValue = TextFieldValue("", TextRange.Zero)
                        onValueChange(0)
                    } else {
                        val clamped = digits.toInt().coerceIn(0, maxValue)
                        val display = if (clamped.toString() != digits && digits.toInt() > maxValue) {
                            clamped.toString()
                        } else {
                            digits
                        }
                        fieldValue = TextFieldValue(display, TextRange(display.length))
                        onValueChange(clamped)
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() },
                ),
                textStyle = AppTypography.timerCard.copy(
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                ),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        val nowFocused = state.isFocused
                        if (nowFocused && !isFocused) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            val currentText = fieldValue.text
                            fieldValue = fieldValue.copy(
                                selection = TextRange(0, currentText.length),
                            )
                        }
                        isFocused = nowFocused
                    },
            )

            Spacer(modifier = Modifier.height(3.dp))

            val dotColor = if (isFocused) {
                colors.accent
            } else {
                colors.textSecondary.copy(alpha = 0.55f)
            }
            Canvas(
                modifier = Modifier
                    .width(44.dp)
                    .height(3.dp),
            ) {
                drawLine(
                    color = dotColor,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(
                        intervals = floatArrayOf(1f, 6.dp.toPx()),
                        phase = 0f,
                    ),
                )
            }
        }

        StepperButton(
            text = "−",
            onClick = {
                focusManager.clearFocus()
                onDecrement()
            },
        )
    }
}

@Composable
private fun StepperButton(text: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val view = LocalView.current
    Surface(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            },
        shape = CircleShape,
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.outline),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = AppTypography.title,
                color = colors.textPrimary,
            )
        }
    }
}
