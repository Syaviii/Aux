package com.sya.tube.web;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

public class BrowserStartupTest {
    private final AtomicInteger creations = new AtomicInteger();
    private final List<String> pages = new ArrayList<>();
    private final BrowserStartup browser = new BrowserStartup(creations::incrementAndGet, pages::add, () -> "saved");

    @Before public void firstFrame() { browser.frameDrawn(); }

    @Test public void browserConstructionWaitsForTheFirstNativeFrame() {
        BrowserStartup startup = new BrowserStartup(creations::incrementAndGet, pages::add, () -> "saved");
        startup.resume(); startup.ready();
        assertEquals(0, creations.get());
        startup.frameDrawn();
        assertEquals(1, creations.get());
        assertEquals(List.of("saved"), pages);
    }

    @Test public void waitsForBrowserWarmupAndAResumedScreen() {
        browser.resume();
        assertEquals(0, creations.get());
        browser.ready();
        assertEquals(1, creations.get());
        assertEquals(List.of("saved"), pages);
    }
    @Test public void warmupBeforeResumeDoesNotCreateAHiddenBrowser() {
        browser.ready();
        assertEquals(0, creations.get());
        browser.resume();
        assertEquals(List.of("saved"), pages);
    }
    @Test public void anEarlySearchReplacesTheSavedPage() {
        browser.resume();
        browser.navigate("search");
        browser.ready();
        assertEquals(List.of("search"), pages);
    }
    @Test public void homeReplacesAnEarlierQueuedSearch() {
        browser.navigate("search");
        browser.navigate("home");
        browser.ready();
        browser.resume();
        assertEquals(List.of("home"), pages);
    }
    @Test public void leavingBeforeWarmupFinishesWaitsForReopen() {
        browser.resume();
        browser.pause();
        browser.ready();
        assertEquals(0, creations.get());
        browser.resume();
        assertEquals(1, creations.get());
        assertEquals(List.of("saved"), pages);
    }
    @Test public void navigationWhilePausedWaitsWithoutReloadingTheSavedPage() {
        browser.ready(); browser.resume(); browser.pause();
        browser.navigate("library");
        assertEquals(List.of("saved"), pages);
        browser.resume();
        assertEquals(List.of("saved", "library"), pages);
        assertEquals(1, creations.get());
    }
    @Test public void aDestroyedViewIgnoresTheLateWarmupCallback() {
        browser.resume(); browser.navigate("search"); browser.close();
        browser.ready(); browser.resume(); browser.navigate("home");
        assertEquals(0, creations.get());
        assertTrue(pages.isEmpty());
    }
    @Test public void repeatedResumeAndReadyDoNotReloadThePage() {
        browser.ready(); browser.resume(); browser.ready(); browser.pause(); browser.resume();
        assertEquals(1, creations.get());
        assertEquals(List.of("saved"), pages);
        browser.navigate("watch");
        assertEquals(List.of("saved", "watch"), pages);
    }
    @Test public void navigationDuringConstructionUsesTheLatestRequest() {
        BrowserStartup[] owner = new BrowserStartup[1];
        owner[0] = new BrowserStartup(() -> owner[0].navigate("new search"), pages::add, () -> "saved");
        owner[0].frameDrawn();
        owner[0].ready(); owner[0].resume();
        assertEquals(List.of("new search"), pages);
    }
    @Test public void reopeningWithADeferredRequestSuppressesAutomaticCarFollow() {
        browser.resume(); browser.navigate("search"); browser.pause(); browser.ready();
        assertTrue(browser.resume());
        assertEquals(List.of("search"), pages);
        browser.pause();
        assertFalse(browser.resume());
    }
    @Test public void aQueuedSearchCanBeRestoredAfterViewRecreation() {
        browser.resume(); browser.navigate("search");
        String saved = browser.pendingPage();
        browser.close();
        BrowserStartup restored = new BrowserStartup(creations::incrementAndGet, pages::add, () -> "saved");
        restored.frameDrawn();
        restored.navigate(saved);
        restored.ready(); restored.resume();
        assertEquals(List.of("search"), pages);
        assertEquals(1, creations.get());
    }
}
