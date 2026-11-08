package com.sya.tube.settings;

import org.junit.Test;
import static org.junit.Assert.*;
import static com.sya.tube.settings.SettingsNavigation.Kind.*;

public class SettingsNavigationTest {
    private SettingsNavigation serverForm(SettingsNavigation.Kind kind) {
        SettingsNavigation navigation = new SettingsNavigation();
        navigation.open(SERVERS);
        navigation.open(kind);
        return navigation;
    }

    @Test public void backRejectsTheDepartedFormsResponse() {
        SettingsNavigation navigation = serverForm(JELLYFIN);
        SettingsNavigation.Request request = navigation.beginRequest();
        assertTrue(navigation.accepts(request));
        assertTrue(navigation.back());
        assertFalse(navigation.accepts(request));
        assertEquals(SERVERS, navigation.current().kind);
    }

    @Test public void reopeningTheSameKindOfFormDoesNotAcceptTheOldResponse() {
        SettingsNavigation navigation = serverForm(PLEX);
        SettingsNavigation.Request request = navigation.beginRequest();
        navigation.back();
        navigation.open(PLEX);
        assertFalse(navigation.accepts(request));
    }

    @Test public void newAttemptSupersedesThePreviousRequestOnTheSameForm() {
        SettingsNavigation navigation = serverForm(JELLYFIN);
        SettingsNavigation.Request first = navigation.beginRequest();
        SettingsNavigation.Request second = navigation.beginRequest();
        assertFalse(navigation.accepts(first));
        assertTrue(navigation.accepts(second));
    }

    @Test public void cancelingForRecreationKeepsTheFormButRejectsItsRequest() {
        SettingsNavigation navigation = serverForm(PLEX);
        SettingsNavigation.Frame form = navigation.current();
        SettingsNavigation.Request request = navigation.beginRequest();
        navigation.cancelRequests();
        assertSame(form, navigation.current());
        assertFalse(navigation.accepts(request));
    }

    @Test public void stackSnapshotRestoresTheSubmenuAndEachPagesScroll() {
        SettingsNavigation navigation = new SettingsNavigation();
        navigation.current().scrollY = 800;
        navigation.open(SERVERS);
        navigation.current().scrollY = 140;
        navigation.open(JELLYFIN);
        navigation.current().scrollY = 270;
        SettingsNavigation restored = new SettingsNavigation(navigation.snapshot());
        assertEquals(JELLYFIN, restored.current().kind);
        assertEquals(270, restored.current().scrollY);
        restored.back();
        assertEquals(SERVERS, restored.current().kind);
        assertEquals(140, restored.current().scrollY);
        restored.back();
        assertEquals(MAIN, restored.current().kind);
        assertEquals(800, restored.current().scrollY);
        assertFalse(restored.back());
    }

    @Test public void sponsorSubmenuSurvivesSnapshotRestore() {
        SettingsNavigation navigation = new SettingsNavigation();
        navigation.open(SPONSOR);
        navigation.current().scrollY = 500;
        SettingsNavigation restored = new SettingsNavigation(navigation.snapshot());
        assertEquals(SPONSOR, restored.current().kind);
        assertEquals(500, restored.current().scrollY);
        assertTrue(restored.back());
        assertEquals(MAIN, restored.current().kind);
    }

    @Test public void retainedFormKeepsUnsavedCredentialsInMemory() {
        SettingsNavigation navigation = serverForm(JELLYFIN);
        SettingsNavigation.Frame form = navigation.current();
        form.address = "http://example.test";
        form.username = "someone";
        form.password = "unsaved password";
        form.token = "unsaved token";
        navigation.cancelRequests();
        assertSame(form, navigation.current());
        assertEquals("unsaved password", navigation.current().password);
        assertEquals("unsaved token", navigation.current().token);
    }

    @Test public void persistedSnapshotKeepsAddressAndUserWithoutPasswordOrToken() {
        SettingsNavigation navigation = serverForm(PLEX);
        navigation.current().address = "http://example.test";
        navigation.current().username = "someone";
        navigation.current().password = "unsaved password";
        navigation.current().token = "unsaved token";
        SettingsNavigation restored = new SettingsNavigation(navigation.snapshot());
        assertEquals("http://example.test", restored.current().address);
        assertEquals("someone", restored.current().username);
        assertEquals("", restored.current().password);
        assertEquals("", restored.current().token);
    }

    @Test public void snapshotDoesNotChangeWhenTheLiveFormIsEdited() {
        SettingsNavigation navigation = serverForm(JELLYFIN);
        navigation.current().address = "first";
        SettingsNavigation.Snapshot snapshot = navigation.snapshot();
        navigation.current().address = "second";
        assertEquals("first", new SettingsNavigation(snapshot).current().address);
    }

    @Test public void leavingAFormClearsItsCredentialsAndClosingRejectsCallbacks() {
        SettingsNavigation navigation = serverForm(JELLYFIN);
        SettingsNavigation.Frame form = navigation.current();
        form.password = "password";
        form.token = "token";
        navigation.back();
        assertEquals("", form.password);
        assertEquals("", form.token);
        navigation.open(PLEX);
        form = navigation.current();
        form.token = "another token";
        SettingsNavigation.Request request = navigation.beginRequest();
        navigation.close();
        assertFalse(navigation.accepts(request));
        assertEquals("", form.token);
    }
}
