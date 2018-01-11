/**
 * @author    Rajiv M.
 * @copyright  Copyright (C) 2014 VSNMobil. All rights reserved.
 * @license    http://www.apache.org/licenses/LICENSE-2.0
 */
package com.vsnmobil.vbttnbatt.service;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

import com.vsnmobil.vbttnbatt.Constants;
import com.vsnmobil.vbttnbatt.DeviceControlActivity;
import com.vsnmobil.vbttnbatt.executor.ProcessQueueExecutor;
import com.vsnmobil.vbttnbatt.executor.ReadWriteCharacteristic;
import com.vsnmobil.vbttnbatt.utils.LogUtils;
import com.vsnmobil.vbttnbatt.R;

/**
 * BluetoothLeService.java
 *
 * The communication between the Bluetooth Low Energy device will be communicated through this service class only
 * The initial connect request and disconnect request will be executed in this class.Also, all the status from the Bluetooth device
 * will be notified in the corresponding callback methods.
 *
 */
public class BluetoothLeService extends Service {
	private String TAG = com.vsnmobil.vbttnbatt.utils.LogUtils.makeLogTag(BluetoothLeService.class);

	// Constants going to use in the broadcast receiver as intent action.
	public final static String ACTION_GATT_SERVICES_DISCOVERED = "com.vsnmobil.vbttnbatt.ACTION_GATT_SERVICES_DISCOVERED";
	public final static String ACTION_GATT_CONNECTED = "com.vsnmobil.vbttnbatt.ACTION_GATT_CONNECTED";
	public final static String ACTION_GATT_DISCONNECTED = "com.vsnmobil.vbttnbatt.ACTION_GATT_DISCONNECTED";
	public final static String ACTION_DATA_RESPONSE = "com.vsnmobil.vbttnbatt.ACTION_DATA_RESPONSE";


	public final static String EXTRA_DATA = "com.vsnmobil.vbttnbatt.EXTRA_DATA";
	public final static String EXTRA_STATUS = "com.vsnmobil.vbttnbatt.EXTRA_STATUS";
	public final static String EXTRA_ADDRESS = "com.vsnmobil.vbttnbatt.EXTRA_ADDRESS";

	private  BluetoothManager bluetoothManager = null;
	private static BluetoothAdapter bluetoothAdapter = null;
	private static BluetoothGattService gattService = null;
	private BluetoothDevice device = null;
	public BluetoothGattCharacteristic mCharIdentify = null;
	public BluetoothGattCharacteristic mCharBlock = null;
	public BluetoothGattCharacteristic mCharVerification = null;
	
	// Hao 061715 version 1.2: change battery reading algorithm
	public BluetoothGattService batteryService = null;
	public int battLevel;
	public int battReadCount;

	public ProcessQueueExecutor processQueueExecutor=new ProcessQueueExecutor();
	public HashMap<String,BluetoothGatt> bluetoothGattMap;

	private NotificationCompat.Builder notifyBuilder = null;
	private Notification notification = null;
	@Override
	public void onCreate() {
		super.onCreate();
		//if blue tooth adapter is not initialized stop the service.
		if(isBluetoothEnabled(this)==false){
			stopForeground(false);
			BluetoothLeService.this.stopSelf(); 
		}
		// To add and maintain the BluetoothGatt object of each BLE device.
		bluetoothGattMap = new HashMap<String,BluetoothGatt>();
		//To execute the read and write operation in a queue.
		if (!processQueueExecutor.isAlive()) {
			processQueueExecutor.start();
		}

	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		//if blue tooth adapter is not initialized stop the service.
		if(isBluetoothEnabled(this)==false){
			stopForeground(false);
			BluetoothLeService.this.stopSelf(); 
		}
		 /* Invoking the default notification service */
        notifyBuilder =new NotificationCompat.Builder(this);
        notifyBuilder.setContentTitle(getString(R.string.app_name)).setSmallIcon(R.drawable.ic_launcher);
        notifyBuilder.setAutoCancel(false);
        notifyBuilder.setPriority(Notification.PRIORITY_MIN);
        /* Creates an explicit intent for an Activity in your app */
        Intent resultIntent = new Intent(this, DeviceControlActivity.class);
        resultIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int notificationCode = (int) System.currentTimeMillis();
        PendingIntent resultPendingIntent = PendingIntent.getActivity(this, notificationCode, resultIntent,PendingIntent.FLAG_UPDATE_CURRENT);
        notifyBuilder.setContentIntent(resultPendingIntent);
        notification = notifyBuilder.build();
		// To keep running the service always in background.
		startForeground(notificationCode, notification);
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		//To stop the foreground service.
		stopForeground(false);
		//Stop the read / write operation queue.
		processQueueExecutor.interrupt();
	}

	@Override
	public void onTaskRemoved(Intent rootIntent) {
		super.onTaskRemoved(rootIntent);
	}

	/**
	 * Manage the BLE service
	 */
	private final IBinder binder = new LocalBinder();
	//Local binder to bind the service and communicate with this BluetoothLeService class.
	public class LocalBinder extends Binder {
		public BluetoothLeService getService() {
			return BluetoothLeService.this;
		}
	}

	@Override
	public boolean onUnbind(Intent intent) {
		// In this particular example,close() is invoked when the UI is
		// disconnected from the Service.
		return super.onUnbind(intent);
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return binder;
	}

	/**
	 * Initializes a reference to the local Blue tooth adapter.
	 * @return Return true if the initialization is successful.
	 */
	public boolean initialize() {
		// For API level 18 and above, get a reference to BluetoothAdapter
		// through BluetoothManager.
		if (bluetoothManager == null) {
			bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
			if (bluetoothManager == null) {
				return false;
			}
		}

		bluetoothAdapter = bluetoothManager.getAdapter();
		if (bluetoothAdapter == null) {
			return false;
		}
		return true;
	}

	/**
	 * To read the value from the BLE Device
	 * @param BluetoothGatt object of the device.
	 * @param BluetoothGattCharacteristic of the device.
	 */
	public void readCharacteristic(final BluetoothGatt mGatt,final BluetoothGattCharacteristic characteristic) {
		if (!checkConnectionState(mGatt)) {
			return;
		}
		ReadWriteCharacteristic readWriteCharacteristic=new ReadWriteCharacteristic(ProcessQueueExecutor.REQUEST_TYPE_READ_CHAR, mGatt, characteristic);
		ProcessQueueExecutor.addProcess(readWriteCharacteristic);
	}

	/**
	 * To write the value to BLE Device
	 * @param BluetoothGatt object of the device.
	 * @param BluetoothGattCharacteristic of the device.
	 * @param byte value to write on to the BLE device.
	 */
	public void writeCharacteristic(final BluetoothGatt mGatt,final BluetoothGattCharacteristic characteristic, byte[] b) {
		if (!checkConnectionState(mGatt)) {
			return;
		}
		characteristic.setValue(b);
		ReadWriteCharacteristic readWriteCharacteristic=new ReadWriteCharacteristic(ProcessQueueExecutor.REQUEST_TYPE_WRITE_CHAR, mGatt, characteristic);
		ProcessQueueExecutor.addProcess(readWriteCharacteristic);
	}

	/**
	 * Enables or disables notification on a give characteristic.
	 * @param characteristic Characteristic to act on.
	 * @param characteristic Characteristic to act on.
	 * @param enabled If true, enable notification. False otherwise.
	 */
	public void setCharacteristicNotification(final BluetoothGatt mGatt,BluetoothGattCharacteristic characteristic, boolean enabled) {
		if (!checkConnectionState(mGatt)) {
			return;
		}
		if (!mGatt.setCharacteristicNotification(characteristic, enabled)) {
			return;
		}
		final BluetoothGattDescriptor clientConfig = characteristic.getDescriptor(Constants.CLIENT_CHARACTERISTIC_CONFIG);
		if (clientConfig == null) {
			return;
		}
		clientConfig.setValue(enabled ? Constants.ENABLE_NOTIFICATION_VALUE: Constants.DISABLE_NOTIFICATION_VALUE);
		ReadWriteCharacteristic readWriteCharacteristic=new ReadWriteCharacteristic(ProcessQueueExecutor.REQUEST_TYPE_WRITE_DESCRIPTOR, mGatt, clientConfig);
		ProcessQueueExecutor.addProcess(readWriteCharacteristic);
	}

	/**
	 * Connects to the GATT server hosted on the Blue tooth LE device.
	 * @param address The device address of the destination device.
	 * @return Return true if the connection is initiated successfully. The connection result is reported asynchronously through the
	 * {@code BluetoothGattCallback# onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)} callback.
	 */
	public boolean connect(final String address) {
		if (bluetoothAdapter == null || address == null) {
			return false;
		}
		BluetoothGatt bluetoothGatt = bluetoothGattMap.get(address);
		if (bluetoothGatt != null) {
			bluetoothGatt.disconnect();
			bluetoothGatt.close();
		}
		device = bluetoothAdapter.getRemoteDevice(address);
		int connectionState = bluetoothManager.getConnectionState(device, BluetoothProfile.GATT);
		if (connectionState == BluetoothProfile.STATE_DISCONNECTED) {
			if (device == null) {
				return false;
			}
			// We want to directly connect to the device, so we are setting the
			// autoConnect parameter to false.
			BluetoothGatt mBluetoothGatt = device.connectGatt(this, false, mGattCallbacks);
			// Add the each BluetoothGatt in to an array list.
			if (!bluetoothGattMap.containsKey(address)) {
				bluetoothGattMap.put(address, mBluetoothGatt);
			} else {
				bluetoothGattMap.remove(address);
				bluetoothGattMap.put(address, mBluetoothGatt);
			}
		} else {
			return false;
		}
		return true;
	}

	/**
	 * To disconnect the connected Blue tooth Low energy Device from the APP.
	 * @param BluetoothGatt pass the GATT object of the device which need to be disconnect.
	 */
	public void disconnect(BluetoothGatt gatt) {
		if (gatt != null) {
			BluetoothDevice device = gatt.getDevice();
			String deviceAddress = device.getAddress();
			try {
				bluetoothGattMap.remove(deviceAddress);
				gatt.disconnect();
				// Hao: don't call gatt.close();  calling close will cause onConnectionStateChange() to not be called.
			} catch (Exception e) {
				LogUtils.LOGI(TAG,e.getMessage()) ;  
			}
		}
	}

	/**
	 * Hao 070215 version 1.3: Add power off menu
	 * To disconnect the connected Blue tooth Low energy Device from the APP.
	 * @param BluetoothGatt pass the GATT object of the device which need to be disconnect.
	 */
	public void power_off(BluetoothGatt gatt) {
		if (gatt != null) {
			broadcastUpdate(ACTION_DATA_RESPONSE, "Turning OFF...V.ALRT will beep twice & blink red-green LED", "");				
			appVerification(gatt, getGattChar(gatt, Constants.SERVICE_VSN_SIMPLE_SERVICE,Constants.CHAR_APP_VERIFICATION),Constants.RESET_TURN_OFF_VALUE);	
		}
	}
	
	
	/**
	 * To check the connection status of the GATT object.
	 * @param BluetoothGatt pass the GATT object of the device.
	 * @return If connected it will return true else false.
	 */
	public boolean checkConnectionState(BluetoothGatt gatt) {
		if (bluetoothAdapter == null) {
			return false;
		}
		BluetoothDevice device = gatt.getDevice();
		String deviceAddress = device.getAddress();
		final BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceAddress);
		int connectionState = bluetoothManager.getConnectionState(bluetoothDevice,BluetoothProfile.GATT);
		if (connectionState == BluetoothProfile.STATE_CONNECTED) {
			return true;
		}
		return false;
	}

	/**
	 * To check the connection status of the GATT object.
	 * @param String MAC address of the device
	 * @return If connected it will return true else false.
	 */
	public boolean checkConnectionState(String deviceAddress) {
		if (bluetoothAdapter == null) {
			return false;
		}
		final BluetoothDevice btDevice = bluetoothAdapter.getRemoteDevice(deviceAddress);
		int connectionState = bluetoothManager.getConnectionState(btDevice, BluetoothProfile.GATT);
		if (connectionState == BluetoothProfile.STATE_CONNECTED) {
			return true;
		}
		return false;
	}
	// The connection status of the Blue tooth Low energy Device will be
	// notified in the below callback.
	private BluetoothGattCallback mGattCallbacks = new BluetoothGattCallback() {
		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status,int newState) {
			BluetoothDevice device = gatt.getDevice();
			String deviceAddress = device.getAddress();

			try {
				switch (newState) {
				case BluetoothProfile.STATE_CONNECTED:
					//start service discovery
					gatt.discoverServices();
					break;
				case BluetoothProfile.STATE_DISCONNECTED:
					try {
						bluetoothGattMap.remove(deviceAddress);
						gatt.disconnect();
						gatt.close();
					} catch (Exception e) {
						LogUtils.LOGI(TAG,e.getMessage()) ;  
					}
					broadcastUpdate(ACTION_GATT_DISCONNECTED, deviceAddress,status);
					break;
				default:
					break;
				}
			} catch (NullPointerException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			BluetoothDevice device = gatt.getDevice();
			String deviceAddress = device.getAddress();
			if (status == BluetoothGatt.GATT_SUCCESS) {
				broadcastUpdate(ACTION_GATT_CONNECTED, deviceAddress, status);
				// Do APP verification as soon as service discovered.
				try {
					appVerification(gatt, getGattChar(gatt, Constants.SERVICE_VSN_SIMPLE_SERVICE,Constants.CHAR_APP_VERIFICATION),Constants.NEW_APP_VERIFICATION_VALUE);
				} catch (Exception e) {}

				for (BluetoothGattService service : gatt.getServices()) {

					if ((service == null) || (service.getUuid() == null)) {
						continue;
					}
					
					if (Constants.SERVICE_VSN_SIMPLE_SERVICE.equals(service.getUuid())) {
						mCharVerification =  service.getCharacteristic(Constants.CHAR_APP_VERIFICATION);
						// Configure for quick key press 
						enableForDetect(gatt,service.getCharacteristic(Constants.CHAR_DETECTION_CONFIG),Constants.ENABLE_KEY_DETECTION_VALUE);
					
						// Set notification for key press
						setCharacteristicNotification(gatt,service.getCharacteristic(Constants.CHAR_DETECTION_NOTIFY),true);
					}
					
	                // Hao 061715 version 1.2: Save battery service for reading later...i.e. after changing connection interval
					if (Constants.SERVICE_BATTERY_LEVEL.equals(service.getUuid())) {
						batteryService = service;
						battLevel = 0;
						battReadCount = 0;
						//Read the device battery percentage
						//readCharacteristic(gatt,service.getCharacteristic(Constants.CHAR_BATTERY_LEVEL));
					}
					
					if (Constants.SERVICE_ADJIST_CONNECTION_INTERVAL.equals(service.getUuid())) {
						// write for adjust connection control value to make the device response time as 1.1 second.
						writeCharacteristic(gatt, service.getCharacteristic(Constants.CHAR_ADJIST_CONNECTION_INTERVAL),Constants.ADJIST_CONNECTION_INTERVAL_VALUE);
						LogUtils.LOGI(TAG, "*** change connection interval");
					}					
				}
			} else {
				// Service discovery failed close and disconnect the GATT object of the device.
				gatt.disconnect();
				gatt.close();
			}
		}

		// CallBack when the response available for registered the notification( Battery Status, Fall Detect, Key Press)
		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt,BluetoothGattCharacteristic characteristic) {
			//broadcastUpdate(ACTION_DATA_RESPONSE, characteristic.getUuid().toString(), "");

			final String keyValue = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0).toString();

			if (keyValue.equalsIgnoreCase("1")) {
				
				if (battReadCount < 3) {
					broadcastUpdate(ACTION_DATA_RESPONSE, "Battery read not complete...", "");				
				}
				else {
					// Hao
					broadcastUpdate(ACTION_DATA_RESPONSE, "Turning OFF...PLEASE WAIT for V.ALRT to beep twice & blink red-green LED", "");				
					appVerification(gatt, getGattChar(gatt, Constants.SERVICE_VSN_SIMPLE_SERVICE,Constants.CHAR_APP_VERIFICATION),Constants.RESET_TURN_OFF_VALUE);					
				}
				
				/* Hao
				final Intent intent = new Intent(Intent.ACTION_CAMERA_BUTTON);
				intent.putExtra(EXTRA_KEY_EVENT, value);
				intent.putExtra(EXTRA_ADDRESS, address);
				sendBroadcast(intent); */
			} else if (keyValue.equalsIgnoreCase("3")) {
				//broadcastUpdate(ACTION_DATA_RESPONSE, "Alert Pressed", "");				

			}
			
		}

		// Callback when the response available for Read Characteristic Request
		@Override
		public void onCharacteristicRead(BluetoothGatt gatt,BluetoothGattCharacteristic characteristic, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				// Display received battery value.
				if (Constants.CHAR_BATTERY_LEVEL.equals(characteristic.getUuid())) {
					
					// Hao 061715 version 1.2: take 3 readings before reporting result
					battLevel += characteristic.getIntValue( BluetoothGattCharacteristic.FORMAT_UINT8, 0);
					if (battReadCount < 3){
						battReadCount++;
						LogUtils.LOGI(TAG, "*** Read Battery Level "+battReadCount);

						readCharacteristic(gatt,batteryService.getCharacteristic(Constants.CHAR_BATTERY_LEVEL));
					}
					else {
						LogUtils.LOGI(TAG, "*** Done Reading Battery Level ");

						String batteryValue = String.valueOf(battLevel/3.0);

						broadcastUpdate(ACTION_DATA_RESPONSE, "Avg Reading = "+batteryValue, status);
						if ((battLevel/3.0) > 50.0) {
							broadcastUpdate(ACTION_DATA_RESPONSE, "Battery is GOOD\r\n\nPRESS V.ALRT ONCE TO TURN IT OFF or SELECT MENU OPTION", status);
						}
						else {
							broadcastUpdate(ACTION_DATA_RESPONSE, "Battery is BAD\r\n\nPRESS V.ALRT TO TURN IT OFF\r\nAND REPLACE V.ALRT BATTERY\r\n", status);
						}						
					}
				}
			}
			else {
				battReadCount = 3;
				broadcastUpdate(ACTION_DATA_RESPONSE, "FAILED Reading battery\r\nPRESS V.ALRT TO TURN IT OFF AND REPEAT TEST\r\n", status);
			}
		}

		// Callback when the response available for Write Characteristic Request
		@Override
		public void onCharacteristicWrite(BluetoothGatt gatt,BluetoothGattCharacteristic characteristic, int status) {
			//broadcastUpdate(ACTION_DATA_RESPONSE,characteristic.getUuid().toString(), status);

			// Hao 061715 version 1.2: callback for connection interval change
			if (Constants.CHAR_ADJIST_CONNECTION_INTERVAL.equals(characteristic.getUuid())) {
				//Read the device battery percentage
				if (batteryService != null) {
					LogUtils.LOGI(TAG, "*** Read First Battery Level");
					battReadCount++;
					readCharacteristic(gatt,batteryService.getCharacteristic(Constants.CHAR_BATTERY_LEVEL));
				}
			}
			
		}

		// Callback when the response available for Read Descriptor Request
		@Override
		public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,int status) {
		}

		// Callback when the response available for Write Descriptor Request
		@Override
		public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,int status) {
			//broadcastUpdate(ACTION_DATA_RESPONSE, "enabled key press event", status);
		}

		@Override
		public void onReadRemoteRssi(BluetoothGatt gatt, final int rssi,int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				String rssiValue = Integer.toString(rssi);
			}
		}
	};

	/**
	 * To write the value to BLE Device for APP verification
	 * @param BluetoothGatt object of the device.
	 * @param BluetoothGattCharacteristic of the device.
	 */
	public void appVerification(final BluetoothGatt mGatt, final BluetoothGattCharacteristic ch,final byte[] value) {
		writeCharacteristic(mGatt, ch, value);
	}

	/**
	 * To write the value to BLE Device for Emergency / Fall alert
	 * @param BluetoothGatt object of the device.
	 * @param BluetoothGattCharacteristic of the device.
	 * @param byte value to write on to the BLE device.
	 */
	public void enableForDetect(final BluetoothGatt mGatt, final BluetoothGattCharacteristic ch,final byte[] value) {
		writeCharacteristic(mGatt, ch, value);
	}

	/**
	 * To get the characteristic of the corresponding BluetoothGatt object and
	 * service UUID and Characteristic UUID.
	 * @param BluetoothGatt object of the device.
	 * @param Service UUID.
	 * @param Characteristic UUID.
	 * @return BluetoothGattCharacteristic of the given service and Characteristic UUID.  
	 */
	public  BluetoothGattCharacteristic getGattChar(BluetoothGatt mGatt, UUID serviceuuid,UUID charectersticuuid) {
		gattService = mGatt.getService(serviceuuid);
		return gattService.getCharacteristic(charectersticuuid);
	}

	/**
	 * To get the List of BluetoothGattCharacteristic from the given GATT object for Service UUID 
	 * @param BluetoothGatt object of the device.
	 * @param Service UUID.
	 * @return List of BluetoothGattCharacteristic.
	 */
	public List<BluetoothGattCharacteristic> getGattCharList(BluetoothGatt mGatt, UUID serviceuuid) {
		gattService = mGatt.getService(serviceuuid);
		return gattService.getCharacteristics();
	}
	/**
	 * To get the BluetoothGatt of the corresponding device
	 * @param String key value of hash map.
	 * @return BluetoothGatt of the device from the array
	 */
	public BluetoothGatt getGatt(String bGattkey) {
		return bluetoothGattMap.get(bGattkey);
	}

	/**
	 * Broadcast the values to the UI if the application is in foreground.
	 * @param String intent action.
	 * @param String value to update to the receiver.
	 */
	private void broadcastUpdate(final String action, final String value,final String address) {
		final Intent intent = new Intent(action);
		intent.putExtra(EXTRA_DATA, value);
		intent.putExtra(EXTRA_ADDRESS, address);
		sendBroadcast(intent);
	}

	/**
	 * Broadcast the values to the UI if the application is in foreground.
	 * @param String intent action.
	 * @param String address of the device.
	 * @param int connection status of the device.
	 */
	public void broadcastUpdate(final String action, final String address, final int status) {
		final Intent intent = new Intent(action);
		intent.putExtra(EXTRA_DATA, address);
		intent.putExtra(EXTRA_STATUS, status);
		sendBroadcast(intent);
	}

	/**
     * To check the device bluetooth is enabled or not.
     * @param Context pass the context of your activity.
     * @return boolean Bluetooth is enabled / disabled.
     */
    public static boolean isBluetoothEnabled(Context context) {
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        return bluetoothAdapter.isEnabled();
    }
}