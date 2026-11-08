"""Native D-pad refresh regression; start on a channel's nonempty VOD grid.

Fresh external UI Automator sessions avoid the long-lived instrumentation
accessibility cache observed on API 30 after replacing Compose catalog nodes.
Never run alongside instrumentation. Uses tools/device.py's designated AVD.
"""
import time
import xml.etree.ElementTree as ET
from device import adb, hierarchy, observe

assert 'Twitch20Test' in adb('emu', 'avd', 'name', capture_output=True, text=True).stdout

def snapshot():
    root = ET.fromstring(hierarchy())
    focused = next((n for n in root.iter('node') if n.get('focused') == 'true'), None)
    return root, focused

last_card = ''
for step in range(100):
    root, focused = snapshot()
    assert focused is not None, 'No actual Android keyboard focus'
    description = focused.get('content-desc', '')
    if ', vod,' in description:
        last_card = description.rsplit(', vod,', 1)[0]
    if any(n.get('text') == 'Обновить список' for n in focused.iter('node')):
        break
    assert ', vod,' in description or any(n.get('text') in ('Загрузка…', 'Загрузить ещё') for n in focused.iter('node')), 'Unexpected focus outside VOD grid'
    adb('shell', 'input', 'keyevent', 'KEYCODE_DPAD_DOWN')
else:
    raise AssertionError('VOD footer not reached within 100 observed steps')
assert last_card, 'No VOD card observed'
observe('beta4-refresh-before-final')
adb('shell', 'input', 'keyevent', 'KEYCODE_DPAD_CENTER')
deadline = time.monotonic() + 35
while time.monotonic() < deadline:
    root, focused = snapshot()
    if focused is not None and focused.get('content-desc', '').startswith(last_card):
        observe('beta4-refresh-restored-final')
        print('PASS: explicit refresh restored the last VOD card with real D-pad focus')
        break
    time.sleep(.5)
else:
    observe('beta4-refresh-failed-final')
    raise AssertionError('Refresh did not restore the selected VOD card')
