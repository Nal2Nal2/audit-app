package com.nal2nal2.audit;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // 웹 화면이 위젯에 요약을 넘겨줄 수 있게 연결해요
        registerPlugin(WidgetBridgePlugin.class);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onPause() {
        super.onPause();
        AuditWidget.refreshAll(this);
    }
}
