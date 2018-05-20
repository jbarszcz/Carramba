package com.example.carramba;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;


public class Utils {

    public static String getCurrentTimeStamp() {
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date now = new Date();
        String strDate = sdfDate.format(now);
        return strDate;
    }

    public static JSONObject createEmptyJsonObject() throws JSONException {

//        JSONObject params = new JSONObject();
//        params.put("datetime","");
//        params.put("tripNumber", "1");
//        params.put("longitude","");
//        params.put("latitude","");
//        params.put("fuelConsumption","");
//
//        JSONObject data = new JSONObject();
//        JSONArray dataArray = new JSONArray();
//        dataArray.put(params);
//
//        data.put("data",dataArray);


        //return data;

        return new JSONObject("{\"data\": [{}]}");
    }
}
