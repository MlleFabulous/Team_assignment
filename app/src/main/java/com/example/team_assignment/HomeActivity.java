package com.example.team_assignment;

import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.util.Locale;

public class HomeActivity extends AppCompatActivity {
    private EditText input_time;
    private TextView textViewCountDownTimer;
    private Button button_set;
    private Button button_start;
    private long timeLeftInMilliseconds;
    private long startTimeInMilliseconds;
    private long stopTime;
    private CountDownTimer countDownTimer;
    private boolean isTimerRunning;
    private ShakeDetection shakeDetection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        shakeDetection = new ShakeDetection(this);

        input_time = findViewById(R.id.input_time);
        button_start = findViewById(R.id.button_start_pause);
        button_set = findViewById(R.id.button_set);
        textViewCountDownTimer = findViewById(R.id.text_view_countdown);

        button_set.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                String input = input_time.getText().toString();
                if (input.length() == 0) {
                    Toast.makeText(HomeActivity.this, "Please enter a number", Toast.LENGTH_SHORT).show();
                    return;
                }

                long millisInput = Long.parseLong(input) * 60000;
                if (millisInput == 0) {
                    Toast.makeText(HomeActivity.this, "Please enter a positive number", Toast.LENGTH_SHORT).show();
                    return;
                }

                setTime(millisInput);
                input_time.setText("");
            }
        });

        button_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTimer();
            }
        });



    }

    private void setTime(long milliseconds) {
        startTimeInMilliseconds = milliseconds;
        resetTimer();
        closeKeyboard();
    }

    private void startTimer() {
        stopTime = System.currentTimeMillis() + timeLeftInMilliseconds;

        countDownTimer = new CountDownTimer(timeLeftInMilliseconds, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftInMilliseconds = millisUntilFinished;
                updateCountDownText();
            }

            @Override
            public void onFinish() {
                isTimerRunning = false;
                updateWatchInterface();
            }
        }.start();

        isTimerRunning = true;
        updateWatchInterface();
    }

    private void pauseTimer() {
        countDownTimer.cancel();
        isTimerRunning = false;
        updateWatchInterface();
    }

    public void resetTimer() {
        timeLeftInMilliseconds = startTimeInMilliseconds;
        updateCountDownText();
        updateWatchInterface();
    }

    public void stopTimer(){
        countDownTimer.cancel();
        isTimerRunning = false;
        textViewCountDownTimer.setText("00:00");
        updateWatchInterface();
    }

    private void updateCountDownText() {
        //from milliseconds to second, minutes and hours
        int hours = (int) (timeLeftInMilliseconds / 1000) / 3600;
        int minutes = (int) ((timeLeftInMilliseconds / 1000) % 3600) / 60;
        int seconds = (int) (timeLeftInMilliseconds / 1000) % 60;


        //Convert to String
        String timeLeftFormatted;
        if (hours > 0) {
            timeLeftFormatted = String.format(Locale.getDefault(),
                    "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            timeLeftFormatted = String.format(Locale.getDefault(),
                    "%02d:%02d", minutes, seconds);
        }

        textViewCountDownTimer.setText(timeLeftFormatted);
    }

    private void updateWatchInterface() {
        if (isTimerRunning) {
            input_time.setVisibility(View.INVISIBLE);
            button_set.setVisibility(View.INVISIBLE);
            button_start.setVisibility(View.INVISIBLE);
        } else {
            input_time.setVisibility(View.VISIBLE);
            button_set.setVisibility(View.VISIBLE);
            button_start.setText("Start");

            if (timeLeftInMilliseconds < 1000) {
                button_start.setVisibility(View.INVISIBLE);
            } else {
                button_start.setVisibility(View.VISIBLE);
            }
        }
    }

    private void closeKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putLong("startTimeInMillis", startTimeInMilliseconds);
        editor.putLong("millisLeft", timeLeftInMilliseconds);
        editor.putBoolean("timerRunning", isTimerRunning);
        editor.putLong("endTime", stopTime);

        editor.apply();

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);

        startTimeInMilliseconds = prefs.getLong("startTimeInMillis", 600000);
        timeLeftInMilliseconds = prefs.getLong("millisLeft", startTimeInMilliseconds);
        isTimerRunning = prefs.getBoolean("timerRunning", false);

        updateCountDownText();
        updateWatchInterface();

        if (isTimerRunning) {
            stopTime = prefs.getLong("endTime", 0);
            timeLeftInMilliseconds = stopTime - System.currentTimeMillis();

            if (timeLeftInMilliseconds < 0) {
                timeLeftInMilliseconds = 0;
                isTimerRunning = false;
                updateCountDownText();
                updateWatchInterface();
            } else {
                startTimer();
            }
        }
    }


    protected void onResume() {
        super.onResume();
        shakeDetection.registerListener();
    }

    protected void onPause() {
        super.onPause();
        shakeDetection.unregisterListener();
    }


    private class ShakeDetection implements SensorEventListener {

        private SensorManager sensorManager;
        private Sensor accelerometer;
        private float x,y,z,last_x,last_y,last_z;
        private boolean isFirstValue;
        private float shakeThreshold = 10f;
        private HomeActivity homeActivity;

        public ShakeDetection(HomeActivity homeActivity){
            this.homeActivity=homeActivity;

            sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);


            registerListener();
        }


        @Override
        public void onSensorChanged(SensorEvent event) {
            x = event.values[0];
            y = event.values[1];
            z = event.values[2];
            if(isFirstValue) {
                float deltaX = Math.abs(last_x - x);
                float deltaY = Math.abs(last_y - y);
                float deltaZ = Math.abs(last_z - z);
                // If the values of acceleration have changed on at least two axes, then we assume that we are in a shake motion
                if((deltaX > shakeThreshold && deltaY > shakeThreshold)
                        || (deltaX > shakeThreshold && deltaZ > shakeThreshold)
                        || (deltaY > shakeThreshold && deltaZ > shakeThreshold)) {

                    homeActivity.stopTimer();
                }
            }
            last_x = x;
            last_y = y;
            last_z = z;
            isFirstValue = true;
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {

        }

        public void unregisterListener(){
            sensorManager.unregisterListener(this, accelerometer);
            Toast.makeText(getApplicationContext(), "The listener is unregistered", Toast.LENGTH_LONG).show();
        }

        public void registerListener(){
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
            Toast.makeText(getApplicationContext(), "The listener is registered", Toast.LENGTH_LONG).show();
        }
    }

}








