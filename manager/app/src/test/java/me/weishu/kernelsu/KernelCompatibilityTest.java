package me.weishu.kernelsu;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KernelCompatibilityTest {
    @Test
    public void nonGkiKernelsDoNotInheritTheS21Exception() {
        assertFalse(KernelCompatibility.isSupportedKernel("gts7l", "4.19.113-27114284"));
        assertFalse(KernelCompatibility.isSupportedKernel("other", "5.4.242-30958140-abG991BXXSJHZC2"));
        assertFalse(KernelCompatibility.isSupportedKernel("o1s", "5.4.242-other-firmware"));
        assertTrue(KernelCompatibility.isSupportedKernel("o1s", "5.4.242-30958140-abG991BXXSJHZC2"));
        assertFalse(KernelCompatibility.isGkiVersion(5, 4));
    }

    @Test
    public void gkiVersionFloorIsRetained() {
        assertFalse(KernelCompatibility.isGkiVersion(4, 19));
        assertFalse(KernelCompatibility.isGkiVersion(5, 9));
        assertTrue(KernelCompatibility.isGkiVersion(5, 10));
        assertTrue(KernelCompatibility.isGkiVersion(6, 1));
        assertTrue(KernelCompatibility.isSupportedKernel("oriole", "5.10.101-android12-9-g30979850fc20"));
        assertTrue(KernelCompatibility.isSupportedKernel("other", "6.6.1-android15-0"));
    }

    @Test
    public void dxe1ModuleBlockIsLimitedToTheVerifiedProfile() {
        assertTrue(KernelCompatibility.isModuleLoadingBlocked("SM-T878U", "T878USQS8DXE1", "4.19.113-27114284"));
        assertFalse(KernelCompatibility.isModuleLoadingBlocked("SM-T875", "T878USQS8DXE1", "4.19.113-27114284"));
        assertFalse(KernelCompatibility.isModuleLoadingBlocked("SM-T878U", "T878USQS9DXG3", "4.19.113-27114284"));
        assertFalse(KernelCompatibility.isModuleLoadingBlocked("SM-T878U", "T878USQS8DXE1", "4.19.113-custom"));
        assertFalse(KernelCompatibility.isModuleLoadingBlocked(null, null, null));
    }

    @Test
    public void invalidKernelVersionsFailClosed() {
        assertFalse(KernelCompatibility.isSupportedKernel("other", null));
        assertFalse(KernelCompatibility.isSupportedKernel("other", ""));
        assertFalse(KernelCompatibility.isSupportedKernel("other", "unknown"));
        assertFalse(KernelCompatibility.isSupportedKernel("other", "Linux 6.6.1"));
        assertFalse(KernelCompatibility.isSupportedKernel("other", "99999999999999.10.1"));
    }
}
