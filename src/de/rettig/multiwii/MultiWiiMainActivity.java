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
import android.widget.ImageView.ScaleType;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;


/**
 * This is the main Activity that displays the current chat session.
 */
public class MultiWiiMainActivity extends Activity {

	Timer timer = new Timer();

	final int dataLength = 154;
	byte[] buffer = new byte[dataLength];
	int dataIndex = 0;
	Bitmap picWiiFront;
	Bitmap picWiiSide;
	Bitmap picWiiUp;
	
	ImageView imageViewRoll;
	ImageView imageViewPitch;
	ImageView imageViewMag;
	

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
	private ProgressBar pAz;
	private ProgressBar pAy;
	private ProgressBar pAx;
	private SeekBar seekBarAngle;
	private Copter copter = new Copter();

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
		imageViewMag = (ImageView) findViewById(R.id.imageView1);
		
		pAx = (ProgressBar) findViewById(R.id.progressBar1);
		pAy = (ProgressBar) findViewById(R.id.progressBar2);
		pAz = (ProgressBar) findViewById(R.id.progressBar3);

		pAx.setMax(512);
		pAy.setMax(512);
		pAz.setMax(512);

		seekBarAngle = (SeekBar) findViewById(R.id.seekBar1);
		seekBarAngle.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				copter.angleX = progress;
				updateUI();
			}
		});
		picWiiFront = BitmapFactory.decodeResource(getResources(), R.drawable.wiifront);
		picWiiSide = BitmapFactory.decodeResource(getResources(), R.drawable.wiiside);
		picWiiUp = BitmapFactory.decodeResource(getResources(), R.drawable.wiiup);

		mTitle = (TextView) findViewById(R.id.title_left_text);
		mTitle.setText(R.string.app_name);
		mTitle = (TextView) findViewById(R.id.title_right_text);
		mButtonSend = (Button) findViewById(R.id.button_send);

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
					timer.schedule(pollTask, 1000,100);
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
		for (int i=0;i<length;i++){
			if (data[i]=='M' & dataIndex == dataLength){
				processData();
				dataIndex = 0;
			} else if (dataIndex < dataLength){
				//Log.d(TAG,"data at "+dataIndex+" :"+data[i]);
				buffer[dataIndex++] = data[i];
			}
		}
	}

	private int bytesToInt(byte b1, byte b2){
		return  (b1 & 0xFF) + (b2 << 8); 
	}
	
	private void processData() {
		copter.ax = bytesToInt(buffer[2], buffer[3]); 
		copter.ay = bytesToInt(buffer[4], buffer[5]); 
		copter.az = bytesToInt(buffer[6], buffer[7]); 
	
		copter.gx = bytesToInt(buffer[8], buffer[9]); 
		copter.gy = bytesToInt(buffer[10], buffer[11]); 
		copter.gz = bytesToInt(buffer[12], buffer[13]); 
	
		copter.magX = bytesToInt(buffer[14], buffer[15])/3; 
		copter.magY = bytesToInt(buffer[16], buffer[17])/3; 
		copter.magZ = bytesToInt(buffer[18], buffer[19])/3; 
				
		copter.baro = bytesToInt(buffer[20], buffer[21]); 
		copter.head = bytesToInt(buffer[22], buffer[23]); 
		
		copter.angleX = bytesToInt(buffer[78], buffer[79]) /10;
		copter.angleY = bytesToInt(buffer[80], buffer[81]) /10; 

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
		matrix.setRotate(copter.angleX);
		Bitmap bmp = Bitmap.createBitmap(picWiiFront, 0, 0, picWiiFront.getWidth(), picWiiFront.getHeight(), matrix, true);
		imageViewRoll.setImageBitmap(bmp);
		
		matrix = new Matrix();
		matrix.setRotate(copter.angleY);
		bmp = Bitmap.createBitmap(picWiiSide, 0, 0, picWiiSide.getWidth(), picWiiSide.getHeight(), matrix, true);
		imageViewPitch.setImageBitmap(bmp);

		matrix = new Matrix();
		matrix.setRotate(copter.head);
		bmp = Bitmap.createBitmap(picWiiSide, 0, 0, picWiiSide.getWidth(), picWiiSide.getHeight(), matrix, true);
		imageViewMag.setImageBitmap(bmp);

		pAx.setProgress(copter.ax+256);
		pAy.setProgress(copter.ay+256);
		pAz.setProgress(copter.az+256);

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
