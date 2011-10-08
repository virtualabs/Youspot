package com.youspot.storage;

import java.util.ArrayList;

import com.youspot.WifiSpot;

public class OfflineSpots {

	private StorageHelper m_storage = null;
	
	public OfflineSpots(StorageHelper storage) {
		m_storage = storage;
	}
	
	public void saveSpot(WifiSpot spot) {
		m_storage.saveSpot(spot);
	}
	
	public void saveSpots(ArrayList<WifiSpot> spots) {
		m_storage.saveSpots(spots);
	}
	
	public ArrayList<WifiSpot> loadSpots() {
		return m_storage.loadSpots();
	}
	
	public int count() {
		return m_storage.loadSpots().size();
	}
	
	public void clearSpots() {
		m_storage.clearSpots();
	}
	
}
