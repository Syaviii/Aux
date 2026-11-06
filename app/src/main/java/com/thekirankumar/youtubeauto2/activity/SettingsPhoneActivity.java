// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.activity;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.sya.tube.library.MediaServers;
import com.sya.tube.library.PhoneVideos;
import com.sya.tube.library.SubscriptionImport;
import com.sya.tube.library.Subscriptions;
import com.sya.tube.player.SponsorBlock;
import com.sya.tube.settings.Backup;
import com.sya.tube.settings.SettingsUi;
import com.sya.tube.settings.TubePrefs;
import com.thekirankumar.youtubeauto2.BuildConfig;
import com.thekirankumar.youtubeauto2.R;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tube's settings, one scrolling page of grouped settings with its own pages for SponsorBlock, channels and media servers */
public class SettingsPhoneActivity extends AppCompatActivity {
    private interface Page { String title(); void build(LinearLayout page); }

    private final ArrayDeque<Page> pages = new ArrayDeque<>();
    private final ExecutorService work = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private SettingsUi ui;
    private FrameLayout content;
    private TextView heading;
    private ActivityResultLauncher<String> exportFile;
    private ActivityResultLauncher<String[]> importFile, importChannels;
    private Runnable plexPoll;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        ui = new SettingsUi(this);
        LinearLayout root = ui.column();
        root.setBackgroundColor(getColor(R.color.tube_background));
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(ui.dp(4), 0, ui.dp(12), 0);
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.tube_ic_back);
        back.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.tube_on_surface)));
        back.setBackgroundResource(R.drawable.bg_round_ripple);
        back.setContentDescription("Back");
        back.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12));
        back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        bar.addView(back, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        heading = ui.text("Settings", 20, getColor(R.color.tube_on_surface), true);
        heading.setPadding(ui.dp(8), 0, 0, 0);
        bar.addView(heading);
        root.addView(bar, new LinearLayout.LayoutParams(-1, ui.dp(56)));
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        exportFile = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), this::writeBackup);
        importFile = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::readBackup);
        importChannels = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::readChannels);
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (pages.size() > 1) { pages.pop(); stopPlexPoll(); render(); return; }
                finish();
            }
        });
        open(mainPage());
    }

    @Override protected void onResume() {
        super.onResume();
        // A permission granted in the system dialog or a file picked meanwhile shows up at once
        if (!pages.isEmpty()) render();
    }

    @Override protected void onDestroy() { stopPlexPoll(); work.shutdownNow(); super.onDestroy(); }

    private void open(Page page) { pages.push(page); render(); }

    private Page shownPage;
    private ScrollView shownScroll;

    private void render() {
        Page page = pages.peek();
        if (page == null) return;
        heading.setText(page.title());
        // The same page built again keeps its place, a permission dialog or an Unfollow must not jump back to the top
        int keep = page == shownPage && shownScroll != null ? shownScroll.getScrollY() : 0;
        ScrollView scroll = new ScrollView(this);
        LinearLayout column = ui.column();
        column.setPadding(0, 0, 0, ui.dp(32));
        page.build(column);
        scroll.addView(column);
        content.removeAllViews();
        content.addView(scroll);
        shownPage = page;
        shownScroll = scroll;
        if (keep > 0) scroll.post(() -> scroll.scrollTo(0, keep));
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }

    // The main page

    private Page mainPage() {
        return new Page() {
            public String title() { return "Settings"; }
            public void build(LinearLayout page) {
                LinearLayout car = ui.section(page, "Car screen");
                ui.toggle(car, "Auto fullscreen", "Start videos in fullscreen", "pref_auto_fullscreen", true, null);
                ui.toggle(car, "Simpler YouTube pages", "Hide comments, Shorts and shopping", "pref_car_clean", true, null);
                ui.toggle(car, "Background audio", "Keep audio playing in other apps", "pref_background_audio", true, null);
                ui.toggle(car, "Fullscreen captions", "Show captions when available", "pref_car_captions", false, null);
                ui.toggle(car, "Steering wheel skip", "Jump 10 seconds with next or previous", TubePrefs.SKIP_SEEKS, true, null);
                ui.toggle(car, "Continue watching", "Show unfinished videos on YouTube home", TubePrefs.CONTINUE_ROW, false, null);
                ui.toggle(car, "Open channels on Home", "Show followed channels instead of the feed", TubePrefs.HOME_CHANNELS, false, null);

                LinearLayout playback = ui.section(page, "Playback");
                String[] speedLabels = new String[TubePrefs.SPEEDS.length], speedValues = new String[TubePrefs.SPEEDS.length];
                for (int i = 0; i < TubePrefs.SPEEDS.length; i++) { speedValues[i] = TubePrefs.speedText(TubePrefs.SPEEDS[i]); speedLabels[i] = speedValues[i] + "x"; }
                ui.choice(playback, "Playback speed", "Use this speed for all videos", speedLabels, speedValues, TubePrefs.speedText(TubePrefs.speed(SettingsPhoneActivity.this)),
                    value -> TubePrefs.get(SettingsPhoneActivity.this).edit().putString(TubePrefs.SPEED, value).apply());
                ui.toggle(playback, "Block ads", "Block and skip YouTube ads", "pref_block_ads", true, null);
                ui.link(playback, "SponsorBlock", sponsorSummary(), v -> open(sponsorPage()));
                ui.note(page, "Restart Aux after changing Simpler YouTube pages or Block ads");

                LinearLayout data = ui.section(page, "Video quality");
                ui.choice(data, "Mobile data", null, new String[]{"360p", "480p", "720p", "1080p"}, new String[]{"medium", "large", "hd720", "hd1080"},
                    TubePrefs.get(SettingsPhoneActivity.this).getString(TubePrefs.QUALITY_MOBILE, "hd720"), value -> TubePrefs.get(SettingsPhoneActivity.this).edit().putString(TubePrefs.QUALITY_MOBILE, value).apply());
                ui.choice(data, "Wi-Fi", null, new String[]{"Auto", "480p", "720p", "1080p"}, new String[]{"auto", "large", "hd720", "hd1080"},
                    TubePrefs.get(SettingsPhoneActivity.this).getString(TubePrefs.QUALITY_WIFI, "hd720"), value -> TubePrefs.get(SettingsPhoneActivity.this).edit().putString(TubePrefs.QUALITY_WIFI, value).apply());
                ui.note(page, "Video settings overrides this for one video\n\nBelow 1080p on mobile data, Jellyfin and Plex also reduce quality\n\n"
                    + (TubePrefs.onMobileData(SettingsPhoneActivity.this) ? "Using mobile data" : "Using Wi-Fi"));

                LinearLayout library = ui.section(page, "Library");
                int channels = Subscriptions.list(SettingsPhoneActivity.this).size();
                ui.link(library, "Your channels", channels == 0 ? "Follow channels without a Google account" : channels == 1 ? "1 channel" : channels + " channels", v -> open(channelsPage()));
                if (PhoneVideos.allowed(SettingsPhoneActivity.this)) ui.info(library, "Phone videos", "Browse this phone's videos in the car's Library");
                else ui.action(library, "Phone videos", "Allow access to play this phone's videos in the car", "Allow", true, v -> requestPermissions(new String[]{PhoneVideos.PERMISSION}, 10));
                List<MediaServers.Server> servers = MediaServers.list(SettingsPhoneActivity.this);
                StringBuilder names = new StringBuilder();
                for (MediaServers.Server s : servers) names.append(names.length() == 0 ? "" : ", ").append(s.name);
                ui.link(library, "Media servers", servers.isEmpty() ? "Play from your Jellyfin or Plex server" : names.toString(), v -> open(serversPage()));

                LinearLayout voice = ui.section(page, "Voice search");
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) ui.info(voice, "Microphone", "Allowed for voice search in the car");
                else ui.action(voice, "Microphone", "Allow access to search by voice in the car", "Allow", true, v -> requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 11));

                LinearLayout backup = ui.section(page, "Backup");
                ui.link(backup, "Export backup", "Save settings, channels, history, queue, bookmarks and server addresses",
                    v -> exportFile.launch("Aux backup " + new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date()) + ".json"));
                ui.link(backup, "Restore backup", "Restore an Aux backup file", v -> importFile.launch(new String[]{"application/json", "application/octet-stream", "text/plain", "*/*"}));

                LinearLayout advanced = ui.section(page, "Advanced");
                ui.toggle(advanced, "Desktop site", "Use YouTube's desktop layout", "pref_enable_desktop_mode", false, null);
                ui.toggle(advanced, "Hide playback notification", "Also hide lock screen controls", "pref_disable_notifications", false, null);
                ui.toggle(advanced, "Crash reports", "Ask to share a report after a crash", com.sya.tube.diag.Diag.CRASH_REPORTS, true, null);
                ui.toggle(advanced, "Keep diagnostics", "Keep logs after closing Aux", com.sya.tube.diag.Diag.KEEP_LOG, true,
                    () -> com.sya.tube.diag.Diag.setKeepLog(com.sya.tube.settings.TubePrefs.get(SettingsPhoneActivity.this).getBoolean(com.sya.tube.diag.Diag.KEEP_LOG, true)));
                ui.link(advanced, "Share diagnostics", "Share recent logs without account details", v -> {
                    Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain");
                    share.putExtra(Intent.EXTRA_SUBJECT, "Aux diagnostics");
                    share.putExtra(Intent.EXTRA_TEXT, com.sya.tube.diag.Diag.report(SettingsPhoneActivity.this));
                    startActivity(Intent.createChooser(share, "Send Aux diagnostics"));
                });

                LinearLayout about = ui.section(page, "About");
                ui.link(about, "Android Auto setup", "Help Aux appear in your car's app list", v -> new AlertDialog.Builder(SettingsPhoneActivity.this).setTitle("Android Auto setup")
                    .setMessage("Allow apps installed outside the Play Store:\n\n1. Open Android Auto settings on your phone\n2. Tap Version repeatedly to unlock developer settings\n"
                        + "3. Open Developer settings from the menu and enable Unknown sources\n4. Reconnect to your car\n\nIf Aux is still missing, open Aux Setup and tap Fix Aux for Android Auto")
                    .setPositiveButton(android.R.string.ok, null).show());
                TextView updates = ui.link(about, "Updates", updateSummary(), v -> checkForUpdate(true));
                updateRow = updates;
                ui.info(about, "Version", BuildConfig.VERSION_NAME);
                ui.link(about, "Source code and license", "GPL 3.0 or later, built on CarStream and NewPipe Extractor", v -> {
                    String source = com.sya.tube.update.Updates.enabled() ? com.sya.tube.update.Updates.repoPage() : "https://github.com/thekirankumar/carstream-android-auto";
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(source)));
                });
                ui.link(about, "Privacy", "Where your data goes", v -> new AlertDialog.Builder(SettingsPhoneActivity.this).setTitle("Privacy")
                    .setMessage("Aux has no account system or tracking\n\nIt connects to:\n\n• YouTube for pages and videos\n"
                        + "• SponsorBlock to find segments, using part of the video's hashed ID\n• Your Jellyfin or Plex servers, and plex.tv for code sign-in\n"
                        + "• GitHub for updates\n\nSign-in details, channels and history are stored on this phone, and Aux only shares diagnostics or crash reports when you choose to send them")
                    .setPositiveButton(android.R.string.ok, null).show());
                if (com.sya.tube.update.Updates.enabled()) checkForUpdate(false);
            }
        };
    }

    private TextView updateRow;

    private String updateSummary() {
        if (!com.sya.tube.update.Updates.enabled()) return "Update checks are off in this build";
        com.sya.tube.update.Updates.Release known = com.sya.tube.update.Updates.known(this);
        if (known != null && known.newer()) return "Aux " + known.version + " is available, tap to update";
        long checked = com.sya.tube.update.Updates.lastChecked(this);
        return checked == 0 ? "Check for a new version" : "Up to date, checked " + android.text.format.DateUtils.getRelativeTimeSpanString(checked);
    }

    /** A tap checks GitHub and, when a newer Aux is out, downloads it and opens the installer */
    private void checkForUpdate(boolean tapped) {
        if (!com.sya.tube.update.Updates.enabled()) { if (tapped) toast("This build has no update address"); return; }
        if (tapped && updateRow != null) updateRow.setText("Checking");
        com.sya.tube.update.Updates.check(this, tapped, release -> {
            if (isFinishing()) return;
            if (updateRow != null) updateRow.setText(updateSummary());
            if (!tapped) return;
            if (release == null || !release.newer()) { toast("Aux is up to date"); return; }
            new AlertDialog.Builder(this).setTitle("Aux " + release.version).setMessage(release.notes.isEmpty() ? "A new version is ready" : release.notes)
                .setPositiveButton("Update", (d, w) -> com.sya.tube.update.Updates.install(this, release, new com.sya.tube.update.Updates.Progress() {
                    @Override public void onProgress(int percent) { if (updateRow != null) updateRow.setText("Downloading " + percent + "%"); }
                    @Override public void onDone(boolean started, String problem) { if (updateRow != null) updateRow.setText(updateSummary()); if (problem != null) toast(problem); }
                }))
                .setNegativeButton("Later", null).show();
        });
    }

    private String sponsorSummary() {
        if (!TubePrefs.sponsorBlockOn(this)) return "Off";
        StringBuilder skip = new StringBuilder();
        int count = 0;
        for (String[] segment : TubePrefs.SEGMENTS) if ("skip".equals(TubePrefs.segmentMode(this, segment[0]))) { count++; if (count <= 3) skip.append(count == 1 ? "" : ", ").append(segment[1].toLowerCase(java.util.Locale.ROOT)); }
        if (count == 0) return "On, no automatic skips";
        return "Skips " + skip + (count > 3 ? " and " + (count - 3) + " more" : "");
    }

    private Page sponsorPage() {
        return new Page() {
            public String title() { return "SponsorBlock"; }
            public void build(LinearLayout page) {
                LinearLayout card = ui.section(page, "SponsorBlock");
                ui.toggle(card, "Enable SponsorBlock", "Skip community-marked segments", TubePrefs.SPONSORBLOCK, true, SponsorBlock::forget);
                ui.note(page, "Aux sends part of the video's hashed ID to find segments without sharing the full video ID");
                LinearLayout kinds = ui.section(page, "Segment actions");
                for (String[] segment : TubePrefs.SEGMENTS) {
                    String key = "pref_sb_" + segment[0];
                    ui.choice(kinds, segment[1], segment[2], new String[]{"Skip", "Ask", "Off"}, new String[]{"skip", "ask", "off"}, TubePrefs.segmentMode(SettingsPhoneActivity.this, segment[0]),
                        value -> { TubePrefs.get(SettingsPhoneActivity.this).edit().putString(key, value).apply(); SponsorBlock.forget(); });
                }
                ui.note(page, "Ask shows a Skip button during the segment");
            }
        };
    }

    // Followed channels

    private Page channelsPage() {
        return new Page() {
            public String title() { return "Your channels"; }
            public void build(LinearLayout page) {
                ui.note(page, "On a channel or video, open the menu and tap Follow channel\n\nFind new videos in Library on your phone or car, no Google account needed");
                ui.button(page, "Import subscriptions", true, v -> importChannels.launch(new String[]{"application/zip", "text/csv", "text/comma-separated-values", "application/json", "text/xml", "application/octet-stream", "*/*"}));
                ui.note(page, "Import a Google Takeout ZIP or subscriptions.csv, a NewPipe export, or an OPML file");
                ui.button(page, "Open Google Takeout", false, v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://takeout.google.com/takeout/custom/youtube"))));
                List<Subscriptions.Channel> channels = Subscriptions.list(SettingsPhoneActivity.this);
                if (channels.isEmpty()) return;
                LinearLayout card = ui.section(page, channels.size() == 1 ? "1 channel" : channels.size() + " channels");
                for (Subscriptions.Channel channel : channels)
                    ui.action(card, channel.name.isEmpty() ? channel.id : channel.name, null, "Unfollow", false, v -> { Subscriptions.remove(SettingsPhoneActivity.this, channel.id); render(); });
                ui.button(page, "Unfollow all", false, v -> new AlertDialog.Builder(SettingsPhoneActivity.this).setTitle("Unfollow all " + channels.size() + " channels?")
                    .setPositiveButton("Unfollow all", (d, w) -> { Subscriptions.replaceAll(SettingsPhoneActivity.this, new java.util.ArrayList<>()); render(); })
                    .setNegativeButton(android.R.string.cancel, null).show());
            }
        };
    }

    private void readChannels(Uri file) {
        if (file == null) return;
        toast("Reading subscriptions");
        work.execute(() -> {
            try {
                List<Subscriptions.Channel> found = SubscriptionImport.read(this, file);
                int added = Subscriptions.add(this, found);
                if (added > 0) Subscriptions.refresh(this);
                main.post(() -> {
                    new AlertDialog.Builder(this).setTitle(found.isEmpty() ? "No channels found" : "Subscriptions imported")
                        .setMessage(found.isEmpty() ? "No YouTube channels in this file, try your Takeout ZIP or subscriptions.csv"
                            : "Found " + found.size() + (found.size() == 1 ? " channel, added " : " channels, added ") + added + " new")
                        .setPositiveButton(android.R.string.ok, null).show();
                    render();
                });
            } catch (Exception error) {
                main.post(() -> toast("Couldn't read this file"));
            }
        });
    }

    // Media servers

    private Page serversPage() {
        return new Page() {
            public String title() { return "Media servers"; }
            public void build(LinearLayout page) {
                List<MediaServers.Server> servers = MediaServers.list(SettingsPhoneActivity.this);
                if (!servers.isEmpty()) {
                    LinearLayout card = ui.section(page, "Signed in");
                    for (MediaServers.Server server : servers)
                        ui.action(card, server.name, server.kind() + " / " + Uri.parse(server.url).getHost() + (server.user.isEmpty() ? "" : " / " + server.user), "Remove", false,
                            v -> { MediaServers.remove(SettingsPhoneActivity.this, server.id); render(); });
                }
                ui.note(page, "Browse your server in the car's Library, with watch progress synced across devices");
                ui.button(page, "Add Jellyfin server", true, v -> open(jellyfinPage()));
                ui.button(page, "Add Plex server", false, v -> open(plexPage()));
            }
        };
    }

    private Page jellyfinPage() {
        return new Page() {
            public String title() { return "Add Jellyfin"; }
            public void build(LinearLayout page) {
                LinearLayout card = ui.section(page, "Your server");
                EditText address = ui.field(card, "Server address", "192.168.1.20:8096", SettingsUi.URL);
                EditText user = ui.field(card, "Username", "", SettingsUi.TEXT);
                EditText password = ui.field(card, "Password", "", SettingsUi.PASSWORD);
                // Saved passwords then belong to the user name, not the server address
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    address.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
                    user.setAutofillHints(View.AUTOFILL_HINT_USERNAME);
                    password.setAutofillHints(View.AUTOFILL_HINT_PASSWORD);
                }
                ui.note(page, "Away from home, your server needs a public address or VPN\n\nAux doesn't save your password");
                TextView status = ui.note(page, "");
                ui.button(page, "Sign in", true, v -> {
                    String a = address.getText().toString(), u = user.getText().toString(), p = password.getText().toString();
                    if (a.trim().isEmpty() || u.trim().isEmpty()) { status.setText("Enter a server address and username"); return; }
                    status.setText("Signing in");
                    work.execute(() -> {
                        try {
                            MediaServers.Server server = MediaServers.signInJellyfin(SettingsPhoneActivity.this, a, u.trim(), p);
                            MediaServers.add(SettingsPhoneActivity.this, server);
                            main.post(() -> { toast("Signed in to " + server.name); pages.pop(); render(); });
                        } catch (Exception error) {
                            main.post(() -> status.setText(error instanceof SecurityException ? error.getMessage() : "Couldn't reach a Jellyfin server at this address"));
                        }
                    });
                });
            }
        };
    }

    private Page plexPage() {
        return new Page() {
            public String title() { return "Add Plex"; }
            public void build(LinearLayout page) {
                LinearLayout card = ui.section(page, "Sign in with Plex");
                TextView code = ui.info(card, "Enter your code at plex.tv/link", "");
                ui.button(page, "Get a code", true, v -> {
                    code.setVisibility(View.VISIBLE);
                    code.setText("Getting a code");
                    work.execute(() -> {
                        try {
                            String[] pin = MediaServers.plexPin(SettingsPhoneActivity.this);
                            main.post(() -> { showPlexCode(code, pin[1]); startPlexPoll(pin[0], code); });
                        } catch (Exception error) { main.post(() -> code.setText("Couldn't reach Plex, check your connection")); }
                    });
                });
                ui.button(page, "Open plex.tv/link", false, v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://plex.tv/link"))));
                LinearLayout direct = ui.section(page, "Connect by address");
                EditText address = ui.field(direct, "Server address", "192.168.1.20:32400", SettingsUi.URL);
                EditText token = ui.field(direct, "Plex token (optional)", "", SettingsUi.TEXT);
                ui.note(page, "You can leave the token blank if your server allows connections from your network without signing in");
                TextView status = ui.note(page, "");
                ui.button(page, "Add server", false, v -> {
                    String a = address.getText().toString(), t = token.getText().toString().trim();
                    if (a.trim().isEmpty()) { status.setText("Enter the server address"); return; }
                    status.setText("Connecting");
                    work.execute(() -> {
                        try {
                            MediaServers.Server server = MediaServers.plexByAddress(SettingsPhoneActivity.this, a, t);
                            MediaServers.add(SettingsPhoneActivity.this, server);
                            main.post(() -> { toast("Added " + server.name); pages.pop(); render(); });
                        } catch (Exception error) {
                            main.post(() -> status.setText(error instanceof SecurityException ? error.getMessage() : "Couldn't reach a Plex server at this address"));
                        }
                    });
                });
            }
        };
    }

    private void showPlexCode(TextView code, String value) {
        code.setText(value);
        code.setTextSize(28);
        code.setLetterSpacing(0.2f);
        code.setTextColor(getColor(R.color.tube_on_surface));
    }

    /** Checks every two seconds for ten minutes whether the code was entered, then adds every server the account can reach */
    private void startPlexPoll(String pinId, TextView code) {
        stopPlexPoll();
        long until = System.currentTimeMillis() + 10 * 60_000;
        plexPoll = new Runnable() {
            @Override public void run() {
                if (plexPoll != this) return;
                if (System.currentTimeMillis() > until) { code.setText("Code expired, tap Get a code to try again"); code.setTextSize(13); return; }
                Runnable self = this;
                work.execute(() -> {
                    try {
                        String token = MediaServers.plexPinToken(SettingsPhoneActivity.this, pinId);
                        if (token == null) { main.postDelayed(self, 2000); return; }
                        main.post(() -> { code.setText("Signed in, finding your servers"); code.setTextSize(15); });
                        List<MediaServers.Server> found = MediaServers.plexServers(SettingsPhoneActivity.this, token);
                        for (MediaServers.Server server : found) MediaServers.add(SettingsPhoneActivity.this, server);
                        main.post(() -> {
                            plexPoll = null;
                            if (found.isEmpty()) { code.setText("Signed in, but this phone couldn't reach any of your Plex servers"); return; }
                            toast(found.size() == 1 ? "Added " + found.get(0).name : "Added " + found.size() + " servers");
                            pages.pop(); render();
                        });
                    } catch (Exception error) { main.postDelayed(self, 4000); }
                });
            }
        };
        main.postDelayed(plexPoll, 2000);
    }

    private void stopPlexPoll() { if (plexPoll != null) main.removeCallbacks(plexPoll); plexPoll = null; }

    // Backup

    private void writeBackup(Uri file) {
        if (file == null) return;
        work.execute(() -> {
            try (java.io.OutputStream out = getContentResolver().openOutputStream(file, "wt")) {
                out.write(Backup.export(this).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                main.post(() -> toast("Settings saved"));
            } catch (Exception error) { main.post(() -> toast("Couldn't save the file")); }
        });
    }

    private void readBackup(Uri file) {
        if (file == null) return;
        work.execute(() -> {
            try (java.io.InputStream in = getContentResolver().openInputStream(file)) {
                java.io.ByteArrayOutputStream data = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                for (int n; (n = in.read(buffer)) > 0; ) { data.write(buffer, 0, n); if (data.size() > 8 << 20) throw new java.io.IOException("Too large"); }
                String result = Backup.restore(this, data.toString("UTF-8"));
                main.post(() -> { new AlertDialog.Builder(this).setTitle("Settings restored").setMessage(result).setPositiveButton(android.R.string.ok, null).show(); render(); });
            } catch (IllegalArgumentException notTube) { main.post(() -> toast(notTube.getMessage()));
            } catch (Exception error) { main.post(() -> toast("Couldn't read this file")); }
        });
    }
}
