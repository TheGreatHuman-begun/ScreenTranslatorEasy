# Screen Translator — Desktop

A side-panel screen translator designed to run locally. The intended translation backend is **Argos Translate**, which runs on the computer instead of using Google Translate APIs.

## Windows setup

1. Install Python 3.11+.
2. Install Tesseract OCR and make sure `tesseract.exe` is on PATH.
3. Open PowerShell in this folder.
4. Run:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python main.py
```

Install the Argos language packages you need from the Argos Translate package manager. The application itself does not call Google services.

## Controls

- **Translate screen** — hides the panel briefly, captures the desktop, OCRs it, and places results in the side panel.
- **Automatic translation** — repeats the scan roughly every 2.8 seconds.
- **Clear** — clears the result cards.
- Close the panel and reopen it from the tray icon.

## Next packaging step

For a distributable Windows build, package `main.py` with PyInstaller after installing the required OCR/model data. The Android and desktop projects intentionally keep the UI/backend separation so the translation engine can later be replaced with a bundled model.
