package com.example.spindle.presentation.viewmodels

import kotlinx.coroutines.launch

data class SettingsState(
    val engineUrl: String           = "http://127.0.0.1:8000",
    val engineUrlError: String?     = null,
    val ansysExecPath: String       = "/usr/ansys_inc/v222/ansys/bin/ansys222",
    val autoNavigateOnComplete: Boolean = false,
    val pollIntervalMs: Long        = 2_000L,
    val isSaving: Boolean           = false,
    val lastSavedAt: String?        = null
) : MviState

sealed interface SettingsIntent : MviIntent {
    data class UpdateEngineUrl(val v: String)    : SettingsIntent
    data class UpdateAnsysPath(val v: String)    : SettingsIntent
    data class UpdateAutoNavigate(val v: Boolean): SettingsIntent
    data class UpdatePollInterval(val v: Long)   : SettingsIntent
    data object Save                              : SettingsIntent
    data object RestoreDefaults                   : SettingsIntent
    data object TestEngineConnection              : SettingsIntent
}

sealed interface SettingsEffect : MviEffect {
    data class ShowSnackbar(val msg: String) : SettingsEffect
    data object SavedOk                      : SettingsEffect
}

class SettingsViewModel : BaseViewModel<SettingsState, SettingsIntent, SettingsEffect>(
    SettingsState(
        engineUrl           = Prefs.get("engineUrl", "http://127.0.0.1:8000"),
        ansysExecPath       = Prefs.get("ansysPath", "/usr/ansys_inc/v222/ansys/bin/ansys222"),
        autoNavigateOnComplete = Prefs.getBool("autoNavigate", false),
        pollIntervalMs      = Prefs.getLong("pollMs", 2_000L)
    )
) {
    override fun processIntent(intent: SettingsIntent) = when (intent) {
        is SettingsIntent.UpdateEngineUrl    -> updateState {
            copy(engineUrl = intent.v, engineUrlError = validateUrl(intent.v))
        }
        is SettingsIntent.UpdateAnsysPath    -> updateState { copy(ansysExecPath = intent.v) }
        is SettingsIntent.UpdateAutoNavigate -> updateState { copy(autoNavigateOnComplete = intent.v) }
        is SettingsIntent.UpdatePollInterval -> updateState { copy(pollIntervalMs = intent.v) }
        is SettingsIntent.Save               -> save()
        is SettingsIntent.RestoreDefaults    -> updateState { SettingsState() }
        is SettingsIntent.TestEngineConnection -> testConnection()
    }

    private fun save() {
        updateState { copy(isSaving = true) }
        scope.launch {
            Prefs.put("engineUrl",    currentState.engineUrl)
            Prefs.put("ansysPath",    currentState.ansysExecPath)
            Prefs.putBool("autoNavigate", currentState.autoNavigateOnComplete)
            Prefs.putLong("pollMs",   currentState.pollIntervalMs)
            val ts = java.time.LocalDateTime.now().toString().take(16)
            updateState { copy(isSaving = false, lastSavedAt = ts) }
            emitEffect(SettingsEffect.SavedOk)
        }
    }

    private fun testConnection() {
        scope.launch {
            try {
                val url  = java.net.URL("${currentState.engineUrl}/api/health")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 3_000; conn.readTimeout = 3_000
                val code = conn.responseCode; conn.disconnect()
                emitEffect(SettingsEffect.ShowSnackbar(
                    if (code == 200) "✓ Engine reachable" else "⚠ Engine responded HTTP $code"
                ))
            } catch (e: Exception) {
                emitEffect(SettingsEffect.ShowSnackbar("✗ Cannot reach engine: ${e.message}"))
            }
        }
    }

    private fun validateUrl(url: String): String? {
        if (url.isBlank()) return "URL cannot be blank"
        if (!url.startsWith("http://") && !url.startsWith("https://")) return "Must start with http://"
        return null
    }
}

private object Prefs {
    private val node = java.util.prefs.Preferences.userRoot().node("com/example/spindle")
    fun get(key: String, default: String): String   = node.get(key, default)
    fun put(key: String, value: String)              = node.put(key, value)
    fun getBool(key: String, default: Boolean): Boolean = node.getBoolean(key, default)
    fun putBool(key: String, value: Boolean)         = node.putBoolean(key, value)
    fun getLong(key: String, default: Long): Long    = node.getLong(key, default)
    fun putLong(key: String, value: Long)            = node.putLong(key, value)
}
