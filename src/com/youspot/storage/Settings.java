package com.youspot.storage;

import java.util.ArrayList;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;

import com.youspot.WifiSpot;

public class Settings {
	
	private final int USERID = 1;
	private final int REPORT_OWN = 2;
	private final int OFFLINE_MODE = 3;
	private final int VIBRATOR = 4;
	
	public static int VIBRATOR_ON = 1;
	public static int VIBRATOR_OFF = 0;
	
	public static int REPORT_OWN_ON = 1;
	public static int REPORT_OWN_OFF = 0;
	
	public static int OFFLINE_MODE_ON = 1;
	public static int OFFLINE_MODE_OFF = 0;
	
	private StorageHelper m_storage = null;
	
	private String m_user_token = "";
	private String m_userid = "";
	private boolean m_offline_mode = false;
	private boolean m_report_own = false;
	private boolean m_vibrate = false;
	private WifiManager m_wifi = null;
	
	public Settings(StorageHelper storage) {
		m_storage = storage;
			
		/* load settings from database */
		loadFromStorage();
	}

	private String _getUserId() {
		String userid = m_storage.getSetting(USERID);
		if (userid == null)
		{
			/* try to retrieve it from shared preferences */
			/*
			SharedPreferences sp = m_storage.getContext().getSharedPreferences("no3g", Context.MODE_PRIVATE)
			if (sp != null)
				userid = sp.getString("userid","");
			else
				userid = "";
			*/
			m_storage.setSetting(USERID, "");
			return "";
		}
		else
			return userid;
	}
		
	private int getReportOwn() {
		/* Get setting */
		String report_own = m_storage.getSetting(REPORT_OWN);
		if (report_own != null)
			return Integer.valueOf(report_own);
		else
		{
			m_storage.setSetting(REPORT_OWN, String.valueOf(REPORT_OWN_ON));
			return REPORT_OWN_ON;
		}
	}
		
	private int getOfflineMode() {
		String offline = m_storage.getSetting(OFFLINE_MODE);
		if (offline != null)
			return Integer.valueOf(offline);
		else
		{
			m_storage.setSetting(REPORT_OWN, String.valueOf(OFFLINE_MODE_OFF));
			return OFFLINE_MODE_OFF;
		}
	}
	
	private int getVibrator() {
		String vibrator = m_storage.getSetting(VIBRATOR);
		if (vibrator != null)
			return Integer.valueOf(vibrator);
		else
		{
			m_storage.setSetting(VIBRATOR, String.valueOf(VIBRATOR_ON));
			return VIBRATOR_ON;
		}
	}
	
	private void loadFromStorage() {
		/* Load settings */
		m_userid = _getUserId();
		m_offline_mode = (getOfflineMode()==OFFLINE_MODE_ON);
		m_report_own = (getReportOwn()==REPORT_OWN_ON);
		m_vibrate = (getVibrator()==VIBRATOR_ON);
	}
	
	/********************************************
	 * 
	 * Exposed methods
	 * 
	 ********************************************/
	
	public String getUserId() {
		return m_userid;
	}
	
	public void setUserId(String userId) {
		m_userid = userId;
	}
	
	public void setUserToken(String token) {
		m_user_token = token;
	}

	public void setReportOwn(boolean bReportOwn) {
		m_report_own = bReportOwn;
	}
	
	public boolean shouldReportOwn() {
		return m_report_own;
	}
	
	public boolean shouldVibrate() {
		return  m_vibrate;
	}
	
	public void setVibrate(boolean bVibrate) {
		m_vibrate = bVibrate;
	}

	public void setOfflineMode(boolean bOfflineMode) {
		m_offline_mode = bOfflineMode;
	}

	public boolean isOfflineModeSet() {
		return m_offline_mode;
	}	
		
	public String getUserToken() {
		return m_user_token;
	}
	
	public void commitChanges() {
		/* Save offline mode setting */
		if (m_offline_mode)
			m_storage.setSetting(OFFLINE_MODE, String.valueOf(OFFLINE_MODE_ON));
		else
			m_storage.setSetting(OFFLINE_MODE, String.valueOf(OFFLINE_MODE_OFF));
		
		/* Save 'report own' setting */
		if (m_report_own)
			m_storage.setSetting(REPORT_OWN, String.valueOf(REPORT_OWN_ON));
		else
			m_storage.setSetting(REPORT_OWN, String.valueOf(REPORT_OWN_OFF));
		
		/* Save vibtrator setting */
		if (m_vibrate)
			m_storage.setSetting(VIBRATOR, String.valueOf(VIBRATOR_ON));
		else
			m_storage.setSetting(VIBRATOR, String.valueOf(VIBRATOR_OFF));
		
		/* Save user id */
		m_storage.setSetting(USERID,m_userid);
	}
}
