package edu.nmsu.problem;

/**
 * Created by nandofioretto on 11/2/16.
 */
public class Parameters {

    private static int horizon;// = 12;
    private static String deviceDictionaryPath = "resources/DeviceDictionary.json";
    private static double[] priceSchema;
    private static int granularity = 60; // <--- this is in minutes

    //hr [ 0,  8) --- 0.198
    //hr [ 8, 12) --- 0.225
    //hr [12, 14) --- 0.249
    //hr [14, 18) --- 0.849
    //hr [18, 22) --- 0.225
    //hr [22,  0) --- 0.198
    private static double[] std_rate = {0.198, 0.198, 0.198, 0.198, 0.225, 0.225, 0.249, 0.849, 0.849, 0.225, 0.225, 0.198};


    public static void setHorizon(int horizon) {
        Parameters.horizon = horizon;
        Parameters.priceSchema = new double[horizon];
        for(int i = 0; i < horizon; i++) {
            double index = i*12.0/horizon;
            priceSchema[i] = Parameters.std_rate[(int) index];
        }
    }

    public static void setGranularity(int gran) {
        Parameters.granularity = gran;
    }

    //public static void setPriceSchema(double[] priceSchema) { Parameters.priceSchema =  priceSchema;}


    public static int getHorizon() {
        return horizon;
    }
    public static double[] getPriceSchema() {
        return priceSchema;
    }
    public static int getGran() {
        return granularity;
    }
    public static String getDeviceDictionaryPath() {
        return deviceDictionaryPath;
    }

}
