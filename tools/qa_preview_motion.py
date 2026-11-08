"""Record the actual release preview and verify motion-off pixels; no saved preferences changed."""
import hashlib, json, subprocess, time
import device as d

assert 'Twitch20Test' in d.adb('emu', 'avd', 'name', capture_output=True, text=True).stdout
package = 'com.oprawa.twitch20'
out = d.ROOT / 'evidence' / 'android'
original = d.adb('shell', 'settings', 'get', 'global', 'animator_duration_scale', capture_output=True, text=True).stdout.strip()
def key(name): d.adb('shell', 'input', 'keyevent', 'KEYCODE_' + name)
def scale(value): d.adb('shell', 'settings', 'put', 'global', 'animator_duration_scale', value)
def shot(name):
    data = d.adb('exec-out', 'screencap', '-p', capture_output=True).stdout
    (out / (name + '.png')).write_bytes(data)
    return hashlib.sha256(data).hexdigest()
def pair(name):
    time.sleep(1)
    a = shot(name + '-a'); time.sleep(2); b = shot(name + '-b')
    assert a == b, name + ' was not static'
    return a
try:
    scale('0')
    system_static = pair('beta8-final-system-motion-off')
    d.focus('Интерфейс'); key('DPAD_CENTER')
    d.focus('Анимации: Включены'); key('DPAD_CENTER')
    scale('1')
    app_static = pair('beta8-final-app-motion-off')
    key('DPAD_CENTER')  # Same focused switch: enable draft animation without saving.
    d.adb('shell', 'dumpsys', 'gfxinfo', package, 'reset', capture_output=True)
    print('Recording 26 seconds of native motion', flush=True)
    d.adb('shell', 'screenrecord', '--time-limit', '26', '--bit-rate', '6000000', '/sdcard/beta8-motion.mp4')
    d.adb('pull', '/sdcard/beta8-motion.mp4', str(out / 'beta8-final-motion.mp4'), capture_output=True)
    (d.ROOT / 'evidence' / 'beta8-motion-gfx.txt').write_bytes(d.adb('shell', 'dumpsys', 'gfxinfo', package, capture_output=True).stdout)
    key('HOME')
    d.adb('shell', 'dumpsys', 'gfxinfo', package, 'reset', capture_output=True)
    time.sleep(3)
    (d.ROOT / 'evidence' / 'beta8-motion-background-gfx.txt').write_bytes(d.adb('shell', 'dumpsys', 'gfxinfo', package, capture_output=True).stdout)
    d.adb('shell', 'am', 'start', '-n', package + '/.MainActivity', capture_output=True)
    time.sleep(2); shot('beta8-final-motion-resumed')
    scale('0')
    resumed_static = pair('beta8-final-resumed-motion-off')
    key('BACK'); key('BACK')  # Discard the whole settings draft.
    (d.ROOT / 'evidence' / 'beta8-motion-result.json').write_text(json.dumps({
        'system_off_identical_png': system_static, 'app_off_identical_png': app_static,
        'resumed_system_off_identical_png': resumed_static, 'video_seconds_requested': 26,
        'draft_discarded': True}, indent=2), encoding='utf-8')
    print('System off / app off / resumed off static pairs: PASS; draft discarded', flush=True)
finally:
    if original == 'null': d.adb('shell', 'settings', 'delete', 'global', 'animator_duration_scale')
    else: scale(original)
