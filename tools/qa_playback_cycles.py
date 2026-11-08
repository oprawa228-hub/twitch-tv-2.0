"""Real release D-pad playback/open/return stress on the project's primary AVD.

Start in an authenticated live catalog with a card focused. No credentials,
media URLs, fixtures, touch input or private app files are accessed.
"""
from pathlib import Path
import argparse, json, re, subprocess, time, xml.etree.ElementTree as ET
from device import ADB, SERIAL, ROOT, adb, hierarchy, observe

parser = argparse.ArgumentParser()
parser.add_argument('--cycles', type=int, default=22)
parser.add_argument('--label', default='beta5-cycles')
args = parser.parse_args()
assert 1 <= args.cycles <= 30
assert re.fullmatch(r'[a-zA-Z0-9_-]+', args.label)
assert 'Twitch20Test' in adb('emu', 'avd', 'name', capture_output=True, text=True).stdout
directory = ROOT / 'evidence' / args.label
directory.mkdir(exist_ok=True)

def snapshot():
    data = hierarchy()
    root = ET.fromstring(data)
    return data, root, next((n for n in root.iter('node') if n.get('focused') == 'true'), None)

def key(value): adb('shell', 'input', 'keyevent', value, stdout=subprocess.DEVNULL)

logpath = directory / 'playback-numeric.log'
results = []
with logpath.open('wb') as stream:
    logger = subprocess.Popen([str(ADB), '-s', SERIAL, 'logcat', '-T', '1', '-v', 'threadtime',
        '-s', 'TwitchPlayback:I', '*:S'], stdout=stream, stderr=subprocess.DEVNULL)
    try:
        time.sleep(.5)
        for index in range(1, args.cycles + 1):
            data, root, focused = snapshot()
            assert any(n.get('text') == 'Прямые эфиры' for n in root.iter('node'))
            assert focused is not None and '●' in focused.get('content-desc', ''), 'No live card focused'
            selected = focused.get('content-desc').split(', ')[0]
            (directory / f'{index:02}-before.xml').write_bytes(data)
            offset = logpath.stat().st_size
            started = time.monotonic()
            key('KEYCODE_DPAD_CENTER')
            deadline = started + 60
            while time.monotonic() < deadline:
                sample = logpath.read_bytes()[offset:].decode('utf-8', errors='replace')
                match = re.search(r'request_first_frame_ms=(\d+) source_resolve_ms=(\d+)', sample)
                if match: break
                time.sleep(.25)
            else:
                observe(f'beta5-cycle-{index}-no-frame')
                raise AssertionError(f'Cycle {index}: no first frame in 60s')
            time.sleep(1)
            for _ in range(4):
                data, root, focused = snapshot()
                if any(n.get('text') == 'Прямые эфиры' for n in root.iter('node')): break
                key('KEYCODE_BACK')
            else: raise AssertionError('Back did not return to the catalog')
            assert focused is not None and focused.get('content-desc', '').startswith(selected + ', ')
            (directory / f'{index:02}-returned.xml').write_bytes(data)
            time.sleep(2)  # Same post-release idle window for every memory sample.
            memory = adb('shell', 'dumpsys', 'meminfo', 'com.oprawa.twitch20', capture_output=True, text=True).stdout
            (directory / f'{index:02}-memory.txt').write_text(memory, encoding='utf-8')
            pss = re.search(r'TOTAL PSS:\s*(\d+)', memory) or re.search(r'^\s*TOTAL\s+(\d+)', memory, re.M)
            row = dict(cycle=index, decoded=True, returned=True, selected=selected,
                request_first_frame_ms=int(match[1]), source_resolve_ms=int(match[2]),
                pss_kb=int(pss[1]) if pss else None, elapsed_s=round(time.monotonic()-started, 3))
            results.append(row)
            (directory / 'results.json').write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding='utf-8')
            print(json.dumps({k:v for k,v in row.items() if k != 'selected'}), flush=True)
            if index < args.cycles:
                key('KEYCODE_DPAD_DOWN')
    finally:
        logger.terminate()
        logger.wait(timeout=10)
