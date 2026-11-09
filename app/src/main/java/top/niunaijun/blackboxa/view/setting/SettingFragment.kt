package top.niunaijun.blackboxa.view.setting

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.app.AppManager
import top.niunaijun.blackboxa.util.toast
import top.niunaijun.blackboxa.view.gms.GmsManagerActivity

class SettingFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.setting, rootKey)

        initGms()

        invalidHideState {
            val rootHidePreference: Preference = (findPreference("root_hide")!!)
            val hideRoot = AppManager.mBlackBoxLoader.hideRoot()
            rootHidePreference.setDefaultValue(hideRoot)
            rootHidePreference
        }

        invalidHideState {
            val daemonPreference: Preference = (findPreference("daemon_enable")!!)
            val mDaemonEnable = AppManager.mBlackBoxLoader.daemonEnable()
            daemonPreference.setDefaultValue(mDaemonEnable)
            daemonPreference
        }

        invalidHideState {
            val vpnPreference: Preference = (findPreference("use_vpn_network")!!)
            val mUseVpnNetwork = AppManager.mBlackBoxLoader.useVpnNetwork()
            vpnPreference.setDefaultValue(mUseVpnNetwork)
            vpnPreference
        }

        invalidHideState {
            val disableFlagSecurePreference: Preference = (findPreference("disable_flag_secure")!!)
            val mDisableFlagSecure = AppManager.mBlackBoxLoader.disableFlagSecure()
            disableFlagSecurePreference.setDefaultValue(mDisableFlagSecure)
            disableFlagSecurePreference
        }

        invalidHideState {
            val autoImportPreference: Preference = (findPreference("auto_import_whatsapp")!!)
            val mAutoImportWhatsApp = AppManager.mBlackBoxLoader.autoImportWhatsApp()
            autoImportPreference.setDefaultValue(mAutoImportWhatsApp)
            autoImportPreference
        }

        invalidHideState {
            val autoLaunchPreference: Preference = (findPreference("auto_launch_whatsapp")!!)
            val mAutoLaunchWhatsApp = AppManager.mBlackBoxLoader.autoLaunchWhatsApp()
            autoLaunchPreference.setDefaultValue(mAutoLaunchWhatsApp)
            autoLaunchPreference
        }

        invalidHideState {
            val singleWindowPreference: Preference = (findPreference("single_window_mode")!!)
            val mSingleWindow = AppManager.mBlackBoxLoader.singleWindow()
            singleWindowPreference.setDefaultValue(mSingleWindow)
            singleWindowPreference
        }

        invalidHideState {
            val linkGuidePreference: Preference = (findPreference("link_device_guide")!!)
            val mLinkDeviceGuide = AppManager.mBlackBoxLoader.linkDeviceGuide()
            linkGuidePreference.setDefaultValue(mLinkDeviceGuide)
            linkGuidePreference
        }

        initMigration()
        initSendLogs()
    }

    private fun initGms() {
        val gmsManagerPreference: Preference = (findPreference("gms_manager")!!)

        if (BlackBoxCore.get().isSupportGms) {

            gmsManagerPreference.setOnPreferenceClickListener {
                GmsManagerActivity.start(requireContext())
                true
            }
        } else {
            gmsManagerPreference.summary = getString(R.string.no_gms)
            gmsManagerPreference.isEnabled = false
        }
    }

    private fun invalidHideState(block: () -> Preference) {
        val pref = block()
        pref.setOnPreferenceChangeListener { preference, newValue ->
            val tmpHide = (newValue == true)
            when (preference.key) {
                "root_hide" -> {

                    AppManager.mBlackBoxLoader.invalidHideRoot(tmpHide)
                }
                "daemon_enable" -> {
                    AppManager.mBlackBoxLoader.invalidDaemonEnable(tmpHide)
                }
                "use_vpn_network" -> {
                    AppManager.mBlackBoxLoader.invalidUseVpnNetwork(tmpHide)
                }
                "disable_flag_secure" -> {
                    AppManager.mBlackBoxLoader.invalidDisableFlagSecure(tmpHide)
                }
                "auto_import_whatsapp" -> {
                    AppManager.mBlackBoxLoader.invalidAutoImportWhatsApp(tmpHide)
                }
                "auto_launch_whatsapp" -> {
                    AppManager.mBlackBoxLoader.invalidAutoLaunchWhatsApp(tmpHide)
                }
                "single_window_mode" -> {
                    AppManager.mBlackBoxLoader.invalidSingleWindow(tmpHide)
                }
                "link_device_guide" -> {
                    AppManager.mBlackBoxLoader.invalidLinkDeviceGuide(tmpHide)
                }
            }

            toast(R.string.restart_module)
            return@setOnPreferenceChangeListener true
        }
    }
    private fun initMigration() {
        val exportPreference: Preference? = findPreference("export_whatsapp_sandbox")
        val importPreference: Preference? = findPreference("import_whatsapp_sandbox")

        exportPreference?.setOnPreferenceClickListener {
            it.isEnabled = false
            Thread {
                val file = top.niunaijun.blackboxa.util.MigrationManager.export()
                activity?.runOnUiThread {
                    it.isEnabled = true
                    val msg = if (file != null) {
                        "Exported to: " + file.absolutePath.removePrefix("/sdcard")
                    } else {
                        "Export failed — see logcat tag Migration"
                    }
                    android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_LONG).show()
                }
            }.start()
            true
        }

        importPreference?.setOnPreferenceClickListener {
            android.app.AlertDialog.Builder(requireContext())
                    .setTitle(R.string.import_whatsapp_sandbox)
                    .setMessage("Replaces ALL current WhatsApp clone data with the newest export zip. The clone closes while restoring. Continue?")
                    .setPositiveButton("Restore") { _, _ ->
                        it.isEnabled = false
                        Thread {
                            val ok = top.niunaijun.blackboxa.util.MigrationManager.importNewest()
                            activity?.runOnUiThread {
                                it.isEnabled = true
                                android.widget.Toast.makeText(
                                        requireContext(),
                                        if (ok) "Restore complete — WhatsApp clone relaunched" else "Restore failed — see logcat tag Migration",
                                        android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }.start()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            true
        }
    }

    private fun initSendLogs() {
        val sendLogsPreference: Preference? = findPreference("send_logs")
        sendLogsPreference?.setOnPreferenceClickListener {
            it.isEnabled = false
            BlackBoxCore.get()
                    .sendLogs(
                            "Manual Log Upload from Settings",
                            true,
                            object : BlackBoxCore.LogSendListener {
                                override fun onSuccess() {
                                    activity?.runOnUiThread { sendLogsPreference.isEnabled = true }
                                }

                                override fun onFailure(error: String?) {
                                    activity?.runOnUiThread { sendLogsPreference.isEnabled = true }
                                }
                            }
                    )
            toast("Sending logs... (Check notifications for status)")
            true
        }
    }
}
