package top.niunaijun.blackboxa.view.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackboxa.util.InjectionUtil
import top.niunaijun.blackboxa.view.list.ListViewModel

class WelcomeActivity : AppCompatActivity() {

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
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
        previewInstalledAppList()
        jump()
    }

    private fun jump() {
        MainActivity.start(this)
        finish()
    }

    private fun previewInstalledAppList(){
        val viewModel = ViewModelProvider(this,InjectionUtil.getListFactory()).get(ListViewModel::class.java)
        viewModel.previewInstalledList()
    }
}