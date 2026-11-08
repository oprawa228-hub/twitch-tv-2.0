"""Real Twitch live player, primary named emulator, D-pad only. Preserve saved chat mode."""
import json, time, xml.etree.ElementTree as ET
import device as d

assert 'Twitch20Test' in d.adb('emu', 'avd', 'name', capture_output=True, text=True).stdout
def key(code): d.adb('shell', 'input', 'keyevent', 'KEYCODE_' + code)
def nodes(): return list(ET.fromstring(d.hierarchy()).iter('node'))
def labels(): return [n.get('text') or n.get('content-desc') for n in nodes()]
def wait(label):
    deadline = time.monotonic()+30
    while time.monotonic()<deadline:
        if label in labels(): return
    raise AssertionError('Missing '+label)
def action(label): d.focus(label); key('DPAD_CENTER')
def capture(name):
    (d.ROOT/'evidence'/'android'/('beta9-'+name+'.png')).write_bytes(
        d.adb('exec-out','screencap','-p',capture_output=True).stdout)

wait('Прямые эфиры'); key('DPAD_CENTER'); wait('Пауза')
key('BACK'); key('DPAD_CENTER'); wait('Пауза') # first OK reveals, does not pause
action('Пауза'); wait('К эфиру')
assert 'Чат: вкл' in labels(), 'Expected original primary overlay profile'
capture('live-overlay')
action('Кинотеатр: выкл'); wait('Кинотеатр: вкл')
assert 'Чат: выкл' in labels()
capture('live-theater')
d.focus('Кинотеатр: вкл')
d.adb('shell','input','keyevent','--longpress','KEYCODE_DPAD_CENTER')
wait('Просмотр'); wait('Размер текста: Средний')
capture('theater-profile')
action('Размер текста: Средний'); wait('Кинотеатр — размер текста: Большой')
d.focus('Кинотеатр — размер текста: Большой'); capture('font-preview')
key('BACK'); wait('Размер текста: Средний')
key('BACK'); wait('Настроить кинотеатр')
key('BACK'); wait('Кинотеатр: вкл')
action('Чат: выкл'); wait('Чат: вкл')
assert 'Кинотеатр: выкл' in labels()
action('Качество'); wait('Качество видео'); capture('quality')
key('BACK'); wait('Качество')
key('BACK'); key('BACK'); wait('Прямые эфиры')
(d.ROOT/'evidence'/'beta9-real-live-result.json').write_text(json.dumps({
    'first_ok_reveals_only': 'PASS', 'exclusive_modes':'PASS', 'theater_long_ok_profile':'PASS',
    'font_preview_cancel':'PASS', 'nested_back':'PASS', 'quality_back':'PASS',
    'original_mode_restored': True, 'source':'real Twitch live / primary Android TV emulator'
}, indent=2), encoding='utf-8')
print('Real live beta9 route PASS')
