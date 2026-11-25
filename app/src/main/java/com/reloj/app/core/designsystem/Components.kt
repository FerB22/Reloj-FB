package com.reloj.app.core.designsystem

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reloj.app.R
import kotlinx.coroutines.delay

@Composable
fun PrimaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconRes: Int = R.drawable.ic_add,
    shadowElevation: Dp = 0.dp,
    large: Boolean = false,
) {
    val colors = AppTheme.colors
    val view = LocalView.current
    val cornerRadius = if (large) 18.dp else 14.dp
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            },
        shape = RoundedCornerShape(cornerRadius),
        color = colors.accent,
        shadowElevation = shadowElevation,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (large) 24.dp else 20.dp,
                vertical = if (large) 16.dp else 12.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (large) 10.dp else 8.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier.size(if (large) 24.dp else 20.dp),
            )
            Text(
                text = text,
                style = if (large) {
                    AppTypography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    AppTypography.label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                },
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

/**
 * Botón de papelera con confirmación animada en línea: el primer toque despliega
 * la píldora roja de confirmación junto al icono y se cancela automáticamente tras 3 s.
 */
@Composable
fun ConfirmDeleteButton(
    confirming: Boolean,
    onRequestConfirm: () -> Unit,
    onConfirm: () -> Unit,
    onCancelConfirm: () -> Unit,
    contentDescription: String,
    buttonSize: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val view = LocalView.current

    LaunchedEffect(confirming) {
        if (confirming) {
            delay(3_000L)
            onCancelConfirm()
        }
    }

    val containerColor by animateColorAsState(
        targetValue = if (confirming) colors.danger else colors.surfaceElevated,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "deleteContainerColor",
    )
    val contentColor by animateColorAsState(
        targetValue = if (confirming) {
            if (colors.isDark) Color(0xFF1A1012) else Color.White
        } else {
            colors.textSecondary
        },
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "deleteContentColor",
    )

    Surface(
        modifier = modifier
            .height(buttonSize)
            .clip(CircleShape)
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                if (confirming) onConfirm() else onRequestConfirm()
            },
        shape = CircleShape,
        color = containerColor,
        shadowElevation = if (confirming) 4.dp else 0.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(
                visible = confirming,
                enter = expandHorizontally(
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.End,
                ) + fadeIn(animationSpec = tween(durationMillis = 180)),
                exit = shrinkHorizontally(
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.End,
                ) + fadeOut(animationSpec = tween(durationMillis = 150)),
            ) {
                Text(
                    text = stringResource(R.string.action_confirm),
                    style = AppTypography.label,
                    color = contentColor,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 12.dp, end = 0.dp),
                )
            }

            Box(
                modifier = Modifier.size(buttonSize),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = if (confirming) {
                        stringResource(R.string.action_confirm_delete)
                    } else {
                        contentDescription
                    },
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Fila que reserva un ancho fijo para la acción destructiva en reposo y permite
 * que su píldora de confirmación se expanda sobrepuesta sin reajustar ni mover el texto izquierdo.
 */
@Composable
fun StableActionRow(
    reservedDeleteWidth: Dp,
    modifier: Modifier = Modifier,
    spacing: Dp = 10.dp,
    leftContent: @Composable () -> Unit,
    deleteAction: @Composable () -> Unit,
    trailingAction: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Box { leftContent() }
            Box { trailingAction() }
            Box { deleteAction() }
        },
    ) { measurables, constraints ->
        val gapPx = spacing.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val trailingPlaceable = measurables[1].measure(loose)
        val hasTrailing = trailingPlaceable.width > 0
        val trailingGap = if (hasTrailing) gapPx else 0

        val deleteMaxWidth = (constraints.maxWidth - trailingPlaceable.width - trailingGap).coerceAtLeast(0)
        val deletePlaceable = measurables[2].measure(
            loose.copy(maxWidth = deleteMaxWidth),
        )

        val reservedDeletePx = reservedDeleteWidth.roundToPx()
        val leftMaxWidth = (
            constraints.maxWidth - trailingPlaceable.width - trailingGap - reservedDeletePx - gapPx
            ).coerceAtLeast(0)
        val leftPlaceable = measurables[0].measure(
            loose.copy(maxWidth = leftMaxWidth),
        )

        val rowWidth = constraints.maxWidth
        val rowHeight = maxOf(
            leftPlaceable.height,
            trailingPlaceable.height,
            deletePlaceable.height,
        )

        layout(rowWidth, rowHeight) {
            leftPlaceable.placeRelative(
                x = 0,
                y = (rowHeight - leftPlaceable.height) / 2,
            )
            trailingPlaceable.placeRelative(
                x = rowWidth - trailingPlaceable.width,
                y = (rowHeight - trailingPlaceable.height) / 2,
            )
            deletePlaceable.placeRelative(
                x = rowWidth - trailingPlaceable.width - trailingGap - deletePlaceable.width,
                y = (rowHeight - deletePlaceable.height) / 2,
                zIndex = 1f,
            )
        }
    }
}
