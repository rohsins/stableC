package com.example.rohsins.stablec;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.Switch;

import static com.example.rohsins.stablec.WService.serviceAlive;
import static com.example.rohsins.stablec.WService.serviceSwitchValue;

public class MainActivity extends AppCompatActivity {

    Switch serviceSwitch;
    SharedPreferences settings;
    SharedPreferences.Editor editor;
    static volatile boolean newR = false;

    public static volatile boolean notfirstRun = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settings = getSharedPreferences("msettings", 0);
        editor = settings.edit();

        if (!notfirstRun) {
            serviceSwitchValue = settings.getBoolean("serviceSwitchValue", false);
            notfirstRun = true;
            if (!serviceAlive) {
                Intent intent = new Intent(MainActivity.this, WService.class);
                startService(intent);
            }
        }
        serviceSwitch = findViewById(R.id.mainSwitch);
        serviceSwitch.setChecked(serviceSwitchValue);

        serviceSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                serviceSwitchValue = serviceSwitch.isChecked();
                serviceSwitch.setChecked(serviceSwitchValue);
                editor.putBoolean("serviceSwitchValue", serviceSwitchValue);
                newR = true;
                if(serviceSwitchValue && !WService.serviceAlive) {
                    Intent intent = new Intent(MainActivity.this, WService.class);
                    startService(intent);
                }
                else if(!serviceSwitchValue && WService.serviceAlive) {
                    Intent intent = new Intent(MainActivity.this, WService.class);
                    stopService(intent);
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (newR) {
            editor.commit();
            newR = false;
        }
    }
}
