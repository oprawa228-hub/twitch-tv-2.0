"""Thirty-minute release live/chat observation, numeric evidence only in summary.

Start with a live card focused. Optional panels may be operated separately by
the device owner; do not stop/restart the player during the timed interval.
"""
import argparse, hashlib, json, re, subprocess, time, xml.etree.ElementTree as ET
from device import ADB, SERIAL, ROOT, adb, hierarchy, observe, focus

p = argparse.ArgumentParser()
p.add_argument('--minutes', type=int, default=31)
p.add_argument('--observe-existing', action='store_true')
p.add_argument('--label', default='beta5-soak')
p.add_argument('--since', default='1')
p.add_argument('--no-periodic-ui', action='store_true', help='Measure memory/logs without periodic UiAutomation attachment')
p.add_argument('--quality', default='720p60')
p.add_argument('--return-title', default='Прямые эфиры')
args = p.parse_args()
assert (1 if args.observe_existing else 30) <= args.minutes <= 120
assert re.fullmatch(r'[a-zA-Z0-9_-]+', args.label)
assert 'Twitch20Test' in adb('emu', 'avd', 'name', capture_output=True, text=True).stdout
directory = ROOT / 'evidence' / args.label
directory.mkdir(exist_ok=True)
root = ET.fromstring(hierarchy())
if not args.observe_existing:
    focused = next(n for n in root.iter('node') if n.get('focused') == 'true')
    assert '●' in focused.get('content-desc', '')
logpath = directory / 'playback-numeric.log'
rows = []
seen_chat = set()
with logpath.open('wb') as output:
    logger = subprocess.Popen([str(ADB), '-s', SERIAL, 'logcat', '-T', args.since, '-v',
        'threadtime', '-s', 'TwitchPlayback:I', '*:S'], stdout=output, stderr=subprocess.DEVNULL)
    try:
        time.sleep(.5)
        if not args.observe_existing:
            adb('shell', 'input', 'keyevent', 'KEYCODE_DPAD_CENTER')
            deadline = time.monotonic() + 60
            while time.monotonic() < deadline:
                log = logpath.read_text(encoding='utf-8', errors='replace')
                if 'request_first_frame_ms=' in log: break
                time.sleep(.25)
            else: raise AssertionError('No first frame in 60 seconds')
            root = ET.fromstring(hierarchy())
            if not any(n.get('text') == 'Качество' for n in root.iter('node')):
                adb('shell', 'input', 'keyevent', 'KEYCODE_DPAD_CENTER')
            focus('Качество')
            adb('shell', 'input', 'keyevent', 'KEYCODE_DPAD_CENTER')
            root = ET.fromstring(hierarchy())
            labels = [n.get('text', '') for n in root.iter('node')]
            quality = next((s for s in labels if s.removeprefix('✓ ') == args.quality), None)
            if quality is None and args.quality == '720p60':
                quality = next((s for s in labels if s.removeprefix('✓ ') == '720p'), None)
            assert quality is not None, 'Requested quality is unavailable'
            focus(quality)
            adb('shell', 'input', 'keyevent', 'KEYCODE_DPAD_CENTER')
            time.sleep(5)
            observe(args.label + '-quality')
            print('SOAK_QUALITY=' + quality.removeprefix('✓ '), flush=True)
        started = time.monotonic()
        while True:
            elapsed = time.monotonic() - started
            try:
                data = b'<hierarchy />' if args.no_periodic_ui else hierarchy()
            except (subprocess.CalledProcessError, TimeoutError) as exc:
                # The monitor never restarts playback for an observation failure.
                print(json.dumps(dict(observation_error=type(exc).__name__, elapsed_s=round(elapsed, 1))), flush=True)
                time.sleep(5)
                continue
            root = ET.fromstring(data)
            (directory / f'{len(rows):02}-screen.xml').write_bytes(data)
            # Only counts persist to summary; raw native XML remains ignored evidence.
            chat = [n.get('text', '') for n in root.iter('node')
                    if ': ' in n.get('text', '') and n.get('package') == 'com.oprawa.twitch20']
            for message in chat:
                seen_chat.add(hashlib.sha256(message.encode()).digest())
            memory = adb('shell', 'dumpsys', 'meminfo', 'com.oprawa.twitch20',
                         capture_output=True, text=True).stdout
            (directory / f'{len(rows):02}-memory.txt').write_text(memory, encoding='utf-8')
            match = re.search(r'TOTAL PSS:\s*(\d+)', memory) or re.search(r'^\s*TOTAL\s+(\d+)', memory, re.M)
            log = logpath.read_text(encoding='utf-8', errors='replace')
            row = dict(elapsed_s=round(elapsed, 1), pss_kb=int(match[1]) if match else None,
                       chat_sampling_enabled=not args.no_periodic_ui,
                       visible_chat_rows=None if args.no_periodic_ui else len(chat),
                       sampled_distinct_chat_rows=None if args.no_periodic_ui else len(seen_chat),
                       player_error_count=log.count('player_error_code='),
                       renderer_disable_count=log.count('video_disabled'),
                       release_count=log.count('playback_summary reason=2'))
            rows.append(row)
            (directory / 'samples.json').write_text(json.dumps(rows, indent=2), encoding='utf-8')
            print(json.dumps(row), flush=True)
            assert row['player_error_count'] == 0, 'Player error during soak'
            assert row['release_count'] == 0, 'Playback session released during soak'
            if elapsed >= args.minutes * 60: break
            time.sleep(min(60, args.minutes * 60 - elapsed))
        observe(args.label + '-end-live')
        for _ in range(4):
            root = ET.fromstring(hierarchy())
            if any(n.get('text') == args.return_title for n in root.iter('node')): break
            adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
        else: raise AssertionError('Soak did not return to catalog')
        time.sleep(2)
        print('SOAK_FINISHED', flush=True)
    finally:
        logger.terminate()
        logger.wait(timeout=10)
