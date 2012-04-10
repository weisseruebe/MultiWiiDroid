/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.rettig.multiwii;

import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.BluetoothChat.R;

/**
 * This is the main Activity that displays the current chat session.
 */
public class MultiWiiMainActivity extends Activity {
	
	Timer timer = new Timer();
	
	final int dataLength = 155;
	byte[] buffer = new byte[dataLength];
	int dataIndex = 0;
	Bitmap bitmapOrg;
	ImageView imageViewRoll;
	ImageView imageViewPitch;

	private int angleX;
	private int angleY;

	// Debugging
	private static final String TAG = "MultWii";
	private static final boolean D = true;

	// Message types sent from the BluetoothChatService Handler
	public static final int MESSAGE_STATE_CHANGE = 1;
	public static final int MESSAGE_READ = 2;
	public static final int MESSAGE_WRITE = 3;
	public static final int MESSAGE_DEVICE_NAME = 4;
	public static final int MESSAGE_TOAST = 5;

	// Key names received from the BluetoothChatService Handler
	public static final String DEVICE_NAME = "device_name";
	public static final String TOAST = "toast";

	// Intent request codes
	private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
	private static final int REQUEST_ENABLE_BT = 3;

	// Layout Views
	private TextView mTitle;

	// Name of the connected device
	private String mConnectedDeviceName = null;
	// Local Bluetooth adapter
	private BluetoothAdapter mBluetoothAdapter = null;
	// Member object for the chat services
	private MultiWiiConnectorService mMultiWiiConnectorService = null;
	private Button mButtonSend;


	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if(D) Log.e(TAG, "+++ ON CREATE +++");

		// Set up the window layout
		requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
		setContentView(R.layout.main);
		getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.custom_title);

		imageViewRoll = (ImageView) findViewById(R.id.imageViewRoll);
		imageViewPitch = (ImageView) findViewById(R.id.imageViewPitch);
		
		bitmapOrg = BitmapFactory.decodeResource(getResources(), R.drawable.app_icon);
		
		// Set up the custom title
		mTitle = (TextView) findViewById(R.id.title_left_text);
		mTitle.setText(R.string.app_name);
		mTitle = (TextView) findViewById(R.id.title_right_text);
		mButtonSend = (Button)  findViewById(R.id.button_send);

		// Get local Bluetooth adapter
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		// If the adapter is null, then Bluetooth is not supported
		if (mBluetoothAdapter == null) {
			Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
			finish();
			return;
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		if(D) Log.e(TAG, "++ ON START ++");

		// If BT is not on, request that it be enabled.
		if (!mBluetoothAdapter.isEnabled()) {
			Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
			// Otherwise, setup the chat session
		} else {
			if (mMultiWiiConnectorService == null) setupCommunication();
		}
	}

	@Override
	public synchronized void onResume() {
		super.onResume();
		if(D) Log.e(TAG, "+ ON RESUME +");

		// Performing this check in onResume() covers the case in which BT was
		// not enabled during onStart(), so we were paused to enable it...
		// onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
		if (mMultiWiiConnectorService != null) {
			// Only if the state is STATE_NONE, do we know that we haven't started already
			if (mMultiWiiConnectorService.getState() == MultiWiiConnectorService.STATE_NONE) {
				// Start the Bluetooth chat services
				mMultiWiiConnectorService.start();
			}
		}
	}

	private void setupCommunication() {
		mButtonSend.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				mMultiWiiConnectorService.write("M".getBytes());
			}
		});

		mMultiWiiConnectorService = new MultiWiiConnectorService(this, mHandler);
	}



	@Override
	public synchronized void onPause() {
		super.onPause();
		if(D) Log.e(TAG, "- ON PAUSE -");
	}

	@Override
	public void onStop() {
		super.onStop();
		if(D) Log.e(TAG, "-- ON STOP --");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		// Stop the Bluetooth chat services
		if (mMultiWiiConnectorService != null) mMultiWiiConnectorService.stop();
		if(D) Log.e(TAG, "--- ON DESTROY ---");
	}

	private void ensureDiscoverable() {
		if(D) Log.d(TAG, "ensure discoverable");
		if (mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
			Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
			discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
			startActivity(discoverableIntent);
		}
	}

	// The Handler that gets information back from the BluetoothChatService
	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MESSAGE_STATE_CHANGE:
				if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
				switch (msg.arg1) {
				case MultiWiiConnectorService.STATE_CONNECTED:
					mTitle.setText(R.string.title_connected_to);
					mTitle.append(mConnectedDeviceName);
					timer.schedule(pollTask, 1000,250);
					break;
				case MultiWiiConnectorService.STATE_CONNECTING:
					mTitle.setText(R.string.title_connecting);
					break;
				case MultiWiiConnectorService.STATE_LISTEN:
				case MultiWiiConnectorService.STATE_NONE:
					mTitle.setText(R.string.title_not_connected);
					break;
				}
				break;
			case MESSAGE_DEVICE_NAME:
				// save the connected device's name
				mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
				Toast.makeText(getApplicationContext(), "Connected to "	+ mConnectedDeviceName, Toast.LENGTH_SHORT).show();
				break;
			case MESSAGE_TOAST:
				Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),	Toast.LENGTH_SHORT).show();
				break;
			case MESSAGE_READ:
				receiveData((byte[])msg.obj,msg.arg1);
				break;
			}
		}
	};


	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(D) Log.d(TAG, "onActivityResult " + resultCode);
		switch (requestCode) {
		case REQUEST_CONNECT_DEVICE_SECURE:
			// When DeviceListActivity returns with a device to connect
			if (resultCode == Activity.RESULT_OK) {
				connectDevice(data, true);
			}
			break;

		case REQUEST_ENABLE_BT:
			// When the request to enable Bluetooth returns
			if (resultCode == Activity.RESULT_OK) {
				setupCommunication();
			} else {
				// User did not enable Bluetooth or an error occured
				Log.d(TAG, "BT not enabled");
				Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
				finish();
			}
		}
	}

	protected void receiveData(byte[] data, int length) {
//		Log.d(TAG,"Receive "+length+" "+dataIndex);
		if (dataIndex == 0 & data[0] == 'M'){
			Log.d(TAG, "M");
			System.arraycopy(data, 0, buffer, dataIndex, length);
			dataIndex += length;
		} else if(dataIndex > 0  & dataIndex < dataLength-1){
//			Log.d(TAG, "D");
			System.arraycopy(data, 0, buffer, dataIndex, length);
			dataIndex += length;
		}  
		if (dataIndex == dataLength){
			Log.d(TAG,"Complete "+buffer[buffer.length-1]);
			processData();
			dataIndex = 0;
		}
	}

	private void processData() {
		
		int ax = (buffer[2] & 0xFF) + (buffer[3] << 8); 
		int ay = (buffer[4] & 0xFF) + (buffer[5] << 8); 
		int az = (buffer[6] & 0xFF) + (buffer[7] << 8); 

		ProgressBar pAx = (ProgressBar) findViewById(R.id.progressBar1);
		ProgressBar pAy = (ProgressBar) findViewById(R.id.progressBar2);
		ProgressBar pAz = (ProgressBar) findViewById(R.id.progressBar3);
		
		pAx.setProgress(ax);
		pAy.setProgress(ay);
		pAz.setProgress(az);
		
		angleX = ((buffer[78] & 0xFF) + (buffer[79] << 8)) /10;
		angleY = ((buffer[80] & 0xFF) + (buffer[81] << 8)) /10; 

		updateUI();
	}

	private void connectDevice(Intent data, boolean secure) {
		// Get the device MAC address
		String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
		// Get the BLuetoothDevice object
		BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
		// Attempt to connect to the device
		mMultiWiiConnectorService.connect(device);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.option_menu, menu);
		return true;
	}

	protected void updateUI(){
		Matrix matrix = new Matrix();
		matrix.postRotate(angleX);
		Bitmap bmp = Bitmap.createBitmap(bitmapOrg, 0, 0, bitmapOrg.getWidth(), bitmapOrg.getHeight(), matrix, true);
		imageViewRoll.setImageBitmap(bmp);
		
		matrix = new Matrix();
		matrix.postRotate(angleY);
		bmp = Bitmap.createBitmap(bitmapOrg, 0, 0, bitmapOrg.getWidth(), bitmapOrg.getHeight(), matrix, true);
		
		imageViewPitch.setImageBitmap(bmp);
		
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent serverIntent = null;
		switch (item.getItemId()) {
		case R.id.secure_connect_scan:
			// Launch the DeviceListActivity to see devices and do scan
			serverIntent = new Intent(this, DeviceListActivity.class);
			startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
			return true;

		case R.id.discoverable:
			// Ensure this device is discoverable by others
			ensureDiscoverable();
			return true;
		}
		return false;
	}

	
	TimerTask pollTask = new TimerTask() {
		
		@Override
		public void run() {
			mMultiWiiConnectorService.write("M".getBytes());			
		}
	};
}
