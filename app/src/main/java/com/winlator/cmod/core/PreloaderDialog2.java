package com.winlator.cmod.core;

import android.app.Activity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.winlator.cmod.R;
import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.contentdialog.ContentDialog;

public class PreloaderDialog2 extends ContentDialog {
    public PreloaderDialog2(@NonNull XServerDisplayActivity context) {
        super(context, R.layout.preloader_dialog);
        setCancelable(false);
        setCanceledOnTouchOutside(false);
        findViewById(R.id.BTConfirm).setVisibility(View.GONE);
        findViewById(R.id.BTCancel).setVisibility(View.GONE);

        Window window = getWindow();
        if (window != null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    public synchronized void show(int textResId) {
        if (isShowing()) return;
        ((TextView)findViewById(R.id.TextView)).setText(textResId);
        show();
    }

    public void showOnUiThread(Activity activity, final int textResId) {
        activity.runOnUiThread(() -> show(textResId));
    }

    public void closeOnUiThread(Activity activity) {
        activity.runOnUiThread(this::dismiss);
    }
}
