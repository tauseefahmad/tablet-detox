package com.tabletdetox.home;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.Dialog;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class MainActivity extends Activity {

    // Home apps, in this order of preference:
    //  1. a list saved at install time:  am start -n com.tabletdetox.home/.MainActivity --es apps "pkg1,pkg2"
    //     (--es apps auto  goes back to 2; add --es stamp <unique> so it's applied only once, see saveConfig)
    //  2. apps.txt (built in as Apps.LIST), topped up with apps you installed, up to TOP_UP.
    // Missing apps are skipped. A chosen list can be any length: tiles wrap into rows that fit the
    // screen (more across in landscape), and the page scrolls once they don't all fit.
    // Tap opens. Hold a tile to move, replace or remove it. Swipe up for every app.
    private static final String PREFS = "config";
    private static final int TOP_UP = 12;
    private static final String ADD = "add";
    private static final List<String> NOT_APPS = Arrays.asList("com.android.vending", "com.android.settings");
    private static final String[] EDIT_ITEMS = {"Move", "Replace", "Remove from home", "Uninstall"};
    private static final String[] EDIT_ITEMS_SYSTEM = {"Move", "Replace", "Remove from home", "App info"};
    private static final String[] SETTINGS_ITEMS = {"Wallpaper", "Screen time", "Tablet settings", "Choose home app"};

    // Layout in dp. Columns follow the screen width, so rotating just reflows the grid.
    private static final int MARGIN = 48, GAP = 24, RADIUS = 20, TILE_MIN = 140;

    // Wallpapers, saved by name: a gradient top to bottom, then a glow from the top left and one from
    // the bottom right. Colours only, so every one costs the same. "Yours" shows the tablet's own
    // wallpaper under a dark veil.
    private static final String YOURS = "Your wallpaper";
    private static final String[] WALLPAPER_NAMES = {"Dusk", "Midnight", "Plum", "Forest", "Graphite", "Ocean", "Ember",
            "Sunset", "Lagoon", "Aurora", "Coral", "Iris", "Meadow", "Gold", YOURS};
    private static final int[][] WALLPAPERS = {
        {0xFF15100F, 0xFF0A0809, 0xFF050405, 0x36FF8048, 0x2EAA3C8C},
        {0xFF0E1220, 0xFF080A13, 0xFF040509, 0x384A6CFF, 0x2E5A3CC8},
        {0xFF170E19, 0xFF0C070D, 0xFF050306, 0x38C050B4, 0x2E6440D2},
        {0xFF0C150F, 0xFF070C09, 0xFF030604, 0x3050B478, 0x28289682},
        {0xFF151617, 0xFF0B0C0D, 0xFF050506, 0x1CFFFFFF, 0x16A0B4C8},
        {0xFF0A1517, 0xFF060C0E, 0xFF030607, 0x3426B4C8, 0x2E2A64C0},
        {0xFF190D08, 0xFF0D0605, 0xFF060303, 0x40FF5A24, 0x32C8283C},
        {0xFF8C3A5E, 0xFF5E2348, 0xFF22101E, 0xA0FF8A4C, 0x80E0457A},
        {0xFF14588E, 0xFF15366E, 0xFF0A1632, 0x9030C8E0, 0x807A4CE0},
        {0xFF0F4048, 0xFF0F2E3A, 0xFF08141C, 0x9038E0A0, 0x90A050F0},
        {0xFFC0604E, 0xFF8A3456, 0xFF2E1230, 0x90FFB070, 0x70E04090},
        {0xFF5A3CAE, 0xFF3A2A84, 0xFF16123A, 0x90C080FF, 0x804A7CFF},
        {0xFF2E6E4C, 0xFF1C4A40, 0xFF0A201E, 0x90A8E070, 0x6030A8A8},
        {0xFFB07624, 0xFF86401E, 0xFF2A1410, 0x90FFD060, 0x70FF5040},
        null,
    };

    private static Typeface regular, light;
    private float density = 1f;
    private int insetTop, insetBottom;

    private FrameLayout root; // the page, inside a vertical scroller
    private TextView screenTime, moveHint;
    private int cols, tileW, tileH, gridTop;
    private final List<View> tiles = new ArrayList<>();
    private final List<String> homeApps = new ArrayList<>(); // as shown, in order
    private String movingKey; // the app being moved, if any
    private boolean dirty = true;
    private Dialog drawer;
    private GestureDetector swipes;

    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            dirty = true;
            if (hasWindowFocus() && movingKey == null) refresh();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        saveConfig(getIntent());
        if (regular == null) {
            regular = Typeface.create("sans-serif", Typeface.NORMAL);
            light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        }
        density = getResources().getDisplayMetrics().density;
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        showWallpaper(wallpaper());

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root = new FrameLayout(this);
        root.setClipChildren(false);
        scroller.addView(root, new FrameLayout.LayoutParams(-1, -2));
        scroller.setOnApplyWindowInsetsListener((v, in) -> {
            insetTop = in.getSystemWindowInsetTop();
            insetBottom = in.getSystemWindowInsetBottom();
            dirty = true;
            refresh();
            return in.consumeSystemWindowInsets();
        });
        // Hold anywhere that isn't a tile: settings.
        root.setOnLongClickListener(v -> { showSettings(null); return true; });
        swipes = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (a == null || b == null || movingKey != null) return false;
                // Only a swipe that starts low on the screen, so scrolling a long grid still scrolls.
                boolean low = a.getRawY() > getResources().getDisplayMetrics().heightPixels * 0.6f;
                if (low && b.getY() - a.getY() < -dp(100) && Math.abs(vy) > Math.abs(vx)) { openDrawer(-1); return true; }
                return false;
            }
        });
        setContentView(scroller);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(packagesChanged, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(packagesChanged, f);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packagesChanged);
        super.onDestroy();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        if (swipes != null) swipes.onTouchEvent(e);
        return super.dispatchTouchEvent(e);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        saveConfig(intent);
        if (drawer != null) drawer.dismiss();
        if (movingKey != null) endMove();
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        refreshScreenTime();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && movingKey == null) refresh();
    }

    // Rotated or resized (split screen, a foldable): reflow the grid for the new width.
    @Override
    public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        showWallpaper(wallpaper());
        dirty = true;
        refresh();
    }

    @Override
    public void onBackPressed() {
        if (movingKey != null) endMove();
    }

    private void refresh() {
        if (!dirty) return;
        dirty = false;
        build();
    }

    // Saves an app list passed with --es apps "...". The stamp install.sh sends stops Android from
    // replaying an old start and undoing changes made on the tablet since.
    private boolean saveConfig(Intent intent) {
        String apps = intent == null ? null : intent.getStringExtra("apps");
        if (apps == null) return false;
        SharedPreferences prefs = getSharedPreferences(PREFS, 0);
        String stamp = intent.getStringExtra("stamp");
        if (stamp != null && stamp.equals(prefs.getString("stamp", null))) return false;
        SharedPreferences.Editor e = prefs.edit();
        if (stamp != null) e.putString("stamp", stamp);
        apps = apps.trim();
        if (apps.isEmpty() || apps.equalsIgnoreCase("auto")) e.remove("apps");
        else e.putString("apps", apps);
        e.apply();
        dirty = true;
        return true;
    }

    private List<String> appList(PackageManager pm) {
        List<String> out = new ArrayList<>();
        String saved = getSharedPreferences(PREFS, 0).getString("apps", null);
        String[] wanted = saved != null ? saved.split("[,\\s]+") : Apps.LIST;
        for (String p : wanted)
            if (!p.isEmpty() && !out.contains(p) && !p.equals(getPackageName()) && pm.getLaunchIntentForPackage(p) != null) out.add(p);
        if (saved == null) {
            // Top up with apps people installed (or updated), skipping stock system tools.
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                if (out.size() >= TOP_UP) break;
                String p = ri.activityInfo.packageName;
                int flags = ri.activityInfo.applicationInfo.flags;
                boolean stockSystem = (flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        && (flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
                if (stockSystem || out.contains(p) || NOT_APPS.contains(p) || p.equals(getPackageName())) continue;
                out.add(p);
            }
        }
        return out;
    }

    private void build() {
        root.removeAllViews();
        tiles.clear();
        homeApps.clear();
        movingKey = null;
        int width = getResources().getDisplayMetrics().widthPixels;
        int m = dp(MARGIN), g = dp(GAP);
        cols = Math.max(3, Math.min(8, (width - 2 * m + g) / (dp(TILE_MIN) + g)));
        tileW = (width - 2 * m - g * (cols - 1)) / cols;
        tileH = Math.round(tileW * 0.86f);

        // Clock, date and screen time at the top left; the settings button at the top right.
        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        String day = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMd");
        TextClock date = clock(day, day, sp(18), 0xB3FFFFFF, regular);
        date.setOnClickListener(v -> start(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)));
        clock.addView(date, new LinearLayout.LayoutParams(-2, -2));
        TextClock time = clock("h:mm", "H:mm", sp(88), Color.WHITE, light);
        time.setFontFeatureSettings("tnum");
        time.setIncludeFontPadding(false);
        time.setOnClickListener(v -> start(new Intent(AlarmClock.ACTION_SHOW_ALARMS)));
        clock.addView(time, new LinearLayout.LayoutParams(-2, -2));
        screenTime = text(sp(15), 0x8CFFFFFF, regular);
        screenTime.setOnClickListener(v -> openScreenTime());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
        sp.topMargin = dp(8);
        clock.addView(screenTime, sp);
        root.addView(clock, at(m, insetTop + dp(40), -2, -2));
        refreshScreenTime();

        final ImageView gear = new ImageView(this);
        gear.setImageDrawable(new LineIcon(LineIcon.SETTINGS));
        gear.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable gbg = new GradientDrawable();
        gbg.setShape(GradientDrawable.OVAL);
        gbg.setColor(0x13FFFFFF);
        gear.setBackground(gbg);
        gear.setContentDescription("Home settings");
        gear.setOnClickListener(v -> showSettings(gear));
        root.addView(gear, at(width - m - dp(52), insetTop + dp(40), dp(52), dp(52)));

        gridTop = insetTop + dp(40) + sp(18) + sp(88) + sp(15) + dp(72);
        moveHint = text(sp(16), 0xB3FFFFFF, regular);
        moveHint.setText("Tap where it should go. Tap it again when you're done.");
        moveHint.setVisibility(View.GONE);
        root.addView(moveHint, at(m, gridTop - dp(40), -2, -2));

        PackageManager pm = getPackageManager();
        for (String pkg : appList(pm)) {
            Drawable icon = null;
            try { icon = pm.getApplicationIcon(pkg); } catch (Exception ignored) {}
            int i = homeApps.size();
            homeApps.add(pkg);
            addTile(pkg, labelOf(pkg), icon, null, gridX(i), gridY(i));
        }
        int n = homeApps.size();
        addTile(ADD, "Add app", null, LineIcon.PLUS, gridX(n), gridY(n));
        root.setMinimumHeight(gridY(n) + tileH + dp(MARGIN) + insetBottom);
    }

    // A quiet "glass" tile: the app's icon above its name.
    private void addTile(final String key, String label, Drawable icon, int[] lineIcon, int x, int y) {
        FrameLayout card = new FrameLayout(this);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(RADIUS));
        bg.setColor(0x13FFFFFF);
        bg.setStroke(Math.max(1, dp(1)), 0x17FFFFFF);
        card.setBackground(bg);
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(Gravity.CENTER);
        ImageView iv = new ImageView(this);
        int size = lineIcon != null ? dp(36) : Math.min(dp(64), tileW / 2);
        if (lineIcon != null) iv.setImageDrawable(new LineIcon(lineIcon));
        else if (icon != null) iv.setImageDrawable(fit(icon, size));
        inner.addView(iv, new LinearLayout.LayoutParams(size, size));
        TextView t = text(sp(15), 0xE6FFFFFF, regular);
        t.setText(label);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10), 0, dp(10), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(10);
        inner.addView(t, lp);
        card.addView(inner, new FrameLayout.LayoutParams(-1, -1));
        card.setTag(key);
        card.setContentDescription(label);
        // Pressed: lift a little, like the TV tiles do on focus.
        card.setOnTouchListener((v, e) -> {
            boolean down = e.getActionMasked() == MotionEvent.ACTION_DOWN;
            boolean up = e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL;
            if (down || up) {
                bg.setColor(down ? 0x38FFFFFF : 0x13FFFFFF);
                v.animate().scaleX(down ? 0.96f : 1f).scaleY(down ? 0.96f : 1f).setDuration(100).start();
            }
            return false;
        });
        card.setOnClickListener(v -> tapped(key));
        if (!ADD.equals(key)) card.setOnLongClickListener(v -> { showEditMenu(key); return true; });
        root.addView(card, at(x, y, tileW, tileH));
        tiles.add(card);
    }

    private void tapped(String key) {
        if (movingKey != null) {
            int from = homeApps.indexOf(movingKey), to = ADD.equals(key) ? homeApps.size() - 1 : homeApps.indexOf(key);
            if (key.equals(movingKey) || to < 0) { endMove(); return; }
            homeApps.add(to, homeApps.remove(from));
            // Slide the tiles into their new places: no rebuild, so it stays quick on old tablets.
            for (int i = Math.min(from, to); i <= Math.max(from, to); i++) {
                View card = tile(homeApps.get(i));
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) card.getLayoutParams();
                lp.leftMargin = gridX(i);
                lp.topMargin = gridY(i);
                card.setLayoutParams(lp);
            }
            saveHome(false);
            return;
        }
        if (ADD.equals(key)) { openDrawer(-1); return; }
        Intent i = getPackageManager().getLaunchIntentForPackage(key);
        if (i != null) start(i);
    }

    private void start(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception ignored) {}
    }

    // Hold a tile: move it, swap it for another, or take it off Home. Removing only takes the tile away:
    // the app stays installed. Apps that came with the tablet can't be uninstalled, so they get App
    // info instead, where Android offers to turn them off.
    private void showEditMenu(final String key) {
        final int index = homeApps.indexOf(key);
        if (index < 0) return;
        final boolean user = userInstalled(key);
        menu(tile(key), labelOf(key), user ? EDIT_ITEMS : EDIT_ITEMS_SYSTEM, which -> {
            if (which == 0) startMove(key);
            else if (which == 1) openDrawer(index);
            else if (which == 2) { homeApps.remove(index); saveHome(true); }
            else {
                Uri u = Uri.parse("package:" + key);
                start(user ? new Intent(Intent.ACTION_DELETE, u) : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, u));
            }
        });
    }

    private void startMove(String key) {
        movingKey = key;
        GradientDrawable outline = new GradientDrawable();
        outline.setCornerRadius(dp(RADIUS));
        outline.setStroke(dp(3), Color.WHITE);
        ((FrameLayout) tile(key)).setForeground(outline);
        moveHint.setVisibility(View.VISIBLE);
    }

    private void endMove() {
        View card = tile(movingKey);
        if (card != null) ((FrameLayout) card).setForeground(null);
        movingKey = null;
        moveHint.setVisibility(View.GONE);
        refresh(); // anything installed or removed while moving
    }

    private void saveHome(boolean rebuild) {
        getSharedPreferences(PREFS, 0).edit().putString("apps", android.text.TextUtils.join(",", homeApps)).apply();
        if (rebuild) { dirty = true; refresh(); }
    }

    private void showSettings(View anchor) {
        menu(anchor, "Home settings", SETTINGS_ITEMS, which -> {
            if (which == 0) showWallpapers();
            else if (which == 1) openScreenTime();
            else if (which == 2) start(new Intent(Settings.ACTION_SETTINGS));
            else start(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
    }

    // Every app with its icon, by name, in a panel from the right. Type to narrow it. With replaceIndex
    // -1 a tap opens the app and a hold adds it to Home; otherwise a tap puts it in that tile's place.
    private void openDrawer(final int replaceIndex) {
        if (drawer != null) return;
        final PackageManager pm = getPackageManager();
        final TreeMap<String, String> byLabel = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
            String p = ri.activityInfo.packageName;
            if (p.equals(getPackageName()) || byLabel.containsValue(p)) continue;
            if (replaceIndex >= 0 && homeApps.contains(p)) continue;
            String label = labelOf(p);
            byLabel.put(byLabel.containsKey(label) ? label + " (" + p + ")" : label, p);
        }
        final List<String> labels = new ArrayList<>(byLabel.keySet());
        final List<String> shown = new ArrayList<>(labels);
        final Map<String, Drawable> icons = new HashMap<>();
        final int iconSize = dp(40);

        final Dialog d = new Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar);
        drawer = d;
        d.setOnDismissListener(x -> drawer = null);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(28), insetTop + dp(28), dp(28), insetBottom + dp(16));
        TextView head = text(sp(26), Color.WHITE, regular);
        head.setText(replaceIndex < 0 ? "All apps" : "Replace " + labelOf(homeApps.get(replaceIndex)));
        box.addView(head);
        if (replaceIndex < 0) {
            TextView tip = text(sp(13), 0x80FFFFFF, regular);
            tip.setText("Tap to open. Hold to add it to Home.");
            box.addView(tip);
        }

        final EditText search = new EditText(this);
        search.setHint("Search");
        search.setHintTextColor(0x66FFFFFF);
        search.setTextColor(Color.WHITE);
        search.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(18));
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_GO);
        GradientDrawable field = new GradientDrawable();
        field.setCornerRadius(dp(14));
        field.setColor(0x1AFFFFFF);
        search.setBackground(field);
        search.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = dp(16);
        box.addView(search, slp);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setVerticalScrollBarEnabled(false);
        GradientDrawable press = new GradientDrawable();
        press.setCornerRadius(dp(14));
        press.setColor(0x26FFFFFF);
        list.setSelector(press);
        final BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int pos) { return shown.get(pos); }
            @Override public long getItemId(int pos) { return pos; }
            @Override public View getView(int pos, View convert, ViewGroup parent) {
                TextView t = convert != null ? (TextView) convert : text(sp(18), 0xE6FFFFFF, regular);
                String label = shown.get(pos), pkg = byLabel.get(label);
                t.setText(label);
                t.setSingleLine(true);
                t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                t.setGravity(Gravity.CENTER_VERTICAL);
                t.setMinHeight(dp(60));
                t.setPadding(dp(12), 0, dp(12), 0);
                // Icons load as rows appear, drawn small to save RAM.
                Drawable ic = icons.get(pkg);
                if (ic == null) {
                    try { ic = fit(pm.getApplicationIcon(pkg), iconSize); } catch (Exception e) { ic = new ColorDrawable(0); }
                    ic.setBounds(0, 0, iconSize, iconSize);
                    icons.put(pkg, ic);
                }
                t.setCompoundDrawablesRelative(ic, null, null, null);
                t.setCompoundDrawablePadding(dp(16));
                t.setAlpha(replaceIndex < 0 && homeApps.contains(pkg) ? 0.55f : 1f);
                return t;
            }
        };
        list.setAdapter(adapter);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lp.topMargin = dp(12);
        box.addView(list, lp);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                String needle = s.toString().trim().toLowerCase(Locale.getDefault());
                shown.clear();
                for (String l : labels) if (l.toLowerCase(Locale.getDefault()).contains(needle)) shown.add(l);
                adapter.notifyDataSetChanged();
            }
        });
        final Pick choose = pos -> {
            String pkg = byLabel.get(shown.get(pos));
            d.dismiss();
            if (replaceIndex >= 0) {
                homeApps.set(replaceIndex, pkg);
                saveHome(true);
            } else tapped(pkg);
        };
        search.setOnEditorActionListener((v, action, ev) -> {
            if (shown.isEmpty()) return false;
            choose.at(0);
            return true;
        });
        list.setOnItemClickListener((p, v, pos, id) -> choose.at(pos));
        if (replaceIndex < 0) list.setOnItemLongClickListener((p, v, pos, id) -> {
            String pkg = byLabel.get(shown.get(pos));
            if (homeApps.contains(pkg)) homeApps.remove(pkg);
            else homeApps.add(pkg);
            saveHome(true);
            adapter.notifyDataSetChanged();
            return true;
        });

        d.setContentView(box);
        Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(0xF7161113));
        int width = getResources().getDisplayMetrics().widthPixels;
        w.setLayout(Math.min(width, dp(440)), -1);
        w.setGravity(Gravity.END);
        w.setDimAmount(0.5f);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        d.show();
    }

    interface Pick { void at(int which); }

    // A small menu next to what was held, in the same glass as the tiles: under it, or above it when
    // there's no room. Without an anchor it sits in the middle. Tapping outside closes it.
    private void menu(View anchor, String title, String[] items, final Pick pick) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(8), rowH = dp(52);
        box.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(RADIUS));
        bg.setColor(0xFF231D1F);
        bg.setStroke(Math.max(1, dp(1)), 0x17FFFFFF);
        box.setBackground(bg);
        TextView head = text(sp(13), 0x80FFFFFF, regular);
        head.setText(title);
        head.setSingleLine(true);
        head.setEllipsize(android.text.TextUtils.TruncateAt.END);
        head.setPadding(dp(16), dp(8), dp(16), dp(4));
        box.addView(head);
        for (int i = 0; i < items.length; i++) {
            final int which = i;
            TextView t = text(sp(17), 0xE6FFFFFF, regular);
            t.setText(items[i]);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding(dp(16), 0, dp(16), 0);
            GradientDrawable press = new GradientDrawable();
            press.setCornerRadius(dp(RADIUS - 8));
            press.setColor(0x26FFFFFF);
            android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
            states.addState(new int[] {android.R.attr.state_pressed}, press);
            t.setBackground(states);
            t.setOnClickListener(v -> { d.dismiss(); pick.at(which); });
            box.addView(t, new LinearLayout.LayoutParams(-1, rowH));
        }
        d.setContentView(box);
        Window win = d.getWindow();
        win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        win.setDimAmount(0.3f);
        android.view.WindowManager.LayoutParams lp = win.getAttributes();
        int w = dp(280), h = items.length * rowH + 2 * pad + dp(36);
        lp.width = w;
        lp.height = h;
        if (anchor != null) {
            int[] at = new int[2];
            anchor.getLocationOnScreen(at);
            int sw = getResources().getDisplayMetrics().widthPixels, sh = getResources().getDisplayMetrics().heightPixels;
            win.setGravity(Gravity.TOP | Gravity.START);
            lp.x = Math.max(dp(16), Math.min(at[0], sw - w - dp(16)));
            lp.y = at[1] + anchor.getHeight() + dp(8);
            if (lp.y + h > sh - dp(16)) lp.y = Math.max(dp(16), at[1] - h - dp(8));
        }
        win.setAttributes(lp);
        d.show();
    }

    private void showWallpapers() {
        String[] names = WALLPAPER_NAMES.clone();
        names[wallpaper()] = names[wallpaper()] + "  ✓";
        menuList("Wallpaper", names, which -> {
            getSharedPreferences(PREFS, 0).edit().putString("wallpaper", WALLPAPER_NAMES[which]).apply();
            showWallpaper(which);
        });
    }

    // A scrolling list in a panel from the right, for lists too long for a menu. Each tap applies at
    // once and leaves the panel open, so wallpapers can be tried one after another.
    private void menuList(String title, final String[] items, final Pick pick) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(28), insetTop + dp(28), dp(28), insetBottom + dp(16));
        TextView head = text(sp(26), Color.WHITE, regular);
        head.setText(title);
        box.addView(head);
        ListView list = new ListView(this);
        list.setDivider(null);
        GradientDrawable press = new GradientDrawable();
        press.setCornerRadius(dp(14));
        press.setColor(0x26FFFFFF);
        list.setSelector(press);
        final String[] labels = items.clone();
        final BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount() { return labels.length; }
            @Override public Object getItem(int pos) { return labels[pos]; }
            @Override public long getItemId(int pos) { return pos; }
            @Override public View getView(int pos, View convert, ViewGroup parent) {
                TextView t = convert != null ? (TextView) convert : text(sp(18), 0xE6FFFFFF, regular);
                t.setText(labels[pos]);
                t.setGravity(Gravity.CENTER_VERTICAL);
                t.setMinHeight(dp(56));
                t.setPadding(dp(12), 0, dp(12), 0);
                return t;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            for (int i = 0; i < labels.length; i++) labels[i] = WALLPAPER_NAMES.length == labels.length
                    ? WALLPAPER_NAMES[i] + (i == pos ? "  ✓" : "") : labels[i];
            adapter.notifyDataSetChanged();
            pick.at(pos);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lp.topMargin = dp(16);
        box.addView(list, lp);
        d.setContentView(box);
        Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(0xF2161113));
        w.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels, dp(360)), -1);
        w.setGravity(Gravity.END);
        w.setDimAmount(0f); // the wallpaper needs to be seen undimmed
        d.show();
    }

    private int wallpaper() {
        String saved = getSharedPreferences(PREFS, 0).getString("wallpaper", null);
        return Math.max(0, Arrays.asList(WALLPAPER_NAMES).indexOf(saved));
    }

    // Static gradient, drawn once by the window: no extra view, no animation.
    private void showWallpaper(int i) {
        Window w = getWindow();
        int[] c = WALLPAPERS[i];
        if (c == null) {
            w.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            w.setBackgroundDrawable(new ColorDrawable(0x8C000000));
            return;
        }
        w.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        int big = Math.max(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels);
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {c[0], c[1], c[2]});
        GradientDrawable top = radial(c[3], 0.08f, -0.12f, big * 0.65f);
        GradientDrawable bottom = radial(c[4], 1.0f, 1.1f, big * 0.52f);
        for (GradientDrawable g : new GradientDrawable[] {base, top, bottom}) g.setDither(true);
        w.setBackgroundDrawable(new LayerDrawable(new Drawable[] {base, top, bottom}));
    }

    private static GradientDrawable radial(int color, float cx, float cy, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setColors(new int[] {color, color & 0x00FFFFFF});
        g.setGradientCenter(cx, cy);
        g.setGradientRadius(r);
        return g;
    }

    // Screen time today, from Android's own usage log: time each app spent in front since midnight.
    // Needs usage access, which install.sh grants (or Settings, by tapping the line). Home doesn't count.
    private boolean hasUsageAccess() {
        AppOpsManager ops = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        return ops != null && ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    private void refreshScreenTime() {
        if (screenTime == null) return;
        if (!hasUsageAccess()) { screenTime.setText("Tap to see your screen time"); return; }
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) { screenTime.setText(""); return; }
        Calendar midnight = Calendar.getInstance();
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        midnight.set(Calendar.MILLISECOND, 0);
        long now = System.currentTimeMillis(), total = 0;
        Map<String, Long> openSince = new HashMap<>();
        try {
            UsageEvents events = usm.queryEvents(midnight.getTimeInMillis(), now);
            UsageEvents.Event e = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(e);
                String p = e.getPackageName();
                if (p == null || p.equals(getPackageName())) continue;
                if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    if (!openSince.containsKey(p)) openSince.put(p, e.getTimeStamp());
                } else if (e.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                    Long since = openSince.remove(p);
                    if (since != null) total += e.getTimeStamp() - since;
                }
            }
        } catch (Exception ignored) {}
        for (Map.Entry<String, Long> o : openSince.entrySet()) total += Math.max(0, now - o.getValue());
        long min = total / 60000;
        screenTime.setText("Screen time today  " + (min < 60 ? min + " min" : (min / 60) + " h " + (min % 60) + " min"));
    }

    private void openScreenTime() {
        if (!hasUsageAccess()) { start(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); return; }
        for (Intent i : new Intent[] {
                new Intent().setClassName("com.google.android.apps.wellbeing",
                        "com.google.android.apps.wellbeing.settings.TopLevelSettingsActivity"),
                new Intent().setClassName("com.samsung.android.forest", "com.samsung.android.forest.launcher.LauncherActivity"),
                new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)}) {
            if (i.resolveActivity(getPackageManager()) != null) { start(i); return; }
        }
    }

    private int gridX(int i) {
        return dp(MARGIN) + (i % cols) * (tileW + dp(GAP));
    }

    private int gridY(int i) {
        return gridTop + (i / cols) * (tileH + dp(GAP));
    }

    private View tile(String key) {
        for (View t : tiles) if (key != null && key.equals(t.getTag())) return t;
        return null;
    }

    private String labelOf(String pkg) {
        PackageManager pm = getPackageManager();
        try { return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); }
        catch (Exception e) { return pkg; }
    }

    private boolean userInstalled(String pkg) {
        try { return (getPackageManager().getApplicationInfo(pkg, 0).flags & ApplicationInfo.FLAG_SYSTEM) == 0; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }

    // Draws an icon once into a small bitmap: big adaptive icons cost RAM on an old tablet.
    private Drawable fit(Drawable d, int size) {
        try {
            Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, size, size);
            d.draw(new Canvas(b));
            return new BitmapDrawable(getResources(), b);
        } catch (Throwable t) {
            return d;
        }
    }

    private int dp(int v) {
        return Math.round(v * density);
    }

    private int sp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics()));
    }

    // Touch-only: no grey box when a key press (or adb) puts the device in keyboard mode.
    private static void noFocusBox(View v) {
        if (android.os.Build.VERSION.SDK_INT >= 26) v.setDefaultFocusHighlightEnabled(false);
    }

    private TextClock clock(String f12, String f24, int px, int color, Typeface tf) {
        TextClock c = new TextClock(this);
        c.setFormat12Hour(f12);
        c.setFormat24Hour(f24);
        c.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        c.setTextColor(color);
        c.setTypeface(tf);
        noFocusBox(c);
        return c;
    }

    private TextView text(int px, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        t.setTextColor(color);
        if (tf != null) t.setTypeface(tf);
        noFocusBox(t);
        return t;
    }

    private static FrameLayout.LayoutParams at(int x, int y, int w, int h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.leftMargin = x;
        lp.topMargin = y;
        return lp;
    }
}
