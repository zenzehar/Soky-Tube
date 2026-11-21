#!/usr/bin/env python3
"""English-only noncommercial source patch for gadmin2151/ParkedTube v1.5.0.

Run from the upstream ParkedTube repository root. The original LICENSE and
NOTICE remain applicable. This patch does not bypass driver-distraction rules.
"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path.cwd()
STRINGS = ROOT / "app/src/main/res/values/strings.xml"
MAIN = ROOT / "app/src/main/java/dev/codex/aaparkedyoutube/MainActivity.java"
PREFS = ROOT / "app/src/main/java/dev/codex/aaparkedyoutube/BrowserPreferences.java"
GRADLE = ROOT / "app/build.gradle"
VERSION = ROOT / "gradle.properties"

WORDS = {
    "app_name": "ParkedTube English",
    "loading_youtube": "Loading YouTube",
    "error_title": "Unable to open YouTube",
    "error_message": "YouTube could not be loaded. Check your connection and WebView, then try again.",
    "retry": "Try again",
    "search_placeholder": "Search YouTube",
    "search": "Search",
    "space": "Space",
    "go_back": "Back",
    "home": "YouTube Home",
    "bookmarks": "Bookmarks",
    "settings": "Settings",
    "close": "Close",
    "clear_text": "Clear text",
    "recent_searches": "Recent searches",
    "clear_searches": "Clear search history",
    "cancel": "Cancel",
    "clear": "Clear",
    "history_cleared": "Search history cleared",
    "save_current_page": "Bookmark this page",
    "remove_current_bookmark": "Remove this page from bookmarks",
    "remove_bookmark": "Remove bookmark",
    "remove_named_bookmark": "Remove bookmark: %1$s",
    "no_bookmarks": "No bookmarks yet",
    "refresh_page": "Refresh page",
    "readability": "Text size",
    "zoom_percent": "%1$d%%",
    "resume_last_page": "Open last page",
    "backspace": "Backspace",
    "switch_language": "Switch language",
    "switch_symbols": "Numbers and symbols",
    "blocked_link": "This link is not a YouTube page",
}

def one(source: str, old: str, new: str, description: str) -> str:
    count = source.count(old)
    if count != 1:
        raise ValueError(f"{description}: expected 1 occurrence, found {count}")
    return source.replace(old, new, 1)

def regexp(source: str, pattern: str, new: str, description: str) -> str:
    output, count = re.subn(pattern, lambda m: new, source, count=1, flags=re.S)
    if count != 1:
        raise ValueError(f"{description}: expected 1 occurrence, found {count}")
    return output

def main() -> None:
    for path in (STRINGS, MAIN, PREFS, GRADLE, VERSION):
        if not path.is_file():
            raise FileNotFoundError(path)
    root = ET.parse(STRINGS).getroot()
    names = set()
    for element in root.findall("string"):
        name = element.attrib["name"]
        if name not in WORDS:
            raise ValueError("Untranslated string: " + name)
        element.text = WORDS[name]
        names.add(name)
    if names != set(WORDS):
        raise ValueError("Missing keys: " + repr(set(WORDS) - names))
    ET.indent(root, space="    ")
    new_xml = '<?xml version="1.0" encoding="utf-8"?>\n' + ET.tostring(root, encoding="unicode") + "\n"

    activity = MAIN.read_text(encoding="utf-8")
    activity = regexp(activity, r'    private static final String\[\] RU_ROWS = \{\n.*?\n    \};\n', "", "Remove Russian key rows")
    activity = one(activity, '    private boolean keyboardEnglish;\n', '', "Remove Russian keyboard state")
    activity = one(activity, '        keyboardEnglish = preferences.englishKeyboard();\n', '', "Remove Russian keyboard default")
    activity = one(activity, 'String[] rows = keyboardSymbols ? SYMBOL_ROWS : keyboardEnglish ? EN_ROWS : RU_ROWS;', 'String[] rows = keyboardSymbols ? SYMBOL_ROWS : EN_ROWS;', "English-only keyboard")
    activity = regexp(activity, r'        Button language = makeKeyboardButton\(keyboardEnglish \? "РУ" : "EN",.*?        addKeyboardKey\(bottomRow, language, 1\.1f\);\n\n', '', "Remove language toggle")
    # The upstream YouTube search CSS selector contains a Russian "Search" fallback.
    # Use the English selector only; third-party video titles are untouched.
    activity = activity.replace("Поиск", "Search")
    activity = one(activity, 'private static final String YOUTUBE_URL = "https://www.youtube.com/";', 'private static final String YOUTUBE_URL = "https://www.youtube.com/?hl=en";', "English YouTube homepage")
    activity = one(activity, 'navigateTo("https://www.youtube.com/results?search_query=" + Uri.encode(clean));', 'navigateTo("https://www.youtube.com/results?search_query=" + Uri.encode(clean) + "&hl=en");', "English search URL")

    preferences = PREFS.read_text(encoding="utf-8")
    preferences = one(preferences,
       '    boolean englishKeyboard() { return preferences.getBoolean("english_keyboard", false); }\n'
       '    void setEnglishKeyboard(boolean english) { preferences.edit().putBoolean("english_keyboard", english).apply(); }\n',
       '', "Remove language preference")
    gradle = one(GRADLE.read_text(encoding="utf-8"),
       'applicationId "dev.codex.aaparkedyoutube"',
       'applicationId "dev.codex.aaparkedyoutube.en"',
       "Unique install package")
    version = VERSION.read_text(encoding="utf-8")
    version = one(version, "PARKEDTUBE_VERSION_NAME=1.5.0", "PARKEDTUBE_VERSION_NAME=1.5.0-en.2", "English version name")
    version = one(version, "PARKEDTUBE_VERSION_CODE=7", "PARKEDTUBE_VERSION_CODE=9", "Version code")
    for path, contents in ((STRINGS, new_xml), (MAIN, activity), (PREFS, preferences), (GRADLE, gradle), (VERSION, version)):
        if re.search(r"[\u0400-\u04FF]", contents):
            raise ValueError("Cyrillic text in " + str(path))
        path.write_text(contents, encoding="utf-8")
        print("Updated:", path.relative_to(ROOT))
    for folder in (ROOT / "app/src/main/java", ROOT / "app/src/main/res"):
        for path in folder.rglob("*"):
            if path.suffix in (".xml", ".java", ".html", ".js") and re.search(r"[\u0400-\u04FF]", path.read_text(encoding="utf-8")):
                raise ValueError("Untranslated Cyrillic text in " + str(path))
    print("PASS: Native Android resources and keyboard are English-only.")
    print("NOTE: YouTube website text and video content can follow account preferences.")

if __name__ == "__main__":
    main()
