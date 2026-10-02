@echo off
python -m venv .venv
call .venv\Scripts\activate
python -m pip install --upgrade pip
pip install -r requirements.txt
echo.
echo Now install Tesseract OCR for Windows and add tesseract.exe to PATH.
echo Then install the Argos language packages you want and run run_windows.bat.
pause
