package com.masterzoul.cinespotlisttv;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MainActivity extends Activity {
    private static final String API = "https://cinespotlist3.pages.dev/api";

    private static final int BG = Color.rgb(23, 22, 37);
    private static final int CARD = Color.rgb(33, 31, 49);
    private static final int BORDER = Color.rgb(69, 64, 91);
    private static final int WHITE = Color.rgb(247, 246, 250);
    private static final int MUTED = Color.rgb(185, 181, 201);
    private static final int ORANGE = Color.rgb(233, 154, 81);
    private static final int YELLOW = Color.rgb(224, 193, 79);
    private static final int NETFLIX = Color.rgb(229, 9, 20);

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(10);
    private final List<Item> allItems = Collections.synchronizedList(new ArrayList<>());
    private final List<Item> searchItems = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, CardRefs> cardRefs = new ConcurrentHashMap<>();
    private final Map<String, Bitmap> imageCache = new ConcurrentHashMap<>();
    private final Set<String> enrichInflight = ConcurrentHashMap.newKeySet();
    private final Map<Integer, String> genres = new HashMap<>();

    private LinearLayout content;
    private TextView sectionTitle;
    private TextView status;
    private LinearLayout searchPanel;
    private EditText searchInput;
    private ImageButton searchIcon;
    private ImageButton catalogIcon;
    private Button netflixIcon;
    private ImageButton sortIcon;
    private ImageButton refreshFab;

    private String mode = "latest";
    private boolean sortRating = false;
    private int visibleCount = 10;
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            initGenres();
            buildUi();
            enterImmersiveMode();
            status.setText("Loading latest titles…");
            status.setVisibility(View.VISIBLE);
            ui.postDelayed(this::loadCatalog, 250);
        } catch (Throwable t) {
            showFatalStartupError(t);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void enterImmersiveMode() {
        try {
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
            );
            getWindow().setStatusBarColor(BG);
            getWindow().setNavigationBarColor(BG);

            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    View.SYSTEM_UI_FLAG_LOW_PROFILE
            );

            if (android.os.Build.VERSION.SDK_INT >= 30) {
                WindowInsetsController c = getWindow().getInsetsController();
                if (c != null) {
                    c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) ui.postDelayed(this::enterImmersiveMode, 120);
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        setContentView(root);

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setBackgroundColor(BG);
        root.addView(main, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(14), dp(8), dp(14), dp(6));
        header.setBackgroundColor(BG);
        main.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.20f
        ));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(titleRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.50f
        ));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(titleBox, new LinearLayout.LayoutParams(0, -1, 1f));

        TextView title = text("Latest Movies & Series", 31, WHITE, true);
        titleBox.addView(title);

        TextView meta = text("Made with love by Masterzoul | V1.6.5 (13.9.2026 | 11:28 AM)", 14, MUTED, false);
        meta.setPadding(0, dp(3), 0, 0);
        titleBox.addView(meta);

        LinearLayout controlRow = new LinearLayout(this);
        controlRow.setOrientation(LinearLayout.HORIZONTAL);
        controlRow.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(controlRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.50f
        ));

        searchPanel = new LinearLayout(this);
        searchPanel.setOrientation(LinearLayout.HORIZONTAL);
        searchPanel.setGravity(Gravity.CENTER_VERTICAL);
        searchPanel.setVisibility(View.GONE);
        LinearLayout.LayoutParams splp = new LinearLayout.LayoutParams(0, -1, 1f);
        splp.rightMargin = dp(10);
        controlRow.addView(searchPanel, splp);

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        searchInput.setTextColor(WHITE);
        searchInput.setHintTextColor(MUTED);
        searchInput.setHint("Search movies or series…");
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT);
        searchInput.setPadding(dp(12), 0, dp(12), 0);
        searchInput.setBackground(roundRect(CARD, BORDER, 1, 10));
        searchPanel.addView(searchInput, new LinearLayout.LayoutParams(0, dp(46), 1f));

        Button go = smallTextButton("Go", 16);
        go.setOnClickListener(v -> runSearch(searchInput.getText().toString()));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(74), dp(46));
        glp.leftMargin = dp(7);
        searchPanel.addView(go, glp);

        Button clear = smallTextButton("×", 22);
        clear.setOnClickListener(v -> {
            searchInput.setText("");
            searchPanel.setVisibility(View.GONE);
            mode = "latest";
            visibleCount = 10;
            refreshControlStates();
            render();
        });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(52), dp(46));
        clp.leftMargin = dp(7);
        searchPanel.addView(clear, clp);

        Space spacer = new Space(this);
        controlRow.addView(spacer, new LinearLayout.LayoutParams(0, dp(1), 1f));

        searchIcon = iconButton(com.masterzoul.cinespotlisttv.R.drawable.ic_search_tv, "Search");
        searchIcon.setOnClickListener(v -> {
            boolean show = searchPanel.getVisibility() != View.VISIBLE;
            searchPanel.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) searchInput.requestFocus();
            refreshControlStates();
        });
        controlRow.addView(searchIcon, iconLp());

        catalogIcon = iconButton(com.masterzoul.cinespotlisttv.R.drawable.ic_clapper_tv, "Browse");
        catalogIcon.setOnClickListener(this::showCatalogMenu);
        controlRow.addView(catalogIcon, iconLp());

        netflixIcon = smallTextButton("N", 30);
        netflixIcon.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        netflixIcon.setTextColor(NETFLIX);
        netflixIcon.setContentDescription("Netflix");
        netflixIcon.setOnClickListener(v -> {
            mode = "netflix";
            visibleCount = 10;
            searchPanel.setVisibility(View.GONE);
            refreshControlStates();
            render();
        });
        controlRow.addView(netflixIcon, iconLp());

        sortIcon = iconButton(com.masterzoul.cinespotlisttv.R.drawable.ic_sort_tv, "Sort");
        sortIcon.setOnClickListener(v -> {
            sortRating = !sortRating;
            refreshControlStates();
            render();
            Toast.makeText(this, sortRating ? "Sort: IMDb" : "Sort: Date", Toast.LENGTH_SHORT).show();
        });
        controlRow.addView(sortIcon, iconLp());

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(10), 0, dp(10), 0);
        main.addView(body, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.80f
        ));

        LinearLayout bodyHead = new LinearLayout(this);
        bodyHead.setOrientation(LinearLayout.HORIZONTAL);
        bodyHead.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bhp = new LinearLayout.LayoutParams(-1, dp(42));
        body.addView(bodyHead, bhp);

        sectionTitle = text("Latest Movies & Series", 22, WHITE, true);
        bodyHead.addView(sectionTitle, new LinearLayout.LayoutParams(0, -1, 1f));

        status = text("", 15, MUTED, false);
        status.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        status.setVisibility(View.GONE);
        bodyHead.addView(status, new LinearLayout.LayoutParams(0, -1, 1f));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        body.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(2), 0, dp(70));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        refreshFab = iconButton(com.masterzoul.cinespotlisttv.R.drawable.ic_refresh_tv, "Refresh");
        refreshFab.setOnClickListener(v -> loadCatalog());
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.END | Gravity.BOTTOM);
        rlp.setMargins(0, 0, dp(18), dp(18));
        root.addView(refreshFab, rlp);

        refreshControlStates();
    }

    private void showCatalogMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("Latest");
        popup.getMenu().add("Movies");
        popup.getMenu().add("Series");
        popup.getMenu().add("Upcoming");
        popup.setOnMenuItemClickListener(item -> {
            String t = item.getTitle().toString();
            if ("Movies".equals(t)) mode = "movies";
            else if ("Series".equals(t)) mode = "series";
            else if ("Upcoming".equals(t)) mode = "upcoming";
            else mode = "latest";
            visibleCount = 10;
            searchPanel.setVisibility(View.GONE);
            refreshControlStates();
            render();
            return true;
        });
        popup.show();
    }

    private ImageButton iconButton(int drawable, String description) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(drawable);
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setPadding(dp(12), dp(12), dp(12), dp(12));
        b.setContentDescription(description);
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);
        applyFocusStyle(b, false);
        return b;
    }

    private Button smallTextButton(String label, float sp) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        b.setTextColor(WHITE);
        b.setGravity(Gravity.CENTER);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setPadding(dp(6), 0, dp(6), 0);
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);
        applyFocusStyle(b, false);
        return b;
    }

    private LinearLayout.LayoutParams iconLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(56), dp(50));
        lp.leftMargin = dp(9);
        return lp;
    }

    private void applyFocusStyle(View v, boolean active) {
        v.setBackground(roundRect(CARD, active ? ORANGE : BORDER, active ? 2 : 1, 11));
        v.setOnFocusChangeListener((view, hasFocus) -> {
            boolean on = hasFocus || controlIsActive(view);
            view.setScaleX(1f);
            view.setScaleY(1f);
            view.setBackground(roundRect(CARD, on ? ORANGE : BORDER, on ? 2 : 1, 11));
        });
    }

    private boolean controlIsActive(View v) {
        if (v == searchIcon) return searchPanel != null && searchPanel.getVisibility() == View.VISIBLE;
        if (v == netflixIcon) return "netflix".equals(mode);
        if (v == sortIcon) return sortRating;
        if (v == catalogIcon) return !"netflix".equals(mode) && !"search".equals(mode);
        return false;
    }

    private void refreshControlStates() {
        if (searchIcon == null) return;
        View[] views = {searchIcon, catalogIcon, netflixIcon, sortIcon, refreshFab};
        for (View v : views) {
            if (v != null) {
                boolean active = controlIsActive(v);
                v.setBackground(roundRect(CARD, active ? ORANGE : BORDER, active ? 2 : 1, 11));
            }
        }
        if (sectionTitle != null) sectionTitle.setText(modeTitle());
    }

    private String modeTitle() {
        switch (mode) {
            case "movies": return "Movies";
            case "series": return "Series";
            case "netflix": return "Netflix Malaysia";
            case "upcoming": return "Upcoming";
            case "search": return "Search Results";
            default: return "Latest Movies & Series";
        }
    }

    private TextView text(String value, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private GradientDrawable roundRect(int fill, int stroke, int width, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(width), stroke);
        return g;
    }

    private void showFatalStartupError(Throwable t) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(40), dp(30), dp(40), dp(30));
        root.setBackgroundColor(BG);
        TextView title = text("Latest Movies & Series", 32, WHITE, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView msg = text("App startup error\n\n" + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()), 20, MUTED, false);
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(0, dp(20), 0, 0);
        root.addView(msg, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    private void loadCatalog() {
        if (loading) return;
        loading = true;
        status.setText("Loading…");
        status.setVisibility(View.VISIBLE);

        pool.execute(() -> {
            try {
                String today = dateNow(0);
                String ago = dateNow(-12);
                List<Future<List<Item>>> fs = new ArrayList<>();

                fs.add(pool.submit(() -> fetchTmdb("/movie/now_playing",
                        mapOf("region","MY","page","1"), "movie", false, false)));
                fs.add(pool.submit(() -> fetchTmdb("/discover/movie",
                        mapOf("watch_region","MY","with_watch_monetization_types","flatrate","sort_by","primary_release_date.desc",
                                "primary_release_date.gte",ago,"primary_release_date.lte",today,"include_adult","false","page","1"),
                        "movie", false, false)));
                fs.add(pool.submit(() -> fetchTmdb("/discover/tv",
                        mapOf("watch_region","MY","with_watch_monetization_types","flatrate","sort_by","first_air_date.desc",
                                "first_air_date.gte",ago,"first_air_date.lte",today,"include_adult","false","page","1"),
                        "tv", false, false)));
                fs.add(pool.submit(() -> fetchTmdb("/discover/movie",
                        mapOf("watch_region","MY","with_watch_providers","8","with_watch_monetization_types","flatrate",
                                "sort_by","primary_release_date.desc","primary_release_date.gte",ago,"primary_release_date.lte",today,
                                "include_adult","false","page","1"), "movie", true, false)));
                fs.add(pool.submit(() -> fetchTmdb("/discover/tv",
                        mapOf("watch_region","MY","with_watch_providers","8","with_watch_monetization_types","flatrate",
                                "sort_by","first_air_date.desc","first_air_date.gte",ago,"first_air_date.lte",today,
                                "include_adult","false","page","1"), "tv", true, false)));
                fs.add(pool.submit(() -> fetchTmdb("/movie/upcoming",
                        mapOf("region","MY","page","1"), "movie", false, true)));

                LinkedHashMap<String, Item> merged = new LinkedHashMap<>();
                for (Future<List<Item>> future : fs) {
                    for (Item item : future.get()) {
                        Item old = merged.get(item.key());
                        if (old == null) merged.put(item.key(), item);
                        else {
                            old.netflix = old.netflix || item.netflix;
                            old.upcoming = old.upcoming || item.upcoming;
                            if (TextUtils.isEmpty(old.overview)) old.overview = item.overview;
                        }
                    }
                }

                allItems.clear();
                allItems.addAll(merged.values());
                loading = false;

                ui.post(() -> {
                    status.setVisibility(View.GONE);
                    render();
                });
            } catch (Exception e) {
                loading = false;
                ui.post(() -> {
                    status.setText("Failed to load: " + safeMessage(e));
                    status.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private List<Item> fetchTmdb(String path, Map<String,String> params, String media, boolean netflix, boolean upcoming) throws Exception {
        Uri.Builder b = Uri.parse(API + "/tmdb").buildUpon();
        b.appendQueryParameter("path", path);
        for (Map.Entry<String,String> e : params.entrySet()) {
            b.appendQueryParameter(e.getKey(), e.getValue());
        }
        JSONObject root = getJson(b.build().toString());
        JSONArray a = root.optJSONArray("results");
        List<Item> out = new ArrayList<>();
        if (a == null) return out;

        for (int i=0;i<a.length();i++) {
            JSONObject r = a.optJSONObject(i);
            if (r == null) continue;
            Item item = itemFromJson(r, media);
            item.netflix = netflix;
            item.upcoming = upcoming;
            if (!TextUtils.isEmpty(item.title)) out.add(item);
        }
        return out;
    }

    private Item itemFromJson(JSONObject r, String media) {
        Item x = new Item();
        x.id = r.optInt("id");
        x.media = media;
        x.title = firstNonEmpty(r.optString("title"), r.optString("name"), "Untitled");
        x.originalTitle = firstNonEmpty(r.optString("original_title"), r.optString("original_name"), x.title);
        x.date = firstNonEmpty(r.optString("release_date"), r.optString("first_air_date"), "");
        x.originalLanguage = cleanValue(r.optString("original_language", ""));
        x.overview = cleanValue(r.optString("overview", ""));
        x.posterPath = cleanValue(r.optString("poster_path", ""));
        x.tmdb = r.optDouble("vote_average", 0);

        JSONArray gs = r.optJSONArray("genre_ids");
        if (gs != null) {
            for (int i=0;i<gs.length();i++) {
                String g = genres.get(gs.optInt(i));
                if (g != null) x.genreList.add(g);
            }
        }
        return x;
    }

    private void render() {
        ui.post(() -> {
            refreshControlStates();
            cardRefs.clear();
            content.removeAllViews();

            List<Item> list = filteredItems();
            if (list.isEmpty()) {
                TextView empty = text("No titles available.", 22, MUTED, false);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(0, dp(60), 0, dp(60));
                content.addView(empty, new LinearLayout.LayoutParams(-1, -2));
                return;
            }

            int count = Math.min(visibleCount, list.size());

            for (int i=0;i<count;i+=5) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.TOP);

                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
                rowLp.bottomMargin = dp(9);
                content.addView(row, rowLp);

                for (int j=0;j<5;j++) {
                    int index = i + j;
                    if (index < count) {
                        addCard(row, list.get(index), j);
                    } else {
                        Space s = new Space(this);
                        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(1), 1f);
                        if (j > 0) sp.leftMargin = dp(3);
                        if (j < 4) sp.rightMargin = dp(3);
                        row.addView(s, sp);
                    }
                }
            }

            if (count < list.size()) {
                Button more = smallTextButton("More +10", 17);
                more.setOnClickListener(v -> {
                    visibleCount += 10;
                    render();
                });
                LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(170), dp(48));
                mlp.gravity = Gravity.CENTER_HORIZONTAL;
                mlp.topMargin = dp(4);
                mlp.bottomMargin = dp(16);
                content.addView(more, mlp);
            }

            enrichVisible(list.subList(0, count));
        });
    }

    private void addCard(LinearLayout row, Item item, int column) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setBackground(roundRect(CARD, BORDER, 1, 13));

        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
        if (column > 0) cp.leftMargin = dp(3);
        if (column < 4) cp.rightMargin = dp(3);
        row.addView(card, cp);

        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackgroundColor(Color.rgb(45,42,60));
        card.addView(poster, new LinearLayout.LayoutParams(-1, dp(178)));

        TextView meta = text(metaLine(item), 12, YELLOW, true);
        meta.setSingleLine(true);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            meta.setAutoSizeTextTypeUniformWithConfiguration(9, 12, 1, TypedValue.COMPLEX_UNIT_SP);
        }
        meta.setPadding(0, dp(5), 0, 0);
        card.addView(meta);

        TextView title = text(displayTitle(item), 18, WHITE, true);
        title.setPadding(0, dp(4), 0, 0);
        card.addView(title);

        TextView genre = text(item.genreList.isEmpty() ? "-" : TextUtils.join(" • ", item.genreList), 13, MUTED, false);
        genre.setPadding(0, dp(4), 0, 0);
        card.addView(genre);

        TextView synopsis = text(TextUtils.isEmpty(item.overview) ? "No synopsis available." : item.overview, 13, MUTED, false);
        synopsis.setLineSpacing(0, 1.08f);
        synopsis.setMaxLines(3);
        synopsis.setEllipsize(TextUtils.TruncateAt.END);
        synopsis.setPadding(0, dp(6), 0, 0);
        card.addView(synopsis);

        Button synMore = smallTextButton("more", 11);
        synMore.setTextColor(ORANGE);
        synMore.setBackgroundColor(Color.TRANSPARENT);
        synMore.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        synMore.setOnClickListener(v -> {
            boolean expanded = synopsis.getMaxLines() == Integer.MAX_VALUE;
            synopsis.setMaxLines(expanded ? 3 : Integer.MAX_VALUE);
            synopsis.setEllipsize(expanded ? TextUtils.TruncateAt.END : null);
            synMore.setText(expanded ? "more" : "less");
        });
        card.addView(synMore, new LinearLayout.LayoutParams(-1, dp(30)));

        if (item.netflix) {
            TextView provider = text("Netflix Malaysia", 12, NETFLIX, true);
            provider.setPadding(0, dp(1), 0, dp(2));
            card.addView(provider);
        }

        LinearLayout ratingsRow = new LinearLayout(this);
        ratingsRow.setOrientation(LinearLayout.HORIZONTAL);
        ratingsRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rrlp = new LinearLayout.LayoutParams(-1, dp(36));
        rrlp.topMargin = dp(2);
        card.addView(ratingsRow, rrlp);

        TextView ratings = text(ratingsLine(item), 12, WHITE, true);
        ratings.setSingleLine(true);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            ratings.setAutoSizeTextTypeUniformWithConfiguration(8, 12, 1, TypedValue.COMPLEX_UNIT_SP);
        }
        ratings.setGravity(Gravity.CENTER_VERTICAL);
        ratingsRow.addView(ratings, new LinearLayout.LayoutParams(0, -1, 1f));

        Button lang = smallTextButton(langLabel(item.currentLang), 10);
        lang.setPadding(dp(4), 0, dp(4), 0);
        lang.setOnClickListener(v -> cycleLanguage(item));
        ratingsRow.addView(lang, new LinearLayout.LayoutParams(dp(42), dp(30)));

        CardRefs refs = new CardRefs();
        refs.meta = meta;
        refs.synopsis = synopsis;
        refs.ratings = ratings;
        refs.lang = lang;
        refs.poster = poster;
        cardRefs.put(item.key(), refs);

        loadPoster(item, poster);
        updateCard(item);
    }

    private List<Item> filteredItems() {
        List<Item> source = "search".equals(mode)
                ? new ArrayList<>(searchItems)
                : new ArrayList<>(allItems);

        String today = dateNow(0);
        String ago = dateNow(-12);
        List<Item> out = new ArrayList<>();

        for (Item x : source) {
            if ("search".equals(mode)) {
                out.add(x);
                continue;
            }

            boolean latest = !TextUtils.isEmpty(x.date)
                    && x.date.compareTo(ago) >= 0
                    && x.date.compareTo(today) <= 0;

            switch (mode) {
                case "movies":
                    if (latest && "movie".equals(x.media) && !x.upcoming) out.add(x);
                    break;
                case "series":
                    if (latest && "tv".equals(x.media) && !x.upcoming) out.add(x);
                    break;
                case "netflix":
                    if (latest && x.netflix && !x.upcoming) out.add(x);
                    break;
                case "upcoming":
                    if (x.upcoming && !TextUtils.isEmpty(x.date) && x.date.compareTo(today) >= 0) out.add(x);
                    break;
                default:
                    if (latest && !x.upcoming) out.add(x);
            }
        }

        if (sortRating) {
            out.sort((a,b) -> Double.compare(ratingScore(b), ratingScore(a)));
        } else if ("upcoming".equals(mode)) {
            out.sort(Comparator.comparing(a -> a.date == null ? "9999" : a.date));
        } else {
            out.sort((a,b) -> String.valueOf(b.date).compareTo(String.valueOf(a.date)));
        }

        return out;
    }

    private double ratingScore(Item x) {
        if (x.imdb != null) return x.imdb;
        return x.tmdb > 0 ? x.tmdb : -1;
    }

    private void enrichVisible(List<Item> items) {
        for (Item item : items) {
            if (item.enriched) {
                updateCard(item);
                continue;
            }
            if (!enrichInflight.add(item.key())) continue;

            pool.execute(() -> {
                try {
                    Uri ratingsUri = Uri.parse(API + "/ratings").buildUpon()
                            .appendQueryParameter("media", item.media)
                            .appendQueryParameter("id", String.valueOf(item.id))
                            .build();

                    JSONObject r = getJson(ratingsUri.toString());
                    item.imdb = parseNumber(cleanValue(r.optString("imdb", "")));
                    item.rtRaw = cleanValue(r.optString("rt", ""));
                    item.mcRaw = cleanValue(r.optString("metacritic", ""));

                    if (TextUtils.isEmpty(item.rtRaw)) {
                        try {
                            Uri rtUri = Uri.parse(API + "/ratings-rt").buildUpon()
                                    .appendQueryParameter("media", item.media)
                                    .appendQueryParameter("id", String.valueOf(item.id))
                                    .build();
                            JSONObject rr = getJson(rtUri.toString());
                            item.rtRaw = cleanValue(rr.optString("rt", ""));
                        } catch (Exception ignored) {}
                    }

                    try {
                        Uri runtimeUri = Uri.parse(API + "/runtime").buildUpon()
                                .appendQueryParameter("media", item.media)
                                .appendQueryParameter("id", String.valueOf(item.id))
                                .build();
                        JSONObject rr = getJson(runtimeUri.toString());
                        item.runtime = rr.optInt("runtime", 0);
                    } catch (Exception ignored) {}

                    item.enriched = true;
                } catch (Exception ignored) {
                    item.enriched = true;
                } finally {
                    enrichInflight.remove(item.key());
                    ui.post(() -> {
                        updateCard(item);
                        if (sortRating) scheduleResort();
                    });
                }
            });
        }
    }

    private final Runnable resortRunnable = this::render;

    private void scheduleResort() {
        ui.removeCallbacks(resortRunnable);
        ui.postDelayed(resortRunnable, 700);
    }

    private void updateCard(Item item) {
        CardRefs r = cardRefs.get(item.key());
        if (r == null) return;

        r.meta.setText(metaLine(item));
        r.ratings.setText(ratingsLine(item));
        r.lang.setText(langLabel(item.currentLang));

        String overview = overviewForLanguage(item);
        if (!TextUtils.isEmpty(overview)) r.synopsis.setText(overview);

        if ("ar".equals(item.currentLang)) {
            r.synopsis.setTextDirection(View.TEXT_DIRECTION_RTL);
            r.synopsis.setGravity(Gravity.RIGHT);
        } else {
            r.synopsis.setTextDirection(View.TEXT_DIRECTION_LTR);
            r.synopsis.setGravity(Gravity.LEFT);
        }
    }

    private void cycleLanguage(Item item) {
        String next = "en".equals(item.currentLang)
                ? "ms"
                : "ms".equals(item.currentLang) ? "ar" : "en";

        if ("en".equals(next)) {
            item.currentLang = "en";
            updateCard(item);
            return;
        }

        String cached = "ms".equals(next) ? item.ms : item.ar;
        item.currentLang = next;
        updateCard(item);

        if (!TextUtils.isEmpty(cached) || TextUtils.isEmpty(item.overview)) return;

        pool.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("target", next);

                JSONArray arr = new JSONArray();
                JSONObject one = new JSONObject();
                one.put("key", item.key());
                one.put("media", item.media);
                one.put("id", item.id);
                one.put("text", item.overview);
                arr.put(one);
                payload.put("items", arr);

                JSONObject response = postJson(API + "/translate-batch", payload);
                JSONObject trans = response.optJSONObject("translations");
                String translated = trans == null ? "" : cleanValue(trans.optString(item.key(), ""));

                if (!TextUtils.isEmpty(translated)) {
                    if ("ms".equals(next)) item.ms = translated;
                    else item.ar = translated;
                    ui.post(() -> updateCard(item));
                } else {
                    ui.post(() -> {
                        item.currentLang = "en";
                        updateCard(item);
                        Toast.makeText(this, "Translation unavailable", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                ui.post(() -> {
                    item.currentLang = "en";
                    updateCard(item);
                    Toast.makeText(this, "Translation unavailable", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void runSearch(String raw) {
        String q = raw == null ? "" : raw.trim();
        if (q.isEmpty()) return;

        status.setText("Searching…");
        status.setVisibility(View.VISIBLE);
        mode = "search";
        visibleCount = 10;
        refreshControlStates();

        pool.execute(() -> {
            try {
                Uri u = Uri.parse(API + "/search").buildUpon()
                        .appendQueryParameter("q", q)
                        .build();

                JSONObject root = getJson(u.toString());
                JSONArray a = root.optJSONArray("results");
                List<Item> result = new ArrayList<>();

                if (a != null) {
                    for (int i=0;i<a.length();i++) {
                        JSONObject r = a.optJSONObject(i);
                        if (r == null) continue;

                        String media = cleanValue(r.optString("media_type", ""));
                        if (!"movie".equals(media) && !"tv".equals(media)) continue;

                        result.add(itemFromJson(r, media));
                    }
                }

                searchItems.clear();
                searchItems.addAll(result);

                ui.post(() -> {
                    status.setVisibility(View.GONE);
                    render();
                });
            } catch (Exception e) {
                ui.post(() -> {
                    status.setText("Search failed: " + safeMessage(e));
                    status.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void loadPoster(Item item, ImageView view) {
        if (TextUtils.isEmpty(item.posterPath)) return;

        String url = "https://image.tmdb.org/t/p/w342" + item.posterPath;
        Bitmap cached = imageCache.get(url);

        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }

        pool.execute(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(6000);
                c.setReadTimeout(7000);
                c.setRequestProperty("User-Agent", "CineSpotList-TV/4.1");

                try (InputStream in = c.getInputStream()) {
                    Bitmap b = BitmapFactory.decodeStream(in);
                    if (b != null) {
                        imageCache.put(url, b);
                        ui.post(() -> {
                            CardRefs current = cardRefs.get(item.key());
                            if (current != null && current.poster == view) {
                                view.setImageBitmap(b);
                            }
                        });
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.disconnect();
            }
        });
    }

    private String displayTitle(Item x) {
        if (!TextUtils.isEmpty(x.originalTitle)
                && !x.originalTitle.equalsIgnoreCase(x.title)) {
            return x.originalTitle + " " + x.title;
        }
        return x.title;
    }

    private String metaLine(Item x) {
        String date = formatDate(x.date);
        String type = "movie".equals(x.media) ? "MOVIE" : "SERIES";
        String lang = langCode(x.originalLanguage);
        String runtime = x.runtime > 0 ? formatRuntime(x.runtime) : "-";
        return date + " | " + type + " | " + lang + " | " + runtime;
    }

    private String ratingsLine(Item x) {
        String imdb = x.imdb == null ? "-" : String.format(Locale.US, "%.1f", x.imdb);
        String rt = formatRt(x.rtRaw);
        String mc = formatMc(x.mcRaw);
        String tmdb = x.tmdb > 0 ? String.format(Locale.US, "%.1f", x.tmdb) : "-";
        return "IMDb " + imdb + " | RT " + rt + " | MC " + mc + " | TMDb " + tmdb;
    }

    private String overviewForLanguage(Item x) {
        if ("ms".equals(x.currentLang) && !TextUtils.isEmpty(x.ms)) return x.ms;
        if ("ar".equals(x.currentLang) && !TextUtils.isEmpty(x.ar)) return x.ar;
        return TextUtils.isEmpty(x.overview) ? "No synopsis available." : x.overview;
    }

    private String langLabel(String lang) {
        if ("ms".equals(lang)) return "mal";
        if ("ar".equals(lang)) return "ar";
        return "eng";
    }

    private String cleanValue(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s) || "n/a".equalsIgnoreCase(s) || "undefined".equalsIgnoreCase(s)) {
            return "";
        }
        return s;
    }

    private String formatRt(String raw) {
        raw = cleanValue(raw);
        if (TextUtils.isEmpty(raw)) return "-";
        try {
            String s = raw.replace("%", "").trim();
            double n = Double.parseDouble(s);
            double f = n / 20.0;
            return (Math.abs(f - Math.rint(f)) < 0.001
                    ? String.format(Locale.US, "%.0f", f)
                    : String.format(Locale.US, "%.1f", f)) + "/5";
        } catch (Exception e) {
            return raw;
        }
    }

    private String formatMc(String raw) {
        raw = cleanValue(raw);
        if (TextUtils.isEmpty(raw)) return "-";
        try {
            return String.format(Locale.US, "%.1f", Double.parseDouble(raw) / 10.0);
        } catch (Exception e) {
            return raw;
        }
    }

    private Double parseNumber(String s) {
        s = cleanValue(s);
        if (TextUtils.isEmpty(s)) return null;
        try {
            return Double.parseDouble(s.replace("%","").trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String formatRuntime(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        if (h > 0 && m > 0) return h + "h " + m + "m";
        if (h > 0) return h + "h";
        return m + "m";
    }

    private String formatDate(String raw) {
        if (TextUtils.isEmpty(raw)) return "-";
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat out = new SimpleDateFormat("d MMM yyyy", Locale.ENGLISH);
            return out.format(in.parse(raw));
        } catch (Exception e) {
            return raw;
        }
    }

    private String dateNow(int monthOffset) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.MONTH, monthOffset);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
    }

    private String langCode(String s) {
        if (s == null) return "-";
        switch (s.toLowerCase(Locale.US)) {
            case "en": return "ENG";
            case "ms": return "MSA";
            case "ar": return "ARA";
            case "ko": return "KOR";
            case "ja": return "JPN";
            case "zh": return "ZHO";
            case "th": return "THA";
            case "id": return "IND";
            case "hi": return "HIN";
            case "es": return "SPA";
            case "fr": return "FRA";
            case "de": return "DEU";
            case "it": return "ITA";
            case "pt": return "POR";
            case "ru": return "RUS";
            case "ta": return "TAM";
            case "te": return "TEL";
            default: return s.toUpperCase(Locale.US);
        }
    }

    private JSONObject getJson(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(12000);
        c.setRequestMethod("GET");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "CineSpotList-TV/4.1");

        try {
            int code = c.getResponseCode();
            String body = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
            if (code >= 400) throw new Exception("HTTP " + code);
            return new JSONObject(body);
        } finally {
            c.disconnect();
        }
    }

    private JSONObject postJson(String url, JSONObject payload) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "CineSpotList-TV/4.1");

        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        c.getOutputStream().write(bytes);

        try {
            int code = c.getResponseCode();
            String body = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
            if (code >= 400) throw new Exception("HTTP " + code);
            return new JSONObject(body);
        } finally {
            c.disconnect();
        }
    }

    private String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }

        return sb.toString();
    }

    private Map<String,String> mapOf(String... values) {
        Map<String,String> m = new LinkedHashMap<>();
        for (int i=0;i+1<values.length;i+=2) {
            m.put(values[i], values[i+1]);
        }
        return m;
    }

    private String firstNonEmpty(String... values) {
        for (String v : values) {
            String s = cleanValue(v);
            if (!TextUtils.isEmpty(s)) return s;
        }
        return "";
    }

    private String safeMessage(Exception e) {
        String s = e.getMessage();
        return TextUtils.isEmpty(s) ? e.getClass().getSimpleName() : s;
    }

    private void initGenres() {
        genres.put(28,"Action"); genres.put(12,"Adventure"); genres.put(16,"Animation"); genres.put(35,"Comedy");
        genres.put(80,"Crime"); genres.put(99,"Documentary"); genres.put(18,"Drama"); genres.put(10751,"Family");
        genres.put(14,"Fantasy"); genres.put(36,"History"); genres.put(27,"Horror"); genres.put(10402,"Music");
        genres.put(9648,"Mystery"); genres.put(10749,"Romance"); genres.put(878,"Science Fiction"); genres.put(10770,"TV Movie");
        genres.put(53,"Thriller"); genres.put(10752,"War"); genres.put(37,"Western"); genres.put(10759,"Action & Adventure");
        genres.put(10762,"Kids"); genres.put(10763,"News"); genres.put(10764,"Reality"); genres.put(10765,"Sci-Fi & Fantasy");
        genres.put(10766,"Soap"); genres.put(10767,"Talk"); genres.put(10768,"War & Politics");
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
    }

    @Override
    protected void onDestroy() {
        pool.shutdownNow();
        super.onDestroy();
    }

    static class Item {
        int id;
        String media = "";
        String title = "";
        String originalTitle = "";
        String date = "";
        String originalLanguage = "";
        String overview = "";
        String posterPath = "";
        List<String> genreList = new ArrayList<>();
        boolean netflix = false;
        boolean upcoming = false;
        double tmdb = 0;
        Double imdb = null;
        String rtRaw = "";
        String mcRaw = "";
        int runtime = 0;
        boolean enriched = false;
        String currentLang = "en";
        String ms = "";
        String ar = "";
        String key() { return media + "-" + id; }
    }

    static class CardRefs {
        TextView meta;
        TextView synopsis;
        TextView ratings;
        Button lang;
        ImageView poster;
    }
}
