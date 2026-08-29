### Task 2: MainActivity + overlay permission flow

**Files:**
- Create: `app/src/main/java/com/walesson/screentranslator/MainActivity.kt`
- Create: `app/src/main/res/layout/activity_main.xml`
- Create: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `BubbleService` (started via `Intent`, defined fully in Task 6 — only referenced here by class name and package, safe to compile against once Task 6 exists; for this task, starting it is wired but `BubbleService` can be a stub if built before Task 6, see Step 3 note).
- Produces: entry point that requests `SYSTEM_ALERT_WINDOW` permission and launches `BubbleService`.

- [ ] **Step 1: Create `app/src/main/res/values/strings.xml`**

```xml
<resources>
    <string name="app_name">Screen Translator</string>
    <string name="grant_overlay_permission">Conceder permissão de sobreposição</string>
    <string name="start_bubble">Iniciar bolha flutuante</string>
    <string name="permission_required">Permissão de sobreposição necessária</string>
</resources>
```

- [ ] **Step 2: Create `app/src/main/res/layout/activity_main.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:gravity="center"
    android:padding="24dp">

    <TextView
        android:id="@+id/statusText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textSize="16sp"
        android:paddingBottom="16dp" />

    <Button
        android:id="@+id/grantPermissionButton"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/grant_overlay_permission" />

    <Button
        android:id="@+id/startBubbleButton"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/start_bubble"
        android:layout_marginTop="12dp" />

</LinearLayout>
```

- [ ] **Step 3: Create `MainActivity.kt`**

Note: `BubbleService` is created in Task 6. If executing tasks strictly in order, create it now as an empty stub (`class BubbleService : Service() { override fun onBind(intent: Intent?) = null }` in its own file) so this task compiles; Task 6 will replace the stub body.

```kotlin
package com.walesson.screentranslator

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)

        findViewById<Button>(R.id.grantPermissionButton).setOnClickListener {
            requestOverlayPermission()
        }

        findViewById<Button>(R.id.startBubbleButton).setOnClickListener {
            if (hasOverlayPermission()) {
                startService(Intent(this, BubbleService::class.java))
            } else {
                requestOverlayPermission()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        statusText.text = if (hasOverlayPermission()) {
            "Permissão concedida. Pronto para iniciar."
        } else {
            getString(R.string.permission_required)
        }
    }

    private fun hasOverlayPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }
}
```

- [ ] **Step 4: Create the `BubbleService` stub (only if Task 6 not done yet)**

`app/src/main/java/com/walesson/screentranslator/BubbleService.kt`

```kotlin
package com.walesson.screentranslator

import android.app.Service
import android.content.Intent
import android.os.IBinder

class BubbleService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
```

- [ ] **Step 5: Build the app**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/walesson/screentranslator/MainActivity.kt app/src/main/java/com/walesson/screentranslator/BubbleService.kt app/src/main/res/layout/activity_main.xml app/src/main/res/values/strings.xml
git commit -m "feat: add MainActivity with overlay permission flow"
```
