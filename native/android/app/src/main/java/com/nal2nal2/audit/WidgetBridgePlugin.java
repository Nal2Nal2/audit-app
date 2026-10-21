package com.nal2nal2.audit;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import com.getcapacitor.JSObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "WidgetBridge")
public class WidgetBridgePlugin extends Plugin {
    @PluginMethod
    public void update(PluginCall call) {
        String data = call.getString("data");
        if (data == null) {
            call.reject("data가 없어요");
            return;
        }
        Context ctx = getContext();
        ctx.getSharedPreferences(AuditWidget.PREFS, Context.MODE_PRIVATE)
            .edit().putString(AuditWidget.KEY, data).apply();
        AuditWidget.refreshAll(ctx);
        call.resolve();
    }

    /** 우체국 배달조회 같은 바깥 링크를 폰 브라우저로 열어요 */
    @PluginMethod
    public void openUrl(PluginCall call) {
        String url = call.getString("url");
        if (url == null || !url.startsWith("https://")) { call.reject("주소가 이상해요"); return; }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) { call.reject("브라우저를 열지 못했어요"); }
    }

    /** 등기번호로 우체국 배달조회 화면을 받아와요(웹에서는 보안 때문에 못 하는 걸 폰이 대신해요). 내용 정리는 웹 화면이 해요. */
    @PluginMethod
    public void trace(PluginCall call) {
        String sid = call.getString("sid", "").replaceAll("[^0-9]", "");
        if (sid.length() != 13) { call.reject("등기번호는 13자리예요"); return; }
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("https://service.epost.go.kr/trace.RetrieveDomRigiTraceList.comm").openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome Mobile Safari/537.36");
            c.setRequestProperty("Referer", "https://service.epost.go.kr/trace.RetrieveRegiPrclDeliv.postal?sid1=" + sid);
            try (OutputStream os = c.getOutputStream()) { os.write(("sid1=" + sid).getBytes("UTF-8")); }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while (in != null && (n = in.read(buf)) > 0) bo.write(buf, 0, n);
            byte[] body = bo.toByteArray();
            String cs = "UTF-8";
            String ct = c.getContentType();
            if (ct != null && ct.toLowerCase().contains("charset=")) cs = ct.substring(ct.toLowerCase().indexOf("charset=") + 8).replaceAll("[\"; ].*", "");
            String html = new String(body, Charset.forName(cs));
            if ((ct == null || !ct.toLowerCase().contains("charset=")) && html.toLowerCase().contains("euc-kr")) html = new String(body, Charset.forName("EUC-KR"));
            JSObject ret = new JSObject();
            ret.put("status", code);
            ret.put("html", html);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("우체국에 연결하지 못했어요");
        } finally { if (c != null) c.disconnect(); }
    }
}
