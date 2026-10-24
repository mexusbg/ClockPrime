"""Check every translation against the English strings, without an Android build.

Fails on: strings unknown to English, translated non-translatable strings, format arguments that differ
from English, plurals without an "other" form, unescaped apostrophes, languages missing from
res/xml/locales_config.xml, and store listings over F-Droid's length limits. Languages in COMPLETE must
translate every string; others may be partial (missing strings fall back to English) and only report coverage.
"""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

COMPLETE = {'bg'}
root = Path(__file__).resolve().parents[1]
res = root / 'app/src/main/res'
ARG = re.compile(r'%(?:\d+\$)?[sd]')
errors = []


def catalog(directory):
    return {e.attrib['name']: e for e in ET.parse(directory / 'strings.xml').getroot()}


def language(qualifier):
    """values-pt-rBR -> pt-BR, values-b+sr+Latn -> sr-Latn (the tags locales_config uses)."""
    if qualifier.startswith('b+'):
        return '-'.join(qualifier[2:].split('+'))
    return re.sub(r'-r([A-Z]{2})$', r'-\1', qualifier)


def text(element):
    return ''.join(element.itertext())


def args(value):
    return sorted(ARG.findall(value))


english = catalog(res / 'values')
translatable = {k for k, v in english.items() if v.attrib.get('translatable') != 'false'}
listed = {e.attrib['{http://schemas.android.com/apk/res/android}name'] for e in ET.parse(res / 'xml/locales_config.xml').getroot()}
folders = sorted(d for d in res.glob('values-*') if (d / 'strings.xml').exists())
for folder in folders:
    tag = language(folder.name[len('values-'):])
    strings = catalog(folder)
    if tag not in listed:
        errors.append(f'{tag}: add <locale android:name="{tag}"/> to res/xml/locales_config.xml')
    for name, translated in strings.items():
        where = f'{tag}/{name}'
        if name not in english:
            errors.append(f'{where}: not in values/strings.xml')
            continue
        if name not in translatable:
            errors.append(f'{where}: marked translatable="false" in English')
            continue
        original = english[name]
        if original.tag != translated.tag:
            errors.append(f'{where}: <{translated.tag}> but English uses <{original.tag}>')
            continue
        if original.tag == 'plurals':
            forms = {item.attrib['quantity']: text(item) for item in translated}
            if 'other' not in forms:
                errors.append(f'{where}: plural needs an "other" form')
            allowed = set(args(text(next(i for i in original if i.attrib['quantity'] == 'other'))))
            for quantity, value in forms.items():
                if not set(args(value)) <= allowed:
                    errors.append(f'{where}[{quantity}]: format arguments {args(value)} not in English {sorted(allowed)}')
            values = forms.values()
        else:
            if original.attrib.get('formatted') != 'false' and args(text(original)) != args(text(translated)):
                errors.append(f'{where}: format arguments {args(text(translated))}, English has {args(text(original))}')
            values = [text(translated)]
        if any(re.search(r"(?<!\\)'", v) for v in values):
            errors.append(f"{where}: escape apostrophes as \\'")
    missing = translatable - set(strings)
    if tag in COMPLETE and missing:
        errors.append(f'{tag}: {len(missing)} strings missing: {", ".join(sorted(missing)[:10])}')
    print(f'{tag}: {len(translatable) - len(missing)}/{len(translatable)} strings translated')

# Store listing (F-Droid limits: title 50, short description 80, full description 4000 characters).
for folder in sorted((root / 'fastlane/metadata/android').iterdir()):
    for name, limit in (('title.txt', 50), ('short_description.txt', 80), ('full_description.txt', 4000)):
        path = folder / name
        if path.exists() and len(path.read_text(encoding='utf-8').strip()) > limit:
            errors.append(f'fastlane {folder.name}/{name}: longer than {limit} characters')

if errors:
    print('\n'.join(errors), file=sys.stderr)
    sys.exit(1)
print('Translations verified.')
