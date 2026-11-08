"""Read-only guest comparison of current/old usher parameters. No media URLs or tokens in output.

Only playlists are read; no account credentials, media downloading or playback mutation.
Run: python tools/probe_live_source_profiles.py caedrel --seconds 120
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import datetime
import json
import random
import re
import time
import urllib.parse
import uuid

from probe_twitch import gql, request


def attr(line, name):
    found = re.search(r'(?:^|,)' + re.escape(name) + r'=("[^"]*"|[^,]*)', line.split(":", 1)[-1])
    return found.group(1).strip('"') if found else None


def number(value):
    try:
        return float(value) if value is not None else None
    except ValueError:
        return None


def summarize(media):
    lines = media.splitlines()
    result = {
        "hls": media.startswith("#EXTM3U"),
        "segments": sum(1 for line in lines if line and not line.startswith("#")),
        "sequence": number(next((line.split(":", 1)[1] for line in lines if line.startswith("#EXT-X-MEDIA-SEQUENCE:")), None)),
        "target_duration": number(next((line.split(":", 1)[1] for line in lines if line.startswith("#EXT-X-TARGETDURATION:")), None)),
        "window_seconds": round(sum(number(line.split(":", 1)[1].split(",", 1)[0]) or 0 for line in lines if line.startswith("#EXTINF:")), 3),
        "start_offset": next((number(attr(line, "TIME-OFFSET")) for line in lines if line.startswith("#EXT-X-START:")), None),
        "end_tag": "#EXT-X-ENDLIST" in lines,
        "playlist_type": "VOD" if "#EXT-X-PLAYLIST-TYPE:VOD" in lines else "EVENT" if "#EXT-X-PLAYLIST-TYPE:EVENT" in lines else "NONE",
        "discontinuities": lines.count("#EXT-X-DISCONTINUITY"),
        "server_control": [{"can_block": attr(line, "CAN-BLOCK-RELOAD") == "YES",
                            "skip_until": number(attr(line, "CAN-SKIP-UNTIL")),
                            "skip_dateranges": attr(line, "CAN-SKIP-DATERANGES") == "YES"}
                           for line in lines if line.startswith("#EXT-X-SERVER-CONTROL:")],
    }
    ranges = [line for line in lines if line.startswith("#EXT-X-DATERANGE:") and
              ("twitch-stitched-ad" in line.lower() or "X-TV-TWITCH-AD-" in line)]
    result["commercial_ranges"] = len(ranges)
    # Strict scalar allowlist; no IDs, tracker URLs, source strings or exception bodies.
    result["ad_numeric"] = [{key: number(attr(line, key)) for key in
        ("DURATION", "PLANNED-DURATION", "X-TV-TWITCH-AD-POD-LENGTH", "X-TV-TWITCH-AD-POD-POSITION")} for line in ranges]
    def timestamp(value):
        try:
            return datetime.datetime.fromisoformat(value.replace("Z", "+00:00")).timestamp() if value else None
        except ValueError:
            return None
    intervals = [(timestamp(attr(line, "START-DATE")), number(attr(line, "DURATION"))) for line in ranges
                 if attr(line, "CLASS") == "twitch-stitched-ad"]
    intervals = [(start, start + duration) for start, duration in intervals if start is not None and duration is not None]
    cursor = None
    segment_duration = 0
    segment_ad = []
    for line in lines:
        if line.startswith("#EXT-X-PROGRAM-DATE-TIME:"):
            cursor = timestamp(line.split(":", 1)[1])
        elif line.startswith("#EXTINF:"):
            segment_duration = number(line.split(":", 1)[1].split(",", 1)[0]) or 0
        elif line and not line.startswith("#"):
            segment_ad.append(None if cursor is None else any(start <= cursor < end for start, end in intervals))
            if cursor is not None:
                cursor += segment_duration
    result["segments_in_declared_ad_range"] = sum(value is True for value in segment_ad)
    result["last_segment_in_declared_ad_range"] = segment_ad[-1] if segment_ad else None
    result["tail_seconds_past_declared_ad_end"] = round(cursor - max(end for _, end in intervals), 3) if cursor is not None and intervals else None
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("channel")
    parser.add_argument("--seconds", type=int, default=120)
    parser.add_argument("--variant", choices=("source", "lowest"), default="source")
    parser.add_argument("--compare-user-agent", action="store_true")
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_]{1,30}", args.channel):
        raise SystemExit("Invalid channel name")
    status, response, _ = gql('query($login:String!){streamPlaybackAccessToken(channelName:$login,params:{platform:"web",playerBackend:"mediaplayer",playerType:"site"}){value signature}}', {"login": args.channel})
    token = (response.get("data") or {}).get("streamPlaybackAccessToken")
    if status != 200 or not token:
        print(json.dumps({"stage": "token", "status": status, "available": False}), flush=True)
        return
    base = {"sig": token["signature"], "token": token["value"], "allow_source": "true",
            "allow_audio_only": "true", "supported_codecs": "avc1", "player": "twitchweb",
            "playlist_include_framerate": "true"}
    session = {"play_session_id": uuid.uuid4().hex, "p": random.randrange(100_000_000)}
    old = {**base, **session, "player_backend": "mediaplayer", "reassignments_supported": "true",
           "fast_bread": "false", "cdm": "wv", "acmb": "e30=", "player_version": "1.13.0"}
    old.pop("player")
    old.pop("allow_audio_only")
    profiles = {"current": base, "session_only": {**base, **session}, "old_usher": old}
    if args.compare_user_agent:
        profiles["old_media_user_agent"] = base
    media_headers = {name: {"User-Agent": "okhttp/4.12.1"} for name in profiles}
    if args.compare_user_agent:
        media_headers["old_media_user_agent"] = {"User-Agent": "STTV_PlayerActivity/1.0.2 (Linux;Android 14) AndroidXMedia3/1.8.0"}
    playlist_urls = {}
    for name, params in profiles.items():
        try:
            status, raw, ms = request("https://usher.ttvnw.net/api/channel/hls/" + args.channel + ".m3u8?" + urllib.parse.urlencode(params), headers={"User-Agent": "okhttp/4.12.1"})
            lines = raw.decode(errors="replace").splitlines()
            variants = [line for line in lines if line and not line.startswith("#")]
            print(json.dumps({"profile": name, "stage": "master", "status": status, "variants": len(variants), "ms": ms}), flush=True)
            if status == 200 and variants:
                selected = variants[0]
                if args.variant == "lowest":
                    heights = []
                    for index, line in enumerate(lines[:-1]):
                        match = re.search(r"RESOLUTION=\d+x(\d+)", line)
                        if line.startswith("#EXT-X-STREAM-INF:") and match and not lines[index + 1].startswith("#"):
                            heights.append((int(match.group(1)), lines[index + 1]))
                    if heights:
                        selected = min(heights)[1]
                playlist_urls[name] = selected
        except Exception as error:
            print(json.dumps({"profile": name, "stage": "master", "error_type": type(error).__name__}), flush=True)
    started = time.monotonic()

    def sample(item):
        name, url = item
        result = {"profile": name, "elapsed_s": round(time.monotonic() - started),
                  "utc": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds")}
        try:
            status, raw, ms = request(url, headers=media_headers[name])
            result.update(status=status, ms=ms)
            if status == 200:
                result.update(summarize(raw.decode(errors="replace")))
        except Exception as error:
            result["error_type"] = type(error).__name__
        return result

    with ThreadPoolExecutor(max_workers=3) as pool:
        while True:
            for result in pool.map(sample, playlist_urls.items()):
                print(json.dumps(result), flush=True)
            if time.monotonic() - started >= max(0, min(args.seconds, 600)):
                break
            time.sleep(10)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        # urllib exception messages can contain capabilities: never print their messages.
        print(json.dumps({"stage": "probe", "error_type": type(error).__name__}), flush=True)
