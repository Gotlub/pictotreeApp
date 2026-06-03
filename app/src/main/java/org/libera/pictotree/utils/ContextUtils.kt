package org.libera.pictotree.utils

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

class ContextUtils(base: Context) : ContextWrapper(base) {
    companion object {
        fun updateLocale(context: Context, localeToSwitchTo: Locale): ContextWrapper {
            Locale.setDefault(localeToSwitchTo)
            var updatedContext = context
            val resources = updatedContext.resources
            val configuration = resources.configuration

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val localeList = android.os.LocaleList(localeToSwitchTo)
                android.os.LocaleList.setDefault(localeList)
                configuration.setLocales(localeList)
            } else {
                @Suppress("DEPRECATION")
                configuration.locale = localeToSwitchTo
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                updatedContext = updatedContext.createConfigurationContext(configuration)
            } else {
                @Suppress("DEPRECATION")
                resources.updateConfiguration(configuration, resources.displayMetrics)
            }

            return ContextUtils(updatedContext)
        }
    }
}
