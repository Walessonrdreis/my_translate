# Screen Translator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an Android app that captures the screen, OCRs and translates any English text to PT-BR on-device, and overlays the translation directly on top of the original text.

**Architecture:** Kotlin app with a Foreground Service hosting a draggable floating bubble (`WindowManager` overlay). Tapping the bubble triggers `MediaProjection` screen capture → ML Kit OCR → ML Kit on-device translation → a second `WindowManager` overlay drawing translated text boxes over the original positions. Tapping outside any box dismisses the overlay.

**Tech Stack:** Kotlin, AndroidX, Kotlin Coroutines, ML Kit Text Recognition (`com.google.mlkit:text-recognition`), ML Kit Translate (`com.google.mlkit:translate`), `MediaProjection` API, `WindowManager` overlays.

Full design rationale: `docs/superpowers/specs/2026-08-29-screen-translator-design.md`

## Global Constraints

- Min SDK: 26 (Android 8.0) — required for `TYPE_APPLICATION_OVERLAY`.
- Target/compile SDK: latest stable (35) at plan time.
- Translation direction fixed to EN→PT-BR for v1 (per spec, "Fora de escopo").
- No manual area selection in v1 — always translates the full visible screen (per spec).
- 100% on-device OCR/translation; `INTERNET` permission used only for the one-time ML Kit model download.
- Dismiss overlay on tap outside any translated box (per spec, confirmed by user).
- **Project convention (new, applies to all future plans in this repo):** implementation plans live in `docs/superpowers/plans/YYYY-MM-DD-<feature-name>/` as a set of files, and no single plan file may exceed 200 lines. Split further by task grouping if a file would exceed that.

## Task Index

| # | Task | File |
|---|------|------|
| 1 | Android project scaffold | `01-project-setup.md` |
| 2 | MainActivity + overlay permission flow | `02-main-activity.md` |
| 3 | `TextBlock` model + `TextRecognitionManager` (OCR) | `03-text-recognition.md` |
| 4 | `TranslationManager` (on-device translation) | `04-translation.md` |
| 5 | `ScreenCaptureManager` (MediaProjection) | `05-screen-capture.md` |
| 6 | `BubbleService` + draggable bubble UI | `06-bubble-service.md` |
| 7 | `TranslationOverlayView` (in-place overlay) | `07-overlay-view.md` |
| 8 | MainActivity: request MediaProjection permission | `08-mainactivity-capture-permission.md` |
| 9 | Wire full pipeline in BubbleService + notification | `09-bubbleservice-pipeline.md` |
| 10 | Manual end-to-end verification | `10-manual-verification.md` |

Work through tasks in order — each depends on interfaces produced by earlier tasks (see each file's "Interfaces" block).

---
