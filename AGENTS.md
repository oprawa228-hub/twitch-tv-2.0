# Twitch 2.0

Independent Android TV application. Canonical scope: `docs/TASK.txt`.

- Work only in this project. Old `twitch-tv-by-oprawa-handoff` is read-only visual reference; never publish into its channel.
- Speak Russian to the user. Technical decisions are delegated; continue implementation after research.
- Keep one current plan in `docs/PLAN.md`; record evidence and remaining gaps honestly.
- Keep external Twitch integration separate from UI and playback ownership. Never log tokens or signed media URLs.
- One owner per changed file. Root owns emulator, builds, signing identity, versioning and GitHub releases.
- Verify APK on the named Android emulator using real D-pad inputs. Fixtures, guest Twitch, account, emulator and physical-TV results are separate.
- Keys, credentials, caches, local SDK paths and generated APKs are excluded from Git. Publish only verified artifacts.
