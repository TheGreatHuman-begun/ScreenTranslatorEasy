# Screen Translator

A screen translation utility with a floating button and a right-side translation panel.

## Android

Open the `ScreenTranslator` folder in Android Studio, sync Gradle, and run the `app` configuration on an Android 8.0+ device.

The app requests:
- draw-over-other-apps permission
- screen-capture permission
- notification permission on Android 13+

Once running, the panel sits on the right side of the screen. It hides during capture so it does not translate itself. Results are shown as original text + translation cards rather than painting over the application underneath.

### Current translation backend

The prototype uses an isolated ML Kit local-model adapter. Translation is performed on-device after the model is prepared; there is no Google Translate web/API call. The `TranslationEngine` interface is intentionally separate from the UI so an open-source bundled model can replace this adapter in the next build.

## Desktop

See `desktop/README.md`. The desktop version uses PySide6 + MSS + Tesseract OCR + Argos Translate and is designed to work locally rather than through Google Translate.

## Roadmap

1. Bundle an open-source Android OCR/translation model so the Android APK has no Google ML Kit dependency at all.
2. Add source-language auto detection and downloadable language packs.
3. Add region selection so only a chosen rectangle is OCR'd.
4. Add copy, text-to-speech, and translation history.
5. Package Windows as a standalone executable.
