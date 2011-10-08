package com.youspot.storage;

import android.content.Context;

public class StorageManager {

	private StorageHelper m_storage = null;
	private Settings m_settings = null;
	private OfflineSpots m_off_spots = null;
	private static StorageManager m_inst = null;
	
	private StorageManager(Context context){
		m_storage = new StorageHelper(context);
		m_settings = new Settings(m_storage);
		m_off_spots = new OfflineSpots(m_storage);
	}
	
	public static StorageManager getInstance(Context context) {
		if (m_inst == null)
			m_inst = new StorageManager(context);
		return m_inst;
	}
	
	public Settings getInternalSettings() {
		return m_settings;
	}

	public static Settings getSettings(Context context) {
		return StorageManager.getInstance(context).getInternalSettings();
	}
	
	public OfflineSpots getInternalOfflineSpots() {
		return m_off_spots;
	}
		
	public static OfflineSpots getOfflineSpots(Context context) {
		return StorageManager.getInstance(context).getInternalOfflineSpots();
	}
}
