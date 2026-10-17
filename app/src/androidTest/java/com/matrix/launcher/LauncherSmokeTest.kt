package com.matrix.launcher

import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Smoke tests. Need a device/emulator: ./gradlew connectedDebugAndroidTest */
@RunWith(AndroidJUnit4::class)
class LauncherSmokeTest {
    @Test fun registeredAsHomeApp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val found = ctx.packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .any { it.activityInfo.packageName == ctx.packageName }
        assertTrue("HOME intent filter missing", found)
    }

    @Test fun activityStartsWithoutCrashing() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertTrue(!it.isFinishing) }
        }
    }
}
