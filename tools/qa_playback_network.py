"""Release D-pad: buffering pause, network error, Home/foreground recovery.

Start with a live card focused. Only the named project AVD is modified;
Wi-Fi is restored even when an assertion fails.
"""
import re, subprocess, time, xml.etree.ElementTree as ET
from device import ADB, SERIAL, ROOT, adb, hierarchy, observe

assert 'Twitch20Test' in adb('emu', 'avd', 'name', capture_output=True, text=True).stdout
directory = ROOT / 'evidence' / 'beta5-network'
directory.mkdir(exist_ok=True)

def texts(): return {n.get('text', '') for n in ET.fromstring(hierarchy()).iter('node')}
def key(value): adb('shell', 'input', 'keyevent', value, stdout=subprocess.DEVNULL)
def foreground():
    adb('shell', 'am', 'start', '-n', 'com.oprawa.twitch20/.MainActivity', stdout=subprocess.DEVNULL)
def wait_for(predicate, seconds=60):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        values = texts()
        if predicate(values): return values
        time.sleep(.5)
    observe('beta5-network-timeout')
    raise AssertionError('Expected state not reached')

logpath = directory / 'playback-numeric.log'
with logpath.open('wb') as output:
    logger = subprocess.Popen([str(ADB), '-s', SERIAL, 'logcat', '-T', '1', '-v',
        'threadtime', '-s', 'TwitchPlayback:I', '*:S'], stdout=output, stderr=subprocess.DEVNULL)
    try:
        time.sleep(.5)
        key('KEYCODE_DPAD_CENTER')
        wait_for(lambda t: 'Пауза' in t and 'Буферизация…' not in t)
        observe('beta5-network-before')
        adb('shell', 'svc', 'wifi', 'disable')
        key('KEYCODE_HOME')
        time.sleep(2)
        foreground()
        state = wait_for(lambda t: 'Буферизация…' in t)
        if 'Пауза' not in state:
            key('KEYCODE_DPAD_CENTER')
            state = texts()
        assert 'Пауза' in state
        # HUD on foreground focuses its Pause action.
        key('KEYCODE_DPAD_CENTER')
        wait_for(lambda t: 'К эфиру' in t and 'Буферизация…' in t, 10)
        observe('beta5-buffering-paused')
        print('PASS buffering pause intent', flush=True)
        key('KEYCODE_DPAD_CENTER')
        wait_for(lambda t: 'Видео недоступно' in t, 75)
        observe('beta5-network-error')
        key('KEYCODE_HOME')
        adb('shell', 'svc', 'wifi', 'enable')
        time.sleep(15)
        foreground()
        wait_for(lambda t: 'Видео недоступно' not in t and 'Буферизация…' not in t, 60)
        key('KEYCODE_DPAD_CENTER')  # Reveal auto-hidden HUD if needed.
        observe('beta5-network-foreground-restored')
        state = texts()
        assert 'Видео недоступно' not in state and 'Буферизация…' not in state
        print('PASS error → Home → network restored → foreground clears error', flush=True)
    finally:
        adb('shell', 'svc', 'wifi', 'enable')
        logger.terminate()
        logger.wait(timeout=10)
