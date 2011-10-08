package com.youspot.service;

import java.util.Collection;

import com.youspot.WifiSpotInfo;

public interface IScanner {
	
	public interface INo3gServiceListener {
		public void onUpdateSpots();
	}
	
	public void addListener(INo3gServiceListener listener);
	public void removeListener(INo3gServiceListener listener);
	public void removeAllListeners();
	
	public Collection<WifiSpotInfo> getCurrentSpots();
	public int getNbOfDetectedSpots();
	
	public void startScan();
	public void setOfflineMode(boolean bOffline);
	public boolean isOffline();
	public void suspend();
	public void resume();
	public void clearSpots();

}
