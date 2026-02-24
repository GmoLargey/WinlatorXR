package com.winlator.cmod.xr;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;

import com.winlator.cmod.R;
import com.winlator.cmod.XrActivity;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.xserver.Keyboard;
import com.winlator.cmod.xserver.XKeycode;
import com.winlator.cmod.xserver.XServer;

public class XrKeyboard implements TextWatcher {

    private static final KeyCharacterMap chars = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);

    private final XrActivity instance;
    private String lastText = "";

    public XrKeyboard() {
        instance = XrActivity.getInstance();

        EditText text = instance.findViewById(R.id.XRTextInput);
        text.getEditableText().clear();
        text.addTextChangedListener(this);
    }

    public void sendKey(XKeycode key) {
        Keyboard keyboard = instance.getXServer().keyboard;
        keyboard.setKeyPress(key.id, 0);
        sleep(50);
        keyboard.setKeyRelease(key.id);
    }

    public void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void show() {
        View input = instance.findViewById(R.id.XRTextInput);
        input.setVisibility(View.VISIBLE);
        resetText();
        AppUtils.showKeyboard(instance);
        input.requestFocus();
    }

    public void unload() {
        EditText text = instance.findViewById(R.id.XRTextInput);
        text.removeTextChangedListener(this);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public synchronized void afterTextChanged(Editable e) {
        XServer server = instance.getXServer();
        EditText text = instance.findViewById(R.id.XRTextInput);
        String s = text.getEditableText().toString();
        if (s.length() > lastText.length()) {
            lastText = s;
            char c = s.charAt(s.length() - 1);
            KeyEvent[] events = chars.getEvents(new char[]{c});
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
            sendKey(XKeycode.KEY_DEL);
        }
        if (s.isEmpty()) {
            resetText();
        }
    }

    private synchronized void resetText() {
        EditText text = instance.findViewById(R.id.XRTextInput);
        text.removeTextChangedListener(this);
        text.getEditableText().clear();
        text.getEditableText().append(" ");
        text.addTextChangedListener(this);
    }
}
