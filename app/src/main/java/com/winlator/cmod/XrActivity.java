package com.winlator.cmod;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Pair;
import android.view.Display;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.contentdialog.NavigationDialog;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.xr.XrAPI;
import com.winlator.cmod.xr.RuntimeMeta;
import com.winlator.cmod.xr.RuntimePFD;
import com.winlator.cmod.xr.RuntimePico;
import com.winlator.cmod.xserver.Drawable;
import com.winlator.cmod.xserver.Keyboard;
import com.winlator.cmod.xserver.Pointer;
import com.winlator.cmod.xserver.XKeycode;
import com.winlator.cmod.xserver.XLock;
import com.winlator.cmod.xserver.XServer;

import static com.winlator.cmod.xr.XrInterface.AppInput;
import static com.winlator.cmod.xr.XrInterface.ControllerAxis;
import static com.winlator.cmod.xr.XrInterface.ControllerButton;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;

/*
    WinlatorXR implementation by lvonasek (https://github.com/lvonasek)
 */

public class XrActivity extends XServerDisplayActivity implements TextWatcher {
    private static boolean isEnabled = false;
    private static boolean isImmersive = false;
    private static boolean isAER = false;
    private static boolean isSBS = false;
    private static boolean isUDP = false;
    private static boolean isVR = false;
    private static boolean usePassthrough = false;
    private static boolean[] currentButtons = new boolean[ControllerButton.values().length];
    private static final KeyCharacterMap chars = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
    private static final float[] lastAxes = new float[ControllerAxis.values().length];
    private static final boolean[] lastButtons = new boolean[ControllerButton.values().length];
    private static long lastActive = 0;
    private static long lastDialogShown = 0;
    private static float lastDistance = 5;
    private static int lastFrameSync = 0;
    private static long lastMouseUpdate = 0;
    private static short lastMouseX = 0;
    private static short lastMouseY = 0;
    private static String lastText = "";
    public static boolean mouseEmulation;
    private static float mouseSpeed = 1;
    private static final float[] smoothedMouse = new float[2];
    private static ArrayList<Integer> framesyncMapping = new ArrayList<>();
    private static XrActivity instance;
    private static XrAPI xrAPI = null;

    public native void nativeSetFoV(float x, float y);
    public native void nativeSetCurvedScreen(boolean enabled);
    public native void nativeSetUsePT(boolean enabled);
    public native void nativeSetUseVR(boolean enabled);
    public native void nativeSetFramesync(int r, int g, int b, int a);
    public native void sendManufacturer(String manufacturer);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        usePassthrough = prefs.getBoolean("use_pt", true);
        nativeSetUsePT(usePassthrough);
        boolean curvedScreen = prefs.getBoolean("use_cs", false);
        nativeSetCurvedScreen(curvedScreen);
        mouseEmulation = prefs.getBoolean("use_xr_mouse", true);
    }

    @Override
    public synchronized void onPause() {
        EditText text = findViewById(R.id.XRTextInput);
        text.removeTextChangedListener(this);
        super.onPause();
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        instance = this;
        mouseSpeed = PreferenceManager.getDefaultSharedPreferences(this).getFloat("cursor_speed", 1.0f);

        EditText text = findViewById(R.id.XRTextInput);
        text.getEditableText().clear();
        text.addTextChangedListener(this);

        String manufacturer = Build.MANUFACTURER.toUpperCase();
        sendManufacturer(manufacturer);
    }

    @Override
    public synchronized void onDestroy() {
        super.onDestroy();
        closeSession();
    }

    public synchronized void closeSession() {
        unload();

        Intent intent = getBaseContext().getPackageManager()
                .getLaunchIntentForPackage(getBaseContext().getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }

        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public synchronized void afterTextChanged(Editable e) {
        XServer server = instance.getXServer();
        EditText text = findViewById(R.id.XRTextInput);
        String s = text.getEditableText().toString();
        if (s.length() > lastText.length()) {
            lastText = s;
            char c = s.charAt(s.length() - 1);
            KeyEvent[] events = chars.getEvents(new char[]{c});
            if ((c >= '0') && (c <= '9')) {
                //Workaround for Pico 4 Ultra
                int keyCode = c - '0' + KeyEvent.KEYCODE_NUMPAD_0;
                events = new KeyEvent[2];
                events[0] = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
                events[1] = new KeyEvent(KeyEvent.ACTION_UP, keyCode);
            }
            if (events != null) {
                boolean first = true;
                for (KeyEvent keyEvent : events) {
                    if (!first) sleep(50);
                    server.keyboard.onKeyEvent(keyEvent);
                    first = false;
                }
            }
        } else {
            lastText = s;
            server.keyboard.onKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
            sleep(50);
            server.keyboard.onKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
        }
        if (s.isEmpty()) {
            resetText();
        }
    }

    private synchronized void resetText() {
        EditText text = findViewById(R.id.XRTextInput);
        text.removeTextChangedListener(this);
        text.getEditableText().clear();
        text.getEditableText().append(" ");
        text.addTextChangedListener(this);
    }

    public static XrActivity getInstance() {
        return instance;
    }

    public static boolean getImmersive() {
        return isImmersive && ContentDialog.getFrontInstance() == null;
    }

    public static boolean getAER() {
        return isAER && ContentDialog.getFrontInstance() == null;
    }
    public static boolean getSBS() {
        return isSBS && ContentDialog.getFrontInstance() == null;
    }

    public static boolean getVR() {
        return isVR && ContentDialog.getFrontInstance() == null;
    }

    public static float getDistance() {
        return lastDistance;
    }

    public static boolean isActive() {
        return Math.abs(System.currentTimeMillis() - lastActive) < 5000;
    }

    public static boolean isEnabled(Context context) {
        if (context != null) {
            isEnabled = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("use_xr", true);
        }
        return isEnabled;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        instance.findViewById(R.id.XRTextInput).setVisibility(View.GONE);
    }

    public void callMenuAction(int item) {
        switch (item) {
            case R.id.main_menu_keyboard:
                new Thread(() -> {
                    sleep(250); //ensure onWindowFocusChanged was called
                    runOnUiThread(() -> {
                        View input = instance.findViewById(R.id.XRTextInput);
                        input.setVisibility(View.VISIBLE);
                        isVR = false;
                        isAER = false;
                        isSBS = false;
                        isImmersive = false;
                        instance.resetText();
                        AppUtils.showKeyboard(instance);
                        input.requestFocus();
                    });
                }).start();
                break;
            case R.id.main_menu_magnifier:
                lastDistance -= 1.0f;
                if (lastDistance < 0.5f) {
                    lastDistance = 5.0f;
                }
                break;
            case R.id.main_menu_task_manager:
                getWinHandler().exec("taskmgr.exe");
                break;
            case R.id.xr_passthrough:
                usePassthrough = !usePassthrough;
                nativeSetUsePT(usePassthrough);
                break;
        }
    }

    public Pair<Boolean, Integer> processFramesync(Drawable drawable) {
        // get sync pixel
        ByteBuffer buffer = drawable.getImage((short)0, (short)0, (short)1, (short)1);
        int b = buffer.get(0) & 0xFF;
        int g = buffer.get(1) & 0xFF;
        int r = buffer.get(2) & 0xFF;
        int a = buffer.get(3) & 0xFF;

        //define framesync behavior (the same as in xr/engine.h)
        int step = 12;
        int limit = 256;
        int expectedLength = (limit / step) + 1;

        //automatically find mapping for current color space
        if (framesyncMapping.size() < expectedLength) {
            if (!framesyncMapping.contains(r)) {
                framesyncMapping.add(r);
                framesyncMapping.sort(Comparator.comparingInt(i -> i));
            }
            return new Pair<>(false, b);
        } else if (framesyncMapping.size() == expectedLength) {
            if (!framesyncMapping.contains(r)) {
                framesyncMapping.clear();
                return new Pair<>(false, b);
            }
            r = framesyncMapping.indexOf(r) * step;
        }

        // apply the values
        nativeSetFramesync(r, g, b, a);
        Pair<Boolean, Integer> output = new Pair<>(lastFrameSync != r, b > 0 ? 1 : 0);
        lastFrameSync = r;
        return output;
    }

    public static void openIntent(Activity context, int containerId, String path) {
        // 0. Create the launch intent
        boolean isPico = Build.MANUFACTURER.compareToIgnoreCase("PICO") == 0;
        boolean isPfd = Build.MANUFACTURER.compareToIgnoreCase("PLAY FOR DREAM") == 0;
        Intent intent = new Intent(context, isPico ? RuntimePico.class : isPfd ? RuntimePFD.class : RuntimeMeta.class);
        intent.putExtra("container_id", containerId);
        if (path != null) {
            intent.putExtra("shortcut_path", path);
        }

        // 1. Locate the main display ID and add that to the intent
        final int mainDisplayId = Display.DEFAULT_DISPLAY;
        ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(mainDisplayId);

        // 2. Set the flags: start in a new task and replace any existing tasks in the app stack
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        // 3. Launch the activity.
        // Don't use the container's ContextWrapper, which is adding arguments
        context.getBaseContext().startActivity(intent, options.toBundle());

        // 4. Finish the previous activity: this avoids an audio bug
        context.finish();
    }

    public static void updateControllers() {
        // Get OpenXR data
        float[] axes = instance.getAxes();
        boolean[] buttons = instance.getButtons();

        // Communication between XR and Win32 apps
        try {
            if (xrAPI == null) {
                //Set the param to true and put a udp_debug folder in your Winlator D:\ drive
                //with a file named the IP on LAN to send XR data via UDP traffic to that IP.
                xrAPI = new XrAPI(false);

                //Create UDP listener background thread
                Thread udpThread = new Thread(xrAPI);
                udpThread.setDaemon(true);
                udpThread.start();
            }
            isUDP = xrAPI.getIntValue(AppInput.MODE_VR) > 0;
            isVR = xrAPI.getIntValue(AppInput.MODE_VR) == 1;
            getInstance().nativeSetUseVR(isVR);
            if (isUDP) {
                float fovx = xrAPI.getValue(AppInput.HMD_FOVX);
                float fovy = xrAPI.getValue(AppInput.HMD_FOVY);
                getInstance().nativeSetFoV(fovx, fovy);
                int mode3D = xrAPI.getIntValue(AppInput.MODE_3D);
                if (mode3D >= 0) {
                    isAER = mode3D == 2;
                    isSBS = mode3D == 1;
                }
                String data = xrAPI.encode(axes, buttons, 0) + xrAPI.getFlags();
                xrAPI.send(data.getBytes(StandardCharsets.US_ASCII));
            } else {
                xrAPI.updateImplementation();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Primary controller mapping
        int primaryController = instance.container.getPrimaryController();
        ControllerAxis mouseAxisX = primaryController == 0 ? ControllerAxis.L_X : ControllerAxis.R_X;
        ControllerAxis mouseAxisY = primaryController == 0 ? ControllerAxis.L_Y : ControllerAxis.R_Y;
        ControllerButton primaryGrip = primaryController == 0 ? ControllerButton.L_GRIP : ControllerButton.R_GRIP;
        ControllerButton primaryTrigger = primaryController == 0 ? ControllerButton.L_TRIGGER : ControllerButton.R_TRIGGER;
        ControllerButton primaryUp = primaryController == 0 ? ControllerButton.L_THUMBSTICK_UP : ControllerButton.R_THUMBSTICK_UP;
        ControllerButton primaryDown = primaryController == 0 ? ControllerButton.L_THUMBSTICK_DOWN : ControllerButton.R_THUMBSTICK_DOWN;
        ControllerButton primaryLeft = primaryController == 0 ? ControllerButton.L_THUMBSTICK_LEFT : ControllerButton.R_THUMBSTICK_LEFT;
        ControllerButton primaryRight = primaryController == 0 ? ControllerButton.L_THUMBSTICK_RIGHT : ControllerButton.R_THUMBSTICK_RIGHT;
        ControllerButton primaryPress = primaryController == 0 ? ControllerButton.L_THUMBSTICK_PRESS : ControllerButton.R_THUMBSTICK_PRESS;
        ControllerButton secondaryGrip = primaryController == 1 ? ControllerButton.L_GRIP : ControllerButton.R_GRIP;
        ControllerButton secondaryTrigger = primaryController == 1 ? ControllerButton.L_TRIGGER : ControllerButton.R_TRIGGER;
        ControllerButton secondaryUp = primaryController == 1 ? ControllerButton.L_THUMBSTICK_UP : ControllerButton.R_THUMBSTICK_UP;
        ControllerButton secondaryDown = primaryController == 1 ? ControllerButton.L_THUMBSTICK_DOWN : ControllerButton.R_THUMBSTICK_DOWN;
        ControllerButton secondaryLeft = primaryController == 1 ? ControllerButton.L_THUMBSTICK_LEFT : ControllerButton.R_THUMBSTICK_LEFT;
        ControllerButton secondaryRight = primaryController == 1 ? ControllerButton.L_THUMBSTICK_RIGHT : ControllerButton.R_THUMBSTICK_RIGHT;
        ControllerButton secondaryPress = primaryController == 1 ? ControllerButton.L_THUMBSTICK_PRESS : ControllerButton.R_THUMBSTICK_PRESS;

        // Android UI input
        ContentDialog dialog = ContentDialog.getFrontInstance();
        if (dialog != null) {
            if (getButtonClicked(buttons, primaryPress)) instance.runOnUiThread(dialog::onBackPressed);
            if (getButtonClicked(buttons, primaryUp)) instance.runOnUiThread(() -> dialog.onKeyAction(KeyEvent.KEYCODE_DPAD_UP));
            if (getButtonClicked(buttons, primaryDown)) instance.runOnUiThread(() -> dialog.onKeyAction(KeyEvent.KEYCODE_DPAD_DOWN));
            if (getButtonClicked(buttons, primaryTrigger)) instance.runOnUiThread(() -> dialog.onKeyAction(KeyEvent.KEYCODE_ENTER));
            if (getButtonClicked(buttons, primaryLeft)) instance.runOnUiThread(() -> dialog.onKeyAction(KeyEvent.KEYCODE_DPAD_LEFT));
            if (getButtonClicked(buttons, primaryRight)) instance.runOnUiThread(() -> dialog.onKeyAction(KeyEvent.KEYCODE_DPAD_RIGHT));
            System.arraycopy(buttons, 0, lastButtons, 0, buttons.length);
            lastDialogShown = System.currentTimeMillis();
            isVR = false;
            getInstance().nativeSetUseVR(isVR);
            return;
        } else if (getButtonClicked(buttons, primaryPress)) {
            instance.runOnUiThread(() -> new NavigationDialog(instance).show());
        }

        // Block input shortly after dialog closed
        if (System.currentTimeMillis() - lastDialogShown < 500) {
            System.arraycopy(buttons, 0, lastButtons, 0, buttons.length);
            return;
        }

        try (XLock lock = instance.getXServer().lock(XServer.Lockable.WINDOW_MANAGER, XServer.Lockable.INPUT_DEVICE)) {
            // Mouse control with hand
            float f = 0.75f;
            float meter2px = instance.getXServer().screenInfo.width * 10.0f;
            float dx = (axes[mouseAxisX.ordinal()] - lastAxes[mouseAxisX.ordinal()]) * meter2px;
            float dy = (axes[mouseAxisY.ordinal()] - lastAxes[mouseAxisY.ordinal()]) * meter2px;
            if ((Math.abs(dx) > 300) || (Math.abs(dy) > 300)) {
                dx = 0;
                dy = 0;
            }

            // Mouse control with head
            Pointer mouse = instance.getXServer().pointer;
            if (isImmersive && !isUDP) {
                float angle2px = instance.getXServer().screenInfo.width * 0.05f / f;
                dx = getAngleDiff(lastAxes[ControllerAxis.HMD_YAW.ordinal()], axes[ControllerAxis.HMD_YAW.ordinal()]) * angle2px;
                dy = getAngleDiff(lastAxes[ControllerAxis.HMD_PITCH.ordinal()], axes[ControllerAxis.HMD_PITCH.ordinal()]) * angle2px;
                if (Float.isNaN(dy)) {
                    dy = 0;
                }
                smoothedMouse[0] = mouse.getClampedX() + 0.5f;
                smoothedMouse[1] = mouse.getClampedY() + 0.5f;
            }

            // Mouse smoothing
            dx *= mouseSpeed;
            dy *= mouseSpeed;
            smoothedMouse[0] = smoothedMouse[0] * f + (mouse.getClampedX() + 0.5f + dx) * (1 - f);
            smoothedMouse[1] = smoothedMouse[1] * f + (mouse.getClampedY() + 0.5f - dy) * (1 - f);

            // Mouse "snap turn"
            int snapturn = isImmersive ? 125 : 25;
            if (getButtonClicked(buttons, primaryLeft)) {
                smoothedMouse[0] = mouse.getClampedX() - snapturn;
            }
            if (getButtonClicked(buttons, primaryRight)) {
                smoothedMouse[0] = mouse.getClampedX() + snapturn;
            }

            // Set mouse status
            if (mouseEmulation) {
                mouse.setX((int) smoothedMouse[0]);
                mouse.setY((int) smoothedMouse[1]);
                mouse.setButton(Pointer.Button.BUTTON_LEFT, buttons[primaryTrigger.ordinal()]);
                mouse.setButton(Pointer.Button.BUTTON_RIGHT, buttons[primaryGrip.ordinal()]);
                mouse.setButton(Pointer.Button.BUTTON_SCROLL_UP, buttons[primaryUp.ordinal()]);
                mouse.setButton(Pointer.Button.BUTTON_SCROLL_DOWN, buttons[primaryDown.ordinal()]);

                // Limit cursor updates to the FPS (this prevents freezing)
                long timestamp = System.currentTimeMillis();
                if (timestamp - lastMouseUpdate > 1000 / Math.max(instance.getLastFPS(), 1)) {
                    if ((lastMouseX != mouse.getX()) || (lastMouseY != mouse.getY())) {
                        lastMouseUpdate = timestamp;
                        lastMouseX = mouse.getX();
                        lastMouseY = mouse.getY();
                        mouse.triggerOnPointerMove(lastMouseX, lastMouseY);
                    }
                }
            }

            // Switch immersive/SBS mode
            if (getButtonClicked(buttons, secondaryPress)) {
                if (buttons[primaryGrip.ordinal()]) {
                    isSBS = !isSBS;
                }
                else {
                    isImmersive = !isImmersive;
                }
            }

            // Update keyboard
            currentButtons = buttons;
            mapKey(ControllerButton.L_MENU, XKeycode.KEY_ESC.id);
            mapKey(ControllerButton.R_A, instance.container.getControllerMapping(Container.XrControllerMapping.BUTTON_A));
            mapKey(ControllerButton.R_B, instance.container.getControllerMapping(Container.XrControllerMapping.BUTTON_B));
            mapKey(ControllerButton.L_X, instance.container.getControllerMapping(Container.XrControllerMapping.BUTTON_X));
            mapKey(ControllerButton.L_Y, instance.container.getControllerMapping(Container.XrControllerMapping.BUTTON_Y));
            mapKey(secondaryGrip, instance.container.getControllerMapping(Container.XrControllerMapping.BUTTON_GRIP));
            mapKey(secondaryTrigger, instance.container.getControllerMapping(Container.XrControllerMapping.BUTTON_TRIGGER));
            mapKey(secondaryUp, instance.container.getControllerMapping(Container.XrControllerMapping.THUMBSTICK_UP));
            mapKey(secondaryDown, instance.container.getControllerMapping(Container.XrControllerMapping.THUMBSTICK_DOWN));
            mapKey(secondaryLeft, instance.container.getControllerMapping(Container.XrControllerMapping.THUMBSTICK_LEFT));
            mapKey(secondaryRight, instance.container.getControllerMapping(Container.XrControllerMapping.THUMBSTICK_RIGHT));

            // Store the OpenXR data
            lastActive = System.currentTimeMillis();
            System.arraycopy(axes, 0, lastAxes, 0, axes.length);
            System.arraycopy(buttons, 0, lastButtons, 0, buttons.length);
        }

        //Update haptics
        AppInput[] haptics = {AppInput.L_HAPTICS, AppInput.R_HAPTICS};
        for (int i = 0; i < haptics.length; i++) {
            AppInput haptic = haptics[i];
            float value = xrAPI.getValue(haptic);
            if (value > 0.0f) {
                instance.vibrateController(1, i, value);
                xrAPI.setValue(haptic, value - 0.1f);
            } else {
                xrAPI.setValue(haptic, 0.0f);
            }
        }
    }

    private static float getAngleDiff(float oldAngle, float newAngle) {
        float diff = oldAngle - newAngle;
        while (diff > 180) {
            diff -= 360;
        }
        while (diff < -180) {
            diff += 360;
        }
        return diff;
    }

    private static boolean getButtonClicked(boolean[] buttons, ControllerButton button) {
        return buttons[button.ordinal()] && !lastButtons[button.ordinal()];
    }

    private static void mapKey(ControllerButton xrButton, byte xKeycode) {
        Keyboard keyboard = instance.getXServer().keyboard;
        if (currentButtons[xrButton.ordinal()] != lastButtons[xrButton.ordinal()]) {
            if (currentButtons[xrButton.ordinal()]) {
                keyboard.setKeyPress(xKeycode, 0);
            } else {
                keyboard.setKeyRelease(xKeycode);
            }
        }
    }

    private static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // Rendering
    public native void init(int width, int height, int refresh, int cpu, int gpu);
    public native void bindFramebuffer();
    public native int getWidth();
    public native int getHeight();
    public native boolean initFrame(boolean immersive, boolean sbs, boolean aer, float distance);
    public native void bindFBO(int index);
    public native void endFrame();

    // Input
    public native float[] getAxes();
    public native boolean[] getButtons();

    public native void vibrateController(int duration, int chan, float intensity);
}
