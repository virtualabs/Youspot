package com.youspot;

import android.util.Log;

public class WifiSpot {
	
	private String SSID;
	private String BSSID;
	
	public enum CIPHER {
		OPEN,
		WEP,
		WPA,
		WPA2
	}
	
	private CIPHER ENC;
	private String KEY;
	
	public WifiSpot(){
		this.SSID = new String();
		this.BSSID = new String();
		this.ENC = CIPHER.OPEN;
		this.KEY = new String("");
	}

	public WifiSpot(String SSID, String BSSID) {
		this.SSID = SSID;
		this.BSSID = BSSID;
		this.ENC = CIPHER.OPEN;
		this.KEY = new String("");
	}
	
	public boolean equals(WifiSpot spot) {
		if (spot.getBSSID().equals(""))
		{
			Log.i("No3g.WifiSpot","No BSSID");
			return (spot.getSSID().equals(SSID));
		}
		else
		{
			Log.i("No3g.WifiSpot","SSID1:'"+SSID+"'");
			Log.i("No3g.WifiSpot","SSID2:'"+spot.getSSID()+"'");
			return ((spot.getBSSID().equals(BSSID)) && (spot.getSSID().equals(SSID)));
		}
	}
	
	public CIPHER getEnc() {
		return this.ENC;
	}
	
	public void setEnc(CIPHER enc) {
		this.ENC = enc;
	}
	
	public String getKey() {
		return this.KEY;
	}
	
	public void setKey(String key) {
		this.KEY = key;
	}
	
	public String getSSID() {
		return this.SSID;
	}
	
	public String getBSSID() {
		return this.BSSID;
	}
}
