package com.example.screentranslator

/** Translation backend. Keep this interface independent from the UI so an entirely
 * local/open-source model can replace the default backend without changing the overlay. */
interface TranslationEngine : AutoCloseable {
    fun prepare(onReady: () -> Unit, onError: (Exception) -> Unit)
    fun translate(text: String, onSuccess: (String) -> Unit, onError: (Exception) -> Unit)
}
