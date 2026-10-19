package com.nal2nal2.audit;

import android.util.Xml;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileInputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.xmlpull.v1.XmlPullParser;

/**
 * 웹 화면(WebView)은 폰 글꼴 설정을 따르지 않아서, 폰에 들어 있는 기본 글꼴 파일 위치를 찾아 알려줘요.
 * 영문 기본(sans-serif)과 한글(lang="ko") 글꼴을 굵기별로 돌려줘요.
 */
@CapacitorPlugin(name = "SystemFont")
public class SystemFontPlugin extends Plugin {
    private static final String[] CONFIGS = {
        "/system/etc/font_fallback.xml", "/system/etc/fonts.xml", "/product/etc/fonts_customization.xml"
    };

    @PluginMethod
    public void list(PluginCall call) {
        Map<String, JSObject> out = new LinkedHashMap<>();
        for (String cfg : CONFIGS) {
            File f = new File(cfg);
            if (!f.canRead()) continue;
            try (FileInputStream in = new FileInputStream(f)) {
                XmlPullParser p = Xml.newPullParser();
                p.setInput(in, null);
                String group = null;
                int depth = 0;
                for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
                    if (ev == XmlPullParser.START_TAG && "family".equals(p.getName())) {
                        String name = p.getAttributeValue(null, "name");
                        String lang = p.getAttributeValue(null, "lang");
                        group = "sans-serif".equals(name) ? "latin"
                            : (lang != null && (lang.contains("ko") || lang.contains("Kore"))) ? "ko" : null;
                    } else if (ev == XmlPullParser.END_TAG && "family".equals(p.getName())) {
                        group = null;
                    } else if (ev == XmlPullParser.START_TAG && "font".equals(p.getName()) && group != null) {
                        String style = p.getAttributeValue(null, "style");
                        if (style != null && style.equals("italic")) continue;
                        String w = p.getAttributeValue(null, "weight");
                        String idx = p.getAttributeValue(null, "index");
                        String file = p.nextText().trim();
                        if (file.isEmpty()) continue;
                        File ff = file.startsWith("/") ? new File(file) : new File("/system/fonts/" + file);
                        if (!ff.canRead()) continue;
                        String key = group + "|" + w;
                        if (out.containsKey(key)) continue;
                        JSObject o = new JSObject();
                        o.put("group", group);
                        o.put("weight", w == null ? 400 : Integer.parseInt(w));
                        o.put("path", ff.getAbsolutePath());
                        o.put("index", idx == null ? 0 : Integer.parseInt(idx));
                        out.put(key, o);
                    }
                }
            } catch (Exception ignored) { }
        }
        JSArray arr = new JSArray();
        for (JSObject o : out.values()) arr.put(o);
        JSObject ret = new JSObject();
        ret.put("fonts", arr);
        call.resolve(ret);
    }
}
