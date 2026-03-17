package com.winlator.cmod.renderer;

import com.winlator.cmod.xserver.Drawable;

public class RenderableWindow {
    public final Drawable content;
    public short rootX;
    public short rootY;
    final boolean forceFullscreen;

    public RenderableWindow(Drawable content, int rootX, int rootY) {
        this(content, rootX, rootY, false);
    }

    public RenderableWindow(Drawable content, int rootX, int rootY, boolean forceFullscreen) {
        this.content = content;
        this.rootX = (short)rootX;
        this.rootY = (short)rootY;
        this.forceFullscreen = forceFullscreen;
    }
}
