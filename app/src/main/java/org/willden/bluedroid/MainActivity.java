package org.willden.bluedroid;

import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONException;

import java.io.IOException;
import java.text.DateFormat;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "bluespray";
    public static final int WAIT_FOR_OPEN_CLOSE_MILLIS = 14000;
    private final static int SLOW_DOOR_POLL_INTERVAL = 20 * 1000;
    private final static int FAST_DOOR_POLL_INTERVAL = 1000;
    private static final long MAX_OPEN_CLOSE_WAIT_TIME = 10 * 1000;

    private Door mDoor;
    private TextView mDoorStatus;
    private Button mDoorButton;
    private DoorStatusPoller doorStatusPoller;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDoor = new Door(new BlueSprayAsyncClient(this));
        if (savedInstanceState != null) {
            mDoor.restoreHistory(savedInstanceState);
        }

        mDoorStatus = (TextView) findViewById(R.id.door_status);
        mDoorButton = (Button) findViewById(R.id.garage_door_button);
        updateUiWithDoorState();
        doorStatusPoller = new DoorStatusPoller(mDoor, new Handler(), new Door.DoorUpdateCallback(){
            @Override
            public void execute() {
                updateUiWithDoorState();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mDoor.saveHistory(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (doorStatusPoller != null) {
            doorStatusPoller.start(SLOW_DOOR_POLL_INTERVAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.v(TAG, "Print isFinishing: " + isFinishing());
        if (doorStatusPoller != null) {
            doorStatusPoller.stop();
        }
    }

    public void onGarageDoorButtonClick(View v) throws IOException, JSONException {
        mDoorButton.setEnabled(false);
        mDoorButton.setText(mDoor.isDoorOpen() ? R.string.door_closing : R.string.door_opening);
        doorStatusPoller.stop();
        mDoor.clickDoor(new HandleDoorClickResult());
    }

    private void updateUiWithDoorState() {
        if (mDoor.isDoorStateKnown()) {
            DateFormat dateFormat =
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
            String formattedDate = dateFormat.format(mDoor.getStateTimestamp());
            Resources res = getResources();
            if (mDoor.isDoorOpen()) {
                mDoorStatus.setText(String.format(res.getString(R.string.open_since),
                        formattedDate));
                mDoorButton.setText(R.string.close_door);
            } else {
                mDoorStatus.setText(String.format(res.getString(R.string.closed_since),
                        formattedDate));
                mDoorButton.setText(R.string.open_door);
            }
            mDoorButton.setEnabled(true);
        } else {
            mDoorButton.setEnabled(false);
            mDoorButton.setText(R.string.open_close_door);
            mDoorStatus.setText(R.string.unknown_door_state);
        }
    }

    private class HandleDoorClickResult implements Door.DoorClickCallback {
        @Override
        public void success() {
            // Wait a few seconds, then start polling fast to wait for expected new state.
            doorStatusPoller.delayedStart(WAIT_FOR_OPEN_CLOSE_MILLIS, FAST_DOOR_POLL_INTERVAL,
                    new WaitForDoorStateCallback(!mDoor.isDoorOpen(), MAX_OPEN_CLOSE_WAIT_TIME));
        }

        @Override
        public void failure() {
            mDoorButton.setText(R.string.open_close_door);
            doorStatusPoller.start(SLOW_DOOR_POLL_INTERVAL);
        }
    }

    private class WaitForDoorStateCallback implements Door.DoorUpdateCallback {
        private final boolean stateToWaitFor;
        private final long waitEndTime;

        public WaitForDoorStateCallback(boolean stateToWaitFor, long waitTimeout) {
            this.stateToWaitFor = stateToWaitFor;
            this.waitEndTime = waitTimeout + System.currentTimeMillis();
        }

        @Override
        public void execute() {
            if (mDoor.isDoorOpen() == stateToWaitFor
                    || waitEndTime >= System.currentTimeMillis()) {
                doorStatusPoller.stop();
                updateUiWithDoorState();
                doorStatusPoller.start(SLOW_DOOR_POLL_INTERVAL);
            }
        }
    }

    private static class DoorStatusPoller implements Runnable {

        private final Door mDoor;
        private final Handler mHandler;
        private long mPollInterval;
        private Door.DoorUpdateCallback mCallback;
        private Door.DoorUpdateCallback mDefaultCallback;
        private int startCount = 0;

        public DoorStatusPoller(
                Door door, Handler handler, Door.DoorUpdateCallback defaultCallback) {
            mDoor = door;
            mHandler = handler;
            mDefaultCallback = defaultCallback;
        }

        public void start(long pollInterval) {
            start(pollInterval, mDefaultCallback);
        }

        public void start(long pollInterval, Door.DoorUpdateCallback callback) {
            if (startCount != 0)
                throw new AssertionError();
            mPollInterval = pollInterval;
            mCallback = callback;
            ++startCount;
            run();
        }

        public void delayedStart(
                long delayInterval, long pollInterval, Door.DoorUpdateCallback callback) {
            if (startCount != 0)
                throw new AssertionError();
            mPollInterval = pollInterval;
            mCallback = callback;
            ++startCount;
            mHandler.postDelayed(this, delayInterval);
        }

        public void stop() {
            if (startCount != 1)
                throw new AssertionError();
            --startCount;
            mHandler.removeCallbacks(this);
        }

        @Override
        public void run() {
            final Runnable poller = this;
            mDoor.updateStatus(new Door.DoorUpdateCallback() {
                @Override
                public void execute() {
                    Log.v(TAG, "start count " + startCount);
                    mCallback.execute();
                }
            });
            mHandler.postDelayed(poller, mPollInterval);
        }
    }
}
