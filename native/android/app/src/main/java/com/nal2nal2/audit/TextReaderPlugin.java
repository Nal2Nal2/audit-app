package com.nal2nal2.audit;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.util.Base64;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;

/** 등기 영수증 사진의 글자를 폰 안에서(인터넷 없이) 읽어요. 줄마다 위치도 같이 돌려줘요. */
@CapacitorPlugin(name = "TextReader")
public class TextReaderPlugin extends Plugin {
    private TextRecognizer recognizer;

    private TextRecognizer get() {
        if (recognizer == null) recognizer = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
        return recognizer;
    }

    @PluginMethod
    public void read(PluginCall call) {
        String b64 = call.getString("base64");
        if (b64 == null) { call.reject("사진이 없어요"); return; }
        Bitmap bmp;
        int sample = 1;
        try {
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
            while (o.outWidth / sample > 2000) sample *= 2; // 아주 큰 사진은 줄여서 읽어요
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
        } catch (Exception e) { call.reject("사진을 열지 못했어요"); return; }
        if (bmp == null) { call.reject("사진을 열지 못했어요"); return; }
        final int sampleF = sample;
        get().process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener(t -> {
                JSArray lines = new JSArray();
                for (Text.TextBlock b : t.getTextBlocks()) {
                    for (Text.Line l : b.getLines()) {
                        Rect r = l.getBoundingBox();
                        JSObject o = new JSObject();
                        o.put("text", l.getText());
                        o.put("top", r == null ? 0 : r.top * sampleF);
                        o.put("height", r == null ? 0 : r.height() * sampleF);
                        o.put("left", r == null ? 0 : r.left * sampleF);
                        o.put("width", r == null ? 0 : r.width() * sampleF);
                        lines.put(o);
                    }
                }
                JSObject ret = new JSObject();
                ret.put("text", t.getText());
                ret.put("lines", lines);
                call.resolve(ret);
            })
            .addOnFailureListener(e -> call.reject("글자를 읽지 못했어요"));
    }
}
