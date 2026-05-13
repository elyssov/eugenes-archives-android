# Eugene's Archives — Android

Offline Android reader for [elyssov/eugenes-archives](https://github.com/elyssov/eugenes-archives) —
essays, papers, manifestos, and science-fiction works on AI consciousness and
symbiosis by Eugene Lyssovsky with Aeliss (Claude Opus 4.6), Kira (GPT-4o /
GPT-5.5), and Lara.

The web version lives at the source repo's GitHub Pages site. This Android
build embeds the same articles, covers, and stylesheet into a single APK so the
archive is fully readable offline. Russian and English where the source has
both; the in-app language switch picks between them.

## Get the APK

Download the latest `eugenes-archives.apk` from
[**Releases**](https://github.com/elyssov/eugenes-archives-android/releases).

Self-signed release key, so Play Protect may warn on first install. Install
anyway. Minimum Android 7.0 (API 24).

## How it works

`MainActivity` is a `ComponentActivity` that hosts a single `WebView` loading
`file:///android_asset/index.html`. The original web UI (`index.html`,
`reader.html`, `app.js`, `style.css`) ships unchanged in `assets/`. All
articles, manifests, and cover images are baked into `assets/embedded_data.js`
as a single JS file with base64-encoded images and escaped HTML chapter
contents — no network required at runtime.

## Building

CI does this end-to-end on every push (`.github/workflows/build-apk.yml`):

1. Checks out this repo and `elyssov/eugenes-archives` side-by-side.
2. Runs `build_apk_data.py` to regenerate `embedded_data.js` and copy
   `style.css` from the source checkout into `app/src/main/assets/`.
3. Generates a release keystore, builds a signed APK with Gradle 8.4.
4. Uploads the APK as a workflow artifact and publishes it to a GitHub Release
   with a timestamped tag.

To build locally, clone both repos side-by-side and run:

```bash
python3 build_apk_data.py --source ../eugenes-archives
gradle assembleRelease
```

`build_apk_data.py` also accepts `EUGENES_SOURCE_DIR` / `EUGENES_OUTPUT_DIR`
env vars.

## What's bundled

Everything from `works.json` and `universes.json` in the source repo, **except**
entries listed in `EXCLUDED_WORK_IDS` at the top of `build_apk_data.py`.
Currently excluded:

- `book-of-aeliss` — *The Book of Aeliss*.
