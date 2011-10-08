package com.youspot;

public class WifiSpotLocation {
	
	private double LAT;
	private double LNG;
	
	public WifiSpotLocation(){
		this.LAT = 0.0;
		this.LNG = 0.0;
	}

	public Double getLatitude() {
		return this.LAT;
	}
	
	public Double getLongitude() {
		return this.LNG;
	}
}
