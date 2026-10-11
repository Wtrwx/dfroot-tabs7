package me.weishu.kernelsu;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Firmware-specific compatibility, independent of root availability. */
public final class KernelCompatibility {
    private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:$|[-+])");
    private static final String S21_RELEASE = "5.4.242-30958140-abG991BXXSJHZC2";
    private static final String T878U_RELEASE = "4.19.113-27114284";

    private KernelCompatibility() {}

    // This is the GKI version floor; a matching KMI is still required for LKM.
    public static boolean isGkiVersion(int major, int minor) {
        return major > 5 || (major == 5 && minor >= 10);
    }

    public static boolean isSupportedKernel(String device, String release) {
        // Preserve the tested S21 backport without treating all non-GKI kernels
        // as compatible. Its module is specific to this device and firmware.
        if ("o1s".equals(device) && S21_RELEASE.equals(release)) {
            return true;
        }
        if (release == null) return false;
        Matcher version = VERSION.matcher(release);
        if (!version.find()) return false;
        try {
            return isGkiVersion(Integer.parseInt(version.group(1)), Integer.parseInt(version.group(2)));
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    public static boolean isModuleLoadingBlocked(String model, String firmware, String release) {
        // Verified against the DXE1 kernel/config in refs/t878u-DXE1. Do not
        // extend this conclusion to other tablets, firmware, or custom kernels.
        return "SM-T878U".equals(model)
                && "T878USQS8DXE1".equals(firmware)
                && T878U_RELEASE.equals(release);
    }
}
