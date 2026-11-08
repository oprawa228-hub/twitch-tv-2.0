"""Native same-Activity relaunch and paused/playing VOD intent regression.

Start with an actual VOD already playing. Uses a plain explicit launch like an
external launcher; never adds CLEAR_TASK/REORDER_TO_FRONT to mask app behavior.
"""
import argparse, re, subprocess, time, xml.etree.ElementTree as ET
import device

p = argparse.ArgumentParser()
p.add_argument('--serial', required=True, choices=['emulator-5560', 'emulator-5562'])
p.add_argument('--label', default='beta6-relaunch')
args = p.parse_args()
device.SERIAL = args.serial
expected = {'emulator-5560': 'Twitch20Test', 'emulator-5562': 'Twitch20Release'}[args.serial]
assert expected in device.adb('emu', 'avd', 'name', capture_output=True, text=True).stdout

def key(value): device.adb('shell', 'input', 'keyevent', value, stdout=subprocess.DEVNULL)
def labels(): return {n.get('text', '') for n in ET.fromstring(device.hierarchy()).iter('node')}
def activity():
    data = device.adb('shell', 'dumpsys', 'activity', 'activities', capture_output=True, text=True).stdout
    owners = re.findall(r'\* Hist #\d+: ActivityRecord\{(\S+) u0 com\.oprawa\.twitch20/\.MainActivity', data)
    assert len(owners) == 1, f'Expected one MainActivity, found {len(owners)}'
    return owners[0]
def hud():
    state = labels()
    assert 'Прямые эфиры' not in state, 'Relaunch lost the player route'
    if 'Качество' not in state: key('KEYCODE_DPAD_CENTER'); state = labels()
    assert 'ЗАПИСЬ' in state and 'Видео недоступно' not in state
    return state
def position(state):
    value = next(s.split(' / ')[0] for s in state if re.fullmatch(r'[\d:]+ / [\d:]+', s))
    result = 0
    for part in value.split(':'): result = result * 60 + int(part)
    return result
def relaunch(owner):
    key('KEYCODE_HOME'); time.sleep(2)
    device.adb('shell', 'am', 'start', '-n', 'com.oprawa.twitch20/.MainActivity', stdout=subprocess.DEVNULL)
    time.sleep(2)
    assert activity() == owner, 'Relaunch created a second Activity'

state = hud()
assert 'Пауза' in state
device.focus('Пауза'); key('KEYCODE_DPAD_CENTER')
state = hud(); assert 'Смотреть' in state
paused_at = position(state); owner = activity()
device.observe(args.label + '-paused-before')
relaunch(owner)
state = hud(); assert 'Смотреть' in state and 'Пауза' not in state
assert abs(position(state) - paused_at) <= 1, 'Paused position advanced on relaunch'
device.observe(args.label + '-paused-after')
print(f'PASS paused relaunch retained position_s={paused_at} and Activity', flush=True)
device.focus('Смотреть'); key('KEYCODE_DPAD_CENTER'); time.sleep(3)
relaunch(owner)
state = hud(); assert 'Пауза' in state
assert position(state) > paused_at, 'Playing VOD did not progress after relaunch'
device.observe(args.label + '-playing-after')
print('PASS playing relaunch retained Activity and playback intent', flush=True)
