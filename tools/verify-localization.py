"""Verify complete Bulgarian coverage and matching format arguments without an Android build."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

res = Path(__file__).resolve().parents[1] / 'app/src/main/res'
catalogs = [{e.attrib['name']: e for e in ET.parse(res / directory / 'strings.xml').getroot()}
            for directory in ('values', 'values-bg')]
en, bg = catalogs
assert {k for k, v in en.items() if v.attrib.get('translatable') != 'false'} == set(bg)
for name, translated in bg.items():
    original = en[name]
    assert original.tag == translated.tag, name
    original_text, translated_text = ''.join(original.itertext()), ''.join(translated.itertext())
    assert sorted(re.findall(r'%\d*\$?[sd]', original_text)) == sorted(re.findall(r'%\d*\$?[sd]', translated_text)), name
    if original.tag == 'plurals':
        assert {x.attrib['quantity'] for x in original} == {x.attrib['quantity'] for x in translated}, name
print(f'{len(bg)} translated resources: coverage and format arguments verified.')
