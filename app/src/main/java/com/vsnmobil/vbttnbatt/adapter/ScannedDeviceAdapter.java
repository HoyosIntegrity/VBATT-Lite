/**
 * @author    Rajiv M.
 * @copyright  Copyright (C) 2014 VSNMobil. All rights reserved.
 * @license    http://www.apache.org/licenses/LICENSE-2.0
 */
package com.vsnmobil.vbttnbatt.adapter;

import java.util.List;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.vsnmobil.vbttnbatt.R;

/**
 * ScannedDeviceAdapter.java
 * 
 * This adapter is used to load the scanned puck in list view.
 * 
 */
public class ScannedDeviceAdapter extends ArrayAdapter<ScannedDevice> {
	private List<ScannedDevice> list;
	private LayoutInflater inflater;
	private int resId;

	// Constructor
	public ScannedDeviceAdapter(Context context, int resId,
			List<ScannedDevice> objects) {
		super(context, resId, objects);
		inflater = (LayoutInflater) context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		this.resId = resId;
		list = objects;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ScannedDevice item = (ScannedDevice) getItem(position);
		if (convertView == null) {
			convertView = inflater.inflate(resId, null);
		}
		TextView deviceNameTextview = (TextView) convertView
				.findViewById(R.id.device_name);
		TextView deviceAddress = (TextView) convertView
				.findViewById(R.id.device_address);
		deviceNameTextview.setText(item.getDisplayName());
		
		// Hao 061015 version 1.1: Replaced BT Address with RSSI value
		//deviceAddress.setText(item.getDevice().getAddress());
		deviceAddress.setText("RSSI = "+String.valueOf(item.getRssi()));
		return convertView;
	}

	/** add or update BluetoothDevice */
	public void update(BluetoothDevice newDevice, int rssi, byte[] scanRecord) {
		if ((newDevice == null) || (newDevice.getAddress() == null)) {
			return;
		}
		// Hao 061015 version 1.1: Filter out non-V.ALRT devices
		if ((newDevice.getName() == null) || !newDevice.getName().startsWith("V.ALRT")) {
			return;
		}

		boolean contains = false;
		for (ScannedDevice device : list) {
			if (newDevice.getAddress().equals(device.getDevice().getAddress())) {
				contains = true;
				device.setRssi(rssi); // update
				break;
			}
		}

		if (!contains) {
			// add new BluetoothDevice into the adapter.
			list.add(new ScannedDevice(newDevice, rssi));
		}
		// Refresh the list view.
		notifyDataSetChanged();
	}
}
