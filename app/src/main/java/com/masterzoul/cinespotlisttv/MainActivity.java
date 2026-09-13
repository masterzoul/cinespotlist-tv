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
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import java.net.URLEncoder;
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
    private static final int BORDER = Color.rgb(67, 62, 89);
    private static final int WHITE = Color.rgb(247, 246, 250);
    private static final int MUTED = Color.rgb(185, 181, 201);
    private static final int ORANGE = Color.rgb(233, 154, 81);
    private static final int YELLOW = Color.rgb(224, 193, 79);
    private static final int NETFLIX = Color.rgb(229, 76, 76);

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(10);
    private final List<Item> allItems = Collections.synchronizedList(new ArrayList<>());
    private final List<Item> searchItems = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, CardRefs> cardRefs = new ConcurrentHashMap<>();
    private final Map<String, Bitmap> imageCache = new ConcurrentHashMap<>();
    private final Set<String> enrichInflight = ConcurrentHashMap.newKeySet();
    private final Map<Integer, String> genres = new HashMap<>();

    private LinearLayout content;
    private TextView status;
    private EditText searchInput;
    private LinearLayout searchRow;
    private Button sortBtn;
    private Button modeBtnLatest, modeBtnMovies, modeBtnSeries, modeBtnNetflix, modeBtnUpcoming;

    private String mode = "latest";
    private boolean sortRating = false;
    private int visibleCount = 10;
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        enterImmersiveMode();
        initGenres();
        buildUi();
        loadCatalog();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void enterImmersiveMode() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(42), dp(24), dp(42), dp(24));
        setContentView(root);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titleRow.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView title = text("CineSpotList TV", 36, WHITE, true);
        titles.addView(title);
        TextView sub = text("Latest Movies & Series • Native TV v3.0.0", 18, MUTED, false);
        sub.setPadding(0, dp(5), 0, 0);
        titles.addView(sub);

        Button refresh = button("Refresh", 18);
        refresh.setOnClickListener(v -> loadCatalog());
        titleRow.addView(refresh, lpWrap(dp(140), dp(58), dp(10)));

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setFillViewport(true);
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        hsv.addView(controls, new HorizontalScrollView.LayoutParams(-2, -2));
        LinearLayout.LayoutParams hsvLp = new LinearLayout.LayoutParams(-1, dp(72));
        hsvLp.topMargin = dp(18);
        root.addView(hsv, hsvLp);

        Button searchToggle = button("Search", 18);
        searchToggle.setOnClickListener(v -> {
            searchRow.setVisibility(searchRow.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            if (searchRow.getVisibility() == View.VISIBLE) searchInput.requestFocus();
        });
        controls.addView(searchToggle, controlLp());

        modeBtnLatest = addModeButton(controls, "Latest", "latest");
        modeBtnMovies = addModeButton(controls, "Movies", "movies");
        modeBtnSeries = addModeButton(controls, "Series", "series");
        modeBtnNetflix = addModeButton(controls, "Netflix", "netflix");
        modeBtnUpcoming = addModeButton(controls, "Upcoming", "upcoming");

        sortBtn = button("Sort: Date", 18);
        sortBtn.setOnClickListener(v -> {
            sortRating = !sortRating;
            sortBtn.setText(sortRating ? "Sort: IMDb" : "Sort: Date");
            render();
        });
        controls.addView(sortBtn, controlLp());

        searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setVisibility(View.GONE);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, dp(70));
        searchLp.topMargin = dp(8);
        root.addView(searchRow, searchLp);

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        searchInput.setTextColor(WHITE);
        searchInput.setHintTextColor(MUTED);
        searchInput.setHint("Search movies or series…");
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT);
        searchInput.setPadding(dp(18), 0, dp(18), 0);
        searchInput.setBackground(roundRect(CARD, BORDER, 2, 12));
        searchRow.addView(searchInput, new LinearLayout.LayoutParams(0, dp(56), 1f));

        Button go = button("Go", 18);
        go.setOnClickListener(v -> runSearch(searchInput.getText().toString()));
        LinearLayout.LayoutParams glp = lpWrap(dp(100), dp(56), dp(10));
        searchRow.addView(go, glp);

        Button clear = button("Clear", 18);
        clear.setOnClickListener(v -> {
            searchInput.setText("");
            mode = "latest";
            visibleCount = 10;
            render();
        });
        searchRow.addView(clear, lpWrap(dp(110), dp(56), dp(10)));

        status = text("Loading…", 20, MUTED, false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = dp(10);
        slp.bottomMargin = dp(10);
        root.addView(status, slp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(8), 0, dp(40));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        highlightModeButton();
    }

    private Button addModeButton(LinearLayout controls, String label, String value) {
        Button b = button(label, 18);
        b.setOnClickListener(v -> {
            mode = value;
            visibleCount = 10;
            highlightModeButton();
            render();
        });
        controls.addView(b, controlLp());
        return b;
    }

    private LinearLayout.LayoutParams controlLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(150), dp(56));
        lp.setMargins(0, 0, dp(12), 0);
        return lp;
    }

    private LinearLayout.LayoutParams lpWrap(int w, int h, int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.leftMargin = left;
        return lp;
    }

    private TextView text(String value, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String value, float sp) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(value);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        b.setTextColor(WHITE);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), 0, dp(12), 0);
        styleFocusable(b, false);
        return b;
    }

    private void styleFocusable(View v, boolean active) {
        v.setBackground(roundRect(CARD, active ? ORANGE : BORDER, active ? 3 : 2, 14));
        v.setOnFocusChangeListener((view, hasFocus) -> {
            view.setScaleX(hasFocus ? 1.05f : 1f);
            view.setScaleY(hasFocus ? 1.05f : 1f);
            view.setBackground(roundRect(CARD, hasFocus || isActiveButton(view) ? ORANGE : BORDER, hasFocus ? 4 : 2, 14));
        });
    }

    private boolean isActiveButton(View v) {
        if (!(v instanceof Button)) return false;
        return v == activeModeButton();
    }

    private Button activeModeButton() {
        switch (mode) {
            case "movies": return modeBtnMovies;
            case "series": return modeBtnSeries;
            case "netflix": return modeBtnNetflix;
            case "upcoming": return modeBtnUpcoming;
            default: return modeBtnLatest;
        }
    }

    private void highlightModeButton() {
        Button[] bs = {modeBtnLatest, modeBtnMovies, modeBtnSeries, modeBtnNetflix, modeBtnUpcoming};
        for (Button b : bs) {
            if (b != null) b.setBackground(roundRect(CARD, b == activeModeButton() ? ORANGE : BORDER, b == activeModeButton() ? 3 : 2, 14));
        }
    }

    private GradientDrawable roundRect(int fill, int stroke, int width, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(width), stroke);
        return g;
    }

    private void loadCatalog() {
        if (loading) return;
        loading = true;
        status.setText("Loading latest titles…");
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
                    status.setText("Ready • " + allItems.size() + " titles loaded");
                    render();
                });
            } catch (Exception e) {
                loading = false;
                ui.post(() -> status.setText("Failed to load. Press Refresh. " + safeMessage(e)));
            }
        });
    }

    private List<Item> fetchTmdb(String path, Map<String,String> params, String media, boolean netflix, boolean upcoming) throws Exception {
        Uri.Builder b = Uri.parse(API + "/tmdb").buildUpon();
        b.appendQueryParameter("path", path);
        for (Map.Entry<String,String> e : params.entrySet()) b.appendQueryParameter(e.getKey(), e.getValue());
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
        x.originalLanguage = r.optString("original_language", "");
        x.overview = r.optString("overview", "");
        x.posterPath = r.optString("poster_path", "");
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
            cardRefs.clear();
            content.removeAllViews();
            List<Item> list = filteredItems();
            if (list.isEmpty()) {
                TextView empty = text(mode.equals("search") ? "No search results." : "No titles available.", 26, MUTED, false);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(0, dp(80), 0, dp(80));
                content.addView(empty, new LinearLayout.LayoutParams(-1, -2));
                return;
            }

            int count = Math.min(visibleCount, list.size());
            for (int i=0;i<count;i+=2) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.TOP);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
                rowLp.bottomMargin = dp(22);
                content.addView(row, rowLp);

                Item left = list.get(i);
                addCard(row, left, true);
                if (i+1<count) addCard(row, list.get(i+1), false);
                else {
                    Space s = new Space(this);
                    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(1), 1f);
                    sp.leftMargin = dp(11);
                    row.addView(s, sp);
                }
            }

            if (count < list.size()) {
                Button more = button("More +10", 22);
                more.setOnClickListener(v -> {
                    visibleCount += 10;
                    render();
                });
                LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(220), dp(62));
                mlp.gravity = Gravity.CENTER_HORIZONTAL;
                mlp.topMargin = dp(8);
                mlp.bottomMargin = dp(30);
                content.addView(more, mlp);
            }

            enrichVisible(list.subList(0, count));
        });
    }

    private void addCard(LinearLayout row, Item item, boolean left) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(16));
        card.setBackground(roundRect(CARD, BORDER, 2, 22));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
        if (left) cp.rightMargin = dp(11); else cp.leftMargin = dp(11);
        row.addView(card, cp);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.TOP);
        card.addView(top, new LinearLayout.LayoutParams(-1, -2));

        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackgroundColor(Color.rgb(45,42,60));
        top.addView(poster, new LinearLayout.LayoutParams(dp(170), dp(255)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, -2, 1f);
        ilp.leftMargin = dp(18);
        top.addView(info, ilp);

        TextView meta = text(metaLine(item), 18, YELLOW, true);
        info.addView(meta);

        TextView title = text(displayTitle(item), 27, WHITE, true);
        title.setPadding(0, dp(6), 0, 0);
        info.addView(title);

        TextView genre = text(item.genreList.isEmpty() ? "-" : TextUtils.join(" • ", item.genreList), 20, MUTED, false);
        genre.setPadding(0, dp(8), 0, 0);
        info.addView(genre);

        if (item.netflix) {
            TextView provider = text("Netflix Malaysia", 19, NETFLIX, true);
            provider.setPadding(0, dp(7), 0, 0);
            info.addView(provider);
        }

        TextView synopsis = text(TextUtils.isEmpty(item.overview) ? "No synopsis available." : item.overview, 20, MUTED, false);
        synopsis.setLineSpacing(0, 1.15f);
        synopsis.setMaxLines(3);
        synopsis.setEllipsize(TextUtils.TruncateAt.END);
        synopsis.setPadding(0, dp(12), 0, 0);
        info.addView(synopsis);

        Button synMore = button("more", 16);
        synMore.setTextColor(ORANGE);
        synMore.setBackgroundColor(Color.TRANSPARENT);
        synMore.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        synMore.setOnClickListener(v -> {
            boolean expanded = synopsis.getMaxLines() == Integer.MAX_VALUE;
            synopsis.setMaxLines(expanded ? 3 : Integer.MAX_VALUE);
            synopsis.setEllipsize(expanded ? TextUtils.TruncateAt.END : null);
            synMore.setText(expanded ? "more" : "less");
        });
        LinearLayout.LayoutParams smlp = new LinearLayout.LayoutParams(-1, dp(42));
        info.addView(synMore, smlp);

        LinearLayout ratingsRow = new LinearLayout(this);
        ratingsRow.setOrientation(LinearLayout.HORIZONTAL);
        ratingsRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rrlp = new LinearLayout.LayoutParams(-1, dp(48));
        rrlp.topMargin = dp(8);
        card.addView(ratingsRow, rrlp);

        TextView ratings = text(ratingsLine(item), 18, WHITE, true);
        ratings.setGravity(Gravity.CENTER_VERTICAL);
        ratingsRow.addView(ratings, new LinearLayout.LayoutParams(0, -1, 1f));

        Button lang = button(langLabel(item.currentLang), 15);
        lang.setMinWidth(0);
        lang.setPadding(dp(10), 0, dp(10), 0);
        lang.setOnClickListener(v -> cycleLanguage(item));
        ratingsRow.addView(lang, new LinearLayout.LayoutParams(dp(62), dp(38)));

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
        List<Item> source = mode.equals("search") ? new ArrayList<>(searchItems) : new ArrayList<>(allItems);
        String today = dateNow(0);
        String ago = dateNow(-12);
        List<Item> out = new ArrayList<>();

        for (Item x : source) {
            if (mode.equals("search")) {
                out.add(x);
                continue;
            }
            boolean latest = !TextUtils.isEmpty(x.date) && x.date.compareTo(ago) >= 0 && x.date.compareTo(today) <= 0;
            switch (mode) {
                case "movies":
                    if (latest && "movie".equals(x.media) && !x.upcoming) out.add(x);
                    break;
                case "series":
                    if (latest && "tv".equals(x.media)) out.add(x);
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
        } else if (mode.equals("upcoming")) {
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
                    String imdb = r.optString("imdb", "");
                    String rt = r.optString("rt", "");
                    String mc = r.optString("metacritic", "");
                    item.imdb = parseNumber(imdb);
                    item.rtRaw = rt;
                    item.mcRaw = mc;

                    if (TextUtils.isEmpty(item.rtRaw)) {
                        try {
                            Uri rtUri = Uri.parse(API + "/ratings-rt").buildUpon()
                                    .appendQueryParameter("media", item.media)
                                    .appendQueryParameter("id", String.valueOf(item.id))
                                    .build();
                            JSONObject rr = getJson(rtUri.toString());
                            item.rtRaw = rr.optString("rt", "");
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
        String next = "en".equals(item.currentLang) ? "ms" : "ms".equals(item.currentLang) ? "ar" : "en";
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
                String translated = trans == null ? "" : trans.optString(item.key(), "");
                if (!TextUtils.isEmpty(translated)) {
                    if ("ms".equals(next)) item.ms = translated; else item.ar = translated;
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
        status.setText("Searching “" + q + "”…");
        mode = "search";
        visibleCount = 10;
        highlightModeButton();
        pool.execute(() -> {
            try {
                Uri u = Uri.parse(API + "/search").buildUpon().appendQueryParameter("q", q).build();
                JSONObject root = getJson(u.toString());
                JSONArray a = root.optJSONArray("results");
                List<Item> result = new ArrayList<>();
                if (a != null) {
                    for (int i=0;i<a.length();i++) {
                        JSONObject r = a.optJSONObject(i);
                        if (r == null) continue;
                        String media = r.optString("media_type", "");
                        if (!media.equals("movie") && !media.equals("tv")) continue;
                        result.add(itemFromJson(r, media));
                    }
                }
                searchItems.clear();
                searchItems.addAll(result);
                ui.post(() -> {
                    status.setText(result.size() + " search results");
                    render();
                });
            } catch (Exception e) {
                ui.post(() -> status.setText("Search failed. " + safeMessage(e)));
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
                c.setRequestProperty("User-Agent", "CineSpotList-TV/3.0");
                try (InputStream in = c.getInputStream()) {
                    Bitmap b = BitmapFactory.decodeStream(in);
                    if (b != null) {
                        imageCache.put(url, b);
                        ui.post(() -> {
                            CardRefs current = cardRefs.get(item.key());
                            if (current != null && current.poster == view) view.setImageBitmap(b);
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
        if (!TextUtils.isEmpty(x.originalTitle) && !x.originalTitle.equalsIgnoreCase(x.title)) {
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

    private String formatRt(String raw) {
        if (TextUtils.isEmpty(raw) || "N/A".equalsIgnoreCase(raw)) return "-";
        try {
            String s = raw.replace("%", "").trim();
            double n = Double.parseDouble(s);
            double f = n / 20.0;
            return (Math.abs(f - Math.rint(f)) < 0.001 ? String.format(Locale.US, "%.0f", f) : String.format(Locale.US, "%.1f", f)) + "/5";
        } catch (Exception e) {
            return raw;
        }
    }

    private String formatMc(String raw) {
        if (TextUtils.isEmpty(raw) || "N/A".equalsIgnoreCase(raw)) return "-";
        try {
            return String.format(Locale.US, "%.1f", Double.parseDouble(raw) / 10.0);
        } catch (Exception e) {
            return raw;
        }
    }

    private Double parseNumber(String s) {
        try {
            if (TextUtils.isEmpty(s) || "N/A".equalsIgnoreCase(s)) return null;
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
        c.setRequestProperty("User-Agent", "CineSpotList-TV/3.0");
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
        c.setRequestProperty("User-Agent", "CineSpotList-TV/3.0");
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
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private Map<String,String> mapOf(String... values) {
        Map<String,String> m = new LinkedHashMap<>();
        for (int i=0;i+1<values.length;i+=2) m.put(values[i], values[i+1]);
        return m;
    }

    private String firstNonEmpty(String... values) {
        for (String v : values) if (!TextUtils.isEmpty(v) && !"null".equalsIgnoreCase(v)) return v;
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
