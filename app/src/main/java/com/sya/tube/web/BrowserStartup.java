package com.sya.tube.web;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class BrowserStartup {
    private final Runnable create;
    private final Consumer<String> load;
    private final Supplier<String> savedPage;
    private boolean ready, drawn, active, created, creating, closed;
    private String pending;

    public BrowserStartup(Runnable create, Consumer<String> load, Supplier<String> savedPage) {
        this.create = create;
        this.load = load;
        this.savedPage = savedPage;
    }
    public void ready() { ready = true; advance(); }
    public void frameDrawn() { drawn = true; advance(); }
    public boolean resume() {
        boolean explicitNavigation = pending != null;
        active = true;
        advance();
        return explicitNavigation;
    }
    public void pause() { active = false; }
    public String pendingPage() { return pending; }
    public void navigate(String url) {
        if (closed) return;
        pending = url;
        advance();
    }
    public void close() { closed = true; active = false; pending = null; }

    private void advance() {
        if (closed || !active || !ready || !drawn || creating) return;
        boolean initial = !created;
        if (initial) {
            creating = true;
            try { create.run(); created = true; }
            finally { creating = false; }
        }
        if (closed || !active || !initial && pending == null) return;
        String url = pending == null ? savedPage.get() : pending;
        pending = null;
        load.accept(url);
    }
}
