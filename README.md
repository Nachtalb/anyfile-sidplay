# anyfile-sidplay

A headless **SID (Commodore 64 music) → WAV render companion** for [AnyFile](https://github.com/Nachtalb/anyfile).

No UI, no launcher icon. AnyFile binds its service over AIDL, hands it a `.sid` file, and gets back a
44.1 kHz 16-bit PCM WAV of the chosen subsong, which it plays in its own audio player.

## Why a separate, GPL-licensed app

Real SID playback means [libsidplayfp](https://github.com/libsidplayfp/libsidplayfp) with the
[reSIDfp](https://github.com/libsidplayfp/libresidfp) emulation. Both are **GPL v2**, so they can't be
linked into AnyFile. They live here instead, in a separate open-source app. Only data crosses the
process boundary: a tune fd in, a WAV fd out.

## Architecture

```
AnyFile  ──bind IConverter (shared AIDL)──►  SidRenderService (headless)
         ◄─── onResult(wavFd) ─────────     └─ libsidplayfp + reSIDfp (vendored, GPL) via JNI
```

- `app/src/main/cpp/libsidplayfp/` – libsidplayfp 3.1.2, `src/` only. The exSID, USBSID and SIDLite
  builders are removed. `config.h` and `sidplayfp/sidversion.h` are hand-written in place of the
  autotools output.
- `app/src/main/cpp/libresidfp/` – libresidfp 1.2.3, `src/` only. `siddefs-fp.h` and
  `residfp/sidversion.h` are pre-substituted from their `.in` templates.
- `sid_core.{h,cpp}` – load a tune from memory, select a subsong, render PCM. No JNI, so the host
  harness `tools/sidrender.cpp` runs the same code.
- `WavRenderer.kt` – streams PCM into a WAV file. Rendering stops at the `seconds` cap (default 180),
  or after 5 s of silence once the tune has ended.
- `*.aidl` – the shared companion contract, a verbatim copy of AnyFile's.

Options bundle: `subsong` (1-based, 0 = the tune's start song), `seconds` (1..1200).

RSID and BASIC tunes can need the real C64 KERNAL/BASIC ROMs, which are Commodore's copyright and are not
bundled, so some of them won't play. Plain PSID tunes need no ROMs.

## Build

```
gradle :app:assembleDebug
```

Host check of the native core, no device needed:

```
cmake -S app/src/main/cpp -B /tmp/sidhost && cmake --build /tmp/sidhost -j
/tmp/sidhost/sidrender tune.sid out.wav [subsong] [seconds]
```

On-device test: push a tune to `/sdcard/Android/data/com.nachtalb.anyfilesidplay/files/sample.sid`, then
run `gradle :app:connectedDebugAndroidTest`.

## Licensing

- This app: **GPL-2.0-or-later**. Full source is this repository.
- libsidplayfp and libresidfp notices: `COPYING`, `AUTHORS*` and `README.md` in each vendored directory.
