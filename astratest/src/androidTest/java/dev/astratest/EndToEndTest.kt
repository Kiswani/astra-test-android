package dev.astratest

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CheckoutTestActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48)
            addView(TextView(this@CheckoutTestActivity).apply { text = "Checkout"; textSize = 24f })
            addView(TextView(this@CheckoutTestActivity).apply { text = "Total  AED 305.00"; textSize = 18f })
            addView(EditText(this@CheckoutTestActivity).apply { hint = "Password"; setText("secret"); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD })
            addView(Button(this@CheckoutTestActivity).apply { text = "Apply WELCOME20" })
        })
    }
}

@RunWith(AndroidJUnit4::class)
class EndToEndTest {
    @Test fun sendsARealReportWithAScreenshot() {
        val args = InstrumentationRegistry.getArguments()
        val key = args.getString("astraKey") ?: return
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        AstraTest.start(app, key, AstraTest.Options(appVersion = "9.9.0", build = "1"))
        AstraTest.identify(email = "sdk-e2e@astratest.dev", name = "Android SDK e2e")
        ActivityScenario.launch(CheckoutTestActivity::class.java).use {
            Thread.sleep(1500)
            android.util.Log.e("Checkout", "promo returned percent, treated as fils")
            val done = CountDownLatch(1)
            var ref: String? = null
            var err: Throwable? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                AstraTest.send("Android SDK end-to-end check: promo code shows applied but the total does not change.") { r ->
                    r.onSuccess { ref = it.ref }.onFailure { err = it }
                    done.countDown()
                }
            }
            assertTrue("timed out", done.await(90, TimeUnit.SECONDS))
            assertTrue("failed: ${err?.message}", ref != null)
            android.util.Log.i("AstraE2E", "SENT $ref")
            println("SENT $ref")
        }
    }

    @Test fun opensTheSheet() {
        val args = InstrumentationRegistry.getArguments()
        val key = args.getString("astraKey") ?: return
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        AstraTest.start(app, key)
        ActivityScenario.launch(CheckoutTestActivity::class.java).use {
            Thread.sleep(1500)
            AstraTest.showReport()
            Thread.sleep(if (args.getString("holdSheet") != null) 15000 else 2000)
        }
    }
}
