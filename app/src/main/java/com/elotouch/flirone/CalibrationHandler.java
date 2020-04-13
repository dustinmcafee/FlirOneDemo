package com.elotouch.flirone;

import com.flir.thermalsdk.image.DistanceUnit;
import com.flir.thermalsdk.image.ThermalImage;
import com.flir.thermalsdk.image.palettes.PaletteManager;

import java.util.Arrays;
import java.util.List;

public class CalibrationHandler {
    public static final String[] palettes = {"iron", "Arctic", "blackhot", "bw", "Coldest", "ColorWheel_Redhot", "ColorWheel6", "ColorWheel12", "DoubleRainbow2", "lava", "rainbow", "rainHC", "whitehot", "Hottest"};
    public CalibrationHandler(){}
    public static void setAtmosphericTemperature(ThermalImage img, double temp){
        img.getImageParameters().setAtmosphericTemperature(temp);
    }
    public static void setDistance(ThermalImage img, double distance){
        img.getImageParameters().setDistance(distance);
    }
    public static void setEmissivity(ThermalImage img, double emissivity){
        img.getImageParameters().setEmissivity(emissivity);
    }
    public static void setExternalOpticsTemperature(ThermalImage img, double temp){
        img.getImageParameters().setExternalOpticsTemperature(temp);
    }
    public static void setExternalOpticsTransmission(ThermalImage img, double transmission){
        img.getImageParameters().setExternalOpticsTransmission(transmission);
    }
    public static void setReflectiveTemperature (ThermalImage img, double temp){
        img.getImageParameters().setReflectedTemperature(temp);
    }
    public static void setRelativeHumidity(ThermalImage img, double humidity){
        img.getImageParameters().setRelativeHumidity(humidity);
    }
    public static void setTransmission(ThermalImage img, double transmission){
        img.getImageParameters().setTransmission(transmission);
    }
    public static void setDistanceUnit (ThermalImage img, DistanceUnit unit){
        img.setDistanceUnit(unit);
    }
    public static void setPalette (ThermalImage img, int i){
        img.setPalette(PaletteManager.getDefaultPalettes().get(i));
    }
    public static void setPalette (ThermalImage img, String name){
        List<String> arr = Arrays.asList(palettes);
        if(arr.contains(name)){
            img.setPalette(PaletteManager.getDefaultPalettes().get(arr.indexOf(name)));
        }
    }
}
