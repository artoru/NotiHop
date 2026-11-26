package com.artor.notihop

import android.content.Context

object Prefs {
    private const val NAME = "notihop_prefs"
    const val MODE_RECEIVER = "receiver"
    const val MODE_SENDER   = "sender"

    private fun p(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun isAutostartEnabled(ctx: Context) = p(ctx).getBoolean("autostart", false)
    fun setAutostartEnabled(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("autostart", v).apply()

    fun getMode(ctx: Context): String = p(ctx).getString("mode", MODE_RECEIVER) ?: MODE_RECEIVER
    fun setMode(ctx: Context, v: String) = p(ctx).edit().putString("mode", v).apply()

    fun getDeviceAddress(ctx: Context): String? = p(ctx).getString("device_addr", null)
    fun getDeviceName(ctx: Context): String? = p(ctx).getString("device_name", null)
    fun setDevice(ctx: Context, address: String, name: String) =
        p(ctx).edit().putString("device_addr", address).putString("device_name", name).apply()

    // ── App filter ────────────────────────────────────────────────────────────

    fun isPhoneCallForwardEnabled(ctx: Context) = p(ctx).getBoolean("phone_call_forward", false)
    fun setPhoneCallForwardEnabled(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("phone_call_forward", v).apply()

    fun isPersistentNotifications(ctx: Context) = p(ctx).getBoolean("persistent_notifications", true)
    fun setPersistentNotifications(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("persistent_notifications", v).apply()

    fun isAutoEnableNewApps(ctx: Context) = p(ctx).getBoolean("auto_enable_new_apps", true)
    fun setAutoEnableNewApps(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("auto_enable_new_apps", v).apply()

    fun isKnownFilterApp(ctx: Context, pkg: String): Boolean =
        (p(ctx).getStringSet("filter_known_pkgs", emptySet()) ?: emptySet()).contains(pkg)

    fun isFilterAppEnabled(ctx: Context, pkg: String): Boolean =
        p(ctx).getBoolean("filter_pkg_$pkg", true)

    fun addFilterApp(ctx: Context, pkg: String, label: String, enabled: Boolean) {
        val known = (p(ctx).getStringSet("filter_known_pkgs", emptySet()) ?: emptySet()).toMutableSet()
        known.add(pkg)
        p(ctx).edit()
            .putStringSet("filter_known_pkgs", known)
            .putString("filter_label_$pkg", label)
            .putBoolean("filter_pkg_$pkg", enabled)
            .apply()
    }

    fun setFilterAppEnabled(ctx: Context, pkg: String, enabled: Boolean) {
        p(ctx).edit().putBoolean("filter_pkg_$pkg", enabled).apply()
    }

    data class FilterApp(val pkg: String, val label: String, val enabled: Boolean)

    fun getFilterApps(ctx: Context): List<FilterApp> {
        val prefs = p(ctx)
        val known = prefs.getStringSet("filter_known_pkgs", emptySet()) ?: emptySet()
        return known.map { pkg ->
            FilterApp(
                pkg = pkg,
                label = prefs.getString("filter_label_$pkg", pkg) ?: pkg,
                enabled = prefs.getBoolean("filter_pkg_$pkg", true)
            )
        }.sortedBy { it.label.lowercase() }
    }
}
