package me.weishu.kernelsu.magica;

import android.os.Build;
import android.system.Os;

import java.io.FileInputStream;
import java.io.IOException;

import me.weishu.kernelsu.KernelCompatibility;
import me.weishu.kernelsu.R;

/** Read-only checks shared by the UI, boot receiver, and app-zygote preload. */
public final class JailbreakSupport {
    private JailbreakSupport() {}

    public static boolean isKernelSupported() {
        return getKernelBlockReason() == 0;
    }

    public static int getKernelBlockReason() {
        final String release;
        try {
            release = Os.uname().release;
        } catch (Exception e) {
            return R.string.jailbreak_unsupported_kernel;
        }
        if (KernelCompatibility.isModuleLoadingBlocked(Build.MODEL, Build.VERSION.INCREMENTAL, release)) {
            return R.string.jailbreak_blocked_firmware;
        }
        if (!KernelCompatibility.isSupportedKernel(Build.DEVICE, release)) {
            return R.string.jailbreak_unsupported_kernel;
        }
        return 0;
    }

    public static int getJailbreakBlockReason() {
        int kernelReason = getKernelBlockReason();
        if (kernelReason != 0) return kernelReason;

        // Never spawn getenforce (or any other child process) from app zygote.
        // An unreadable status is unknown, not evidence of Permissive mode.
        try (FileInputStream enforce = new FileInputStream("/sys/fs/selinux/enforce")) {
            int value = enforce.read();
            if (value == '0') return 0;
            if (value == '1') return R.string.jailbreak_requires_permissive;
        } catch (IOException | SecurityException ignored) {
            // Fail closed when the SELinux status cannot be established.
        }
        return R.string.jailbreak_selinux_unknown;
    }
}
