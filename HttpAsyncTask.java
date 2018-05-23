package com.example.carramba;

import android.content.Intent;
import android.os.AsyncTask;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.IOException;

import okhttp3.Response;

//<Params, Progress, Result>
public class HttpAsyncTask extends AsyncTask<String, Void, String> {
    private ServerUploadService uploadService;
    private String jsonData;
    private String url;
    public static final String TAG = "CARRAMBA: HttpAsync ";

    HttpAsyncTask(ServerUploadService uploadService, String jsonData, String url) {
        this.uploadService = uploadService;
        this.jsonData = jsonData;
        this.url = url;
    }

    @Override
    protected String doInBackground(String... params) {
        try {
            Log.d(TAG, "doInBackground: POSTING http...");
            Response response = uploadService.postToServer(jsonData, url);
            Log.d(TAG, "doInBackground: HTTP " + response.toString());
            return Integer.toString(response.code());

        } catch (IOException e) {
            Log.d(TAG, "doInBackground: ERROR");
            e.printStackTrace();
            sendErrorBroadcast(e.getMessage());
            return "Error";
        }

    }

    @Override
    protected void onPostExecute(String s){
        sendInfoBroadcast("Server response: " + s);
    }

    public void sendErrorBroadcast(String errorMessage) {
        Intent errorIntent = new Intent("error");
        errorIntent.putExtra("error", errorMessage);
        LocalBroadcastManager.getInstance(uploadService.getContext()).sendBroadcast(errorIntent);

    }

    public void sendInfoBroadcast(String info) {
        Intent infoIntent = new Intent("info");
        infoIntent.putExtra("info", info);
        LocalBroadcastManager.getInstance(uploadService.getContext()).sendBroadcast(infoIntent);

    }
}
