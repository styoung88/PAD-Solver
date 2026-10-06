package com.example.phoneapp.capture;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

// Runs in the test APK's own process, without the target APK or its Kotlin runtime.
public class PhotoPermissionProbeActivity extends Activity {
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        status = new TextView(this);
        status.setText("Permission probe ready");
        Button request = new Button(this);
        request.setAllCaps(false);
        request.setText("Request photo access");
        request.setOnClickListener(view -> {
            status.setText("Request pending");
            requestPermissions(getIntent().getStringArrayExtra("permissions"), 1);
        });
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(request);
        content.addView(status);
        setContentView(content);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        status.setText("Request completed");
    }
}
