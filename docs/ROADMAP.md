# Dock roadmap

Dock is an open-source, fully offline document scanner for Android: camera → find the page →
straighten it → clean it up → PDF. No accounts, no cloud, no AI, no network permission.

## How we work

Every phase runs the same loop:

1. **Build**: implement the phase in small, testable pieces. Pure logic lives in `core:scanner`
   with unit tests; image algorithms get on-device tests with real or synthetic photos.
2. **Verify on the emulator**: install, click through the flows, check logs, inspect exported files.
3. **Test on a real phone**: the emulator camera can't judge edge detection, focus, lighting or
   speed. Real paper on a real desk is the only honest test.
4. **Feedback → fix**: anything that feels slow or wrong becomes a task, and every detection
   failure on a real photo becomes a regression test (`core/scanner/src/androidTest/assets`).
5. **Tag a version** once the phase's checklist passes.

## Phase 0: Foundation ✅

- [x] Gradle project, version catalog, `:app` + `:core:scanner` modules
- [x] Jetpack Compose + Material 3 (dynamic color, dark mode)
- [x] OpenCV 4.12 from Maven Central, CameraX 1.5
- [x] `INTERNET` permission explicitly removed

## Phase 1: Prototype (core loop) ✅

- [x] Camera with live document outline (CameraX analysis → OpenCV, frames dropped when busy, never lag)
- [x] Tap-to-focus, pinch-zoom, torch
- [x] Capture → automatic edge detection → perspective correction
- [x] Import photos from the gallery (Photo Picker, no storage permission)
- [x] Corner editor with magnifier, "Auto" / "Full page" reset
- [x] Filters: Original, Auto (shadow removal + white balance), Grayscale, B&W
- [x] Rotate pages
- [x] Multi-page session, page grid, delete
- [x] PDF export (JPEGs embedded as-is, so files stay small)

## Phase 2: Library & navigation ✅

The library is plain files: PDFs and folders live in a real folder (Documents/Dock by
default) granted through the system folder picker, so they're visible in any file manager.
No database to get out of sync.

- [x] First-launch storage setup: creates Documents/Dock, or the user picks any folder
- [x] Floating frosted-glass island (Home · Shelf) + camera bubble, ported from braim
- [x] Home: every PDF newest first, grouped by date, with search across names and folders
- [x] Shelf: real sub-folders; create, rename, delete; browse into folders
- [x] PDF rows: first-page thumbnail, page count, size; share, rename, move, delete
- [x] Review screen: scanned pages + save sheet (name + destination folder)
- [x] Built-in PDF viewer
- [x] Design pass with the apple-design skill: large tight titles, springs, glass chrome
- [ ] Keep an unsaved scan across process death (draft session on disk)
- [ ] Swipe between pages in the editor
- [ ] Add pages to an existing PDF
- [ ] Delete with undo instead of a confirmation dialog
- [ ] Pinch-zoom in the viewer
- [ ] Move UI strings to `strings.xml` (ready for translations)

## Phase 2.5: Page editor ← we are here

Two-stage rendering: the expensive OpenCV base (crop, perspective, filter, rotation) only
re-runs when those change; brightness/contrast and ink are composited on top cheaply and
previewed live with the same math.

- [x] Toolbar in order: Retake · Crop · Edit text · Filters · Bright · Eraser · Markup · Adjust · Reorder
- [x] Retake: camera replaces the page's photo, keeping its filter and brightness
- [x] Crop (with rotate)
- [x] Edit text: placeholder until OCR (Phase 5)
- [x] Filters with "Apply to all pages"
- [x] Bright: brightness + contrast sliders, live preview, "Apply to all pages"
- [x] Eraser: paint with a color (white by default), eyedropper picks colors from the page, undo
- [x] Markup: preset colors + full color picker, stroke width and opacity, undo
- [x] Adjust: A3/A4/A5/Letter/Legal, portrait/landscape; move (drag) and resize (pinch or
      slider) the page; put several pages on one sheet (e.g. ID card front + back); pages
      stay on the paper; exported as vectors, no re-rasterizing
- [x] Reorder: drag and drop in the review grid, with auto-scroll
- [ ] **Your test on a real phone** ← next step
- [ ] Auto filter washes out large dark color blocks (e.g. a card's blue header); use Original for those for now
- [ ] Pinch-zoom while drawing for precise erasing
- [ ] Strokes stay put on rotate, but not when the crop changes afterwards

## Phase 3: Scan quality

- [ ] Auto-capture when the outline is steady (`QuadSmoother.stableFrames` is already there)
- [ ] Batch mode: keep shooting, review at the end
- [ ] True aspect-ratio estimation from perspective (pages come out slightly too wide or tall
      under strong tilt today) + "snap to A4 / Letter"
- [ ] Mid-edge handles in the corner editor
- [ ] More filters: "Light text", "Whiteboard", brightness/contrast sliders
- [ ] Detection tuning on a corpus of real photos (light desks, glossy paper, receipts, ID cards)

## Phase 4: Export

- [ ] Page size options: Auto / A4 / Letter / fit to image; margins
- [ ] Quality presets (small / balanced / high)
- [ ] Export as JPEG / PNG images
- [ ] Import existing PDFs (`PdfRenderer`) to append scans
- [ ] Print via the Android print framework

## Phase 5: Offline OCR (optional)

- [ ] Tesseract (on-device) text recognition
- [ ] Searchable PDFs (invisible text layer), copy text from a page
- [ ] Search across documents

## Phase 6: Release

- [x] License: MIT
- [ ] Final application ID (permanent once published)
- [ ] Size: per-ABI APKs / App Bundle; optionally a custom OpenCV build with only
      `core`, `imgproc`, `imgcodecs` (~23 MB → ~5–8 MB native code)
- [ ] Baseline profiles for startup and scroll performance
- [ ] Accessibility pass (TalkBack, touch targets, contrast)
- [ ] GitHub Actions CI (build + unit tests), F-Droid metadata, reproducible builds
