package com.example.flirone;

import com.flir.thermalsdk.androidsdk.ThermalSdkAndroid;
import com.flir.thermalsdk.image.DistanceUnit;
import com.flir.thermalsdk.image.ThermalImage;
import com.flir.thermalsdk.image.ThermalValue;
import com.flir.thermalsdk.image.palettes.Palette;
import com.flir.thermalsdk.image.palettes.PaletteManager;

import java.util.Arrays;
import java.util.List;

/**
 * There are several parameters to set. The most important are reflected temperature and emissivity. They define, how much heat object emits. The reflected temperature should be measured first, then emissivity.
 * Other parameters can be measured afterwards. They do not have such big impact on the temperature's value, but allow fine adjustments.
 *
 * Reflected temperature is a parameter used to compensate for the radiation reflected in the object. If the emissivity is low and the object's temperature relatively far from that of the reflected, it will be important to set and compensate for the reflected apparent temperature correctly.
 * Emissivity is a value between 0 and 1 that specifies how much radiation an object emits, compared to the radiation of a theoretical reference object of the same temperature (called a 'black body').
 * Atmospheric temperature is a temperature of an atmosphere surrounding the camera. 'The atmosphere' is the medium between the object being measured and the camera, normally air.
 * Distance is a numerical description of how far apart objects are.
 * External optics temperature is a temperature of an external accessory attached to the Thermal camera. It is used to compensate for optical accessory, such as a heat shield or a macro lenses. External optics will absorb some of the radiation.
 * External optics transmission defines the amplitude, intensity, or total power of a transmitted wave relative to an incident wave. Used to compensate for optical accessory, such as a heat shield or a macro lenses. External optics will absorb some of the radiation.
 * Reference temperature is a temperature, which the ordinary measured values can be compared with.
 * Relative humidity is a term used to describe the amount of water vapor that exists in a gaseous mixture of air and water.
 * Transmission is the amplitude, intensity, or total power of a transmitted wave relative to an incident wave. Defines how well the atmosphere transmits the heat.
 *
 * All Temperature is in Kelvin
 */
public class CalibrationHandler {
    static double atmosphericTemperature = -1;
    static double distance = -1;
    static double emissivity = -1;
    static double externalOpticsTemperature = -1;
    static double externalOpticsTransmission = -1;
    static double reflectiveTemperature = -1;
    static double relativeHumidity = -1;
    static double transmission = -1;
    static DistanceUnit distanceUnit = DistanceUnit.METER;
    static Palette palette = null;
    private static final String[] palettes = {"iron", "Arctic", "blackhot", "bw", "Coldest", "ColorWheel_Redhot", "ColorWheel6", "ColorWheel12", "DoubleRainbow2", "lava", "rainbow", "rainHC", "whitehot", "Hottest"};
    public static boolean calibrationButtonHidden = true;
    public CalibrationHandler(){}

    static void calibrate(ThermalImage img){
        setDefaults(img);
        img.getImageParameters().setAtmosphericTemperature(atmosphericTemperature);
        img.getImageParameters().setDistance(distance);
        img.getImageParameters().setEmissivity(emissivity);
        img.getImageParameters().setExternalOpticsTemperature(externalOpticsTemperature);
        img.getImageParameters().setExternalOpticsTransmission(externalOpticsTransmission);
        img.getImageParameters().setReflectedTemperature(reflectiveTemperature);
        img.getImageParameters().setRelativeHumidity(relativeHumidity);
        img.getImageParameters().setTransmission(transmission);
        img.setDistanceUnit(distanceUnit);

        List<String> arr = Arrays.asList(palettes);
        if(arr.contains(palette.name)){
            img.setPalette(PaletteManager.getDefaultPalettes().get(arr.indexOf(palette.name)));
        }
    }

    /**
     * Set the Atmospheric temperature for the given ThermalImage
     * @param temp Temperature in
     */
    static void setAtmosphericTemperature(double temp){
        atmosphericTemperature = cToK(temp);
    }
    static void setDistance(double dist){
        distance = dist;
    }
    static void setEmissivity(double emiss){
        emissivity = emiss;
    }
    static void setExternalOpticsTemperature(double temp){
        externalOpticsTemperature = cToK(temp);
    }
    static void setExternalOpticsTransmission(double transmission){
        externalOpticsTransmission = transmission;
    }
    static void setReflectiveTemperature(double temp){
        reflectiveTemperature = cToK(temp);
    }
    static void setRelativeHumidity(double humidity){
        relativeHumidity = humidity;
    }
    static void setTransmission(double trans){
        transmission = trans;
    }
    static void setDistanceUnit(DistanceUnit unit){
        distanceUnit = unit;
    }
    private static void setDefaults(ThermalImage img){
        if(palette == null){
            palette = PaletteManager.getDefaultPalettes().get(0);
            img.setPalette(palette);
        }
        if(atmosphericTemperature == -1){
            atmosphericTemperature = img.getImageParameters().getAtmosphericTemperature();
        }
        if(distance == -1){
            distance = img.getImageParameters().getDistance();
        }
        if(emissivity == -1){
            emissivity = img.getImageParameters().getEmissivity();
        }
        if(externalOpticsTemperature == -1){
            externalOpticsTemperature = img.getImageParameters().getExternalOpticsTemperature();
        }
        if(externalOpticsTransmission == -1){
            externalOpticsTransmission = img.getImageParameters().getExternalOpticsTransmission();
        }
        if(reflectiveTemperature == -1){
            reflectiveTemperature = img.getImageParameters().getReflectedTemperature();
        }
        if(relativeHumidity == -1){
            relativeHumidity = img.getImageParameters().getRelativeHumidity();
        }
        if(transmission == -1){
            transmission = img.getImageParameters().getTransmission();
        }
        if(calibrationButtonHidden){
            FlirCameraActivity.getInstance().toggleCalibrationButton();
        }
    }

    static void setPalette(String name){
        List<String> arr = Arrays.asList(palettes);
        if(arr.contains(name)){
            palette = PaletteManager.getDefaultPalettes().get(arr.indexOf(name));
        }
    }

    private static double kToF(double k){
        return ((k - 273.15) * 9/5) + 32;
    }
    private static double fToK(double f){
        return ((f - 32) * 5/9) + 273.15;
    }
    static double kToC(double k){
        return k - 273.15;
    }
    private static double cToK(double c){
        return c + 273.15;
    }
    private static double cToF(double c){
        return (c * 9/5) + 32;
    }
    private static double fToC(double f){
        return (f - 32) * 5/9;
    }
}
