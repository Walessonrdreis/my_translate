### Task 8: Request MediaProjection permission from `MainActivity`

**Files:**
- Modify: `app/src/main/java/com/walesson/screentranslator/MainActivity.kt`

**Interfaces:**
- Consumes: `android.media.projection.MediaProjectionManager`, `BubbleService` constants defined in Task 9 (`ACTION_START_WITH_PROJECTION`, `EXTRA_RESULT_CODE`, `EXTRA_RESULT_DATA`) — since Task 9 hasn't run yet if executing in order, add these three constants to the `BubbleService` stub now (see Step 2) and Task 9 will use them as-is.
- Produces: `MainActivity` requests the screen-capture intent and forwards the result to `BubbleService` as a foreground service start.

- [ ] **Step 1: Add the three constants to the `BubbleService` stub**

In `BubbleService.kt`, add inside the class:

```kotlin
companion object {
    const val ACTION_START_WITH_PROJECTION = "action_start_with_projection"
    const val EXTRA_RESULT_CODE = "extra_result_code"
    const val EXTRA_RESULT_DATA = "extra_result_data"
}
```

- [ ] **Step 2: Replace the `startBubbleButton` click listener in `MainActivity.kt`**

Add these imports:

```kotlin
import android.app.Activity
import android.media.projection.MediaProjectionManager
import androidx.activity.result.contract.ActivityResultContracts
```

Add this property and launcher inside the `MainActivity` class:

```kotlin
private val projectionManager by lazy {
    getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
}

private val captureLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { result ->
    if (result.resultCode == Activity.RESULT_OK && result.data != null) {
        val intent = Intent(this, BubbleService::class.java).apply {
            action = BubbleService.ACTION_START_WITH_PROJECTION
            putExtra(BubbleService.EXTRA_RESULT_CODE, result.resultCode)
            putExtra(BubbleService.EXTRA_RESULT_DATA, result.data)
        }
        startForegroundService(intent)
    }
}
```

Replace the existing `startBubbleButton` click listener body with:

```kotlin
findViewById<Button>(R.id.startBubbleButton).setOnClickListener {
    if (hasOverlayPermission()) {
        captureLauncher.launch(projectionManager.createScreenCaptureIntent())
    } else {
        requestOverlayPermission()
    }
}
```

- [ ] **Step 3: Build the app**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/walesson/screentranslator/MainActivity.kt app/src/main/java/com/walesson/screentranslator/BubbleService.kt
git commit -m "feat: request MediaProjection permission from MainActivity"
```
