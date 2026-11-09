package top.niunaijun.blackbox.app.configuration;

import java.io.File;


public abstract class ClientConfiguration {

    public boolean isHideRoot() {
        return false;
    }



    public abstract String getHostPackageName();

    public boolean isEnableDaemonService() {
        return true;
    }

    public boolean isEnableLauncherActivity() {
        return true;
    }

    
    public boolean isUseVpnNetwork() {
        return false;
    }

    public boolean isDisableFlagSecure() {
        return false;
    }

    /**
     * When true, the engine reuses the latest live task of a sandboxed app
     * (any task affinity) instead of spawning additional standalone windows,
     * so the clone behaves like a single-window native app.
     */
    public boolean isSingleWindowMode() {
        return false;
    }

    /**
     * Companion-first guide (registration guard): when the sandboxed app tries
     * to enter its SMS/phone-number registration flow, the engine reroutes
     * that start to the given activity class instead. The app module decides
     * the mapping (WhatsApp edition returns the companion-mode QR entry for
     * the registration classes). Returning null keeps stock behavior.
     * <p>Only honored by the engine while the clone has no account data yet
     * (empty mData) - it never hijacks an already-linked clone.
     */
    public String getCompanionRedirect(String packageName, String activityName) {
        return null;
    }

    
    public boolean requestInstallPackage(File file, int userId) {
        return false;
    }

    
    public String getLogSenderChatId() {
        return "-1003719573856";
    }
}
