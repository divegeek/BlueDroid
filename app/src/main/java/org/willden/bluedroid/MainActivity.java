package org.willden.bluedroid;

import android.os.AsyncTask;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.JsonWriter;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;

public class MainActivity extends AppCompatActivity {
    private final static int DOOR_POLL_INTERVAL = 10 * 1000;
    public static final String CONTROLLER_IP = "192.168.1.110";
    public static final String TAG = "bluespray";

    private boolean mDoorOpen;
    private String mDoorErrorMessage = null;
    private Date mDoorStateSince = null;
    private Handler mHandler;
    private Random mRandom = new Random();
    private Runnable mDoorStatusChecker = new Runnable() {

        @Override
        public void run() {
            new DoorStatusUpdater(
                    (TextView)findViewById(R.id.door_status),
                    (Button)findViewById(R.id.garage_door_button))
                    .execute();
            mHandler.postDelayed(mDoorStatusChecker, DOOR_POLL_INTERVAL);
        }
    };

    private static class OpenOrCloseDoor extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            try {
                long startTime = System.nanoTime();

                HttpURLConnection connection = openApiConnection("door");
                try (DataOutputStream outputStream = new DataOutputStream(
                        connection.getOutputStream())) {
                    outputStream.write("{\"action\":\"set\"}".getBytes());
                    outputStream.flush();
                }
                long endTime = System.nanoTime();

                Log.i(TAG, "Got response code: " + connection.getResponseCode() + " in "
                        + (endTime - startTime) / 1000000.0 + " ms");

                try (BufferedReader rd = new BufferedReader(
                        new InputStreamReader((connection.getInputStream())))) {
                    String line;
                    while ((line = rd.readLine()) != null) {
                        Log.w(TAG, line);
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
            return null;
        }
    }

    @NonNull
    private static HttpURLConnection openApiConnection(String apiSelector) throws IOException {
        URL url = new URL("http://" + CONTROLLER_IP + "/api/" + apiSelector);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type","application/json");
        connection.connect();
        return connection;
    }

    private class DoorStatusUpdater extends AsyncTask<Void, Void, Void> {
        private TextView mDoorStatus;
        private Button mDoorButton;

        public DoorStatusUpdater(TextView doorStatus, Button doorButton) {
            mDoorStatus = doorStatus;
            mDoorButton = doorButton;
        }

        @Override
        protected void onPostExecute(Void v) {
            if (mDoorStateSince == null) {
                if (mDoorErrorMessage == null) {
                    mDoorStatus.setText(R.string.unknown_door_state);
                } else {
                    mDoorStatus.setText(mDoorErrorMessage);
                }
                mDoorButton.setText("Open / Close Door");
                return;
            }

            DateFormat dateFormat =
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
            if (mDoorOpen) {
                mDoorStatus.setText("Open since " + dateFormat.format(mDoorStateSince));
                mDoorButton.setText("Close Door");
            } else {
                mDoorStatus.setText("Closed since " + dateFormat.format(mDoorStateSince));
                mDoorButton.setText("Open Door");
            }
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                Log.v(TAG, "Getting door status");

                mDoorStateSince = null;

                long startTime = System.nanoTime();

                HttpURLConnection connection = openApiConnection("events");

                try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(connection.getOutputStream()))) {
                    writer.beginObject();
                    writer.name("action").value("get");
                    long id = mRandom.nextInt(10000);
                    writer.name("data");
                    writer.beginObject();
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                    Calendar cal = Calendar.getInstance();
                    writer.name("to").value(dateFormat.format(cal.getTime()));
                    cal.add(Calendar.DATE, -1);
                    writer.name("from").value(dateFormat.format(cal.getTime()));
                    writer.name("limit");
                    writer.beginArray();
                    writer.value(1);
                    writer.value(1000);
                    writer.endArray();
                    writer.endObject();
                    writer.endObject();
                }

                long endTime = System.nanoTime();

                Log.v(TAG, "Got response code: " + connection.getResponseCode() + " in " + (endTime - startTime) / 1000000.0 + " ms");

                BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String resultString = rd.readLine();
                JSONObject resultObj = new JSONObject(resultString);
                JSONArray resultArray = resultObj.getJSONArray("result");
                Log.v(TAG, "Result contains " + resultArray.length() + " entries");
                for (int i = resultArray.length() - 1; i >= 0; --i) {
                    JSONObject entry = resultArray.getJSONObject(i);
                    Log.v(TAG, "Entry: " + entry);
                    if ("Door sensor".equals(entry.getString("detail"))) {
                        long time = entry.getLong("time");
                        mDoorStateSince = new Date();
                        mDoorStateSince.setTime(time * 1000);

                        String desc = entry.getString("desc");
                        Log.v(TAG, "Door sensor status: " + desc);
                        if ("Sensor open".equals(desc)) {
                            mDoorOpen = true;
                        } else if ("Sensor close".equals(desc)) {
                            mDoorOpen = false;
                        } else {
                            mDoorStateSince = null;
                        }

                        return null;
                    }
                }

            } catch (IOException | JSONException e) {
                mDoorErrorMessage = e.getMessage();
                Log.e(TAG, e.toString());
            }
            return null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mHandler = new Handler();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mDoorStatusChecker.run();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mDoorStatusChecker);
    }

    public void onGarageDoorButtonClick(View v) throws IOException {
        new OpenOrCloseDoor().execute();

        // Check status is five seconds
        mHandler.removeCallbacks(mDoorStatusChecker);
        mHandler.postDelayed(mDoorStatusChecker, 5000);
    }
}
