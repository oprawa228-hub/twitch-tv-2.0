"""Native release recovery from real network errors; no injected player state.

Start on the live catalog with a live card focused. This tests network error
recovery and quality persistence, not reproduction/fixing of an OS codec fault.
"""
import subprocess, time, xml.etree.ElementTree as ET
from device import adb, hierarchy, observe, focus

assert 'Twitch20Test' in adb('emu', 'avd', 'name', capture_output=True, text=True).stdout

def key(name): adb('shell', 'input', 'keyevent', name, stdout=subprocess.DEVNULL)
def texts(): return {n.get('text', '') for n in ET.fromstring(hierarchy()).iter('node')}
def wait_for(predicate, timeout=60):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        state = texts()
        if predicate(state): return state
        time.sleep(.25)
    observe('beta5-final-quality-timeout')
    raise AssertionError('Expected native state not reached')

def wait_ready():
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        state = texts()
        loading = any('Буферизация' in t or 'Подключение к видео' in t for t in state)
        if 'Пауза' in state and not loading: return
        if not loading and 'Видео недоступно' not in state and 'Качество' not in state:
            key('KEYCODE_DPAD_CENTER')  # Reveal hidden controls, never toggle visible Pause.
        time.sleep(.25)
    observe('beta5-final-quality-not-ready')
    raise AssertionError('Playback did not become ready')

def select_quality(label):
    state = texts()
    if 'Качество' not in state: key('KEYCODE_DPAD_CENTER')
    focus('Качество'); key('KEYCODE_DPAD_CENTER')
    state = texts()
    actual = label if label in state else '✓ ' + label
    assert actual in state
    focus(actual); key('KEYCODE_DPAD_CENTER')
    wait_ready()

def network_error(label):
    adb('shell', 'svc', 'wifi', 'disable')
    key('KEYCODE_HOME')
    time.sleep(2)
    adb('shell', 'am', 'start', '-n', 'com.oprawa.twitch20/.MainActivity', stdout=subprocess.DEVNULL)
    wait_for(lambda state: 'Видео недоступно' in state, 75)
    observe(label)
    assert 'Качество' in texts(), 'Cached quality choices unavailable in error'
    adb('shell', 'svc', 'wifi', 'enable')
    time.sleep(10)  # Android Wi-Fi enable returns before validation/DNS is ready.

try:
    state = texts()
    assert 'Прямые эфиры' in state
    key('KEYCODE_DPAD_CENTER'); wait_ready()
    select_quality('480p'); observe('beta5-final-quality480-before-error')
    network_error('beta5-final-error-quality-available')
    select_quality('360p'); observe('beta5-final-error-quality360-recovered')
    print('PASS error → Quality360 → playback', flush=True)
    network_error('beta5-final-error-before-retry')
    focus('Повторить'); key('KEYCODE_DPAD_CENTER'); wait_ready()
    observe('beta5-final-retry360-playing')
    focus('Качество'); key('KEYCODE_DPAD_CENTER')
    assert '✓ 360p' in texts(), 'Fresh-source Retry lost manual quality'
    observe('beta5-final-retry360-selected')
    print('PASS fresh-source Retry retains selected360', flush=True)
    key('KEYCODE_BACK')
    for _ in range(4):
        if 'Прямые эфиры' in texts(): break
        key('KEYCODE_BACK')
    else: raise AssertionError('Did not return to catalog')
finally:
    adb('shell', 'svc', 'wifi', 'enable')
