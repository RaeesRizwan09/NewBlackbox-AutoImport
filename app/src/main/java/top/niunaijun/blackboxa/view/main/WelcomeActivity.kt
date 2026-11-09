package top.niunaijun.blackboxa.view.main

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import top.niunaijun.blackboxa.util.InjectionUtil
import top.niunaijun.blackboxa.view.list.ListViewModel

class WelcomeActivity : AppCompatActivity() {

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        scheduleAutoLaunch()
        jump()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A: install-if-missing / B: update-if-newer for WhatsApp (clones the
        // host device's own copy). Runs before the home list is loaded so a
        // sandboxed copy of WhatsApp is available on first run.
        try {
            top.niunaijun.blackboxa.util.AutoImportManager.importAllIfNeeded()
        } catch (t: Throwable) {
            android.util.Log.w("AutoImportWelcome", "auto-import failed", t)
        }
        scheduleAutoLaunch()
        previewInstalledAppList()
        jump()
    }

    /**
     * Kick the WhatsApp clone to the front when auto-launch is enabled, so the
     * user lands inside WhatsApp right after opening BlackBox.
     *
     * The launch must happen AFTER MainActivity is on screen (jump() below):
     * launching before it means MainActivity immediately starts over the
     * clone's task and buries it behind the home screen.
     */
    private fun scheduleAutoLaunch() {
        Handler(Looper.getMainLooper()).postDelayed(
                { postImport() },
                AUTO_LAUNCH_DELAY_MILLIS
        )
    }

    private fun postImport() {
        try {
            top.niunaijun.blackboxa.util.AutoImportManager.launchCloneToFront()
        } catch (t: Throwable) {
            android.util.Log.w("AutoImportWelcome", "auto-launch failed", t)
        }
    }

    private fun jump() {
        MainActivity.start(this)
        finish()
    }

    private fun previewInstalledAppList(){
        val viewModel = ViewModelProvider(this,InjectionUtil.getListFactory()).get(ListViewModel::class.java)
        viewModel.previewInstalledList()
    }

    companion object {
        /** Lets MainActivity finish its first frame before the clone is brought up. */
        private const val AUTO_LAUNCH_DELAY_MILLIS = 800L
    }
}
