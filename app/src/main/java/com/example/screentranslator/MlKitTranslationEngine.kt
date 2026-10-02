package com.example.screentranslator

import android.content.Context
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

/**
 * Local-model backend used by the prototype. The model is downloaded once and
 * translation itself happens on-device. The app UI does not depend on a Google
 * Translate web API. This class is deliberately isolated so it can be replaced
 * by a bundled open-source model later.
 */
class MlKitTranslationEngine(
    context: Context,
    source: String = TranslateLanguage.ENGLISH,
    target: String = TranslateLanguage.PERSIAN
) : TranslationEngine {
    private val translator: Translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(source)
            .setTargetLanguage(target)
            .build()
    )

    override fun prepare(onReady: () -> Unit, onError: (Exception) -> Unit) {
        translator.downloadModelIfNeeded()
            .addOnSuccessListener { onReady() }
            .addOnFailureListener { onError(it) }
    }

    override fun translate(text: String, onSuccess: (String) -> Unit, onError: (Exception) -> Unit) {
        translator.translate(text)
            .addOnSuccessListener(onSuccess)
            .addOnFailureListener(onError)
    }

    override fun close() = translator.close()
}
