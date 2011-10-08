package com.youspot.storage;

import java.util.ArrayList;
import java.util.HashMap;

import com.youspot.WifiSpot;
import com.youspot.WifiSpot.CIPHER;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class StorageHelper extends SQLiteOpenHelper {
	
    private static final String DATABASE_NAME = "youspot2";
    private static final String DATABASE_SPOTS_TABLE = "spots";
    private static final String DATABASE_SETTINGS_TABLE = "settings";
    private static final int DATABASE_VERSION = 1;
    
    private static final String DATABASE_SPOTS_CREATE = "create table spots (id integer primary key autoincrement, SSID text not null, BSSID text not null, CIPHER text not null);";
    private static final String DATABASE_SETTINGS_CREATE = "create table settings (name text not null primary key, value text not null);";
	
	private Context m_context = null;
	private SQLiteDatabase m_db = null;
	private boolean m_bLoaded = false;
	
	public StorageHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
		m_context = context;
		loadWriteAccessDB();
	}
	
    @Override
    public void onCreate(SQLiteDatabase db) 
    {
    	/* Create the offline spots database */
        db.execSQL(DATABASE_SPOTS_CREATE);

        /* Create and populate our settings table */
        db.execSQL(DATABASE_SETTINGS_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, 
                          int newVersion) 
    {
        db.execSQL("DROP TABLE IF EXISTS spots");
        db.execSQL("DROP TABLE IF EXISTS settings");
        onCreate(db);
    }

    private void loadWriteAccessDB() {
    	if (!m_bLoaded)
    	{
    		m_db = getWritableDatabase();
    		m_bLoaded = true;
    	}
    }
    
    
    /***************************************************************
     * 
     * Public methods
     * 
     ***************************************************************/

    
    /***************************************************************
     * Spots management
     ***************************************************************/

    
    /**
     * Save a wireless spot in sqlite database
     * 
     * @param spot spot to save in database
     */
    
    public void saveSpot(WifiSpot spot) {
    	
    	/* Insert wireless spot */
		ContentValues values = new ContentValues();
		values.put("BSSID",spot.getBSSID());
		values.put("SSID", spot.getSSID());
		values.put("CIPHER",spot.getEnc().toString());
		m_db.insert(DATABASE_SPOTS_TABLE, null,values);
    }
    
    
    /**
     * Save multiple wireless spots 
     * 
     * @param spots list of spots to save into database
     */
    
	public void saveSpots(ArrayList<WifiSpot> spots) {
		for (WifiSpot spot: spots) {
			saveSpot(spot);
		}
	}
	
	
	/**
	 * Load spots from database
	 * 
	 * @return list of spots stored in database
	 */
	
	public ArrayList<WifiSpot> loadSpots() {
		ArrayList<WifiSpot> spots = new ArrayList<WifiSpot>();
		int ssid_id = -1;
		int bssid_id = -1;
		int cipher_id = -1;
		WifiSpot s;
		
		Cursor cur = m_db.query(DATABASE_SPOTS_TABLE, new String[] {
        		"id", 
        		"SSID",
        		"BSSID",
                "CIPHER"}, 
                null, 
                null, 
                null, 
                null, 
                null);
		
		if(cur.moveToFirst())
		{
			do
			{
				ssid_id = cur.getColumnIndexOrThrow("SSID");
				bssid_id = cur.getColumnIndexOrThrow("BSSID");
				cipher_id = cur.getColumnIndexOrThrow("CIPHER");
				
				s = new WifiSpot(cur.getString(ssid_id), cur.getString(bssid_id));
				s.setEnc(CIPHER.valueOf(cur.getString(cipher_id)));
				spots.add(s);
				
			}
			while(cur.moveToNext());
		}
		cur.close();
		return spots;
	}
    
	
	/**
	 * Clear stored spots from database
	 */
	
	public void clearSpots() {
		m_db.execSQL("DELETE FROM spots;");
	}
	
	
	/**
	 * Close database helper
	 */
	
	public void close() {
		m_db.close();
		super.close();
	}
	
	
    /***************************************************************
     * Settings management
     ***************************************************************/
	
	public String getSetting(int setting_id) {
		String res;
		Cursor cur = m_db.query(DATABASE_SETTINGS_TABLE, new String[] {
        		"name", 
        		"value"
        		}, 
                "name=?", 
                new String[]{String.valueOf(setting_id)}, 
                null, 
                null, 
                null);
		
		/* Get the first setting */
		if(cur.moveToFirst())
		{
			res = cur.getString(1); /* second column */
			cur.close();
			return res;
		}
		else
		{
			cur.close();
			return null;
		}
	}
	
	public void setSetting(int setting_id, String value) {
		ContentValues values = new ContentValues();
		values.put("name", setting_id);
		values.put("value", value);
		if ( m_db.update(DATABASE_SETTINGS_TABLE, values, "name=?", new String[]{String.valueOf(setting_id)})==0 )
			m_db.insert(DATABASE_SETTINGS_TABLE, null, values);			
	}
	
	public HashMap<Integer,String> getSettings() {
		HashMap<Integer,String> settings = new HashMap<Integer,String>();
		
		Cursor cur = m_db.query(DATABASE_SETTINGS_TABLE, new String[] {
        		"name", 
        		"value"
        		}, 
                null, 
                null, 
                null, 
                null, 
                null);
		
		if (cur.moveToFirst())
		{
			do
			{
				settings.put(new Integer(cur.getInt(0)), cur.getString(1));
			} while (cur.moveToNext());
		}
		cur.close();
		return settings;
	}
	
}