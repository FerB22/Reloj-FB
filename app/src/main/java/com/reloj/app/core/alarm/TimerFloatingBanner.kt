package com.reloj.app.core.alarm

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import com.reloj.app.R
import com.reloj.app.core.data.local.ThemePreferences
import com.reloj.app.core.designsystem.AppColorScheme
import com.reloj.app.core.designsystem.buildAppColorScheme
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Banner flotante propio para temporizadores cuando el dispositivo está desbloqueado.
 * Se superpone limpiamente en la parte superior mediante [WindowManager] sin tapar
 * ni robar el foco de juegos u otras aplicaciones activas.
 */
class TimerFloatingBanner(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var rootView: View? = null

    fun canShow(): Boolean = Settings.canDrawOverlays(context)

    fun show(
        title: String,
        subtitle: String,
        isAlarm: Boolean,
        onOpenFullScreen: () -> Unit,
        onStop: () -> Unit,
    ): Boolean {
        if (!canShow()) return false
        dismissImmediate()

        val scheme = resolveColorScheme()
        val container = FrameLayout(context)
        val card = buildCardView(
            scheme = scheme,
            title = title,
            subtitle = subtitle,
            isAlarm = isAlarm,
            onOpenFullScreen = onOpenFullScreen,
            onStop = onStop,
        )

        val maxCardWidth = dp(440)
        val horizontalMargin = dp(24)
        val availableWidth = (screenWidthPx() - horizontalMargin).coerceAtLeast(dp(260))
        val cardParams = FrameLayout.LayoutParams(
            min(availableWidth, maxCardWidth),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_HORIZONTAL,
        )
        container.addView(card, cardParams)

        val windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = topInsetPx() + dp(10)
        }

        val added = runCatching {
            windowManager.addView(container, windowParams)
            true
        }.getOrDefault(false)

        if (!added) return false

        rootView = container
        card.translationY = -dp(96).toFloat()
        card.alpha = 0f
        card.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(260L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        return true
    }

    fun dismissImmediate() {
        val current = rootView ?: return
        rootView = null
        current.animate().cancel()
        runCatching {
            if (current.isAttachedToWindow) {
                windowManager.removeViewImmediate(current)
            }
        }
    }

    private fun buildCardView(
        scheme: AppColorScheme,
        title: String,
        subtitle: String,
        isAlarm: Boolean,
        onOpenFullScreen: () -> Unit,
        onStop: () -> Unit,
    ): LinearLayout {
        val cardBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(24).toFloat()
            setColor(scheme.surface.toArgb())
            setStroke(dp(1), scheme.outline.toArgb())
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cardBg
            elevation = dp(12).toFloat()
            setPadding(dp(14), dp(12), dp(14), dp(12))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onOpenFullScreen()
            }
        }

        // Icono izquierdo dentro de un recuadro con el fondo del tema.
        val iconFrame = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(13).toFloat()
                setColor(scheme.background.toArgb())
            }
        }
        val iconView = ImageView(context).apply {
            setImageResource(if (isAlarm) R.drawable.ic_alarm else R.drawable.ic_timer)
            setColorFilter(scheme.accent.toArgb())
        }
        iconFrame.addView(
            iconView,
            FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER),
        )
        card.addView(iconFrame, LinearLayout.LayoutParams(dp(40), dp(40)))

        // Bloque de datos a la izquierda.
        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val titleView = TextView(context).apply {
            text = title
            setTextColor(scheme.textPrimary.toArgb())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val subtitleView = TextView(context).apply {
            text = subtitle
            setTextColor(scheme.textSecondary.toArgb())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        textColumn.addView(titleView)
        textColumn.addView(
            subtitleView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(2) },
        )
        card.addView(
            textColumn,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
                marginEnd = dp(12)
            },
        )

        // Botón Detener a la derecha estilizado con el color de acento.
        val stopShape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(16).toFloat()
            setColor(scheme.accent.toArgb())
        }
        val rippleColor = ColorStateList.valueOf(
            if (scheme.isDark) 0x33000000 else 0x33FFFFFF,
        )
        val stopButton = TextView(context).apply {
            text = "Detener"
            setTextColor(scheme.onAccent.toArgb())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minHeight = dp(40)
            setPadding(dp(18), dp(9), dp(18), dp(9))
            background = RippleDrawable(rippleColor, stopShape, stopShape)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onStop()
            }
        }
        card.addView(
            stopButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        return card
    }

    private fun resolveColorScheme(): AppColorScheme {
        val appearance = ThemePreferences.get(context).settings.value
        val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val systemDark = nightMask == Configuration.UI_MODE_NIGHT_YES
        return buildAppColorScheme(
            themeMode = appearance.themeMode,
            systemDark = systemDark,
            accent = appearance.accent,
        )
    }

    private fun topInsetPx(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = windowManager.currentWindowMetrics.windowInsets.getInsets(
                WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout(),
            )
            return insets.top
        }
        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) context.resources.getDimensionPixelSize(resId) else dp(28)
    }

    private fun screenWidthPx(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.width()
        } else {
            context.resources.displayMetrics.widthPixels
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics,
        ).roundToInt()
}
