"""Read-only Twitch guest connectivity probe. Never prints tokens or media URLs.

The web GraphQL client ID is public, observed in Streamlink's upstream plugin;
it is NOT this application's registered OAuth client ID. No account data is used.
Run: python tools/probe_twitch.py
"""
import json
import random
import re
import socket
import ssl
import time
import urllib.error
import urllib.parse
import urllib.request

WEB_CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko"
TIMEOUT = 20


def request(url, data=None, headers=None, limit=2_000_000):
    req = urllib.request.Request(url, data=data, headers=headers or {})
    started = time.monotonic()
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT) as response:
            body = response.read(limit)
            return response.status, body, round((time.monotonic() - started) * 1000)
    except urllib.error.HTTPError as error:
        return error.code, error.read(limit), round((time.monotonic() - started) * 1000)


def gql(query, variables=None):
    status, raw, elapsed = request("https://gql.twitch.tv/gql", json.dumps({"query": query, "variables": variables or {}}).encode(), {"Client-ID": WEB_CLIENT_ID, "Content-Type": "application/json"})
    return status, json.loads(raw), elapsed


def emit(name, **values):
    print(json.dumps({"probe": name, **values}, ensure_ascii=True), flush=True)


def playback(kind, identifier):
    field = "streamPlaybackAccessToken" if kind == "live" else "videoPlaybackAccessToken"
    arg = "channelName" if kind == "live" else "id"
    status, response, elapsed = gql('query { ' + field + '(' + arg + ':"' + identifier + '", params:{platform:"web", playerBackend:"mediaplayer", playerType:"site"}){ value signature } }')
    token = (response.get("data") or {}).get(field)
    if not token:
        emit(kind + "_token", status=status, ms=elapsed, errors=response.get("errors"))
        return
    emit(kind + "_token", status=status, ms=elapsed, present=True)
    params = {"sig": token["signature"], "token": token["value"], "allow_source": "true", "allow_audio_only": "true", "supported_codecs": "avc1", "player": "twitchweb", "p": random.randrange(1000000)}
    path = "/api/channel/hls/" + identifier + ".m3u8" if kind == "live" else "/vod/" + identifier + ".m3u8"
    status, raw, elapsed = request("https://usher.ttvnw.net" + path + "?" + urllib.parse.urlencode(params))
    text = raw.decode(errors="replace")
    variants = [line for line in text.splitlines() if line and not line.startswith("#")]
    emit(kind + "_master", status=status, ms=elapsed, hls=text.startswith("#EXTM3U"), variants=len(variants), qualities=re.findall(r'NAME="([^"]+)"', text))
    if status != 200 or not variants:
        return
    status, raw, elapsed = request(variants[0])
    media = raw.decode(errors="replace")
    segments = [line for line in media.splitlines() if line and not line.startswith("#")]
    emit(kind + "_playlist", status=status, ms=elapsed, segments=len(segments), encrypted="#EXT-X-KEY" in media)
    if status == 200 and segments:
        status, chunk, elapsed = request(urllib.parse.urljoin(variants[0], segments[-1 if kind == "live" else 0]), headers={"Range": "bytes=0-65535"}, limit=65536)
        emit(kind + "_segment", status=status, ms=elapsed, bytes_read=len(chunk), ts_sync=chunk[:1] == b"G")


def main():
    status, raw, elapsed = request("https://api.twitch.tv/helix/streams?first=1")
    emit("helix_no_auth", status=status, ms=elapsed, response=json.loads(raw))
    status, result, elapsed = gql('{ streams(first: 8) { edges { node { id title viewersCount broadcaster { id login displayName } game { id name } } } } }')
    streams = [e["node"] for e in (result.get("data") or {}).get("streams", {}).get("edges", [])]
    emit("guest_catalog", status=status, ms=elapsed, streams=streams, errors=result.get("errors"))
    login = streams[0]["broadcaster"]["login"] if streams else "monstercat"
    playback("live", login)
    status, result, elapsed = gql('{ user(login:"' + login + '") { videos(first:3, sort:TIME) { edges { node { id title lengthSeconds } } } clips(first:1, criteria:{period:LAST_WEEK}) { edges { node { id slug title } } } } }')
    emit("guest_channel_media", status=status, ms=elapsed, result=result)
    user = (result.get("data") or {}).get("user") or {}
    videos = user.get("videos", {}).get("edges", [])
    if videos:
        playback("vod", videos[0]["node"]["id"])
    clips = user.get("clips", {}).get("edges", [])
    if clips:
        slug = clips[0]["node"]["slug"]
        status, result, elapsed = gql('{ clip(slug:"' + slug + '") { videoQualities { quality frameRate sourceURL } playbackAccessToken(params:{platform:"web",playerType:"site"}) { value signature } } }')
        clip = (result.get("data") or {}).get("clip") or {}
        qualities = clip.get("videoQualities") or []
        emit("clip_sources", status=status, ms=elapsed, qualities=[q["quality"] for q in qualities], token_present=bool(clip.get("playbackAccessToken")), errors=result.get("errors"))
        if qualities:
            url = qualities[0]["sourceURL"]
            token = clip.get("playbackAccessToken")
            if token:
                url += ("&" if "?" in url else "?") + urllib.parse.urlencode({"sig":token["signature"], "token":token["value"]})
            status, raw, elapsed = request(url, headers={"Range":"bytes=0-65535"}, limit=65536)
            emit("clip_bytes", status=status, ms=elapsed, bytes_read=len(raw), mp4_header=b"ftyp" in raw[:32])
    try:
        with ssl.create_default_context().wrap_socket(socket.create_connection(("irc.chat.twitch.tv", 6697), timeout=10), server_hostname="irc.chat.twitch.tv") as sock:
            sock.settimeout(10)
            nick = "justinfan" + str(random.randrange(100000, 999999))
            sock.sendall(("CAP REQ :twitch.tv/tags twitch.tv/commands\r\nPASS SCHMOOPIIE\r\nNICK " + nick + "\r\nJOIN #" + login + "\r\n").encode())
            buf = b""
            deadline = time.monotonic() + 15
            while time.monotonic() < deadline:
                try:
                    buf += sock.recv(65536)
                    if b"PRIVMSG" in buf:
                        break
                except socket.timeout:
                    break
            emit("guest_irc", welcomed=b" 001 " in buf, joined=b"ROOMSTATE" in buf, messages=buf.count(b"PRIVMSG"), bytes_received=len(buf))
    except (OSError, ssl.SSLError) as error:
        emit("guest_irc", error=type(error).__name__)


if __name__ == "__main__":
    main()
