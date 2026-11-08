"""Native layout evidence on the named primary emulator; restores temporary display settings."""
import argparse, json, subprocess, time, xml.etree.ElementTree as ET
from pathlib import Path
import device as d

assert 'Twitch20Test' in d.adb('emu', 'avd', 'name', capture_output=True, text=True).stdout
package = 'com.oprawa.twitch20'
out = d.ROOT / 'evidence' / 'android'

def key(name): d.adb('shell', 'input', 'keyevent', 'KEYCODE_' + name, stdout=subprocess.DEVNULL)
def wait_text(label):
    end = time.monotonic() + 25
    while time.monotonic() < end:
        if any(n.get('text') == label for n in ET.fromstring(d.hierarchy()).iter('node')): return
    raise AssertionError('Missing ' + label)
def capture(label):
    (out / (label + '.png')).write_bytes(d.adb('exec-out', 'screencap', '-p', capture_output=True).stdout)
def launch():
    d.adb('shell', 'am', 'force-stop', package)
    d.adb('shell', 'am', 'start', '-n', package + '/.MainActivity', stdout=subprocess.DEVNULL)
    wait_text('Прямые эфиры')
def settings():
    d.focus('Настройки'); key('DPAD_CENTER'); wait_text('Предпросмотр')

font = d.adb('shell', 'settings', 'get', 'system', 'font_scale', capture_output=True, text=True).stdout.strip()
parser = argparse.ArgumentParser()
parser.add_argument('--only', choices=['720', '1080', '4k', 'large-text'])
args = parser.parse_args()
try:
    for name, size, density, scale in [('720', '1280x720', 213, '1.0'), ('1080', '1920x1080', 320, '1.0'),
                                      ('4k', '3840x2160', 640, '1.0'), ('large-text', '1280x720', 213, '1.3')]:
        if args.only and args.only != name: continue
        d.adb('shell', 'wm', 'size', size); d.adb('shell', 'wm', 'density', str(density))
        actual = d.adb('shell', 'wm', 'size', capture_output=True, text=True).stdout.strip().splitlines()[-1]
        if not actual.endswith(size):
            print('UNSUPPORTED ' + name + ': requested ' + size + ', device reports ' + actual, flush=True)
            continue
        d.adb('shell', 'settings', 'put', 'system', 'font_scale', scale)
        time.sleep(2)  # Allow Android's asynchronous display/font configuration restart to settle.
        launch(); time.sleep(2)
        capture('beta9-release-catalog-' + name)
        settings(); capture('beta9-release-settings-' + name)
        d.focus('Настроить чат поверх видео'); key('DPAD_CENTER')
        wait_text('Назад к обзору'); capture('beta9-release-overlay-' + name)
        key('BACK'); d.focus('Настроить кинотеатр'); key('DPAD_CENTER')
        wait_text('Назад к обзору'); capture('beta9-release-theater-' + name)
        key('BACK'); key('BACK')
        print('Captured native ' + name + ' at ' + size + '/' + str(density) + ' font ' + scale, flush=True)
finally:
    d.adb('shell', 'wm', 'size', 'reset'); d.adb('shell', 'wm', 'density', '213')
    if font == 'null': d.adb('shell', 'settings', 'delete', 'system', 'font_scale')
    else: d.adb('shell', 'settings', 'put', 'system', 'font_scale', font)
    launch()
