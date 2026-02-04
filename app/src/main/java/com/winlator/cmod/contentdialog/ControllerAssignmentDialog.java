package com.winlator.cmod.contentdialog;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.view.ContextThemeWrapper;
import android.view.InputDevice;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;
import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.XrActivity;
import com.winlator.cmod.inputcontrols.ControllerManager;
import com.winlator.cmod.winhandler.WinHandler;

public class ControllerAssignmentDialog {
    private final ContentDialog dialog;
    private final ControllerManager controllerManager;
    private final WinHandler winHandler;     // may be null
    private final Activity hostActivity;

    private final CheckBox[] checkBoxes = new CheckBox[4];
    private final TextView[] deviceNameTextViews = new TextView[4];
    private final Button[] assignButtons = new Button[4];
    private final Button[] btnMacros = new Button[4];   // <-- Button array
    private final CheckBox[] vibrateBoxes = new CheckBox[4];
    private final Button[] resetButtons = new Button[4];

    private final TextView restartRequiredView;
    private final int initialPlayerCount;

    // ---------- Public entry points -----------------------------------------

    /** Legacy call (e.g., from MainActivity). Will try to grab WinHandler if context is XServerDisplayActivity. */
    public static void show(Context context) {
        show(context, extractWinHandler(context));
    }

    /** Preferred call when you already have the handler. */
    public static void show(Context context, WinHandler winHandler) {
        int initialPlayerCount = ControllerManager.getInstance().getEnabledPlayerCount();
        Activity act = (Activity) context; // all current callers pass an Activity
        new ControllerAssignmentDialog(act, initialPlayerCount, winHandler).showContentDialog();
    }

    private static WinHandler extractWinHandler(Context ctx) {
        if (ctx instanceof XServerDisplayActivity) {
            return ((XServerDisplayActivity) ctx).getWinHandler();
        }
        return null;
    }

    // ---------- Impl ---------------------------------------------------------

    private ControllerAssignmentDialog(Activity activity, int initialPlayerCount, WinHandler winHandler) {
        boolean dark = PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean("dark_mode", false);

        ContextThemeWrapper themed =
                new ContextThemeWrapper(activity, dark ? R.style.ContentDialog : R.style.AppTheme);

        this.dialog = new ContentDialog(themed, R.layout.controller_assignment_dialog);
        this.dialog.setTitle(R.string.controller_manager);

        this.controllerManager = ControllerManager.getInstance();
        this.initialPlayerCount = initialPlayerCount;
        this.winHandler = winHandler;     // can be null
        this.hostActivity = activity;

        initializeViews();

        restartRequiredView = dialog.getContentView().findViewById(R.id.TVRestartRequired);

        if (dark) {
            View root = dialog.getContentView();
            if (root instanceof ViewGroup) setTextColorForDialog((ViewGroup) root, 0xFFFFFFFF);
        }

        populateView();
        setupListeners();
    }

    private static int dp(Context c, int v){
        return Math.round(c.getResources().getDisplayMetrics().density * v);
    }

    @SuppressWarnings("deprecation")
    public void showContentDialog() {
        dialog.show();
        Window w = dialog.getWindow();
        if (w == null) return;

        int widthPx;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.view.WindowMetrics metrics = w.getWindowManager().getCurrentWindowMetrics();
            android.graphics.Rect b = metrics.getBounds();
            widthPx = b.width();
        } else {
            Point p = new Point();
            w.getWindowManager().getDefaultDisplay().getSize(p);
            widthPx = p.x;
        }

        int capPx = dp(dialog.getContext(), 540);
        int target = Math.min((int) (widthPx * 0.90f), capPx);
        w.setLayout(target, WindowManager.LayoutParams.WRAP_CONTENT);


        // Player XR
        View view = dialog.getContentView();
        LinearLayout xr = view.findViewById(R.id.PlayerXR);
        if (XrActivity.isEnabled(view.getContext())) {
            xr.setVisibility(View.VISIBLE);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(view.getContext());
            CheckBox cbMouseLightgun = view.findViewById(R.id.CBPlayerXRMouseLightgun);
            cbMouseLightgun.setChecked(prefs.getBoolean("use_xr_lightgun", false));
            cbMouseLightgun.setOnCheckedChangeListener((compoundButton, checked) -> {
                SharedPreferences.Editor e = prefs.edit();
                e.putBoolean("use_xr_lightgun", checked);
                e.commit();
                XrActivity.mouseLightgun = checked;
            });
            CheckBox cbMouse = view.findViewById(R.id.CBPlayerXRMouse);
            cbMouse.setChecked(prefs.getBoolean("use_xr_mouse", true));
            cbMouse.setOnCheckedChangeListener((compoundButton, checked) -> {
                SharedPreferences.Editor e = prefs.edit();
                e.putBoolean("use_xr_mouse", checked);
                e.commit();
                XrActivity.mouseEmulation = checked;
                cbMouseLightgun.setEnabled(checked);
            });
            cbMouseLightgun.setEnabled(cbMouse.isChecked());
        } else {
            xr.setVisibility(View.GONE);
        }
    }

    private void initializeViews() {
        View view = dialog.getContentView();

        // Player 1
        checkBoxes[0] = view.findViewById(R.id.CBPlayer1);
        deviceNameTextViews[0] = view.findViewById(R.id.TVPlayer1DeviceName);
        assignButtons[0] = view.findViewById(R.id.BTNAssignP1);
        vibrateBoxes[0] = view.findViewById(R.id.CBVibrateP1);
        resetButtons[0] = view.findViewById(R.id.BTNResetP1);
        btnMacros[0] = view.findViewById(R.id.BTNMacrosP1);
        btnMacros[0].setOnClickListener(v ->
                com.winlator.cmod.contentdialog.MacrosDialog.show(hostActivity, 0, winHandler));

        // Player 2
        checkBoxes[1] = view.findViewById(R.id.CBPlayer2);
        deviceNameTextViews[1] = view.findViewById(R.id.TVPlayer2DeviceName);
        assignButtons[1] = view.findViewById(R.id.BTNAssignP2);
        vibrateBoxes[1] = view.findViewById(R.id.CBVibrateP2);
        resetButtons[1] = view.findViewById(R.id.BTNResetP2);
        btnMacros[1] = view.findViewById(R.id.BTNMacrosP2);
        btnMacros[1].setOnClickListener(v ->
                com.winlator.cmod.contentdialog.MacrosDialog.show(hostActivity, 1, winHandler));

        // Player 3
        checkBoxes[2] = view.findViewById(R.id.CBPlayer3);
        deviceNameTextViews[2] = view.findViewById(R.id.TVPlayer3DeviceName);
        assignButtons[2] = view.findViewById(R.id.BTNAssignP3);
        vibrateBoxes[2] = view.findViewById(R.id.CBVibrateP3);
        resetButtons[2] = view.findViewById(R.id.BTNResetP3);
        btnMacros[2] = view.findViewById(R.id.BTNMacrosP3);
        btnMacros[2].setOnClickListener(v ->
                com.winlator.cmod.contentdialog.MacrosDialog.show(hostActivity, 2, winHandler));

        // Player 4
        checkBoxes[3] = view.findViewById(R.id.CBPlayer4);
        deviceNameTextViews[3] = view.findViewById(R.id.TVPlayer4DeviceName);
        assignButtons[3] = view.findViewById(R.id.BTNAssignP4);
        vibrateBoxes[3] = view.findViewById(R.id.CBVibrateP4);
        resetButtons[3] = view.findViewById(R.id.BTNResetP4);
        btnMacros[3] = view.findViewById(R.id.BTNMacrosP4);
        btnMacros[3].setOnClickListener(v ->
                com.winlator.cmod.contentdialog.MacrosDialog.show(hostActivity, 3, winHandler));
    }

    private void populateView() {
        controllerManager.scanForDevices();

        for (int i = 0; i < 4; i++) {
            checkBoxes[i].setChecked(controllerManager.isSlotEnabled(i));
            if (vibrateBoxes[i] != null) {
                vibrateBoxes[i].setChecked(controllerManager.isVibrationEnabled(i));
            }
            InputDevice device = controllerManager.getAssignedDeviceForSlot(i);
            deviceNameTextViews[i].setText(
                    device != null ? device.getName() : dialog.getContext().getString(R.string.not_assigned)
            );
            deviceNameTextViews[i].setSelected(true);
        }
    }

    private void setupListeners() {
        for (int i = 0; i < 4; i++) {
            final int slotIndex = i;

            checkBoxes[i].setOnCheckedChangeListener((buttonView, isChecked) -> {
                controllerManager.setSlotEnabled(slotIndex, isChecked);
                if (!isChecked) {
                    for (int j = slotIndex + 1; j < 4; j++) {
                        if (controllerManager.isSlotEnabled(j)) controllerManager.setSlotEnabled(j, false);
                    }
                } else {
                    for (int j = 0; j < slotIndex; j++) {
                        if (!controllerManager.isSlotEnabled(j)) controllerManager.setSlotEnabled(j, true);
                    }
                }
                populateView();

                if (controllerManager.getEnabledPlayerCount() != initialPlayerCount) {
                    restartRequiredView.setVisibility(View.VISIBLE);
                } else {
                    restartRequiredView.setVisibility(View.GONE);
                }
            });

            vibrateBoxes[i].setOnCheckedChangeListener((b, checked) ->
                    controllerManager.setVibrationEnabled(slotIndex, checked));

            resetButtons[i].setOnClickListener(v -> {
                controllerManager.unassignSlot(slotIndex);
                populateView();
            });

            assignButtons[i].setOnClickListener(v -> {
                String message = dialog.getContext().getString(R.string.press_any_button_for_player) + " " + (slotIndex + 1);
                dialog.setMessage(message);

                dialog.setOnControllerInputListener(device -> {
                    if (!ControllerManager.isGameController(device)) return;
                    controllerManager.assignDeviceToSlot(slotIndex, device);
                    dialog.setMessage(null);
                    dialog.setOnControllerInputListener(null);
                    populateView();
                });
            });
        }

        dialog.setOnConfirmCallback(() -> controllerManager.saveAssignments());
    }

    private void setTextColorForDialog(ViewGroup viewGroup, int color) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child instanceof ViewGroup) {
                setTextColorForDialog((ViewGroup) child, color);
            } else if (child instanceof TextView) {
                ((TextView) child).setTextColor(color);
            }
        }
    }
}
