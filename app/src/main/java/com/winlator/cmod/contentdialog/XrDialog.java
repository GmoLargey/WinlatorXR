package com.winlator.cmod.contentdialog;

import android.content.SharedPreferences;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;
import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.XrActivity;

public class XrDialog extends ContentDialog {

    public XrDialog(XServerDisplayActivity activity) {
        super(activity, R.layout.xr_dialog);
        setTitle(R.string.xr);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        boolean isImmersive = XrActivity.isImmersive;
        boolean isSBS = XrActivity.isSBS;

        CheckBox cbSBS = findViewById(R.id.CBEnableSBS);
        cbSBS.setEnabled(XrActivity.lastMode3D < 0);
        cbSBS.setChecked(isSBS);
        CheckBox cbImmersiveMode = findViewById(R.id.CBEnableImmersiveMode);
        cbImmersiveMode.setEnabled(!XrActivity.isUDP);
        cbImmersiveMode.setChecked(isImmersive);
        CheckBox cbCurvedScreen = findViewById(R.id.CBEnableCurvedScreen);
        cbCurvedScreen.setChecked(preferences.getBoolean("use_cs", true));
        CheckBox cbPassthrough = findViewById(R.id.CBEnablePassthrough);
        cbPassthrough.setChecked(preferences.getBoolean("use_pt", true));
        TextView tvToApplyClose = findViewById(R.id.TVToApplyClose);

        Runnable applyAll = () -> {
            SharedPreferences.Editor e = preferences.edit();
            e.putBoolean("use_cs", cbCurvedScreen.isChecked());
            e.putBoolean("use_pt", cbPassthrough.isChecked());
            e.commit();

            XrActivity.isSBS = cbSBS.isChecked();
            XrActivity.isImmersive = cbImmersiveMode.isChecked();
            XrActivity instance = XrActivity.getInstance();
            instance.nativeSetCurvedScreen(cbCurvedScreen.isChecked());
            instance.nativeSetUsePT(cbPassthrough.isChecked());

            boolean warn = (XrActivity.isImmersive != isImmersive) || (XrActivity.isSBS != isSBS);
            tvToApplyClose.setVisibility(warn ? View.VISIBLE : View.GONE);
        };

        // Apply changes immediatelly
        cbSBS.setOnCheckedChangeListener((compoundButton, b) -> applyAll.run());
        cbImmersiveMode.setOnCheckedChangeListener((compoundButton, b) -> applyAll.run());
        cbCurvedScreen.setOnCheckedChangeListener((compoundButton, b) -> applyAll.run());
        cbPassthrough.setOnCheckedChangeListener((compoundButton, b) -> applyAll.run());

        findViewById(R.id.BTCancel).setVisibility(View.GONE);
        findViewById(R.id.BTConfirm).setVisibility(View.VISIBLE);
        findViewById(R.id.BTConfirm).setOnClickListener(v -> dismiss());
        setOnConfirmCallback(this::dismiss);
    }
}
