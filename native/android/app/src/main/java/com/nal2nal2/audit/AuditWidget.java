package com.nal2nal2.audit;

import android.app.PendingIntent;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import java.io.File;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.RemoteViews;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** 홈 화면 위젯: 앱이 저장해 둔 요약으로 진행률과 다가오는 일정(필드 · 주총 · Audit Lobby)을 보여줘요. */
public class AuditWidget extends AppWidgetProvider {
    static final String PREFS = "audit_widget";
    static final String KEY = "summary";
    static final String CHAR_FILE = "widget_char.png";
    private static final int[][] UP_IDS = {
        {R.id.up0, R.id.upd0, R.id.upt0, R.id.upr0}, {R.id.up1, R.id.upd1, R.id.upt1, R.id.upr1}, {R.id.up2, R.id.upd2, R.id.upt2, R.id.upr2}
    };
    private static final int[][] CELL_IDS = {
        {R.id.k0, R.id.v0, R.id.p0}, {R.id.k1, R.id.v1, R.id.p1},
        {R.id.k2, R.id.v2, R.id.p2}, {R.id.k3, R.id.v3, R.id.p3}
    };

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) mgr.updateAppWidget(id, build(ctx));
    }

    static void refreshAll(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, AuditWidget.class));
        if (ids == null || ids.length == 0) return;
        RemoteViews rv = build(ctx);
        for (int id : ids) mgr.updateAppWidget(id, rv);
    }

    private static RemoteViews build(Context ctx) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_audit);
        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.widget_root, pi);

        String raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null);
        int alpha = 100;
        try { if (raw != null) alpha = new JSONObject(raw).optInt("alpha", 100); } catch (Exception ignored) { }
        rv.setInt(R.id.bg, "setImageAlpha", Math.round(Math.max(0, Math.min(100, alpha)) * 2.55f));
        // 배경이 절반 이상 투명하면 글자를 배경화면에 맞춰 흰색/검정으로 바꿔요
        // 앱 설정에서 고른 캐릭터 그림(폰 안에만 저장)
        try {
            File cf = new File(ctx.getFilesDir(), CHAR_FILE);
            Bitmap bm = cf.exists() ? BitmapFactory.decodeFile(cf.getAbsolutePath()) : null;
            if (bm != null) { rv.setImageViewBitmap(R.id.char_img, bm); rv.setViewVisibility(R.id.char_img, View.VISIBLE); }
            else rv.setViewVisibility(R.id.char_img, View.GONE);
        } catch (Exception e) { rv.setViewVisibility(R.id.char_img, View.GONE); }
        // 일정 칸 글자색(투명 모드에서는 배경화면에 맞춰 바꿔요)
        int dateInk = 0, todayInk = 0;
        if (alpha <= 50) {
            int mode = 0;
            try { if (raw != null) mode = new JSONObject(raw).optInt("textMode", 0); } catch (Exception ignored) { }
            boolean white = mode == 1 || (mode == 0 && wallpaperIsDark(ctx));
            int ink = white ? 0xFFFFFFFF : 0xFF111111;
            int muted = white ? 0xD9FFFFFF : 0xBF111111;
            rv.setTextColor(R.id.title, ink);
            rv.setTextColor(R.id.sub, muted);
            for (int[] c : CELL_IDS) { rv.setTextColor(c[0], muted); rv.setTextColor(c[1], ink); }
            for (int[] u : UP_IDS) { rv.setTextColor(u[2], ink); rv.setTextColor(u[3], muted); }
            // 연한 초록 칸 위에 흰 글자는 안 보여서, 칸 배경을 반투명 어둡게/밝게 바꿔요
            rv.setInt(R.id.up_box, "setBackgroundResource", white ? R.drawable.widget_up_dark : R.drawable.widget_up_light);
            rv.setTextColor(R.id.ok_text, ink);
            dateInk = ink;
            todayInk = white ? 0xFFFFB4A8 : 0xFFC0392B;
        } else {
            rv.setInt(R.id.up_box, "setBackgroundResource", R.drawable.widget_ok_bg);
        }
        if (raw == null) {
            rv.setTextViewText(R.id.title, "기말감사");
            rv.setTextViewText(R.id.sub, "");
            rv.setViewVisibility(R.id.cells, View.GONE);
            hideUp(rv);
            rv.setViewVisibility(R.id.ok_text, View.VISIBLE);
            rv.setTextViewText(R.id.ok_text, "앱을 열고 엑셀을 불러오면 여기에 진행률이 보여요");
            return rv;
        }
        try {
            JSONObject o = new JSONObject(raw);
            String fy = o.optString("fy", "");
            rv.setTextViewText(R.id.title, fy.isEmpty() ? "기말감사" : "기말감사 · FY" + fy);
            rv.setTextViewText(R.id.sub, o.optString("scope", "전체") + " · " + timeOf(o.optString("updated", "")) + " 기준");
            rv.setViewVisibility(R.id.cells, View.VISIBLE);

            JSONArray cells = o.optJSONArray("cells");
            for (int i = 0; i < 4; i++) {
                JSONArray c = cells != null ? cells.optJSONArray(i) : null;
                String k = c != null ? c.optString(0, "") : "";
                int a = c != null ? c.optInt(1, 0) : 0, b = c != null ? c.optInt(2, 0) : 0;
                rv.setTextViewText(CELL_IDS[i][0], k);
                SpannableStringBuilder v = new SpannableStringBuilder(String.valueOf(a));
                int st = v.length();
                v.append("/").append(String.valueOf(b));
                v.setSpan(new RelativeSizeSpan(0.6f), st, v.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                rv.setTextViewText(CELL_IDS[i][1], v);
                rv.setProgressBar(CELL_IDS[i][2], Math.max(b, 1), b == 0 ? 0 : a, false);
            }

            // 다가오는 일정: 앱이 날짜와 이름을 넘겨주고, D-day는 위젯이 오늘 기준으로 다시 계산해요
            JSONArray up = o.optJSONArray("up");
            int shown = 0;
            hideUp(rv);
            if (up != null) {
                for (int i = 0; i < up.length() && shown < 3; i++) {
                    JSONObject e = up.optJSONObject(i);
                    if (e == null) continue;
                    long ds = daysUntil(e.optString("s", "")), de = daysUntil(e.optString("e", e.optString("s", "")));
                    if (ds == Long.MIN_VALUE || de < 0) continue;
                    int[] ids = UP_IDS[shown];
                    rv.setViewVisibility(ids[0], View.VISIBLE);
                    if (ds <= 0) {
                        rv.setTextViewText(ids[1], "오늘");
                        rv.setTextColor(ids[1], todayInk != 0 ? todayInk : ctx.getResources().getColor(R.color.w_danger));
                        rv.setTextViewText(ids[3], de > 0 ? "~" + md(e.optString("e", "")) : "");
                    } else {
                        rv.setTextViewText(ids[1], md(e.optString("s", "")));
                        rv.setTextColor(ids[1], dateInk != 0 ? dateInk : ctx.getResources().getColor(R.color.w_accent));
                        rv.setTextViewText(ids[3], "D-" + ds);
                    }
                    rv.setTextViewText(ids[2], e.optString("t", ""));
                    shown++;
                }
            }
            if (shown == 0) {
                rv.setViewVisibility(R.id.ok_text, View.VISIBLE);
                rv.setTextViewText(R.id.ok_text, "다가오는 일정이 없어요");
            } else rv.setViewVisibility(R.id.ok_text, View.GONE);
        } catch (Exception e) {
            hideUp(rv);
            rv.setViewVisibility(R.id.ok_text, View.VISIBLE);
            rv.setTextViewText(R.id.ok_text, "앱을 한 번 열어 주세요");
        }
        return rv;
    }

    private static void hideUp(RemoteViews rv) {
        for (int[] u : UP_IDS) rv.setViewVisibility(u[0], View.GONE);
    }

    private static String md(String key) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).parse(key);
            return d == null ? "" : new SimpleDateFormat("M/d", Locale.KOREA).format(d);
        } catch (Exception e) { return ""; }
    }

    private static boolean wallpaperIsDark(Context ctx) {
        try {
            if (Build.VERSION.SDK_INT >= 27) {
                WallpaperColors wc = WallpaperManager.getInstance(ctx).getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
                if (wc != null) {
                    if (Build.VERSION.SDK_INT >= 31) {
                        return (wc.getColorHints() & WallpaperColors.HINT_SUPPORTS_DARK_TEXT) == 0;
                    }
                    Color c = wc.getPrimaryColor();
                    return c.luminance() < 0.5f;
                }
            }
        } catch (Exception ignored) { }
        // 배경화면 색을 못 읽으면 다크 모드 여부로 정해요
        int night = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    private static long daysUntil(String key) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
            Date d = f.parse(key);
            if (d == null) return Long.MIN_VALUE;
            Calendar t = Calendar.getInstance();
            t.set(Calendar.HOUR_OF_DAY, 0); t.set(Calendar.MINUTE, 0); t.set(Calendar.SECOND, 0); t.set(Calendar.MILLISECOND, 0);
            Calendar a = Calendar.getInstance();
            a.setTime(d);
            a.set(Calendar.HOUR_OF_DAY, 0); a.set(Calendar.MINUTE, 0); a.set(Calendar.SECOND, 0); a.set(Calendar.MILLISECOND, 0);
            return Math.round((a.getTimeInMillis() - t.getTimeInMillis()) / 86400000.0);
        } catch (Exception e) {
            return Long.MIN_VALUE;
        }
    }

    private static String timeOf(String iso) {
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            in.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            Date d = in.parse(iso.length() >= 19 ? iso.substring(0, 19) : iso);
            if (d == null) return "";
            Calendar now = Calendar.getInstance(), c = Calendar.getInstance();
            c.setTime(d);
            boolean today = now.get(Calendar.YEAR) == c.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR);
            return new SimpleDateFormat(today ? "HH:mm" : "M/d HH:mm", Locale.KOREA).format(d);
        } catch (Exception e) {
            return "";
        }
    }
}
