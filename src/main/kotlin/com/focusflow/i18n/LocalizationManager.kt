package com.focusflow.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.focusflow.data.Database
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LocalizationManager {

    var currentLanguage by mutableStateOf(AppLanguage.ENGLISH)
        private set

    val strings: AppStrings
        get() = translations[currentLanguage] ?: translations[AppLanguage.ENGLISH]!!

    fun setLanguage(language: AppLanguage) {
        currentLanguage = language
    }

    suspend fun loadSavedLanguage() {
        val code = withContext(Dispatchers.IO) {
            Database.getSetting("app_language")
        }
        if (code != null) {
            currentLanguage = AppLanguage.fromCode(code)
        }
    }

    suspend fun saveLanguage(language: AppLanguage) {
        currentLanguage = language
        withContext(Dispatchers.IO) {
            Database.setSetting("app_language", language.code)
        }
    }
}
