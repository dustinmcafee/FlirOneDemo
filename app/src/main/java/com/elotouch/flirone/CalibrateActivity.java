package com.elotouch.flirone;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.flir.thermalsdk.image.DistanceUnit;

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
}
