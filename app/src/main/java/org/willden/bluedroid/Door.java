package org.willden.bluedroid;

import android.os.Bundle;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import cz.msebera.android.httpclient.Header;

public class Door {

    private static final int MAX_HISTORY_DAYS = 10;
    public static final int DEFAULT_MAX_ENTRIES_PER_QUERY = 10;
    public static final int ONE_DAY = 24 * 60 * 60 * 1000;
    private int maxEntriesPerQuery = DEFAULT_MAX_ENTRIES_PER_QUERY;
    private BlueSprayAsyncClient mClient;
    private boolean mLastQuerySuccessful;
    private long mEarliestSearchedDate;
    private long mDoorStateTimestamp;
    private boolean mDoorIsOpen;

    public Door(BlueSprayAsyncClient client) {
        mClient = client;
        mLastQuerySuccessful = false;
    }

    public void restoreHistory(Bundle bundle) {
        if (bundle == null) {
            return;
        }

        mDoorStateTimestamp = bundle.getLong("doorStateTimestamp");
        mDoorIsOpen = bundle.getBoolean("doorIsOpen");
    }

    public void saveHistory(Bundle bundle) {
        if (mLastQuerySuccessful && mDoorStateTimestamp != 0) {
            bundle.putLong("doorStateTimestamp", mDoorStateTimestamp);
            bundle.putBoolean("doorIsOpen", mDoorIsOpen);
        }
    }

    public boolean isDoorStateKnown() {
        return mLastQuerySuccessful && mDoorStateTimestamp != 0;
    }

    public boolean isDoorOpen() {
        return mDoorIsOpen;
    }

    public Date getStateTimestamp() {
        return new Date(mDoorStateTimestamp);
    }

    public void clickDoor(DoorClickCallback callback)
            throws UnsupportedEncodingException, JSONException {
        mClient.sendRequest("door", "set", null /* data */, new DoorClickHandler(callback));
    }

    public interface DoorUpdateCallback {
        public void execute();
    }

    public synchronized void updateStatus(DoorUpdateCallback callback) {
        long searchDate;
        if (mEarliestSearchedDate != 0) {
            searchDate = mEarliestSearchedDate - ONE_DAY;
        } else {
            searchDate = new Date().getTime();
        }
        mEarliestSearchedDate = searchDate;

        try {
            JSONObject data = new JSONObject();
            String searchDateString =
                    new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(searchDate));
            data.put("from", searchDateString);
            data.put("to", searchDateString);
            data.accumulate("limit", 1);
            data.accumulate("limit", maxEntriesPerQuery);
            mClient.sendRequest("events", "get", data, new DoorStatusHandler(callback));
        } catch (JSONException | UnsupportedEncodingException e) {
            Log.e(MainActivity.TAG, "Error sending door request", e);
        }
    }

    private class DoorStatusHandler extends LoggingJsonResponseHandler {
        private final DoorUpdateCallback mCallback;

        public DoorStatusHandler(DoorUpdateCallback callback) {
            mCallback = callback;
        }

        @Override
        public void onSuccess(int responseCode, Header[] headers, JSONObject response) {
            super.onSuccess(responseCode, headers, response);

            try {
                JSONArray resultArray = response.getJSONArray("result");
                int numResults = resultArray.length();
                Log.v(MainActivity.TAG, "Response contains " + numResults + " entries");

                if (numResults == maxEntriesPerQuery) {
                    // Query results were (probably) limited.  Increase the size limit and retry.
                    maxEntriesPerQuery *= 2;
                    mEarliestSearchedDate += ONE_DAY;
                    updateStatus(mCallback);
                    return;
                }

                for (int i = numResults - 1; i >= 0; --i) {
                    JSONObject entry = resultArray.getJSONObject(i);
                    long timestamp = entry.getLong("time") * 1000;
                    if (timestamp < mDoorStateTimestamp) {
                        // No new entries. Bail.
                        mCallback.execute();
                        return;
                    }

                    if (isDoorEntry(entry)) {
                        Log.v(MainActivity.TAG, "Found new entry: " + entry);
                        mDoorStateTimestamp = timestamp;
                        mDoorIsOpen = getDoorState(entry);
                        mLastQuerySuccessful = true;
                        mCallback.execute();
                        return;
                    }
                }

                // Didn't find an entry. Look back further in time?
                if (mEarliestSearchedDate > calculateOldestAllowedDate()) {
                    updateStatus(mCallback);
                } else {
                    mLastQuerySuccessful = false;
                    mCallback.execute();
                }
            } catch (JSONException e) {
                Log.e(MainActivity.TAG, "Error parsing result", e);
            }
        }

        @Override
        public void onFailure(
                int responseCode, Header[] headers, String errorResponse, Throwable exception) {
            super.onFailure(responseCode, headers, errorResponse, exception);
            mLastQuerySuccessful = false;
        }
    }

    private static long calculateOldestAllowedDate() {
        Calendar oldestDate = Calendar.getInstance();
        oldestDate.add(Calendar.DATE, 1 - MAX_HISTORY_DAYS);
        return oldestDate.getTimeInMillis();
    }

    private boolean getDoorState(JSONObject entry) throws JSONException {
        String desc = entry.getString("desc");
        if ("Sensor open".equals(desc)) {
            return true;
        } else if ("Sensor close".equals(desc)) {
            return false;
        } else {
            Log.e(MainActivity.TAG, "Invalid door state " + desc + " found");
            return false;
        }
    }

    private boolean isDoorEntry(JSONObject entry) throws JSONException {
        return "Door sensor".equals(entry.getString("detail"));
    }

    public interface DoorClickCallback {
        public void success();
        public void failure();
    }

    private class DoorClickHandler extends LoggingJsonResponseHandler {
        private DoorClickCallback mCallback;

        public DoorClickHandler(DoorClickCallback callback) {
            mCallback = callback;
        }

        @Override
        public void onSuccess(int responseCode, Header[] headers, JSONObject response) {
            super.onSuccess(responseCode, headers, response);
            mCallback.success();
        }

        @Override
        public void onFailure(
                int responseCode, Header[] headers, String response, Throwable e) {
            super.onFailure(responseCode, headers, response, e);
            mCallback.failure();
        }
    }
}
