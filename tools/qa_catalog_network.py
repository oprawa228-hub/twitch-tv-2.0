"""Release smoke: start on a fresh authenticated live catalog, first card focused."""
import time
import xml.etree.ElementTree as ET
from device import adb, hierarchy, observe

assert 'Twitch20Test' in adb('emu', 'avd', 'name', capture_output=True, text=True).stdout

def snapshot():
    root = ET.fromstring(hierarchy())
    focused = next((n for n in root.iter('node') if n.get('focused') == 'true'), None)
    return root, focused

names = set()
try:
    adb('shell', 'svc', 'wifi', 'disable')
    for step in range(15):
        root, focused = snapshot()
        assert focused is not None
        desc = focused.get('content-desc', '')
        if '●' in desc: names.add(desc.split(', ')[0])
        if any(n.get('text') == 'Повторить загрузку' for n in focused.iter('node')):
            break
        assert '●' in desc or any(n.get('text') in ('Загрузка…', 'Загрузить ещё') for n in focused.iter('node'))
        adb('shell', 'input', 'keyevent', 'KEYCODE_DPAD_DOWN')
    else: raise AssertionError('No next-page retry after network loss')
    assert len(names) == 10, f'Expected retained 30-card page (10 first-column cards), got {len(names)}'
    assert any('●' in n.get('content-desc', '') for n in root.iter('node'))
    observe('beta4-page-offline-retained')
finally:
    adb('shell', 'svc', 'wifi', 'enable')

loaded = False
for attempt in range(3):
    # Wi-Fi enable returns before network validation/DNS is ready. Exercise a
    # further explicit retry if reconnection is still incomplete, as a user can.
    time.sleep(5)
    adb('shell', 'input', 'keyevent', 'KEYCODE_DPAD_CENTER')
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        root, focused = snapshot()
        texts = {n.get('text') for n in root.iter('node')}
        if not texts.intersection(('Обновление…', 'Загрузка…', 'Повторить загрузку')):
            loaded = True
            break
        time.sleep(.5)
    if loaded: break
assert loaded, 'Next-page retry failed after network restoration'
observe('beta4-page-retry-loaded')
# Footer moves below the newly appended data; Up now selects a newly loaded card.
adb('shell', 'input', 'keyevent', 'KEYCODE_DPAD_UP')
root, focused = snapshot()
assert focused is not None and '●' in focused.get('content-desc', '')
assert focused.get('content-desc', '').split(', ')[0] not in names
observe('beta4-page-retry-new-card')
print('PASS: 30 old cards retained offline; retry appended a page and a new card received D-pad focus')
