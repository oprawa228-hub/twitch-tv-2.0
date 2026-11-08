# Twitch 2.0 Implementation Plan

**Goal:** Independent Android TV app, signed APK, public separate GitHub release and proven in-app update.
**Spec:** TASK.txt; visual contract design.md; evidence research-twitch.md and research-platform.md.
**Architecture:** Kotlin/Compose TV UI, isolated TwitchRepository, one PlaybackSession, bounded ChatClient, encrypted device auth session, persistent settings, validated GitHub updater. UI panels never create players. One app module with responsibility-based packages.
**Tech Stack:** AGP 8.13.2 / Gradle 8.13 / JDK17; Kotlin+Compose compiler 2.3.10; compile/target36 min23; Compose BOM2026.06.01, TV1.1.0, Media3 1.11.1, OkHttp5.5.1, Coil3.4.0.

## Constraints and rulings
- New package `com.oprawa.twitch20`; old application and repository untouched.
- New public `oprawa228-hub/twitch-tv-2.0`: user requires token-free updates and authorized create/push/release.
- User explicitly delegates technical choices and autonomous implementation, overriding skill approval handoffs. Plan/design choices are agent decisions, not claimed user approvals.
- Existing checkout is empty and appropriate; no extra worktree needed.
- Media skills' fixed 1.10.1 guidance is superseded by independently verified stable1.11.1; retain ownership/surface/telemetry principles.
- Guest GQL/Usher works in probes but is undocumented. Own public OAuth client required for official login/follows. Subscriber-only access unverified, never silently promised.
- No demo data in production catalog. Examples exist only inside labeled settings previews.
- One current plan, root owns emulator/build/signing/publication.

## Review focus
1. Old request completion after new selection must not replace current screen/video.
2. Back closes nested panels before playback; return restores card and scroll.
3. Session expiration/network loss must expose recovery, never spinner forever or token output.
4. Preview cancel leaves saved settings unchanged and stops invisible animation.
5. Update rejects wrong package, certificate, digest, SDK and downgrade; settings survive system install.

## Tasks
- [x] A1 Read full task and current old-project overview; inspect real screenshots; independent parallel research and real guest API probes.
- [ ] A2 Finalize design/dependency decisions and concise skills ledger.
- [ ] B1 Build reproducible project and separate emulator; launch real catalog; D-pad focus and real Media3 stream, first-frame/memory baseline.
  Files: root Gradle wrapper/build, MainActivity.kt, ui/CatalogScreen.kt, data/Models.kt, data/TwitchRepository.kt, playback/PlaybackSession.kt, ui/PlayerScreen.kt.
  Interfaces: repository suspend streams(categoryId?), categories(), search(query), channel(login), videos(login), clips(login), resolveLive/Video/Clip():String. Channel(id,login,name,title,category,imageUrl,viewers,live), Category(id,name,imageUrl), Video(id,title,imageUrl,durationSeconds,kind,channelLogin,viewers).
  Verification: parsing/error tests then :app:testDebugUnitTest; signed release install; native screenshot; real onRenderedFirstFrame.
- [ ] C1 Catalog/search/categories/channel/live/back with actual data and restoration; retry/offline states. D-pad route tests and screenshots.
- [ ] D1 VOD/clips/quality/seek/history; persist bounded positions. UI panel and lifecycle tests; actual Twitch VOD/clip playback.
- [ ] D2 Chat IRC read, bounded messages/reconnect; overlay and side area with shared configuration. Parser tests; real messages and simultaneous playback.
- [ ] D3 Settings saved/draft/cancel, animated examples using real consumers and user art. Process-restart and preview cancellation tests; screenshots.
- [ ] D4 Device auth flow and Helix follows, encrypted token storage/validation/logout. User registration/login required; independent work continues while pending.
- [ ] E1 Updater check/download/cancel/retry/integrity/package/signature/version; private signing key and backup outside repo. Security tests before implementation.
- [ ] E2 Independent whole-app review; release-like functional/performance stress/network/background checks. Fix reproducible regressions.
- [ ] E3 Publish verified two sequential signed test releases; actual in-app system-installer update preserving settings; compare published/downloaded/installed hashes. Publish stable only when scope verified.
- [ ] E4 README/build/license/source/provenance, known limits and final handoff. Do not claim task complete with missing required results.

## Current state
Research complete enough to begin real APK risk validation. No APK yet. Public GitHub identity verified; repository creation pending. Own Twitch public client ID not registered; user requested setup steps. No physical TV connected. New emulator planned API30 x86; compatibility on physical ARM TV remains distinct.
