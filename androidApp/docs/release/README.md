# Release images

Store-style images of Pardes, the Android app based on Zayit, one per feature, each screenshot in a device frame under a Hebrew caption (`01-home.png` … `09-tablet.png`), and `overview.png` with all of them in a row. Every image, and the overview, carries the line "פרדס מבוסס על זית מאת אליהו גמבש ואינו מטעמו" (release-frame.py draws it by default), because the images get shared one at a time. Captions must not call the app Zayit: it is פרדס, and desktop is "זית למחשב", not "גרסת המחשב".

- `shots/` are the screenshots they are made from. The phone ones are 1080x2400 (420 dpi). `js-reader.png` and `launcher-widgets.png` are Jelly Star size (480x854, 320 dpi). `tablet-search.png` is 1920x1200 (240 dpi). They are from the emulator with the test library (a subset of the desktop library), on the builds of 13–14 October 2026; `home.png`, `search.png` and `tablet-search.png` were taken again on 16 October, after the rename to Pardes, which shows in them. The site's `site/img/*.webp` are these images at 480x853 (the tablet one at 960x600), and `site/img/social.jpg` is 03, 02 and 01 side by side at 1200x630.
- `manifest.json` lists the shots in order with their captions and frames (`phone`, `small` or `tablet`, `light` or `dark` background), and optionally a `footer` added under the default line.

To make them again, from the repository root:

```bash
python3 .claude/skills/zayit-android/scripts/release-frame.py androidApp/docs/release/manifest.json androidApp/docs/release/
```

New screenshots: run `emu-demo.sh on` before taking them, for a clean status bar. For older shots, `statusbar-clean.py` redraws the status bar instead. Both are in `.claude/skills/zayit-android/scripts/`.
