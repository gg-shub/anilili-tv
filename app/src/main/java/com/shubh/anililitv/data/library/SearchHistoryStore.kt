package com.shubh.anililitv.data.library

import android.content.Context
import android.content.SharedPreferences
import com.shubh.anililitv.diagnostics.DiagnosticsLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

object SearchHistoryStore {
    private const val PREFS = "anilili_search_history"
    private const val KEY = "queries"
    private const val MAX_ENTRIES = 12

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var prefs: SharedPreferences

    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history = _history.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _history.value = runCatching {
            prefs.getString(KEY, null)?.let { json.decodeFromString(ListSerializer(String.serializer()), it) }
        }.getOrNull().orEmpty()
    }

    fun record(query: String) {
        val term = query.trim()
        if (term.isEmpty() || !::prefs.isInitialized) return
        val next = (listOf(term) + _history.value.filterNot { it.equals(term, ignoreCase = true) })
            .take(MAX_ENTRIES)
        if (next == _history.value) return
        _history.value = next
        persist(next)
    }

    fun remove(query: String) {
        if (!::prefs.isInitialized) return
        val next = _history.value.filterNot { it.equals(query.trim(), ignoreCase = true) }
        if (next == _history.value) return
        _history.value = next
        persist(next)
    }

    fun clear() {
        if (!::prefs.isInitialized) return
        _history.value = emptyList()
        persist(emptyList())
    }

    private fun persist(entries: List<String>) {
        runCatching {
            prefs.edit().putString(KEY, json.encodeToString(ListSerializer(String.serializer()), entries)).apply()
        }.onFailure { DiagnosticsLog.throwable("SearchHistoryStore persist failed", it) }
    }
}
