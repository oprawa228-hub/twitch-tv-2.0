"""Real D-pad save/restart/restore check for independent chat profiles; no token/log reads."""
import argparse, json, time, xml.etree.ElementTree as ET
from pathlib import Path
import device as d

p = argparse.ArgumentParser()
p.add_argument('--serial', default='emulator-5560')
p.add_argument('--prefix', default='chat-profiles')
a = p.parse_args()
expected = {'emulator-5560': 'Twitch20Test', 'emulator-5562': 'Twitch20Release'}
assert a.serial in expected
d.SERIAL = a.serial
assert expected[a.serial] in d.adb('emu', 'avd', 'name', capture_output=True, text=True).stdout
package = 'com.oprawa.twitch20'
out = d.ROOT / 'evidence'
labels = ['Чат — размер текста', 'Чат — фон', 'Кинотеатр — размер текста', 'Кинотеатр — фон']

def nodes():
    return list(ET.fromstring(d.hierarchy()).iter('node'))

def key(code):
    d.adb('shell', 'input', 'keyevent', 'KEYCODE_' + code)

def wait_text(text):
    deadline = time.monotonic() + 25
    while time.monotonic() < deadline:
        if any(n.get('text') == text for n in nodes()): return
    raise AssertionError('Missing screen: ' + text)

def open_settings():
    wait_text('Прямые эфиры')
    d.focus('Настройки'); key('DPAD_CENTER'); wait_text('Предпросмотр')

def row(label, direction='DOWN'):
    for _ in range(20):
        found = next((n.get('content-desc') for n in nodes()
                      if n.get('content-desc', '').startswith(label + ': ')), None)
        if found:
            d.focus(found)
            return found.split(': ', 1)[1]
        key('DPAD_' + direction)
    raise AssertionError('Missing setting: ' + label)

def set_number(label, target, direction='DOWN'):
    value = row(label, direction)
    for _ in range(14):
        current = int(value.rstrip('%'))
        if current == target: return
        key('DPAD_RIGHT' if current < target else 'DPAD_LEFT')
        value = row(label, direction)
    raise AssertionError('Cannot select value: ' + label)

def save():
    d.focus('Сохранить'); key('DPAD_CENTER'); wait_text('Прямые эфиры')

def capture(name):
    with (out / 'android' / (a.prefix + '-' + name + '.png')).open('wb') as f:
        d.adb('exec-out', 'screencap', '-p', stdout=f)

d.adb('shell', 'am', 'force-stop', package)
d.adb('shell', 'am', 'start', '-n', package + '/.MainActivity', capture_output=True)
open_settings()
initial_modes = [n.get('content-desc') for n in nodes()
                 if n.get('content-desc', '').startswith(('Чат поверх видео: ', 'Кинотеатр: '))]
original = {label: int(row(label).rstrip('%')) for label in labels}
trial = dict(zip(labels, [20, 35, 14, 85]))
# Opacity increments preserve the existing 5% remainder after beta.6 migration.
for label in [labels[1], labels[3]]:
    trial[label] = 30 + original[label] % 10 if label == labels[1] else 80 + original[label] % 10
(out / (a.prefix + '-before.json')).write_text(json.dumps({'profiles': original, 'modes': initial_modes}, ensure_ascii=False), encoding='utf-8')
try:
    for index, (label, value) in enumerate(trial.items()):
        set_number(label, value, 'UP' if index == 0 else 'DOWN')
    capture('draft'); save()
    d.adb('shell', 'am', 'force-stop', package)
    d.adb('shell', 'am', 'start', '-n', package + '/.MainActivity', capture_output=True)
    open_settings()
    modes = [n.get('content-desc') for n in nodes()
             if n.get('content-desc', '').startswith(('Чат поверх видео: ', 'Кинотеатр: '))]
    assert modes == initial_modes, 'Editing profiles activated a different mode'
    actual = {}
    for label in labels:
        actual[label] = int(row(label).rstrip('%'))
        if label == labels[1]: capture('overlay-saved')
    capture('theater-saved')
    assert actual == trial, (actual, trial)
    (out / (a.prefix + '-result.json')).write_text(json.dumps({'profiles': actual, 'modes': modes, 'restart': 'PASS'}, ensure_ascii=False), encoding='utf-8')
    print('Independent profiles survive process restart; mode unchanged: PASS')
finally:
    for index, (label, value) in enumerate(original.items()):
        set_number(label, value, 'UP' if index == 0 else 'DOWN')
    save()
    print('Original profiles restored through UI')
