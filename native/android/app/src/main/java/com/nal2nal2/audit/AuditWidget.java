package com.nal2nal2.audit;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
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

/** 홈 화면 위젯: 앱이 저장해 둔 요약으로 진행률과 Sign-off 급한 회사를 보여줘요. */
public class AuditWidget extends AppWidgetProvider {
    static final String PREFS = "audit_widget";
    static final String KEY = "summary";
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
        if (raw == null) {
            rv.setTextViewText(R.id.title, "기말감사");
            rv.setTextViewText(R.id.sub, "");
            rv.setViewVisibility(R.id.cells, View.GONE);
            rv.setViewVisibility(R.id.alert_row, View.GONE);
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

            // 주총 10일 이내(지난 지 2주 이내 포함)인데 Sign-off 전인 회사
            JSONArray pend = o.optJSONArray("pending");
            List<long[]> list = new ArrayList<>();
            List<String> names = new ArrayList<>();
            if (pend != null) {
                for (int i = 0; i < pend.length(); i++) {
                    JSONObject p = pend.optJSONObject(i);
                    if (p == null) continue;
                    long d = daysUntil(p.optString("agm", ""));
                    if (d == Long.MIN_VALUE || d > 10 || d < -14) continue;
                    list.add(new long[]{d, names.size()});
                    names.add(p.optString("n", ""));
                }
            }
            if (list.isEmpty()) {
                rv.setViewVisibility(R.id.alert_row, View.GONE);
                rv.setViewVisibility(R.id.ok_text, View.VISIBLE);
                rv.setTextViewText(R.id.ok_text, "주총 10일 안에 Sign-off 급한 회사 없음");
            } else {
                Collections.sort(list, (x, y) -> Long.compare(x[0], y[0]));
                SpannableStringBuilder sb = new SpannableStringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(" · ");
                    long d = list.get(i)[0];
                    sb.append(names.get((int) list.get(i)[1])).append(" ");
                    sb.append(d == 0 ? "D-day" : d > 0 ? "D-" + d : "지남");
                }
                sb.setSpan(new StyleSpan(Typeface.BOLD), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                rv.setViewVisibility(R.id.ok_text, View.GONE);
                rv.setViewVisibility(R.id.alert_row, View.VISIBLE);
                rv.setTextViewText(R.id.alert_names, sb);
            }
        } catch (Exception e) {
            rv.setViewVisibility(R.id.alert_row, View.GONE);
            rv.setViewVisibility(R.id.ok_text, View.VISIBLE);
            rv.setTextViewText(R.id.ok_text, "앱을 한 번 열어 주세요");
        }
        return rv;
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
