package me.weishu.kernelsu.magica;

import android.app.ZygotePreload;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;

public class AppZygotePreload implements ZygotePreload {
    public static final String TAG = "KernelSUMagica";

    private static native void forkDontCareAndExecKsud(String ksudPath, String packageName);

    @Override
    public void doPreload(@NonNull ApplicationInfo appInfo) {
        int blockReason = JailbreakSupport.getJailbreakBlockReason();
        if (blockReason != 0) {
            Log.w(TAG, "Skipping Magica on an incompatible system: " + blockReason);
            return;
        }
        File f = new File(appInfo.nativeLibraryDir, "libksud.so");
        if (!f.isFile()) {
            Log.e(TAG, "Cannot start Magica: libksud.so is missing");
            return;
        }
        try {
            System.loadLibrary("kernelsu");
            Log.d(TAG, "executing magica ...");
            forkDontCareAndExecKsud(f.getAbsolutePath(), appInfo.packageName);
        } catch (Throwable t) {
            Log.e(TAG, "failed to late load", t);
        }
    }
}
