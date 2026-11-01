#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

python - <<'PY'
import json
import pathlib
import re
import sys

root = pathlib.Path("src/ds/dsh/DeepSeek")
catalog_source = (root / "UiLanguageCatalog.kt").read_text(encoding="utf-8")
string_literal = re.compile(r'"(?:\\.|[^"\\])*"')
cjk = re.compile(r'[\u3400-\u9fff]')


def decode_java(literal):
    try:
        return json.loads(literal)
    except json.JSONDecodeError:
        body = literal[1:-1]
        escapes = {
            "n": "\n", "r": "\r", "t": "\t", "b": "\b", "f": "\f",
            "\\": "\\", '"': '"', "'": "'",
        }
        def replace(match):
            token = match.group(1)
            if token.startswith("u"):
                return chr(int(token[1:], 16))
            return escapes.get(token, token)
        return re.sub(r'\\(u[0-9a-fA-F]{4}|.)', replace, body)


def joined_literals(source):
    return "".join(decode_java(value) for value in string_literal.findall(source))


def call_arguments(source, method):
    """Extract top-level Java call arguments without parsing nested expressions."""
    prefix = r'(?<![\w.])' if method == "text" else r'\b'
    pattern = re.compile(prefix + re.escape(method) + r'\s*\(')
    for match in pattern.finditer(source):
        cursor = match.end()
        depth = 1
        in_string = False
        escaped = False
        start = cursor
        args = []
        while cursor < len(source) and depth:
            char = source[cursor]
            if in_string:
                if escaped:
                    escaped = False
                elif char == "\\":
                    escaped = True
                elif char == '"':
                    in_string = False
            else:
                if char == '"':
                    in_string = True
                elif char == '(':
                    depth += 1
                elif char == ')':
                    depth -= 1
                    if depth == 0:
                        args.append(source[start:cursor].strip())
                        break
                elif char == ',' and depth == 1:
                    args.append(source[start:cursor].strip())
                    start = cursor + 1
            cursor += 1
        if args:
            yield args


def catalog_entries(source, method):
    entries = []
    for args in call_arguments(source, method):
        if len(args) < 2:
            continue
        chinese = joined_literals(args[0])
        english = joined_literals(args[1])
        if chinese:
            entries.append((chinese, english))
    return entries


entries = catalog_entries(catalog_source, "add")
entries += catalog_entries(catalog_source, "addExact")
exact = {}
for chinese, english in entries:
    if chinese in exact and exact[chinese] != english:
        raise SystemExit(f"Conflicting exact translations for {chinese!r}")
    exact[chinese] = english

bad_english = [(zh, en) for zh, en in exact.items() if cjk.search(en)]
if bad_english:
    print("English catalog values containing CJK:", file=sys.stderr)
    for chinese, english in bad_english[:20]:
        print(repr(chinese), "=>", repr(english), file=sys.stderr)
    raise SystemExit(1)


# Only inspect arguments that are actually handed to UI text sinks. Arbitrary Java
# literals also include prompts, search tokens, identifiers, examples and user-facing
# source documents; treating every literal as a UI label caused false positives.
ui_sources = [
    "DeekseepUi.java", "ChatAppearance.java", "ChatAppearanceUi.java",
    "SpatialMotionUi.java", "ImageCutoutUi.java", "AccountUi.java",
    "AccountManager.java", "AccountCredentialCodec.java", "ChatEditorUi.java",
    "ChatSearchUi.java", "DeekseepTools.java", "SettingsActivity.java",
    "ThemeColorUi.java",
]
sink_arguments = {
    "setText": (0,), "setHint": (0,), "setTitle": (0,), "setMessage": (0,),
    "setContentDescription": (0,), "setPositiveButton": (0,),
    "setNegativeButton": (0,), "setNeutralButton": (0,),
    "makeText": (1,), "text": (1,), "copy": (1, 2),
}
localized_markers = (
    "UiLanguage.text(", "UiLanguage.format(", "UiLanguage.dynamic(",
    "UiLanguageCatalog.toEnglish(", "t(", "tr(",
)
uncovered = []
for name in ui_sources:
    source = (root / name).read_text(encoding="utf-8")
    for method, indexes in sink_arguments.items():
        for args in call_arguments(source, method):
            for index in indexes:
                if index >= len(args):
                    continue
                expression = args[index]
                if any(marker in expression for marker in localized_markers):
                    continue
                literals = [decode_java(value) for value in string_literal.findall(expression)]
                chinese_literals = [value for value in literals if cjk.search(value)]
                if not chinese_literals:
                    continue
                if len(chinese_literals) == 1 and len(literals) == 1:
                    value = chinese_literals[0]
                    if value in exact:
                        continue
                    uncovered.append((name, method, value, "missing exact translation"))
                else:
                    uncovered.append((name, method, " | ".join(chinese_literals),
                                      "dynamic concatenation; use UiLanguage.text/format"))

if uncovered:
    print("Unlocalized Chinese text passed to UI sinks:", file=sys.stderr)
    for name, method, value, reason in sorted(set(uncovered))[:100]:
        print(f"{name}: {method}({value!r}) — {reason}", file=sys.stderr)
    raise SystemExit(1)

# Guard the production contract: catalog lookup must remain exact-only and dynamic
# text must not perform substring replacement.
if "return EXACT.containsKey(value)" not in catalog_source:
    raise SystemExit("UiLanguageCatalog.mightTranslate must use exact lookup")
if "return value;" not in catalog_source[catalog_source.find("fun toEnglish"):]:
    raise SystemExit("UiLanguageCatalog.toEnglish must preserve unknown text unchanged")

language_source = (root / "UiLanguage.java").read_text(encoding="utf-8")
if "UiLanguageCatalog.toEnglish(value.toString())" not in language_source:
    raise SystemExit("UiLanguage.dynamic must use the exact catalog")
wiring = {
    "Main.java": ["UiLanguage.refreshHost(act);", "UiLanguage.detectedLanguage(act)",
                  "UiLanguage.isChinese(act)"],
    "DeekseepUi.java": ["showLanguagePicker", "UiLanguage.MODE_AUTO",
                         "UiLanguage.MODE_CHINESE", "UiLanguage.MODE_ENGLISH"],
}
for name, required in wiring.items():
    source = (root / name).read_text(encoding="utf-8")
    for marker in required:
        if marker not in source:
            raise SystemExit(f"{name} is missing language wiring marker: {marker}")

print(f"Language catalog regression passed ({len(exact)} exact translations, "
      f"{len(ui_sources)} UI sources and UI sink checks)")
PY
