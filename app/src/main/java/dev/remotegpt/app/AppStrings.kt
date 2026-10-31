package dev.remotegpt.app

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.app.LocaleManager
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate

/** Local UI and operation messages share the same per-app Android locale. */
internal object AppStrings {
    private var application: Context? = null
    private var cachedConfiguration: Configuration? = null
    private var localizedContext: Context? = null

    fun initialize(context: Context) { application = context.applicationContext }

    @Synchronized fun get(@StringRes id: Int, vararg args: Any?): String {
        val context = application ?: return ""
        val configuration = Configuration(context.resources.configuration)
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (!appLocales.isEmpty) {
            configuration.setLocales(android.os.LocaleList.forLanguageTags(appLocales.toLanguageTags()))
        } else if (Build.VERSION.SDK_INT >= 33) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (!locales.isEmpty) configuration.setLocales(locales)
        }
        if (configuration != cachedConfiguration || localizedContext == null) {
            localizedContext = context.createConfigurationContext(configuration)
            cachedConfiguration = configuration
        }
        return localizedContext!!.resources.getString(id, *args)
    }
}

internal fun tr(@StringRes id: Int, vararg args: Any?): String = AppStrings.get(id, *args)
