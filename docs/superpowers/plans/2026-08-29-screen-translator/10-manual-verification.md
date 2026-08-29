### Task 10: Manual end-to-end verification (real device required)

**Files:** none — this task is a verification checklist for the app built in Tasks 1-9, not a code change.

**Interfaces:**
- Consumes: the fully wired app from Task 8.
- Produces: confidence that the golden path and the spec's documented error cases behave as designed before calling v1 done.

- [ ] **Step 1: Install and grant permissions**

1. Build and install the debug APK on a physical Android device: `./gradlew installDebug` (emulator screen capture can be unreliable for `MediaProjection`).
2. Open the app, tap "Conceder permissão de sobreposição", grant it in system settings, return to the app.
3. Confirm the status text updates to "Permissão concedida. Pronto para iniciar."

- [ ] **Step 2: Start the bubble and verify the golden path**

1. Tap "Iniciar bolha flutuante" → accept the system screen-capture dialog.
2. Confirm the floating bubble appears over the home screen and is draggable without triggering a tap.
3. Open any app with English text on screen (e.g. a browser article or an e-book page).
4. Tap the floating bubble (a real tap, not a drag) → confirm it dims briefly (loading state from `setLoading(true)`), then translated boxes appear over the original text's exact positions.
5. Tap outside any translated box → confirm the overlay disappears and the bubble remains visible and usable for another translation.

- [ ] **Step 3: Verify documented error cases from the spec**

1. Point the bubble at a screen with no text (e.g. a photo or blank screen) → tap it → confirm no overlay appears and the app does not crash.
2. Before ever translating for the first time, enable airplane mode, then tap the bubble on a screen with English text → confirm the app does not crash even though the ML Kit translation model can't be downloaded (per the design's error-handling section, a friendlier in-overlay message is an acceptable v2 follow-up — the v1 bar is "no crash").
3. Turn airplane mode off, retry the same screen → confirm the model downloads and translation succeeds.
4. From `MainActivity`, revoke the overlay permission via system settings, relaunch the app → confirm it shows "Permissão de sobreposição necessária" and does not attempt to start the bubble service.
5. Tap "Iniciar bolha flutuante" and deny/cancel the system screen-capture dialog → confirm the app does not crash and the bubble (if already shown) simply does nothing on tap since no `captureManager` was set.

- [ ] **Step 4: Record results and close out v1**

If all checks in Steps 2-3 pass, v1 is functionally complete per the design spec. File any failures as follow-up tasks rather than blocking — note them in `docs/superpowers/specs/2026-08-29-screen-translator-design.md` under a new "Known issues" section if any are found.

- [ ] **Step 5: Final commit (only if Step 4 required doc changes)**

```bash
git add docs/superpowers/specs/2026-08-29-screen-translator-design.md
git commit -m "docs: record v1 manual verification results"
```
