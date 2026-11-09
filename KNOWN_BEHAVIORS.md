# KNOWN_BEHAVIORS.md — verified behavior of this edition

WhatsApp-dedicated NewBlackbox edition. Every entry below was verified on a
real device (Redmi Note 11 `f5be654f`, Android 13 / MIUI V816) with the
device's own installed WhatsApp (v2.26.43.76) — no APK is bundled,
downloaded or redistributed anywhere in this repo.

> **Re-verify after every WhatsApp major update** — the client-side
> behaviors below are implementation details of WhatsApp's rendering and
> lifecycle code and can change between versions. Checklist at the bottom.

---

## 1. Engine fixes required for WhatsApp (inherited history)

Two engine patches make WhatsApp usable at all inside the sandbox; they are
part of this repo's history (originally landed in `RaeesRizwan09/NewBlackbox`):

| Fix | Symptom without it |
|---|---|
| **AppOps `SyncNotedAppOp` proxy fix** | FATAL crashes when WhatsApp queries note-op state via the sync API |
| **JobService plain-Service dispatcher fix** | WhatsApp's job scheduling dispatched to non-JobService classes → FATAL |
| FIX-1 SyncNotedAppOp | FATAL on clipboard/media op queries → app killed within seconds of first UI |
| FIX-2 JobService dispatcher | FATAL `ClassCastException` when WhatsApp jobs are scheduled |

With both fixes: registration, login, chat, voice notes, camera and push all
run inside the sandbox with **0 FATALs** in a normal session.

## 2. Auto-import behavior (verified live)

`AutoImportManager` runs at every engine start (in `WelcomeActivity.onCreate`,
before the home list loads):

- **A — install-if-missing**: sandbox copy absent + host copy present → clone
  the host copy via `BlackBoxCore.installPackageAsUser(pkg, 0)`.
  Log: `auto-import com.whatsapp (missing) OK` (~0.4 s on device).
- **B — update-if-newer**: host `versionCode` > sandbox → replace the sandbox
  copy. Needed because WhatsApp's in-app updater cannot complete inside the
  sandbox. Log: `auto-import com.whatsapp (update X -> Y)`.
- Variant targets: `com.whatsapp`, `com.whatsapp.w4b` (Business),
  `com.gbwhatsapp` — each imported **only if present on the host**;
  absent variants are skipped silently.
- Both behaviors are gated by Settings → Others → **Auto import WhatsApp**
  (default ON).

## 3. Auto-launch behavior (verified live, incl. one race-fix)

Open BlackBox → land **inside** the WhatsApp clone, no tap.

- `launchCloneToFront()` picks the first installed variant in the sandbox and
  fires `BlackBoxCore.launchApk(pkg, 0)`, gated by Settings → Others →
  **Launch WhatsApp on start** (default ON).
- Launch runs **after** `MainActivity` gets its first frame (800 ms post on
  the main looper). Launching before that buries the clone behind the home
  screen — verified before the fix (`mLastPausedActivity` = clone's task),
  fixed in commit `3eaecdf`.
- Debug anchors:
  - `logcat | grep AutoImport` — decision log for import + launch.
  - On screen success: `topResumedActivity` is
    `top.niunaijun.blackbox/.proxy.ProxyActivity$P0`.
  - Clone process: `ps -A -o PID,USER,NAME | grep com.whatsapp` — the clone
    runs as `com.whatsapp` under a *freshly allocated* uid (e.g. `u0_a757`);
    the native app keeps its original uid (`u0_a001…`, 10001 on this device).
- On relaunch (`onNewIntent`) the same 800 ms sequence re-runs, so the
  behavior is identical for every entry point.

## 4. FLAG_SECURE / view-once capture matrix (verified 2026-11-09)

With Settings → Others → **disable_flag_secure** ON
(`IWindowSessionProxy` strips `FLAG_SECURE` at window level):

| Content | Expected risk | Actual result |
|---|---|---|
| WhatsApp view-once **photo** | capturable (predicted) | ✅ **capturable** — screenshot shows the photo |
| WhatsApp view-once **video** | expected mostly black (SurfaceFlinger secure layers) | ✅ **capturable — video renders through normal views, not verified/unprotected `SurfaceView` or DRM layer** |

Interpretation: WhatsApp's view-once media path does not use
`SurfaceView.setSecure(true)`/DRM planes for Messenger-style view-once
content; with the flag stripped at the **window** level everything that the
window draws is visible to screen capture.

- **No server-side detection signal found**: the protection is client-side
  only; the app never re-checks the flag after the fact. (Consistent with
  prior analysis; still not an absolute claim about WhatsApp's servers.)
- Remaining likely-but-untested black-screen case: content rendered through
  a **DRM-protected / Play-verified** surface (e.g. Play-protected video) —
  no WhatsApp surface currently behaves that way for view-once.
- Standard caveat stays: this only works inside this app (not system-wide),
  and the sender's view-once guarantee silently stops being enforced. Use
  judiciously.

## 5. Build/signing notes

- `signing/debug.keystore` is **committed** and pinned via
  `signingConfigs.debug` in `app/build.gradle`. Every CI build from the same
  repo is signed with it → consecutive builds can *update* the installed app
  in place; no wipe, no re-registration.
- Only a change of repo or manual removal of the keystore file forces a
  `uninstall` (which wipes the sandbox and the WhatsApp link) again.

## 6. Known issues not fixed (tracked upstream realities)

| Issue | Status |
|---|---|
| EULA-restart SIGKILL on a *fresh* clone right after registration | not fixed; workaround is to just reopen the clone |
| Stale-UID identity mismatch after a clean sandbox install | masked by an engine-level synth-allow; revisit after engine updates |
| No GMS/FCM inside the sandbox | push notifications work only while the clone (or the black daemon) is alive; long-term reliability comes from MIUI-level whitelist/sticky-notification tricks, not FCM |
| Account-re-registration risk discussed earlier | noted; **Linked Devices** (companion mode) remains the recommended way to link the clone to a WhatsApp account *instead of* SMS re-registration |

## 7. Re-test checklist (run after major WhatsApp updates)

1. `AutoImport` log shows `up to date` *(or a successful `update X -> Y` B-path re-import)* for `com.whatsapp`.
2. WhatsApp clone opens by itself from BlackBox (`ProxyActivity$P0` in front, EULA avoided for an already-linked clone).
3. Chat, send/receive media, voice note and camera work; `logcat` session has **0 FATAL**.
4. With `disable_flag_secure` on: screenshot a view-once photo **and** a view-once video → both should be captured cleanly.
5. Push delivery while backgrounded (daemon alive) still works.
