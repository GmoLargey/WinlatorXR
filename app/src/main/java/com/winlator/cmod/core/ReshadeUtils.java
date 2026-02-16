package com.winlator.cmod.core;

import android.content.Context;
import android.util.Log;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.xenvironment.ImageFs;

import java.io.File;

public class ReshadeUtils {

    private static final TarCompressorUtils.Type PKG_TYPE = TarCompressorUtils.Type.ZSTD;
    private static final String RESHADE_PKG = "reshade_6.7.1.tzst";

    public static void extractReshade(Context context, ImageFs imageFs, Shortcut shortcut) {
        // Get destination path
        File exe = getLocalExeFile(imageFs, shortcut);
        File dst = exe.getParentFile();

        // Add or remove Reshade files
        boolean useReshade = shortcut.getExtra("useReshade", "0").equals("1");
        TarCompressorUtils.Status extracted = TarCompressorUtils.isExtracted(PKG_TYPE, context, RESHADE_PKG, dst);
        if (useReshade && (extracted == TarCompressorUtils.Status.NONE)) {
            Log.i("ReshadeUtils", "Extracting reshade to " + dst.getAbsolutePath());
            TarCompressorUtils.extract(PKG_TYPE, context, RESHADE_PKG, dst);
        } else if (!useReshade && (extracted == TarCompressorUtils.Status.FULL)) {
            Log.i("ReshadeUtils", "Removing reshade from " + dst.getAbsolutePath());
            TarCompressorUtils.remove(PKG_TYPE, context, RESHADE_PKG, dst);
        }

        // Log current status
        extracted = TarCompressorUtils.isExtracted(PKG_TYPE, context, RESHADE_PKG, dst);
        Log.i("ReshadeUtils", "Reshade isExtracted=" + extracted);
    }

    private static File getLocalExeFile(ImageFs imageFs, Shortcut shortcut) {
        int linkFollow = 0;
        File exe = getLocalFile(imageFs, shortcut);
        while (exe.getAbsolutePath().endsWith(".lnk")) {
            Log.i("ReshadeUtils", "Shortcut lead to shortcut " + exe.getAbsolutePath());
            try {
                exe = MSLink.getLocalFile(imageFs, shortcut.container, exe);
            } catch (Exception e) {
                e.printStackTrace();
            }
            linkFollow++;
            if (linkFollow > 5) {
                break;
            }
        }
        return exe;
    }

    private static File getLocalFile(ImageFs imageFs, Shortcut shortcut) {
        String winepath = shortcut.getFullExecutable();
        String output = winepath.substring(winepath.indexOf("wine ") + 5);
        output = output.replace(":", "");
        char drive = output.charAt(0);
        if ((drive >= 'A') && (drive <= 'Z')) {
            output = (char)(drive - 'A' + 'a') + output.substring(1);
        }
        output = output.replaceAll("\\\\", "/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < output.length(); i++) {
            if (output.charAt(i) == '/' && i + 1 < output.length()) {
                if ((output.charAt(i + 1) == 32) || (output.charAt(i + 1) == 47)) {
                    continue;
                }
            }
            sb.append(output.charAt(i));
        }

        for (String[] it : Container.drivesIterator(shortcut.container.getDrives())) {
            if (it[0].compareToIgnoreCase(drive + "") == 0) {
                return new File(it[1], sb.substring(2));
            }
        }
        return new File(imageFs.getRootDir(), ImageFs.WINEPREFIX + "/drive_" + sb);
    }
}
