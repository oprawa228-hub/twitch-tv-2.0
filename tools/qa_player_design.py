"""Exact-APK native player design route on primary; keeps the original overlay mode."""
import json, time, xml.etree.ElementTree as ET
import device as d

assert 'Twitch20Test' in d.adb('emu', 'avd', 'name', capture_output=True, text=True).stdout
def key(code): d.adb('shell', 'input', 'keyevent', 'KEYCODE_' + code)
def labels():
    return [n.get('text') or n.get('content-desc') for n in ET.fromstring(d.hierarchy()).iter('node')]
def wait(label):
    until = time.monotonic() + 30
    while time.monotonic() < until:
        if label in labels(): return
    raise AssertionError('Missing ' + label)
def action(label): d.focus(label); key('DPAD_CENTER')
def capture(name):
    (d.ROOT / 'evidence' / 'android' / ('beta8-delivery-' + name + '.png')).write_bytes(
        d.adb('exec-out', 'screencap', '-p', capture_output=True).stdout)

wait('Прямые эфиры'); capture('catalog')
key('DPAD_CENTER'); wait('Пауза')
# Pause before navigating so the HUD cannot time out during evidence capture.
action('Пауза'); wait('К эфиру')
assert 'Чат: вкл' in labels(), 'This primary route expects the recorded original OVERLAY mode'
try:
    action('Кинотеатр: выкл')
    current = labels(); assert 'Чат: выкл' in current and 'Кинотеатр: вкл' in current
    capture('theater-player')
    action('Чат: выкл')
    current = labels(); assert 'Чат: вкл' in current and 'Кинотеатр: выкл' in current
    action('Настройки'); wait('Просмотр')
    action('Настроить кинотеатр'); wait('Назад к обзору')
    assert any(x and x.startswith('Профиль не включён') for x in labels())
    capture('inactive-player-profile')
    key('BACK'); wait('Настроить кинотеатр')
    key('BACK'); wait('Настройки')
    action('Качество'); wait('Качество видео')
    capture('quality')
    key('BACK'); wait('Качество')
    key('BACK'); key('BACK'); wait('Прямые эфиры')
    (d.ROOT / 'evidence' / 'beta8-player-design-result.json').write_text(json.dumps({
        'pause': 'PASS', 'exclusive_modes': 'PASS', 'inactive_profile': 'PASS',
        'nested_back': 'PASS', 'quality_back': 'PASS', 'catalog_return': 'PASS',
        'original_overlay_mode_restored': True}), encoding='utf-8')
    print('Player design route PASS', flush=True)
except Exception:
    capture('route-failure')
    raise
