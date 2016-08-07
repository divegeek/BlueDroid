package org.willden.bluedroid;

import android.content.Context;
import android.util.Log;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.JsonHttpResponseHandler;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.Random;

import cz.msebera.android.httpclient.entity.StringEntity;

public class BlueSprayAsyncClient {
    public static final String CONTROLLER_IP = "192.168.1.110";

    private final Context mContext;
    private final AsyncHttpClient mClient;
    private final Random mRandom;

    public BlueSprayAsyncClient(Context context) {
        mContext = context;
        mClient = new AsyncHttpClient();
        mRandom = new Random();

        // TODO: Figure out what timeouts make sense here.
        mClient.setTimeout(2000);
        mClient.setConnectTimeout(2000);
        mClient.setResponseTimeout(4000);

        // Don't do retries.
        mClient.setMaxRetriesAndTimeout(0, 4000);
    }

    public void sendRequest(String apiSelector, String action, JSONObject data,
                            JsonHttpResponseHandler handler)
            throws JSONException, UnsupportedEncodingException {
        JSONObject request = new JSONObject();
        request.put("action", action);
        if (data != null) {
            request.put("data", data);
        }
        request.put("id", mRandom.nextInt(Integer.MAX_VALUE));

        Log.v(MainActivity.TAG, "Sending " + apiSelector + " request: " + request.toString());
        mClient.post(mContext, "http://" + CONTROLLER_IP + "/api/" + apiSelector,
                new StringEntity(request.toString()), "application/json", handler);
    }

}
