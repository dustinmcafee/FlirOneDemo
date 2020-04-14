package com.elotouch.flirone;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;

import java.util.Objects;

public class CalibrateActivity extends AppCompatActivity {
    private EditText atmosphericTemperature;
    private EditText reflectiveTemperature;
    private  EditText externalOpticsTemperature;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.DarkTheme);
        setContentView(R.layout.activity_calibrate);

        atmosphericTemperature = findViewById(R.id.atmospheric_temperature_value);
        reflectiveTemperature = findViewById(R.id.reflective_temperature_value);
        externalOpticsTemperature = findViewById(R.id.external_optics_temperature_value);
        atmosphericTemperature.setText(String.valueOf(CalibrationHandler.kToC(CalibrationHandler.atmosphericTemperature)), TextView.BufferType.EDITABLE);
        reflectiveTemperature.setText(String.valueOf(CalibrationHandler.kToC(CalibrationHandler.reflectiveTemperature)), TextView.BufferType.EDITABLE);
        externalOpticsTemperature.setText(String.valueOf(CalibrationHandler.kToC(CalibrationHandler.externalOpticsTemperature)), TextView.BufferType.EDITABLE);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return super.onSupportNavigateUp();
    }

    // TODO: implement
    public void saveAll(MenuItem item) {
        CalibrationHandler.setAtmosphericTemperature(Double.parseDouble(atmosphericTemperature.getText().toString()));
        CalibrationHandler.setReflectiveTemperature(Double.parseDouble(reflectiveTemperature.getText().toString()));
        CalibrationHandler.setExternalOpticsTemperature(Double.parseDouble(externalOpticsTemperature.getText().toString()));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar2, menu);
        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(false);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_toolbar_back);

        return super.onCreateOptionsMenu(menu);
    }
}
