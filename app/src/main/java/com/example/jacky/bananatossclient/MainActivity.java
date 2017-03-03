package com.example.jacky.bananatossclient;

import android.Manifest;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity implements BleManager.BleManagerListener{

    private static final String TAG = "BananaToss";
    private static final int PERMISSION_REQUEST_FINE_LOCATION = 1;

    private BleManager mBleManager = null;
    private int mCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBleManager = BleManager.getInstance(this);
        mBleManager.setListener(this);

        // Request Bluetooth scanning permissions
        requestPermissions(
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                PERMISSION_REQUEST_FINE_LOCATION);

        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mBleManager.startScan();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBleManager.stopScan();
    }

    public void performReset(View v)
    {
        mCount = 0;
        updateUI();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void updateUI() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView counterText = (TextView) findViewById(R.id.counter);
                counterText.setTextSize(300);
                counterText.setText(String.format("%d", mCount));
            }
        });
    }

    @Override
    public void onEvent(int event) {
        switch(event) {
            case BleManager.BLE_EVENT_RESET:
                mCount = 0;
                break;
            case BleManager.BLE_EVENT_COUNT_UP:
                mCount++;
                break;
            default:
                Log.d(TAG, "unknown event: " + event);
        }
        updateUI();
    }
}
