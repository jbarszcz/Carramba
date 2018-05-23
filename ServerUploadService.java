package com.example.carramba;


import android.content.Context;

import java.io.IOException;

import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

class ServerUploadService {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private OkHttpClient client;
    private String url;
    private Context mContext;
    private String credential;


    ServerUploadService(String serverUrl, Context mContext) {
        client = new OkHttpClient();
        credential = Credentials.basic("user1","password");
        url = serverUrl;
    }

    Response postToServer(String json, String url) throws IOException {

        RequestBody requestBody = RequestBody.create(JSON, json);
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", credential)
                .post(requestBody)
                .build();

        return client.newCall(request).execute();

    }

    public Context getContext(){
        return mContext;
    }
}