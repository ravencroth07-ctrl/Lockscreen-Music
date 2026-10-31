package app.lockmusic

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore(name = "lockmusic")

object Prefs {
    private val DIM = floatPreferencesKey("dim")
    private val IDLE = stringPreferencesKey("idle_uri")
    private val PLAYER = booleanPreferencesKey("player_enabled")
    private val FAV = stringSetPreferencesKey("favorites")

    /** Mirrors the stored player switch so a broadcast receiver can read it without suspending. */
    @Volatile
    var playerEnabled: Boolean = true

    fun dim(c: Context): Flow<Float> = c.store.data.map { it[DIM] ?: 0.25f }
    fun idleUri(c: Context): Flow<String?> = c.store.data.map { it[IDLE] }
    fun player(c: Context): Flow<Boolean> = c.store.data.map { it[PLAYER] ?: true }
    fun favorites(c: Context): Flow<Set<String>> = c.store.data.map { it[FAV] ?: emptySet() }

    suspend fun setDim(c: Context, v: Float) { c.store.edit { it[DIM] = v.coerceIn(0f, 1f) } }
    suspend fun setIdle(c: Context, uri: String?) {
        c.store.edit { if (uri == null) it.remove(IDLE) else it[IDLE] = uri }
    }
    suspend fun setPlayer(c: Context, on: Boolean) { c.store.edit { it[PLAYER] = on } }
    suspend fun toggleFavorite(c: Context, key: String) {
        c.store.edit { p ->
            val cur = p[FAV] ?: emptySet()
            p[FAV] = if (key in cur) cur - key else cur + key
        }
    }
}
