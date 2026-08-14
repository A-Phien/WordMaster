package dev.johnoreilly.wordmaster.androidApp

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.johnoreilly.wordmaster.shared.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.languageDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "word_master_settings")

class LanguageStore(context: Context) {
    private val store = context.applicationContext.languageDataStore
    private val KEY_LANG = stringPreferencesKey("app_language")

    /** Emits the saved language, defaulting to EN if nothing is saved. */
    val languageFlow: Flow<AppLanguage> = store.data.map { prefs ->
        when (prefs[KEY_LANG]) {
            AppLanguage.VI.name -> AppLanguage.VI
            else                -> AppLanguage.EN
        }
    }

    suspend fun saveLanguage(lang: AppLanguage) {
        store.edit { it[KEY_LANG] = lang.name }
    }
}
