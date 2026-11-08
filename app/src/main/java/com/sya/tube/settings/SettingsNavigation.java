package com.sya.tube.settings;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** The settings stack and unsaved form values, independent of its Activity and views. */
public final class SettingsNavigation {
    public enum Kind { MAIN, SPONSOR, CHANNELS, SERVERS, JELLYFIN, PLEX }

    public static final class Frame {
        public final Kind kind;
        public int scrollY;
        public String address = "", username = "", password = "", token = "";
        private Frame(Kind kind) { this.kind = kind; }
    }

    public static final class SavedPage {
        public final Kind kind;
        public final int scrollY;
        public final String address, username;
        public SavedPage(Kind kind, int scrollY, String address, String username) {
            this.kind = kind; this.scrollY = scrollY; this.address = address; this.username = username;
        }
    }

    public static final class Snapshot {
        public final List<SavedPage> pages;
        public Snapshot(List<SavedPage> pages) { this.pages = Collections.unmodifiableList(new ArrayList<>(pages)); }
    }

    public static final class Request {
        private final Frame origin;
        private final long generation;
        private Request(Frame origin, long generation) { this.origin = origin; this.generation = generation; }
    }

    private final ArrayDeque<Frame> pages = new ArrayDeque<>();
    private long generation;
    private boolean closed;
    public SettingsNavigation() { pages.push(new Frame(Kind.MAIN)); }
    public SettingsNavigation(Snapshot snapshot) {
        this();
        if (snapshot == null || snapshot.pages.isEmpty() || snapshot.pages.get(0).kind != Kind.MAIN) return;
        pages.clear();
        for (SavedPage saved : snapshot.pages) {
            Frame page = new Frame(saved.kind);
            page.scrollY = Math.max(0, saved.scrollY);
            page.address = saved.address == null ? "" : saved.address;
            page.username = saved.username == null ? "" : saved.username;
            pages.push(page);
        }
    }
    public Frame current() { return pages.peek(); }
    public void open(Kind kind) { cancelRequests(); pages.push(new Frame(kind)); }
    public boolean back() {
        cancelRequests();
        if (pages.size() == 1) return false;
        clearSecrets(pages.pop());
        return true;
    }
    public Request beginRequest() { return new Request(current(), ++generation); }
    public boolean accepts(Request request) {
        return !closed && request != null && request.origin == current() && request.generation == generation;
    }
    public void cancelRequests() { generation++; }
    public void close() {
        closed = true;
        cancelRequests();
        for (Frame page : pages) clearSecrets(page);
    }
    private void clearSecrets(Frame page) { page.password = ""; page.token = ""; }

    /** Only non-secret values enter Android's saved state; drafts retain credentials in memory. */
    public Snapshot snapshot() {
        List<SavedPage> saved = new ArrayList<>();
        for (Iterator<Frame> it = pages.descendingIterator(); it.hasNext(); ) {
            Frame page = it.next();
            saved.add(new SavedPage(page.kind, page.scrollY, page.address, page.username));
        }
        return new Snapshot(saved);
    }
}
