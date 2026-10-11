# Dock

An open-source, fully offline document scanner for Android, built with Kotlin and Jetpack Compose.

Point the camera at a page and Dock finds the edges, straightens the page, cleans up shadows and
saves a PDF. No account, no cloud, no AI, no network access: the app doesn't even request the
`INTERNET` permission.

## Features (prototype)

- Live page outline in the camera preview
- Automatic crop + perspective correction, adjustable corners with a magnifier
- Filters: Original, Auto (shadow removal), Grayscale, Black & White
- Import photos from the gallery
- Multi-page PDFs saved straight into a real folder (Documents/Dock by default)
- **Home**: tools — scan, edit PDF, compress, PDF to images, merge, split, images to PDF
- **Files**: every PDF, newest first, with search · **Shelf**: organise PDFs into folders
- Page editor: crop, filters, brightness/contrast, eraser, markup, paper sizes, multi-page sheets
- Built-in PDF viewer; share, rename, move and delete from the list

See [docs/ROADMAP.md](docs/ROADMAP.md) for what's next.

## Project layout

| Module | What it does |
|---|---|
| `core:scanner` | Image pipeline with no UI: `DocumentDetector` (OpenCV edge detection), `PageProcessor` (warp, filters), `ImageFilters`, `JpegPdfWriter` (tiny dependency-free PDF writer) |
| `core:pdf` | PDF operations that keep text and vectors: merge, split, compress (PdfBox-Android) |
| `app` | Compose UI: storage setup, main shell (glass island with Home / Shelf tabs + camera bubble), camera, review + save, page editor, PDF viewer. `data/storage/DockLibrary` wraps the Storage Access Framework |

## Building

Requirements: Android Studio (or JDK 17 + Android SDK 36).

```bash
./gradlew :app:assembleDebug          # build
./gradlew :app:installDebug           # install on a connected device / emulator
./gradlew :core:scanner:testDebugUnitTest                # JVM unit tests
./gradlew :core:scanner:connectedDebugAndroidTest        # detector tests on a device
```

Min SDK 26 (Android 8.0). The debug APK bundles native code for `arm64-v8a` and `x86_64`.

## Tech

Kotlin · Jetpack Compose · Material 3 · CameraX · OpenCV · PdfBox-Android · Coil · Haze (backdrop blur) · Navigation Compose

## License

MIT. See [LICENSE](LICENSE).
