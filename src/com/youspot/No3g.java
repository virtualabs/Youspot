package com.youspot;

import java.util.ArrayList;
import android.app.ListActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.AnimationDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;

import java.util.Collections;
import java.util.concurrent.locks.ReentrantLock;


import android.widget.ListView;

import com.youspot.WifiSpot.CIPHER;
import com.youspot.service.IScanner;
import com.youspot.service.ScannerHandler;
import com.youspot.storage.Settings;
import com.youspot.storage.StorageManager;
import com.youspot.ws.WebService;
import com.youspot.ws.WebService.WebServiceClient;



public class No3g extends ListActivity implements IScanner.INo3gServiceListener {

	
	
	/**
	 * WebService Client binder
	 */

	private class WSClientBinder extends WebServiceClient {
		
		private No3g m_parent = null;
		
		public WSClientBinder(No3g parent) {
			m_parent = parent;
		}
				
		@Override
		public void onClientLoggedIn(String userid, String token) {
			m_parent.onClientLoggedIn(userid, token);
		}
		
		@Override
		public void onCannotLogIn() {
			m_parent.onLoginError();
		}
		
	}
	
	
	/**
	 * Specific adpater for our ListView UI element.
	 * 
	 */
	
	private class WifiSpotsAdapter extends ArrayAdapter<WifiSpotInfo> {

		/**
		 * Constructor
		 */
		
        public WifiSpotsAdapter(Context context, int textViewResourceId, ArrayList<WifiSpotInfo> items) {
                super(context, textViewResourceId, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
                View v = convertView;
                
                /* If layout does not exist, then inflate it from xml */
                if (v == null) {
                    LayoutInflater vi = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    v = vi.inflate(R.layout.scanner_row, null);
                }
                
                /* get the wireless spot information from array */
                WifiSpotInfo spot = this.getItem(position);
                if (spot != null) {
                		ImageView iv = (ImageView) v.findViewById(R.id.wifispot);
                		ImageView ivlvl = (ImageView)v.findViewById(R.id.wifilvl);
                        TextView tt = (TextView) v.findViewById(R.id.toptext);
                        TextView bt = (TextView) v.findViewById(R.id.bottomtext);
                        if (tt != null) {
                        	
                        	/* If spot is known ... */
                        	if (spot.isKnown())
                        	{
                        		/* ... set a green background up */
                        		v.setBackgroundDrawable(getResources().getDrawable(R.drawable.knownbg));
                        	}
                        	else
                        		v.setBackgroundColor(0xF9F9F9);
                        	
                        	/* Set spot's ESSID */
                       		tt.setText(spot.getSpot().getSSID());
                        }
                        
                        /* Set spot's BSSID */
                        if(bt != null){
                              bt.setText(spot.getSpot().getBSSID());
                        }
                        
                        /* Set cipher used */
                        if (iv != null) {
                        	switch(spot.getSpot().getEnc())
                        	{
                        	case WEP:
                        		iv.setImageResource(R.drawable.ic_wifi_wep);
                        		break;
                        	case WPA:
                        		iv.setImageResource(R.drawable.ic_wifi_wpa);
                        		break;
                        	case WPA2:
                        		iv.setImageResource(R.drawable.ic_wifi_wpa2);
                        		break;
                        	default:
                        		iv.setImageResource(R.drawable.ic_wifi_open);
                        	}
                        }
                        
                        /* Display spot's signal level */
                        if (ivlvl != null) {
                        	switch((spot.getSignal()+1)/2)
                        	{
                        	case 0:
                        		ivlvl.setImageResource(R.drawable.ic_lvl1);
                        		break;
                        	case 1:
                        		ivlvl.setImageResource(R.drawable.ic_lvl2);
                        		break;
                        	case 2:
                        		ivlvl.setImageResource(R.drawable.ic_lvl3);
                        		break;
                        	case 3:
                        		ivlvl.setImageResource(R.drawable.ic_lvl4);
                        		break;
                        	case 4:
                        	case 5:
                        		ivlvl.setImageResource(R.drawable.ic_lvl5);
                        		break;
                        	default:
                        		ivlvl.setImageResource(R.drawable.ic_lvl1);
                        		break;
                        	}
                        }
                }
                return v;
        }
	}
	
	/**
	 * 
	 * WIFI connection manager
	 * 
	 */
	
	private static class WifiConnectionManager {

		private enum CONN_STATUS {
			INACTIVE,
			IDLING,
			CONNECTION_ASKED,
			CONNECTING,
			DISCONNECTING,
			DHCPING,
			CONNECTED,
			AUTH_FAILED,
			DISCONNECTED,
			ABORTING
		};

		
		
		private class WaitingAnimation implements Runnable {
			public void run() {
				m_anim_wait.start();
			}
		}

		
		/**
		 * Monitors the wifi state
		 * 
		 * @author virtualabs
		 *
		 */
		
		private class WifiMonitor extends BroadcastReceiver {
			
			
			WifiConnectionManager m_parent = null;
			
			public WifiMonitor(WifiConnectionManager parent) {
				m_parent = parent;				
			}
			
			@Override
			public void onReceive(Context c, Intent intent) {
				Log.i("WifiConnect","Got event:" + intent.getAction());
				/* Get network detailed state */
				NetworkInfo ni = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
				Log.i("WifiConnect","network state: "+ni.getDetailedState().toString());
				switch(ni.getDetailedState())
				{
					case FAILED:
					{
						this.m_parent.onWifiConnectionFailed();
					}
					break;
					
					case CONNECTED:
					{
						this.m_parent.onWifiConnected();
					}
					break;
					
					case AUTHENTICATING:
					{
						this.m_parent.onWifiAuthenticating();
					}
					break;
					
					case CONNECTING:
					{
						this.m_parent.onWifiConnecting();
					}
					break;
					
					case OBTAINING_IPADDR:
					{
						this.m_parent.onWifiIPSolving();
					}
					break;
					
					case DISCONNECTED:
					{
						this.m_parent.onWifiDisconnected();
					}
					break;
				}
			}
		}		
		
		private No3g m_parent; 
		private TextView m_action = null;
		private AnimationDrawable m_anim_wait = null;
		private ImageView m_connect_wait = null;
		private Button m_connect_btn = null;
		private int m_nid = -1;
		private CONN_STATUS m_conn_status;
		private WifiSpot m_spot;
		private WifiMonitor m_mon;
		
		
		public WifiConnectionManager(No3g parent, WifiSpot spot) {
			m_parent = parent;
			m_spot = spot;

			/* Get UI elements */
			m_action = (TextView)m_parent.findViewById(R.id.main_connect_status_txt);
			m_anim_wait = (AnimationDrawable)m_parent.getResources().getDrawable(R.drawable.wait);
			m_connect_btn = (Button)m_parent.findViewById(R.id.main_connect_do);
			m_connect_wait = (ImageView)m_parent.findViewById(R.id.main_connect_status);
			m_connect_wait.setImageDrawable(m_anim_wait);
			
			/* Connect dialog button */
			Button connect_btn = (Button)m_parent.findViewById(R.id.main_connect_do);
			connect_btn.setOnClickListener(new OnClickListener(){
				public void onClick(View v) {
					WifiConnectionManager.this.onConnectBtnClicked();
				}
			});
			
			Button cancel_btn = (Button)m_parent.findViewById(R.id.main_connect_cancel);
			cancel_btn.setOnClickListener(new OnClickListener(){
				public void onClick(View v) {
					WifiConnectionManager.this.onCancelBtnClicked();
				}
			});
			
			/* register the wifi monitor */
			m_mon = new WifiMonitor(this);
			m_parent.registerReceiver(this.m_mon,
					new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION)
			);

			
			m_conn_status = CONN_STATUS.IDLING;
			displayDisconnected();
			m_action.setText(m_parent.getResources().getString(R.string.main_connect_disconnected));
		}
		
		public void release() {
			Log.i("No3g.WifiConnection","Unregister listener");
			m_parent.unregisterReceiver(this.m_mon);
		}
		
		public void setTargetNetwork(WifiSpot spot) {
			m_spot = spot;

			/* Restart UI elements */
			m_conn_status = CONN_STATUS.IDLING;
			displayDisconnected();
			m_action.setText(m_parent.getResources().getString(R.string.main_connect_disconnected));
			this.m_nid = -1;

		}
		
		public void onConnectBtnClicked() {
			if (m_spot != null)
			{
				if (m_conn_status == CONN_STATUS.IDLING)
				{
					Log.i("No3g.Connect","Idling: connecting ...");
					connectTo(m_spot);
				}
				else
				{
					Log.i("No3g.Connect","Busy: disconnecting ...");
					m_conn_status = CONN_STATUS.DISCONNECTING;
					animateWait();
					disconnect();
				}
			}
		}
		
		public void onCancelBtnClicked() {
			Log.i("No3g.Connect","Canceling connection ...");
			if (m_conn_status == CONN_STATUS.IDLING)
			{
				Log.i("No3g.Connect","Idling: close dialog");
				m_conn_status = CONN_STATUS.DISCONNECTING;
				disconnect();
				m_parent.onWifiConnectClosed();
			}
			else
			{
				Log.i("No3g.Connect","Busy: aborting ...");
				m_conn_status = CONN_STATUS.ABORTING;
				disconnect();
				m_action.setText(m_parent.getString(R.string.main_connect_aborting));
			}
		}
		
		public void animateWait() {
			m_anim_wait = (AnimationDrawable)m_parent.getResources().getDrawable(R.drawable.wait);
			m_connect_wait.setImageDrawable(m_anim_wait);
			m_connect_wait.post(new WaitingAnimation());
		}
		
		public void displayConnected() {
			m_connect_wait.setImageResource(R.drawable.connect);
		}
		
		public void displayDisconnected() {
			m_connect_wait.setImageResource(R.drawable.disconnect);
		}
		
		public void connectTo(WifiSpot spot) {
			Log.i("WifiConnect","spot key:" + spot.getKey());
			Log.i("No3g","Start connect ("+spot.getEnc().toString()+")");
			WifiManager wifi = (WifiManager) m_parent.getSystemService(Context.WIFI_SERVICE);
			WifiConfiguration wc = new WifiConfiguration();
			wc.SSID = "\"".concat(spot.getSSID()).concat("\"");
			wc.BSSID = spot.getBSSID().toUpperCase();
			
			/* Configure network options based on selected cipher */
			if (spot.getEnc() == CIPHER.OPEN)
			{
				wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
				wc.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
				wc.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
				wc.allowedAuthAlgorithms.clear();
				wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
				wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
				wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
				wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
				wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
				wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
			}
			else if (spot.getEnc() == CIPHER.WEP)
			{
				wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
				wc.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
				wc.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
				wc.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
				wc.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
				wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
				wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
				wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
				wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);

				wc.wepKeys[0] = "\"".concat(spot.getKey()).concat("\"");
				wc.wepTxKeyIndex = 0;

			}
			else
			{
				wc.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
				wc.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
				wc.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
				wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
				wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
				wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
				wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
				wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
				wc.preSharedKey = "\"".concat(spot.getKey()).concat("\"");

			}

			this.m_nid = wifi.addNetwork(wc);
			boolean b = wifi.enableNetwork(m_nid, true);
			Log.i("WifiPreference", "enableNetwork returned " + b );
			m_conn_status = CONN_STATUS.CONNECTING;
			m_connect_btn.setEnabled(false);
			m_action.setText(m_parent.getString(R.string.main_connect_associating));
			animateWait();
		}
		
		public void disconnect() {
			WifiManager wifi = (WifiManager)m_parent.getSystemService(Context.WIFI_SERVICE);
			SupplicantState curr_state = wifi.getConnectionInfo().getSupplicantState();
			
			switch(curr_state)
			{
				case SCANNING:
					{
						Log.i("No3g.Connect","Scanning: disconnecting ...");
						if (m_nid>=0)
						{
							//wifi.disableNetwork(m_nid);
							wifi.removeNetwork(m_nid);
							wifi.disconnect();

						}
						else
						{
							m_conn_status = CONN_STATUS.IDLING;
						}
						m_connect_btn.setText(m_parent.getString(R.string.main_connect_connect));
						m_connect_btn.setEnabled(true);
					}
					break;
					
				default:
					{
						Log.i("No3g.Connect","Busy: disconnecting ...");
						if (m_nid>=0)
						{
							//wifi.disableNetwork(m_nid);
							wifi.removeNetwork(m_nid);
							//wifi.disconnect();
						}
						m_conn_status = CONN_STATUS.IDLING;
						m_connect_btn.setText(m_parent.getString(R.string.main_connect_connect));
						m_connect_btn.setEnabled(true);
					}
					break;
			}
		}
		
		public void onWifiConnecting() {
			Log.i("No3g.Connect","Wifi state: connecting");
			m_action.setText(m_parent.getString(R.string.main_connect_associating));
		}
		
		public void onWifiConnectionFailed() {
			Log.i("No3g.Connect","Wifi state: connection failed");
			displayDisconnected();
			m_conn_status = CONN_STATUS.IDLING;
			m_connect_btn.setEnabled(true);
			m_action.setText(m_parent.getString(R.string.main_connect_connection_failed));
		}
		
		public void onWifiConnected() {
			Log.i("No3g.Connect","Wifi state: connected");
			m_conn_status = CONN_STATUS.CONNECTED;
			displayConnected();
			m_action.setText(m_parent.getString(R.string.main_connect_connected));
			m_connect_btn.setEnabled(true);
			m_connect_btn.setText(m_parent.getString(R.string.main_connect_disconnect));
		}
		
		public void onWifiAuthenticating() {
			Log.i("No3g.Connect","Wifi state: authenticating");
			if (m_conn_status == CONN_STATUS.CONNECTING)
				m_action.setText(m_parent.getString(R.string.main_connect_authenticating));
			else if (m_conn_status == CONN_STATUS.DISCONNECTING)
			{
				m_action.setText(m_parent.getString(R.string.main_connect_disconnecting));
			}
		}
		
		public void onWifiIPSolving() {
			Log.i("No3g.Connect","Wifi state: dhcp'ing");
			if (m_conn_status == CONN_STATUS.CONNECTING)
				m_action.setText(m_parent.getString(R.string.main_connect_ip_solving));
		}
		
		public void onWifiDisconnected() {
			Log.i("No3g.Connect","Wifi state: disconnected");
			/* If we were previously connecting, then something went wrong */
			if (this.m_conn_status == CONN_STATUS.CONNECTING)
				this.onWifiConnectionFailed();

			m_action.setText(m_parent.getString(R.string.main_connect_disconnected));
			WifiManager wifi = (WifiManager)m_parent.getSystemService(Context.WIFI_SERVICE);
			//wifi.disableNetwork(m_nid);
			wifi.removeNetwork(m_nid);
			m_nid = -1;
			this.m_conn_status = CONN_STATUS.IDLING;
			displayDisconnected();
			m_connect_btn.setEnabled(true);
			m_connect_btn.setText(m_parent.getString(R.string.main_connect_connect));
		}
	}
	
	
	public static int VERSION = 10;

	public WifiManager wifi;
	public ScannerHandler m_service;
	private WSClientBinder m_ws_binder = null;
	private Settings m_settings = null;
	private WifiSpotInfo m_selected_spot = null;
	private ReentrantLock m_spotlist_lock = null;
	private WifiConnectionManager m_wifi_conn_mng = null;
	
	
	private enum STATUS {
		IDLING,
		LOGIN,
		SCANNING,
		CONNECTING,
	};
	private STATUS m_status;
	
	private ArrayList<WifiSpotInfo> m_spots;
	private WifiSpotInfo.WsiComparator m_comp;
	
	/* UI items */
	private LinearLayout m_scanner = null;
	private LinearLayout m_splash = null;
	private AnimationDrawable m_splash_load_anim = null;
	private LinearLayout m_upgrade = null;
	private LinearLayout m_title_bar = null;
	private LinearLayout m_connect = null;
	private TextView m_upgrade_changelog = null;
	private TextView m_status_text = null;

	
	private class LoadingAnimation implements Runnable {
		public void run() {
			m_splash_load_anim.start();
		}
	}
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		this.setContentView(R.layout.main);
		
		/* Register web service client */
		m_ws_binder = new WSClientBinder(this);
		
		/* Settings */
		m_settings = StorageManager.getInstance(getApplicationContext()).getInternalSettings();
		
		m_wifi_conn_mng = new WifiConnectionManager(this,null);
		
		/* Wifi spots */
		m_spots = new ArrayList<WifiSpotInfo>();
		this.setListAdapter(new WifiSpotsAdapter(this, R.layout.scanner_row, m_spots));
		
		/* Spots list lock */
		m_spotlist_lock = new ReentrantLock();
		
		/* comparator */
		m_comp = new WifiSpotInfo.WsiComparator();
		
		/* get UI items */
		m_scanner = (LinearLayout)findViewById(R.id.main_scanner);
		m_splash = (LinearLayout)findViewById(R.id.main_splash_screen);
		m_upgrade = (LinearLayout)findViewById(R.id.main_upgrade);
		m_connect = (LinearLayout)findViewById(R.id.main_connect);
		m_title_bar = (LinearLayout)findViewById(R.id.main_title_bar);
		m_upgrade_changelog = (TextView)findViewById(R.id.main_upgrade_changelog);
		m_status_text = (TextView)findViewById(R.id.main_status_text);
		
		/* splash screen animation */
		ImageView load_anim = (ImageView)findViewById(R.id.main_splash_loading);
		m_splash_load_anim = (AnimationDrawable)this.getResources().getDrawable(R.drawable.wait);
		load_anim.setImageDrawable(m_splash_load_anim);
		load_anim.post(new LoadingAnimation());

		/* Get Connectivity manager */
	    ConnectivityManager connman = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
	    if (connman == null)
	    	Log.e("No3g","Could not get connectivity service");
	    else 
	    {
	    	if(connman.getActiveNetworkInfo() != null)
	    	{
	    		if (connman.getActiveNetworkInfo().isAvailable())
	    		{
	    			/* register client if offline mode is not set*/
	    			if (!StorageManager.getInstance(getApplicationContext()).getInternalSettings().isOfflineModeSet())
	    			{
	    				Log.d("No3g","Delayed client registration");
	    				delayedRegisterClient();
	    			}
	    			else
	    			{
	    				/* offline mode set, launch service as offline */
	    				Log.d("No3g","Offline mode set. Start scanner");
	    				launchServiceOffline();
	    			}	    		
	    		}
	    		else
	    		{
    				/* offline mode set, launch service as offline */
    				Log.d("No3g","Offline mode set. Start scanner");
    				StorageManager.getSettings(getApplicationContext()).setOfflineMode(true);
    				StorageManager.getSettings(getApplicationContext()).commitChanges();
    				launchServiceOffline();
	    		}
	    	}
	    	else
	    	{
				/* offline mode set, launch service as offline */
				Log.d("No3g","Offline mode set. Start scanner");
				StorageManager.getSettings(getApplicationContext()).setOfflineMode(true);
				StorageManager.getSettings(getApplicationContext()).commitChanges();
				launchServiceOffline();
	    	}
	    }
		
		/* idling ... */
		m_status = STATUS.IDLING;
	}
	
	@Override
	public void onDestroy() {
		
		Log.i("No3g.Scanner","Removing listener");
		if (m_service != null)
			m_service.removeListener(this);
		m_wifi_conn_mng.release();
		ScannerHandler.getInstance().suspend();
    	ScannerHandler.getInstance().unbind();
    	Log.i("No3g.Scanner","Clearing spots");
		m_spots.clear();
		super.onDestroy();
	}
	
	public void onUpdateStatus(String string) {
		m_status_text.setText(string);
	}
	
	
	/**
	 * Method used by No3gService to ask a refresh
	 */
	
	public synchronized void onUpdateSpots() {
		/* lock our list */
		m_spotlist_lock.lock();
		try
		{
			/* clear and populate */
			m_spots.clear();
			for (WifiSpotInfo spot : m_service.getCurrentSpots())
				m_spots.add(spot);
			
			/* Then sort the list */
			Collections.sort(m_spots, m_comp);
			((WifiSpotsAdapter)this.getListAdapter()).notifyDataSetChanged();
			
			if (m_service.isOffline())
			{
				onUpdateStatus(String.valueOf("["+getString(R.string.main_offline)+"] "+StorageManager.getOfflineSpots(getApplicationContext()).count())+" "+getString(R.string.scanner_status_offline));
			}
			else
				onUpdateStatus(String.valueOf(m_service.getNbOfDetectedSpots())+" "+getString(R.string.scanner_status));
		}
		finally
		{
			/* unlock */
			m_spotlist_lock.unlock();
		}
	}
	
	/**
	 * Handles stats display
	 */
	
	public void onDisplayAbout() {

		Intent i = new Intent(this, About.class);
		//i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		this.startActivity(i);

	}
	
	
	/**
	 * Handles click events for this list
	 */
	
	@Override
	public synchronized void onListItemClick(ListView lv, View v, int pos, long id) {
		WifiSpotInfo wsi;
		super.onListItemClick(lv, v, pos, id);
		
		/* lock our list */
		m_spotlist_lock.lock();
		try
		{
			wsi = (WifiSpotInfo)getListView().getItemAtPosition(pos);
			if ( (m_status != STATUS.CONNECTING) && (((wsi.getSpot().getEnc()!=CIPHER.OPEN) && wsi.getSpot().getKey().length()>0) || (wsi.getSpot().getEnc() == CIPHER.OPEN)) )
			{
				/* Suspend scanning */
				m_service.suspend();
				
				/* We are going to connect to a spot */
				m_status = STATUS.CONNECTING;
	
				Log.i("No3g.Service","Connect to: "+wsi.getSpot().getSSID());
				m_selected_spot = wsi;
				displayConnect(true);
				
			}
/*
			else
			{
				displayConnect(false);
			}
*/
		}
		finally
		{
			m_spotlist_lock.unlock();
		}
	}
	
	
	public void onWifiConnectClosed() {
		m_status = STATUS.SCANNING;
		displayConnect(false);
		m_service.resume();
	}
	
	public void onCancelBtnClicked() {
		
	}

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	Context ctx = this.getApplicationContext();
        switch (item.getItemId()) {
            case R.id.exit:
            	{
            		this.finish();
            	}
            	break;
            
            case R.id.preferences:
            	{
            		Intent pref_intent = new Intent(ctx, Preferences.class);
            		pref_intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            		startActivity(pref_intent);
            	}
            	break;
            	
            case R.id.about:
            	{
            		onDisplayAbout();
            	}
            	break;
        }
        return true;
    }
    

    /**
     * Displays upgrade screen & changelog
     * 
     * @param changelog
     */
    private void displayUpgrade(String changelog) {
    	m_connect.setVisibility(View.GONE);
    	m_scanner.setVisibility(View.GONE);
    	m_splash.setVisibility(View.GONE);
    	m_title_bar.setVisibility(View.VISIBLE);
    	m_upgrade_changelog.setText(changelog);
    	m_upgrade.setVisibility(View.VISIBLE);
    }
    
    
    /**
     * Displays scanner screen
     */
    
    private void displayScanner() {
    	m_connect.setVisibility(View.GONE);
    	m_upgrade.setVisibility(View.GONE);
    	m_splash.setVisibility(View.GONE);
    	m_scanner.setVisibility(View.VISIBLE);
    	m_title_bar.setVisibility(View.VISIBLE);
     }
    
    
    /**
     * Displays No3g splash screen
     */
    
    private void displaySplashScreen() {
    	m_connect.setVisibility(View.GONE);
    	m_scanner.setVisibility(View.GONE);
    	m_title_bar.setVisibility(View.GONE);
    	m_upgrade.setVisibility(View.GONE);
    	m_splash.setVisibility(View.VISIBLE);
    }
    
    
    /**
     * Displays No3g connect widget
     */
    
    private void displayConnect(boolean bEnabled) {
    	if (bEnabled)
    	{
    		/* Set the essid */
    		TextView ssid = (TextView)findViewById(R.id.main_connect_essid);
    		ssid.setText(m_selected_spot.getSpot().getSSID());
    		TextView key = (TextView)findViewById(R.id.main_connect_key);
    		if (m_selected_spot.getSpot().getEnc() != CIPHER.OPEN)
    			key.setText(m_selected_spot.getSpot().getKey());
    		else
    			key.setText(getString(R.string.main_connect_nokey));
    		m_wifi_conn_mng.setTargetNetwork(m_selected_spot.getSpot());
    		m_connect.setVisibility(View.VISIBLE);
    	}
    	else
    	{
    		m_connect.setVisibility(View.GONE);
    	}
    }
    
    
    /**
     * Register client application to the webservice with a delay (3 sec.)
     */
    
    private void delayedRegisterClient() {
    	m_status = STATUS.LOGIN;
    	displaySplashScreen();
    	Runnable mrun = new Runnable(){
    		public void run() {
    			No3g.this.registerClient();
    		}
    	};
    	Handler mhandler = new Handler();
    	mhandler.postDelayed(mrun,3000);
    }
    
    
    /**
     * Call the remote webservice to register client
     */
    
    private void registerClient() {
    	WebService ws = WebService.getInstance();
    	//ws.checkVersion(VERSION, m_ws_binder);
    	ws.userLogin(m_settings.getUserId(), m_ws_binder);
    }
    
            
    public void onClientLoggedIn(String userid, String token) {

    	/* Set parameters (userid and token) */
    	m_settings.setUserId(userid);
    	m_settings.setUserToken(token);

		/* Get a No3gService handle */
		Log.i("No3g.Scanner","Adding view as a listener");
		m_service = ScannerHandler.getInstance();
		if (m_service != null)
			m_service.addListener(this);
		
		/* Start the service */
        if(ScannerHandler.getInstance()==null)
        	Log.i("No3g","Unable to start background service");
        else
        	ScannerHandler.getInstance().bindToActivity(this);


		/* display scanner */
		displayScanner();
		
		/* and start to scan */
		m_status = STATUS.SCANNING;
		m_service.startScan();

    }
    
    public void onLoginError() {
    	Log.d("No3g","Login error !");
    	launchServiceOffline();
    }
    
    private void launchServiceOffline() {
    	/* Get a No3gService handle */
		Log.i("No3g.Scanner","Adding view as a listener");
		m_service = ScannerHandler.getInstance();
		if (m_service != null)
			m_service.addListener(this);
		
		/* Start the service */
        if(ScannerHandler.getInstance()==null)
        	Log.i("No3g","Unable to start background service");
        else
        	ScannerHandler.getInstance().bindToActivity(this);


		/* display scanner */
		displayScanner();
		
		/* and start to scan */
		m_status = STATUS.SCANNING;
		m_service.startScan();    	    	
    }
}
