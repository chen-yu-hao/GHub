package dev.remotegpt.app

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.app.LocaleManager
import androidx.annotation.StringRes
import androidx.annotation.PluralsRes
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate

/** Local UI and operation messages share the same per-app Android locale. */
internal object AppStrings {
    private var application: Context? = null
    private var cachedConfiguration: Configuration? = null
    private var localizedResources: Resources? = null

    fun initialize(context: Context) { application = context.applicationContext }

    private fun resources(): Resources? {
        val context = application ?: return null
        val configuration = Configuration(context.resources.configuration)
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (!appLocales.isEmpty) {
            configuration.setLocales(android.os.LocaleList.forLanguageTags(appLocales.toLanguageTags()))
        } else if (Build.VERSION.SDK_INT >= 33) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (!locales.isEmpty) configuration.setLocales(locales)
        }
        if (configuration != cachedConfiguration || localizedResources == null) {
            localizedResources = context.createConfigurationContext(configuration).resources
            cachedConfiguration = configuration
        }
        return localizedResources
    }
    @Synchronized fun get(@StringRes id: Int, vararg args: Any?): String = resources()?.getString(id, *args).orEmpty()
    @Synchronized fun quantity(@PluralsRes id: Int, count: Int, vararg args: Any?): String =
        resources()?.getQuantityString(id, count, *args).orEmpty()
}

internal fun tr(@StringRes id: Int, vararg args: Any?): String = AppStrings.get(id, *args)
internal fun trq(@PluralsRes id: Int, count: Int): String = AppStrings.quantity(id, count, count)
