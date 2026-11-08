package com.duong.keyboard;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad, pad, pad);

        TextView guide = new TextView(this);
        guide.setTextSize(16);
        guide.setText("Bước 1: Bật DuongKB trong cài đặt bàn phím.\n"
                + "Bước 2: Chọn DuongKB làm bàn phím đang dùng.\n"
                + "Bước 3: Gõ thử ở ô bên dưới.\n\n"
                + "Mẹo: giữ phím cách để đổi bàn phím, chạm đúp ⇧ để bật Caps Lock.");
        layout.addView(guide);

        Button enable = new Button(this);
        enable.setText("1. Bật bàn phím");
        enable.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            }
        });
        layout.addView(enable);

        Button pick = new Button(this);
        pick.setText("2. Chọn bàn phím");
        pick.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showInputMethodPicker();
            }
        });
        layout.addView(pick);

        EditText test = new EditText(this);
        test.setHint("3. Gõ thử ở đây...");
        layout.addView(test);

        setContentView(layout);
    }
}
