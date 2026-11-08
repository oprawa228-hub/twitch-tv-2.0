"""Real Twitch VOD controls and seek confirmation/cancellation; primary AVD only."""
import json, time, xml.etree.ElementTree as ET
import device as d
assert 'Twitch20Test' in d.adb('emu','avd','name',capture_output=True,text=True).stdout
def key(k): d.adb('shell','input','keyevent','KEYCODE_'+k)
def nodes(): return list(ET.fromstring(d.hierarchy()).iter('node'))
def labels(): return [n.get('text') or n.get('content-desc') for n in nodes()]
def wait(label):
    end=time.monotonic()+40
    while time.monotonic()<end:
        if label in labels(): return
    raise AssertionError('Missing '+label)
def action(label): d.focus(label); key('DPAD_CENTER')
def timeline():
    return next(n.get('content-desc') for n in nodes() if n.get('content-desc','').startswith('Перемотка записи:'))
def capture(name):
    (d.ROOT/'evidence'/'android'/('beta9-'+name+'.png')).write_bytes(d.adb('exec-out','screencap','-p',capture_output=True).stdout)

wait('Прямые эфиры')
d.adb('shell','input','keyevent','--longpress','KEYCODE_DPAD_CENTER')
wait('Клипы')
video=next(n.get('content-desc') for n in nodes() if ', Запись,' in n.get('content-desc',''))
action(video); wait('Пауза'); action('Пауза'); wait('Смотреть')
capture('vod-controls')
key('DPAD_UP'); before=timeline()
key('DPAD_RIGHT'); key('DPAD_RIGHT'); key('DPAD_RIGHT')
draft=timeline(); assert 'Перейти к' in draft
capture('vod-seek-draft')
key('BACK'); assert timeline()==before, (before,timeline())
assert 'Смотреть' in labels()
key('DPAD_RIGHT'); key('DPAD_RIGHT'); target=timeline().replace('Перейти к ','')
key('DPAD_CENTER'); assert timeline()==target, (target,timeline())
assert 'Смотреть' in labels(); capture('vod-seek-confirmed')
key('DPAD_DOWN'); action('Качество'); wait('Качество видео')
options=[n.get('text') for n in nodes() if n.get('text') and 'p' in n.get('text') and len(n.get('text'))<25]
if options:
    action(options[-1]); wait('Качество'); action('Качество'); wait('Качество видео')
    focused=next((n for n in nodes() if n.get('focused')=='true'),None)
    assert focused is not None
    contents=' '.join(n.get('text','') for n in focused.iter('node'))
    assert '✓' in contents, contents
    capture('quality-selected-focus')
key('BACK'); key('BACK'); key('BACK'); wait('Клипы'); key('BACK'); wait('Прямые эфиры')
(d.ROOT/'evidence'/'beta9-real-vod-result.json').write_text(json.dumps({
    'before':before, 'draft':draft, 'confirmed':target, 'cancel_keeps_position':'PASS',
    'confirm_keeps_pause':'PASS', 'quality_selected_focus':'PASS' if options else 'NO_OPTIONS',
    'source':'real Twitch VOD / Android TV emulator'},indent=2,ensure_ascii=False),encoding='utf-8')
print('Real VOD beta9 route PASS')
