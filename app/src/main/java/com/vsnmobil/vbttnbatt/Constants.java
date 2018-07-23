/**
 * @author    Rajiv M.
 * @copyright  Copyright (C) 2014 VSNMobil. All rights reserved.
 * @license    http://www.apache.org/licenses/LICENSE-2.0
 */
package com.vsnmobil.vbttnbatt;

import java.util.UUID;

public class Constants {

	
	// Client Characteristic UUID Values to set for notification.
    public static UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    // To enable the notification value
    public static final byte[] ENABLE_NOTIFICATION_VALUE = { (byte) 0x01,0x00};
    // To disable the notification value
    public static final byte[] DISABLE_NOTIFICATION_VALUE = { (byte) 0x00,0x00 };
    // VSN Simple Service to listen the key press,fall detect and acknowledge and cancel the event.
    public static final UUID SERVICE_VSN_SIMPLE_SERVICE = UUID.fromString("fffffff0-00f7-4000-b000-000000000000");// 0xFFF0
    // Characteristic UUID for key press and fall detect event.
    public static final UUID CHAR_KEY_PRESS = UUID.fromString("fffffff4-00f7-4000-b000-000000000000");// 0xFFF4
    // Characteristic UUID for acknowledge the data received and cancel the key press / fall detect event.
    public static final UUID ACK_DETECT= UUID.fromString("fffffff3-00f7-4000-b000-000000000000");// 0xFFF3
    // Value need to write the acknowledge data received.
    public static  final byte[] RECEIVED_ACK = new byte[]{(byte) 0x01};
    // Value need to write to cancel the key press / fall detect.
    public static  final byte[] CANCEL_ACK = new byte[]{(byte) 0x00};
    
    //Characteristic UUID to secure the puck and restrict to respond to other APP.
    public static final UUID  CHAR_APP_VERIFICATION = UUID.fromString("fffffff5-00f7-4000-b000-000000000000");//0xFFF5
    // New Value need to write with in 30 seconds of connection event occurred.
    public static final byte[] NEW_APP_VERIFICATION_VALUE = { (byte) 0x80,(byte)0xBE, (byte)0xF5,(byte)0xAC,(byte)0xFF};
    public static final UUID CHAR_DETECTION_CONFIG = UUID.fromString("fffffff2-00f7-4000-b000-000000000000");// 0xFFF2
    public static final UUID CHAR_DETECTION_NOTIFY = UUID.fromString("fffffff4-00f7-4000-b000-000000000000");// 0xFFF4
    public static final byte[] ENABLE_KEY_DETECTION_VALUE = new byte[] { (byte) 0x01 };
    
    // Hao added
    public static final byte[] RESET_TURN_OFF_VALUE = { (byte) 0x03,(byte)0x02, (byte)0x73,(byte)0x8F,(byte)0x08};

    // Hao 061715 version 1.2: Change connection interval, then read battery
	// Adjust connection interval using this service, we can adjust the connection interval to 1.5 seconds to save puck battery life.
	public static final UUID SERVICE_ADJIST_CONNECTION_INTERVAL = UUID.fromString("ffffccc0-00f7-4000-b000-000000000000");
	// Characteristic UUID for the  Adjust connection interval
	public static final UUID CHAR_ADJIST_CONNECTION_INTERVAL = UUID.fromString("ffffccc2-00f7-4000-b000-000000000000");
	// Value to be write to the puck. This write value need to be done after all the read / wrte operation.
	public static final byte[] ADJIST_CONNECTION_INTERVAL_VALUE = new byte[] { (byte)0x40, (byte)0x06, (byte)0x50, (byte)0x06, (byte)0x00, (byte)0x00, (byte)0x58, (byte)0x02};


    
    // To read the battery information form the Battery information service.
    public static final UUID SERVICE_BATTERY_LEVEL = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb");
    // Characteristic to read the battery status value.
    public static final UUID CHAR_BATTERY_LEVEL = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
}
