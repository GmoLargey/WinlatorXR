/*
 * Copyright (C) 2024-2026 WinlatorXR
 *
 * This file is part of WinlatorXR.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.winlator.cmod.xr;

import android.content.Context;
import android.util.Log;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.MSLink;
import com.winlator.cmod.core.TarCompressorUtils;
import com.winlator.cmod.core.WineRegistryEditor;
import com.winlator.cmod.xenvironment.ImageFs;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class ReshadeUtils {

    private static final String PATH_CHARS = "qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM01234567890.";
    private static final TarCompressorUtils.Type PKG_TYPE = TarCompressorUtils.Type.ZSTD;
    private static final String[] RESHADE_DIRECTX_CLONES = {"d3d10.dll", "d3d11.dll", "d3d12.dll"};
    private static final String RESHADE_DIRECTX_DLL = "dxgi.dll";
    private static final String RESHADE_DIRECTX_PKG = "reshade-directx.tzst";
    private static final String RESHADE_PLUGINS_PKG = "reshade-plugins.tzst";
    private static final String RESHADE_VULKAN_PKG = "reshade-vulkan.tzst";
    private static final String VULKAN_INI_FILE = "ReShade/ReShadeApps.ini";
    private static final String VULKAN_KEY_32BIT = "Software\\Khronos\\Vulkan\\ImplicitLayers";
    private static final String VULKAN_KEY_64BIT = "Software\\WOW6432Node\\Khronos\\Vulkan\\ImplicitLayers";
    private static final String VULKAN_NAME_32BIT = "C:\\ProgramData\\ReShade\\ReShade32.json";
    private static final String VULKAN_NAME_64BIT = "C:\\ProgramData\\ReShade\\ReShade64.json";

    public static void update(Context context, ImageFs imageFs, Shortcut shortcut) {
        // Get destination path
        File exe = getLocalExeFile(imageFs, shortcut);
        File dst = exe.getParentFile();
        boolean useReshade = shortcut.getExtra("useReshade", "0").equals("1");

        // Update packages
        updatePlugins(context, useReshade, dst);
        updateDirectX(context, useReshade, dst);
        updateVulkan(context, useReshade, shortcut, imageFs, exe);

        // Workaround for launchers
        File ue = locateUE(dst);
        if (ue != null) {
            dst = ue;
            exe = getFirstExe(dst);
            updatePlugins(context, useReshade, dst);
            updateDirectX(context, useReshade, dst);
            updateVulkan(context, useReshade, shortcut, imageFs, exe);
        }
    }

    private static void updateDirectX(Context context, boolean useReshade, File dst) {
        // Add or remove Reshade files
        TarCompressorUtils.Status extracted;
        extracted = TarCompressorUtils.isExtracted(PKG_TYPE, context, RESHADE_DIRECTX_PKG, dst);
        if (extracted != TarCompressorUtils.Status.PARTIAL) {
            if (useReshade) {
                Log.i("ReshadeUtils", "Extracting reshade to " + dst.getAbsolutePath());
                TarCompressorUtils.extract(PKG_TYPE, context, RESHADE_DIRECTX_PKG, dst);
                cloneFile(new File(dst, RESHADE_DIRECTX_DLL), RESHADE_DIRECTX_CLONES);
            } else {
                Log.i("ReshadeUtils", "Removing reshade from " + dst.getAbsolutePath());
                TarCompressorUtils.remove(PKG_TYPE, context, RESHADE_DIRECTX_PKG, dst);
                deleteClones(dst, RESHADE_DIRECTX_CLONES);
            }
        }

        // Log current status
        extracted = TarCompressorUtils.isExtracted(PKG_TYPE, context, RESHADE_DIRECTX_PKG, dst);
        Log.i("ReshadeUtils", "Reshade isExtracted=" + extracted);
    }

    private static void updatePlugins(Context context, boolean useReshade, File dst) {
        if (useReshade) {
            Log.i("ReshadeUtils", "Extracting reshade to " + dst.getAbsolutePath());
            TarCompressorUtils.extract(PKG_TYPE, context, RESHADE_PLUGINS_PKG, dst);
        } else {
            Log.i("ReshadeUtils", "Removing reshade from " + dst.getAbsolutePath());
            TarCompressorUtils.remove(PKG_TYPE, context, RESHADE_PLUGINS_PKG, dst);
        }
    }

    private static void updateVulkan(Context context, boolean useReshade, Shortcut shortcut, ImageFs imageFs, File exe) {
        // Update registry
        File systemRegFile = new File(shortcut.container.getRootDir(), ".wine/system.reg");
        try (WineRegistryEditor registryEditor = new WineRegistryEditor(systemRegFile)) {
            registryEditor.setCreateKeyIfNotExist(true);
            registryEditor.setDwordValue(VULKAN_KEY_32BIT, VULKAN_NAME_32BIT, 0);
            registryEditor.setDwordValue(VULKAN_KEY_64BIT, VULKAN_NAME_64BIT, 0);
        }

        // Update Reshade files
        File dst = new File(imageFs.getRootDir(), ImageFs.WINEPREFIX + "/drive_c/ProgramData/");
        dst.mkdirs();
        TarCompressorUtils.extract(PKG_TYPE, context, RESHADE_VULKAN_PKG, dst);

        // Setup ini file
        try {
            FileOutputStream fos = new FileOutputStream(new File(dst, VULKAN_INI_FILE));
            String data = "Apps=";
            if (useReshade) {
                data += getSandboxedPath(imageFs, shortcut.container, exe);
            }
            fos.write(data.getBytes(StandardCharsets.UTF_8));
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void cloneFile(File file, String[] names) {
        File dir = file.getParentFile();
        for (String name : names) {
            FileUtils.copy(file, new File(dir, name));
        }
    }

    private static void deleteClones(File dir, String[] names) {
        for (String name : names) {
            new File(dir, name).delete();
        }
    }

    private static File getFirstExe(File dst) {
        File[] files = dst.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getAbsolutePath().endsWith(".exe")) {
                    return file;
                }
            }
        }
        return null;
    }

    private static File getLocalExeFile(ImageFs imageFs, Shortcut shortcut) {
        int linkFollow = 0;
        File exe = getLocalFile(imageFs, shortcut);
        while (exe.getAbsolutePath().endsWith(".lnk")) {
            Log.i("ReshadeUtils", "Shortcut lead to shortcut " + exe.getAbsolutePath());
            try {
                Iterable<String[]> drives = Container.drivesIterator(shortcut.container.getDrives());
                exe = MSLink.getLocalFile(imageFs.getRootDir(), ImageFs.WINEPREFIX, drives, exe);
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
                if (PATH_CHARS.indexOf(output.charAt(i + 1)) < 0) {
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

    private static String getSandboxedPath(ImageFs imageFs, Container container, File file) {
        // Get sandboxed drive
        String output = "";
        String base = new File(imageFs.getRootDir(), ImageFs.WINEPREFIX + "/drive_c/").getAbsolutePath();
        if (file.getAbsolutePath().startsWith(base)) {
            output = "C:\\";
        } else {
            for (String[] it : Container.drivesIterator(container.getDrives())) {
                if (file.getAbsolutePath().startsWith(it[1])) {
                    output = it[0].toUpperCase() + ":\\";
                    base = it[1];
                }
            }
        }

        // Get full path
        String path = file.getAbsolutePath().substring(base.length() + 1);
        output += path.replace("/", "\\");
        return output;
    }

    private static File locateUE(File dst) {
        File[] files = dst.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    File result = locateUE(file);
                    if (result != null) {
                        return result;
                    }
                }
            }
        }
        return dst.getAbsolutePath().endsWith("Binaries/Win64") ? dst : null;
    }
}
