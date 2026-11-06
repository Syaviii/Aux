// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.utils;

import android.content.Context;
import android.os.Environment;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.util.HashSet;

/* JADX INFO: loaded from: classes.dex */
public class MemoryStorage {
    public static final String ENV_SECONDARY_STORAGE = "SECONDARY_STORAGE";
    public static final String EXTERNAL_SD_CARD = "externalSdCard";
    public static final String SD_CARD = "sdCard";

    private MemoryStorage() {
    }

    public static boolean isAvailable() {
        String externalStorageState = Environment.getExternalStorageState();
        return "mounted".equals(externalStorageState) || "mounted_ro".equals(externalStorageState);
    }

    public static String[] getStorageDirectories(Context context) {
        String absolutePath;
        int iIndexOf;
        HashSet hashSet = new HashSet();
        File[] externalFilesDirs = ContextCompat.getExternalFilesDirs(context, null);
        for (int i = 0; i < externalFilesDirs.length; i++) {
            if (externalFilesDirs[i] != null && (iIndexOf = (absolutePath = externalFilesDirs[i].getAbsolutePath()).indexOf("/Android/data/")) >= 0 && iIndexOf <= absolutePath.length()) {
                hashSet.add(absolutePath.substring(0, iIndexOf));
            }
        }
        return (String[]) hashSet.toArray(new String[hashSet.size()]);
    }

    public static String getSdCardPath() {
        return Environment.getExternalStorageDirectory().getPath() + "/";
    }

    public static boolean isWritable() {
        return "mounted".equals(Environment.getExternalStorageState());
    }
}
