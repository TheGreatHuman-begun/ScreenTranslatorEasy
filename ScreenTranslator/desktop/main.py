import sys
import time
from dataclasses import dataclass

try:
    import mss
    from PIL import Image
except ImportError as exc:
    raise SystemExit("Install desktop requirements first: pip install -r requirements.txt") from exc

from PySide6.QtCore import Qt, QRect, QTimer, Signal
from PySide6.QtGui import QAction, QGuiApplication, QIcon
from PySide6.QtWidgets import (
    QApplication, QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QTextEdit, QComboBox, QCheckBox, QScrollArea, QFrame, QSystemTrayIcon,
    QMenu, QSizePolicy
)

try:
    import pytesseract
except ImportError:
    pytesseract = None

try:
    import argostranslate.translate as argos_translate
except ImportError:
    argos_translate = None

@dataclass
class TranslationResult:
    source: str
    target: str

class TranslatorBackend:
    """Offline backend. Argos Translate runs locally and does not call Google."""
    def __init__(self):
        self.ready = argos_translate is not None

    def translate(self, text: str, source: str, target: str) -> str:
        if not text.strip():
            return ""
        if not self.ready:
            return "Install argostranslate and its language package."
        try:
            return argos_translate.translate(text, source, target)
        except Exception as exc:
            return f"Translation error: {exc}"

class SidePanel(QWidget):
    request_translate = Signal()

    def __init__(self):
        super().__init__()
        self.setWindowTitle("Screen Translator")
        self.setWindowFlags(Qt.Tool | Qt.FramelessWindowHint | Qt.WindowStaysOnTopHint)
        self.setAttribute(Qt.WA_TranslucentBackground)
        self.setFixedWidth(390)
        self.backend = TranslatorBackend()
        self.cache = {}
        self.auto = False
        self.last_capture = 0.0
        self._build_ui()
        self._position_right()

        self.timer = QTimer(self)
        self.timer.timeout.connect(self.request_translate.emit)
        self.timer.setInterval(2800)

    def _build_ui(self):
        outer = QVBoxLayout(self)
        outer.setContentsMargins(10, 10, 10, 10)
        card = QFrame()
        card.setObjectName("card")
        layout = QVBoxLayout(card)
        layout.setContentsMargins(18, 18, 18, 18)

        header = QHBoxLayout()
        title = QLabel("Screen Translator")
        title.setObjectName("title")
        header.addWidget(title)
        close = QPushButton("×")
        close.setFixedSize(42, 42)
        close.clicked.connect(self.hide)
        header.addWidget(close)
        layout.addLayout(header)

        layout.addWidget(QLabel("SOURCE"))
        self.source = QComboBox()
        self.source.addItems(["en", "de", "ja", "ko", "zh", "fr", "es", "ru", "pt", "it"])
        layout.addWidget(self.source)

        layout.addWidget(QLabel("TRANSLATE TO"))
        self.target = QComboBox()
        self.target.addItems(["fa", "en", "de", "fr", "es", "ja", "ko", "zh"])
        self.target.setCurrentText("fa")
        layout.addWidget(self.target)

        buttons = QHBoxLayout()
        translate = QPushButton("Translate screen")
        translate.clicked.connect(self.request_translate.emit)
        clear = QPushButton("Clear")
        clear.clicked.connect(self.clear_results)
        buttons.addWidget(translate)
        buttons.addWidget(clear)
        layout.addLayout(buttons)

        self.auto_box = QCheckBox("Automatic translation")
        self.auto_box.toggled.connect(self.set_auto)
        layout.addWidget(self.auto_box)

        self.status = QLabel("Ready")
        self.status.setObjectName("status")
        self.status.setWordWrap(True)
        layout.addWidget(self.status)

        self.scroll = QScrollArea()
        self.scroll.setWidgetResizable(True)
        self.results = QWidget()
        self.results_layout = QVBoxLayout(self.results)
        self.results_layout.setAlignment(Qt.AlignTop)
        self.scroll.setWidget(self.results)
        layout.addWidget(self.scroll, 1)

        hint = QLabel("The desktop version captures the screen only while translating, so the panel itself is not included.")
        hint.setObjectName("hint")
        hint.setWordWrap(True)
        layout.addWidget(hint)

        outer.addWidget(card)
        self.setStyleSheet("""
            QWidget { color: #eeeeee; font-size: 13px; }
            QFrame#card { background: #17191f; border-radius: 18px; }
            QLabel#title { font-size: 22px; font-weight: 700; }
            QLabel#status, QLabel#hint { color: #9ea3ad; }
            QComboBox, QPushButton { background: #262a33; border: 1px solid #383d48; border-radius: 9px; padding: 9px; }
            QPushButton:hover { background: #333946; }
            QScrollArea { border: none; }
        """)

    def _position_right(self):
        screen = QGuiApplication.primaryScreen().availableGeometry()
        self.setGeometry(screen.right() - self.width() + 1, screen.top(), self.width(), screen.height())

    def set_auto(self, enabled: bool):
        self.auto = enabled
        if enabled:
            self.status.setText("Auto mode enabled")
            self.timer.start()
        else:
            self.timer.stop()
            self.status.setText("Auto mode off")

    def clear_results(self):
        while self.results_layout.count():
            item = self.results_layout.takeAt(0)
            if item.widget():
                item.widget().deleteLater()
        self.status.setText("Cleared")

    def add_result(self, result: TranslationResult):
        box = QFrame()
        box.setStyleSheet("QFrame { background: #22262e; border-radius: 12px; }")
        l = QVBoxLayout(box)
        l.setContentsMargins(12, 10, 12, 10)
        src = QLabel(result.source)
        src.setStyleSheet("color: #a9adb5;")
        src.setWordWrap(True)
        dst = QTextEdit()
        dst.setReadOnly(True)
        dst.setPlainText(result.target)
        dst.setMaximumHeight(95)
        l.addWidget(src)
        l.addWidget(dst)
        self.results_layout.addWidget(box)

class ScreenTranslatorApp:
    def __init__(self):
        self.app = QApplication(sys.argv)
        self.panel = SidePanel()
        self.panel.request_translate.connect(self.translate_screen)
        self.tray = QSystemTrayIcon(QIcon())
        menu = QMenu()
        show_action = QAction("Show translator")
        show_action.triggered.connect(self.panel.show)
        quit_action = QAction("Quit")
        quit_action.triggered.connect(self.app.quit)
        menu.addAction(show_action)
        menu.addAction(quit_action)
        self.tray.setContextMenu(menu)
        self.tray.show()

    def capture_screen(self):
        self.panel.hide()
        QApplication.processEvents()
        time.sleep(0.18)
        with mss.mss() as sct:
            monitor = sct.monitors[0]
            raw = sct.grab(monitor)
            img = Image.frombytes("RGB", raw.size, raw.rgb)
        self.panel.show()
        return img

    def translate_screen(self):
        if pytesseract is None:
            self.panel.status.setText("OCR is not installed. Run the setup instructions in README.")
            return
        self.panel.status.setText("Capturing and reading text…")
        QApplication.processEvents()
        try:
            image = self.capture_screen()
            data = pytesseract.image_to_data(image, lang=self.panel.source.currentText(), output_type=pytesseract.Output.DICT)
            texts = []
            for i, raw in enumerate(data["text"]):
                text = raw.strip()
                if text and len(text) > 1:
                    texts.append(text)
            if not texts:
                self.panel.status.setText("No text detected")
                return

            self.panel.clear_results()
            source = self.panel.source.currentText()
            target = self.panel.target.currentText()
            unique = []
            seen = set()
            for text in texts:
                if text not in seen:
                    unique.append(text)
                    seen.add(text)

            for text in unique[:80]:
                key = (source, target, text)
                translated = self.panel.cache.get(key)
                if translated is None:
                    translated = self.panel.backend.translate(text, source, target)
                    self.panel.cache[key] = translated
                self.panel.add_result(TranslationResult(text, translated))
            self.panel.status.setText(f"Translated {len(unique)} text regions")
        except Exception as exc:
            self.panel.show()
            self.panel.status.setText(f"Error: {exc}")

    def run(self):
        self.panel.show()
        return self.app.exec()

if __name__ == "__main__":
    raise SystemExit(ScreenTranslatorApp().run())
