package com.reloj.app.core.data.local

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.Color

enum class AppIconPreset(
    val label: String,
    val aliasClassName: String,
    val bgColor: Color,
    val ringColor: Color,
    val handsColor: Color,
    val isDefault: Boolean = false
) {
    CARBON_AMBER(
        label = "Carbón y ámbar",
        aliasClassName = "com.reloj.app.LauncherCarbonAmber",
        bgColor = Color(0xFF121316),
        ringColor = Color(0xFFE8A33D),
        handsColor = Color(0xFFEDEFEF),
        isDefault = true
    ),
    BEIGE_CARBON(
        label = "Beige y carbón",
        aliasClassName = "com.reloj.app.LauncherBeigeCarbon",
        bgColor = Color(0xFFF6F3EB),
        ringColor = Color(0xFFB86E00),
        handsColor = Color(0xFF1C1B1A)
    ),
    NIGHT_TERRACOTTA(
        label = "Noche y terracota",
        aliasClassName = "com.reloj.app.LauncherNightTerracotta",
        bgColor = Color(0xFF181414),
        ringColor = Color(0xFFE07A5F),
        handsColor = Color(0xFFF4EDE4)
    ),
    FOREST_SAGE(
        label = "Bosque y salvia",
        aliasClassName = "com.reloj.app.LauncherForestSage",
        bgColor = Color(0xFF14221C),
        ringColor = Color(0xFF81B29A),
        handsColor = Color(0xFFEDF4EF)
    ),
    SAPPHIRE_ICE(
        label = "Zafiro y hielo",
        aliasClassName = "com.reloj.app.LauncherSapphireIce",
        bgColor = Color(0xFF101A2B),
        ringColor = Color(0xFF7B9ACC),
        handsColor = Color(0xFFF0F4FA)
    ),
    BURGUNDY_GOLD(
        label = "Burdeos y oro",
        aliasClassName = "com.reloj.app.LauncherBurgundyGold",
        bgColor = Color(0xFF2A1219),
        ringColor = Color(0xFFE6B86A),
        handsColor = Color(0xFFFAF3E8)
    ),
    GRAPHITE_CORAL(
        label = "Grafito y coral",
        aliasClassName = "com.reloj.app.LauncherGraphiteCoral",
        bgColor = Color(0xFF222428),
        ringColor = Color(0xFFFF6B5B),
        handsColor = Color(0xFFFFFFFF)
    ),
    COBALT_WHITE(
        label = "Cobalto y blanco",
        aliasClassName = "com.reloj.app.LauncherCobaltWhite",
        bgColor = Color(0xFF1D4ED8),
        ringColor = Color(0xFF93C5FD),
        handsColor = Color(0xFFFFFFFF)
    ),
    TERRACOTTA_CREAM(
        label = "Terracota y crema",
        aliasClassName = "com.reloj.app.LauncherTerracottaCream",
        bgColor = Color(0xFFB84A32),
        ringColor = Color(0xFFF7D7C4),
        handsColor = Color(0xFFFFF8F3)
    ),
    MONO_LIGHT(
        label = "Claro monocromo",
        aliasClassName = "com.reloj.app.LauncherMonoLight",
        bgColor = Color(0xFFEDEFEF),
        ringColor = Color(0xFF8B8F96),
        handsColor = Color(0xFF121316)
    )
}

object AppIconManager {

    private const val PREFS_NAME = "app_icon_prefs"
    private const val KEY_PENDING_ICON = "pending_icon"

    fun getActiveManifestIcon(context: Context): AppIconPreset {
        val pm = context.packageManager
        val packageName = context.packageName
        for (preset in AppIconPreset.entries) {
            val state = pm.getComponentEnabledSetting(ComponentName(packageName, preset.aliasClassName))
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                return preset
            }
            if (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && preset.isDefault) {
                return preset
            }
        }
        return AppIconPreset.CARBON_AMBER
    }

    fun getSelectedIcon(context: Context): AppIconPreset {
        val pendingName = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PENDING_ICON, null)
        val pendingPreset = pendingName?.let { name ->
            AppIconPreset.entries.firstOrNull { it.name == name }
        }
        return pendingPreset ?: getActiveManifestIcon(context)
    }

    fun hasPendingChange(context: Context): Boolean {
        val pendingName = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PENDING_ICON, null)
            ?: return false
        val pendingPreset = AppIconPreset.entries.firstOrNull { it.name == pendingName } ?: return false
        return pendingPreset != getActiveManifestIcon(context)
    }

    /**
     * Guarda la elección del usuario sin alterar los componentes de Android en primer plano,
     * evitando que el sistema cierre el proceso ~3 s después mientras usa la app.
     */
    fun scheduleIconChange(context: Context, target: AppIconPreset) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        if (target == getActiveManifestIcon(context)) {
            editor.remove(KEY_PENDING_ICON)
        } else {
            editor.putString(KEY_PENDING_ICON, target.name)
        }
        editor.apply()
    }

    /**
     * Aplica el cambio de alias pendiente cuando la app pasa a segundo plano (onStop).
     */
    fun applyPendingIconIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pendingName = prefs.getString(KEY_PENDING_ICON, null) ?: return
        val target = AppIconPreset.entries.firstOrNull { it.name == pendingName }
        prefs.edit().remove(KEY_PENDING_ICON).commit()
        if (target != null && target != getActiveManifestIcon(context)) {
            applyManifestIcon(context.applicationContext, target)
        }
    }

    private fun applyManifestIcon(context: Context, target: AppIconPreset) {
        val pm = context.packageManager
        val packageName = context.packageName

        // Habilitar primero el nuevo alias para evitar que el launcher pierda el acceso directo en transición.
        pm.setComponentEnabledSetting(
            ComponentName(packageName, target.aliasClassName),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

        for (preset in AppIconPreset.entries) {
            if (preset == target) continue
            val component = ComponentName(packageName, preset.aliasClassName)
            val currentState = pm.getComponentEnabledSetting(component)
            if (currentState != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                pm.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}
