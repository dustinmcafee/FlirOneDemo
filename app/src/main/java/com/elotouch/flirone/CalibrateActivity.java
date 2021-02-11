package com.example.flirone;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.flir.thermalsdk.image.DistanceUnit;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

public class CalibrateActivity extends AppCompatActivity {
    private EditText atmosphericTemperature;
    private EditText reflectiveTemperature;
    private EditText externalOpticsTemperature;
    private Spinner distanceUnit;
    private Spinner palette;
    private EditText distance;
    private EditText emissivity;
    private EditText externalOpticsTransmission;
    private EditText relativeHumidity;
    private EditText transmission;
    private InputMethodManager imm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibrate);

        atmosphericTemperature = findViewById(R.id.atmospheric_temperature_value);
        reflectiveTemperature = findViewById(R.id.reflective_temperature_value);
        externalOpticsTemperature = findViewById(R.id.external_optics_temperature_value);
        distanceUnit = findViewById(R.id.distance_unit_value);
        palette = findViewById(R.id.palette_value);
        distance = findViewById(R.id.distance_value);
        emissivity = findViewById(R.id.emissivity_value);
        externalOpticsTransmission = findViewById(R.id.external_optics_transmission_value);
        relativeHumidity = findViewById(R.id.relative_humidity_value);
        transmission = findViewById(R.id.transmission_value);

        atmosphericTemperature.setText(String.valueOf(CalibrationHandler.kToC(CalibrationHandler.atmosphericTemperature)), TextView.BufferType.EDITABLE);
        reflectiveTemperature.setText(String.valueOf(CalibrationHandler.kToC(CalibrationHandler.reflectiveTemperature)), TextView.BufferType.EDITABLE);
        externalOpticsTemperature.setText(String.valueOf(CalibrationHandler.kToC(CalibrationHandler.externalOpticsTemperature)), TextView.BufferType.EDITABLE);
        distanceUnit.setSelection(getIndex(distanceUnit, CalibrationHandler.distanceUnit.name()));
        if(CalibrationHandler.palette != null){
            palette.setSelection(getIndex(palette, CalibrationHandler.palette.name));
        }
        distance.setText(String.valueOf(CalibrationHandler.distance), TextView.BufferType.EDITABLE);
        emissivity.setText(String.valueOf(CalibrationHandler.emissivity), TextView.BufferType.EDITABLE);
        externalOpticsTransmission.setText(String.valueOf(CalibrationHandler.externalOpticsTransmission), TextView.BufferType.EDITABLE);
        relativeHumidity.setText(String.valueOf(CalibrationHandler.relativeHumidity), TextView.BufferType.EDITABLE);
        transmission.setText(String.valueOf(CalibrationHandler.transmission), TextView.BufferType.EDITABLE);

        imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
    }

    private int getIndex(Spinner spinner, String myString){
        for (int i = 0; i < spinner.getCount(); i++){
            if (spinner.getItemAtPosition(i).toString().equalsIgnoreCase(myString)){
                return i;
            }
        }
        return 0;
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return super.onSupportNavigateUp();
    }

    public void saveAll(MenuItem item) {
        CalibrationHandler.setAtmosphericTemperature(Double.parseDouble(atmosphericTemperature.getText().toString()));
        CalibrationHandler.setReflectiveTemperature(Double.parseDouble(reflectiveTemperature.getText().toString()));
        CalibrationHandler.setExternalOpticsTemperature(Double.parseDouble(externalOpticsTemperature.getText().toString()));
        CalibrationHandler.setDistanceUnit(DistanceUnit.valueOf(distanceUnit.getItemAtPosition(distanceUnit.getSelectedItemPosition()).toString()));
        CalibrationHandler.setPalette(palette.getItemAtPosition(palette.getSelectedItemPosition()).toString());
        CalibrationHandler.setDistance(Double.parseDouble(distance.getText().toString()));
        CalibrationHandler.setEmissivity(Double.parseDouble(emissivity.getText().toString()));
        CalibrationHandler.setExternalOpticsTransmission(Double.parseDouble(externalOpticsTransmission.getText().toString()));
        CalibrationHandler.setRelativeHumidity(Double.parseDouble(relativeHumidity.getText().toString()));
        CalibrationHandler.setTransmission(Double.parseDouble(transmission.getText().toString()));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar2, menu);
        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(false);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_toolbar_back);

        return super.onCreateOptionsMenu(menu);
    }

    public void toggleKeyboard(MenuItem item) {
        if (imm != null) {
            imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, InputMethodManager.HIDE_IMPLICIT_ONLY);
        }
    }

    public void viewLog(View v) {
        if (CameraHandler.tempLog != null) {
            StringBuilder msgDialog = new StringBuilder();

            if (CameraHandler.tempLog.size() != 0) {
                for (Map.Entry<Long, String> entry : CameraHandler.tempLog.entrySet()) {
                    Date date = new Date(entry.getKey());
                    msgDialog.append(date.toString()).append(":\n==>").append(entry.getValue()).append("\n");
                }
            } else {
                msgDialog.append("There are no logs recorded.");
            }

            String title = CameraHandler.tempLog.size() + " Readings:";
            new AlertDialog.Builder(getWindow().getContext()).setTitle(title).setMessage(msgDialog.toString()).setPositiveButton("Close", null).setNegativeButton("Reset", (dialog, which) -> {
                CameraHandler.resetLog();
                viewLog(v);
            }).setNeutralButton("Save", (dialog, which) -> {
                CameraHandler.saveLog(this,false);
            }).show();
        }
    }

}
