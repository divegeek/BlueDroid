package org.willden.bluedroid;

import android.util.Log;

import com.loopj.android.http.JsonHttpResponseHandler;

import org.json.JSONArray;
import org.json.JSONObject;

import cz.msebera.android.httpclient.Header;

/**
 * Simple JSON response handler that just logs
 */
class LoggingJsonResponseHandler extends JsonHttpResponseHandler {
    @Override
    public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
        Log.v(MainActivity.TAG, "Status: " + statusCode + " Result obj: " + response.toString());
    }

    @Override
    public void onSuccess(int statusCode, Header[] headers, JSONArray response) {
        Log.v(MainActivity.TAG, "Status: " + statusCode + " Result arr: " + response.toString());
    }

    @Override
    public void onSuccess(int statusCode, Header[] headers, String response) {
        Log.v(MainActivity.TAG, "Status: " + statusCode + " Result str: " + response);
    }

    @Override
    public void onFailure(
            int statusCode, Header[] headers, String errorResponse, Throwable throwable) {
        Log.e(MainActivity.TAG, "Status: " + statusCode + " Result str: " + errorResponse,
                throwable);
    }

    @Override
    public void onFailure(
            int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
        Log.e(MainActivity.TAG, "Status: " + statusCode + " Result obj: " + errorResponse,
                throwable);
    }

    @Override
    public void onFailure(
            int statusCode, Header[] headers, Throwable throwable, JSONArray errorResponse) {
        Log.e(MainActivity.TAG, "Status: " + statusCode + " Result obj: " + errorResponse,
                throwable);
    }
}
