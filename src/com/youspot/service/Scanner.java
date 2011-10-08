package com.youspot.service;

/* Java dependencies */
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;


/* Android dependencies */
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.Vibrator;
import android.util.Log;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

/* No3g */
import com.youspot.No3g;
import com.youspot.R;
import com.youspot.storage.Settings;
import com.youspot.storage.StorageManager;
import com.youspot.WifiSpot;
import com.youspot.WifiSpotInfo;
import com.youspot.WifiSpot.CIPHER;
import com.youspot.service.IScanner;
import com.youspot.storage.OfflineSpots;
import com.youspot.ws.WebService;
import com.youspot.ws.WebService.RegisterSpotResponse;

public class Scanner extends Service implements IScanner {

	
	private class WSClientBinder extends WebService.WebServiceClient {
		private Scanner m_parent;
		
		public WSClientBinder(Scanner parent) {
			m_parent = parent;
		}
		
		@Override
		public void onSpotRegistered(RegisterSpotResponse response) {
			m_parent.onSpotRegistered(response);
		}
	}
	
	/*
	 * Properties
	 */
	boolean bConnected = false;
	boolean bOfflineMode = false;
	boolean bOldWifiState = false;
	private WifiManager wifi = null;
	private WifiManager.WifiLock wifilock = null;
	private WifiReceiver receiver = null;
	private ConnectivityManager conn = null;
	private No3gServiceBinder binder;
	private WSClientBinder m_ws_binder;
	private ConcurrentHashMap<String,WifiSpotInfo> m_spots = null;
	private ArrayList<WifiSpot> m_pending_spots = null;
	private ConcurrentHashMap<String,WifiSpotInfo> m_curr_spots = null;
	private ArrayList<IScanner.INo3gServiceListener> m_listeners = null;
	private WebService m_ws = null;
	private boolean m_bUpdateSpots = false;
	private int WIFI_MIN_LVL = 3;
	private boolean m_bSuspended = true; /* suspended by default */
	private Notification m_status_notification = null;
	private OfflineSpots m_off_spots = null;
	private ArrayList<WifiSpot> m_conf_spots = null;
	private Settings m_settings = null;

	
	/*
	 * Methods
	 */

	@Override 
	public void onCreate() { 
	    super.onCreate();
	    
	    /* Create service binder */
	    Log.i("No3g.Service","Creating service ...");
	    this.binder = new No3gServiceBinder(this);
	    
	    /* Create web service binder */
	    this.m_ws_binder = new WSClientBinder(this);
	    
	    /* Initialize private members */
	    Log.i("No3g.Service","Initializing private members");
	    this.m_spots = new ConcurrentHashMap<String,WifiSpotInfo>();
	    this.m_pending_spots = new ArrayList<WifiSpot>();
	    this.m_curr_spots = new ConcurrentHashMap<String,WifiSpotInfo>();
	    this.m_listeners = new ArrayList<IScanner.INo3gServiceListener>(); 
	    
	    /* Get Connectivity manager */
	    this.conn = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
	    if (this.conn == null)
	    	Log.e("No3g.Service","Could not get connectivity service");
	    else if(this.conn.getActiveNetworkInfo() != null)
	    	bConnected = this.conn.getActiveNetworkInfo().isAvailable();
	    
	    /* Are we connected ? (data link active) */
	    if (!bConnected)
	    {
	    	/* Not connected, act as offline */
	    	StorageManager.getSettings(getApplicationContext()).setOfflineMode(true);
	    	setOfflineMode(true);
	    }
	    
		/* Get wifimanager instance */
	    Log.i("No3g.Service","Enabling WiFi ...");
        this.wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
              
		/* Register our broadcast receiver */
        Log.i("No3g.Service","Create our receiver ...");
		if (this.receiver == null)
		{
			this.receiver = new WifiReceiver(this);
			Log.i("No3g.Service","Done.");
		}
		else
			Log.i("No3g.Service","No receiver :S");

		
		/* Register every intent we want to receive ... */
		Log.i("No3g.Service","Register our receiver ...");
		registerReceiver(this.receiver, new IntentFilter(
				WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
		registerReceiver(this.receiver, new IntentFilter(
				WifiManager.WIFI_STATE_CHANGED_ACTION));
		registerReceiver(this.receiver, new IntentFilter(
				ConnectivityManager.CONNECTIVITY_ACTION));
		registerReceiver(this.receiver,
				new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
        
		/* Create wifi Lock */
        this.wifilock = (WifiManager.WifiLock) this.wifi.createWifiLock(WifiManager.WIFI_MODE_SCAN_ONLY,"No3gWifiLock");
        if (this.wifilock == null)
        	Log.e("No3g.Service","Could not create WifiLock");
        
		/* Enable wifi if not already available */
        this.bOldWifiState = this.wifi.isWifiEnabled();
        if (!this.wifi.isWifiEnabled())
			this.wifi.setWifiEnabled(true);
        
        /* Load configured networks */
        List<WifiConfiguration> conf_net = this.wifi.getConfiguredNetworks();
        m_conf_spots = new ArrayList<WifiSpot>();
        for (WifiConfiguration network : conf_net) {
        	Log.d("No3g.Service","Configured network:"+network.SSID);
        	m_conf_spots.add(new WifiSpot(network.SSID.substring(1,network.SSID.length()-1),""));
        }
        
        /* Load settings */
        m_settings = StorageManager.getSettings(getApplicationContext());
        
        /* Create the web service client */
		Log.i("No3g.Service","Creating the webservice client ...");
		m_ws = WebService.getInstance();
		
		/* Create the status bar notification */
		buildStatusNotification();
		
		/* check if offline mode is set */
		if (StorageManager.getInstance(getApplicationContext()).getInternalSettings().isOfflineModeSet())
				bOfflineMode = true;
		
		Log.d("No3g.Service","Instanciante sqlite database");
		m_off_spots = StorageManager.getOfflineSpots(getApplicationContext());
		
	}
	
	
	private void buildStatusNotification()
	{
		CharSequence ticker = this.getString(R.string.notif_scanning_ticker);
		long when = System.currentTimeMillis();
		CharSequence title = this.getString(R.string.notif_scanning_title);
		CharSequence desc = this.getString(R.string.notif_scanning_desc);
		Intent notificationIntent = new Intent(this, No3g.class);
		/*notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);*/
		m_status_notification = new Notification(R.drawable.icon_mini, ticker, when);
		
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		m_status_notification.setLatestEventInfo(this.getApplicationContext(), title, desc, contentIntent);
		m_status_notification.flags |= Notification.FLAG_ONGOING_EVENT;
		m_status_notification.flags |= Notification.FLAG_NO_CLEAR;
	}
	
	private void notifyScanning()
	{
		String ns = Context.NOTIFICATION_SERVICE;
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
		mNotificationManager.notify(1, m_status_notification);
	}
	
	private void removeNotifications()
	{
		String ns = Context.NOTIFICATION_SERVICE;
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
		mNotificationManager.cancel(1);
	}

	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		/* Remove notifications */
		removeNotifications();
		
		/* close the webservice */
		this.m_ws.close();
		
		/* Unlock wifi */
		Log.i("No3g.Service","Releasing wifilock");
		if (this.wifilock != null)
        	this.wifilock.release();
		
		/* Unregistering listener */
		if (receiver != null)
		{
			Log.i("No3g.Service","Unregistering the listener ...");
			unregisterReceiver(receiver);
		}
		
		/* Remove listeners */
		Log.i("No3g.Service","Removing listeners");
		this.binder.removeAllListeners();

		/* Clear spots */
		Log.i("No3g.Service","Clearing spots");
		this.m_spots.clear();
		this.m_curr_spots.clear();

		/* Restore wifi state */
		if (!this.bOldWifiState)
		{
			Log.i("No3g.Service","Restoring wifi state");
			this.wifi.setWifiEnabled(this.bOldWifiState);
		}
		else
			Log.i("No3g.Service","Wifi was already enabled");

	}
	
	/********************************************************
	 * 
	 *			 No3g Service Binder
	 * 
	 ********************************************************/
	
	
	@Override 
    public IBinder onBind(Intent intent) { 
        return this.binder;
    }
	
	/* Service Interface */
	public class No3gServiceBinder extends Binder {
		
		private  IScanner service = null;
		
		public No3gServiceBinder(IScanner service) {
			this.service = service;
		}
		
		public void addListener(IScanner.INo3gServiceListener listener)
		{
			if (service != null)
			{
				service.addListener(listener);
			}
		}
		public void removeListener(IScanner.INo3gServiceListener listener) {
			if (service != null)
				service.removeListener(listener);
		}
		
		public void removeAllListeners() {
			if (service != null)
				service.removeAllListeners();
		}
				
		public IScanner getService() {
			return service;
		}
		
		public Collection<WifiSpotInfo> getCurrentSpots() {
			if (service != null)
				return service.getCurrentSpots();
			else
				return null;
		}
		
		public void startScan() {
			if (service != null)
				service.startScan();
		}
	
		public void suspend() {
			if (service != null)
				service.suspend();
		}
		
		public void resume() {
			if (service != null)
				service.resume();
		}
		
		public void setOfflineMode(boolean bOffline) {
			if (service != null)
				service.setOfflineMode(bOffline);
		}
		
		public boolean isOffline() {
			if (service != null)
				return service.isOffline();
			return false;
		}
		
		public void clearSpots() {
			if (service != null)
				service.clearSpots();
		}
		
		public int getNbOfDetectedSpots() {
			if (service != null)
				return service.getNbOfDetectedSpots();
			return 0;
		}
		
		public void refresh() {
			startScan();
		}
		
	}
	
	/********************************************************
	 * 
	 *			 Service Internals
	 * 
	 ********************************************************/
	
	private void beeep() {
		long[] pattern = {0,200,200,200};
		Vibrator vibro = (Vibrator)getApplicationContext().getSystemService(VIBRATOR_SERVICE);
		if (vibro != null)
			vibro.vibrate(pattern,-1);
	}
	
	public void refresh() {
		if (this.wifi != null)
			this.wifi.startScan();
	}
	
	public void startScan() {
        /* Lock wifi */
        if (this.wifilock != null)
        {
        	this.wifilock.acquire();
			Log.i("No3g.Service","Start scanning");
        }
        notifyScanning();
	    this.wifi.startScan();

    }
	
	public void suspend() {
		Log.i("No3g.Service","Wifi notification suspended");
		m_bSuspended = true;
	}
	
	public void resume() {
		if (m_bSuspended)
		{
			Log.i("No3g.Service","Wifi notification resumed");
			m_bSuspended = false;
			startScan();
		}
	}

	private boolean isSuspended() {
		return m_bSuspended;
	}
	
	public Collection<WifiSpotInfo> getCurrentSpots() {
		return this.m_curr_spots.values();
	}
	
	private void requestSpotsUpdate() {
		if (m_bUpdateSpots==false)
		{
			m_bUpdateSpots = true;
			Log.i("No3g.Service","Spots update requested");
		}
	}
	
	private boolean needSpotsUpdate() {
		Log.i("No3g.Service","Needs update ?");
		return m_bUpdateSpots;
	}
	
	private void spotsUpdateDone() {
		Log.i("No3g.Service","Update done");
		m_bUpdateSpots = false;
	}
	
	
	public void onWifiEnabled() {
		if (!isSuspended())
		{
			Log.i("No3g.Service","Wifi enabled. Start scanning ...");
			this.wifi.startScan();
		}
	}
	
	private boolean belongsToConfiguredNetworks(WifiSpot spot) {
		for (WifiSpot _spot : m_conf_spots) {
			if (spot.equals(_spot))
				
				/* found: true */
				return true;
		}
		/* default: false */
		return false;
	}
	
	public synchronized void onNetworksFound() {
		WifiSpot spot;
		WifiSpotInfo spotinfo;
		int level;
		Log.i("No3g.Service","Found networks");
		/* Get results */
		List<ScanResult> results = this.wifi.getScanResults();
		if (results != null)
		{
			/* Was-seen update */
			for (WifiSpotInfo wsi : m_curr_spots.values())
				if (wsi.wasSeen())
				{
					Log.i("No3g.Service","Lost "+wsi.getSpot().getSSID());
					m_curr_spots.remove(wsi.getSpot().getBSSID());
					this.requestSpotsUpdate();
				}
							
			/* Add only newly detected networks */
		  	for (ScanResult result : results)
	    	{
		  		/* Check if spot belongs to configured networks */
		  		spot = new WifiSpot(result.SSID,result.BSSID);
		  		//Log.i("No3g.Service","Should I report own spot ? "+String.valueOf(m_settings.shouldReportOwn()));
		  		//Log.i("No3g.Service",result.SSID + " belongs to conf spots ? "+String.valueOf(belongsToConfiguredNetworks(spot)));
		  		if (belongsToConfiguredNetworks(spot) && (!m_settings.shouldReportOwn()))
		  		{
		  			Log.d("No3g.Service","Network "+result.SSID+" belongs to own networks.");
		  			Log.d("No3g.Service","... Ignoring it.");
		  			break;
		  		}
		  		
		  		if (!m_spots.containsKey(result.BSSID))
		  		{	
		  			Log.i("No3g.Service","Detected new network: "+result.SSID);
		    		spotinfo = new WifiSpotInfo(spot);
		    		//Log.i("No3g.Service","Got spot: "+ result.SSID);
		    		//Log.i("No3g.Service","Capabilities: "+ result.capabilities);
		    		if (result.capabilities.contains("WEP"))
		    		{
		    			spot.setEnc(WifiSpot.CIPHER.WEP);
		    			//Log.i("No3g.Service","Wipher: WEP");
		    		}
		    		else if (result.capabilities.contains("WPA2"))
		    		{
		    			spot.setEnc(WifiSpot.CIPHER.WPA2);
		    			//Log.i("No3g.Service","Wipher: WPA2");
		    		}
		    		else if (result.capabilities.contains("WPA"))
		    		{
		    			spot.setEnc(WifiSpot.CIPHER.WPA);
		    			//Log.i("No3g.Service","Wipher: WPA");
		    		}
		    	
		    	  	/* Compute signal level */
		    	  	spotinfo.updateSignal(WifiManager.calculateSignalLevel(result.level,10));
		    	  	
		    	  	this.m_spots.put(result.BSSID, spotinfo);
		    	  	
		    	  	if (!m_curr_spots.containsKey(result.BSSID))
		    	  	{
		    	  		this.m_curr_spots.put(result.BSSID, spotinfo);
		    	  		this.requestSpotsUpdate();
		    	  	}
		    	  	
		    	  	m_pending_spots.add(spot);
		  		}
		  		else
		  		{
		  			/* Update signal level */
		  			level = WifiManager.calculateSignalLevel(result.level,10);
		  			if (level != m_spots.get(result.BSSID).getSignal())
		  			{
		  				Log.i("No3g.Service","Update signal: "+result.SSID +"("+ new Integer(level).toString()+")");
		  				m_spots.get(result.BSSID).updateSignal(level);
		  				this.onSpotSignalUpdated(result.BSSID);
		  			}
		  			
		  			/* "See" the spot */
		  			Log.i("No3g.Service",result.SSID + " seen");
	  				m_spots.get(result.BSSID).see();
		  		}
	    	}
		  	
		  	/* Update the known networks from our webservice */
		  	if (!m_pending_spots.isEmpty())
		  	{
		  		Log.i("No3g.Service","Checking and updating networks ...");
		  		checkPendingNetworks();
		  	}

		  	/* Update unknown spots */
		  	if (needSpotsUpdate())
		  		this.notifySpotsUpdate();
		}
		else
			Log.i("No3g.Service","ScanResults is null");
		
		/* Launch a new scan */
		if (!isSuspended())
			this.wifi.startScan();
	}
	
	public synchronized void notifySpotsUpdate() {
		//Log.i("No3g.Service","Updating spots ...");
		for (IScanner.INo3gServiceListener listener : m_listeners)
			listener.onUpdateSpots();
		this.spotsUpdateDone();
	}
	
	public synchronized void onSpotSignalUpdated(String BSSID) {
		if (m_curr_spots.containsKey(BSSID))
		{			
			/* if signal < WIFI_MIN_LVL then remove the network from the current networks */
			if (m_curr_spots.get(BSSID).getSignal()<WIFI_MIN_LVL)
				m_curr_spots.remove(BSSID);
			this.requestSpotsUpdate();
		}
		else  if (m_spots.containsKey(BSSID) && m_spots.get(BSSID).getSignal()>=WIFI_MIN_LVL)
		{
			m_curr_spots.put(BSSID, m_spots.get(BSSID));
			this.requestSpotsUpdate();
		}
	}
	
	/************************************
	 * 
	 * Network state callbacks
	 * 
	 ************************************/
	
	public void onWifiIdle() {
		//Log.i("No3g.Service","Wifi is idling. Start a scan ...");
		//this.wifi.startScan();
	}
	
	public void onNetworkStateChanged(boolean bConnected) {
		if (bConnected)
			this.onNetworkConnected();
		else
			this.onNetworkDisconnected();
	}
	
	
	private void onNetworkConnected() {
		//Log.i("No3g.Service","Can connect to remote ws");
		bConnected = true;
		this.wifi.startScan();
	}
	
	private void onNetworkDisconnected() {
		//Log.i("No3g.Service","Cannot connect to remote ws");
		bConnected = false;
	}
	
	private boolean isConnected() {
		return bConnected;
	}
	
	public void checkPendingNetworks() {
		//Log.i("No3g.Service","Offline mode: "+String.valueOf(bOfflineMode));
		/* If we are connected and not in offline mode */
		if (isConnected() && (!bOfflineMode))
		{
			Log.i("No3g.Service","Checking networks ...");
			try
			{
			
				/* communicate with the webservice */
				if (m_ws.registerSpot(m_pending_spots, StorageManager.getInstance(getApplicationContext()).getInternalSettings().getUserToken() , m_ws_binder))
				{
					Log.i("No3g.Service","Spots registration request sent ...");
					m_pending_spots.clear();
				}
				else
					Log.e("No3g.Service","Unable to send registration request (will retry later)");
			}
			catch(Exception e)
			{
				Log.e("No3g.Service","An exception was raised during registration !");
				e.printStackTrace();
			}
		}
		else
		{
			Log.i("No3g.Service","Not connected. Saved as offline.");
			m_off_spots.saveSpots(m_pending_spots);
			m_pending_spots.clear();
		}
	}

	
	/********************************************************
	 * 
	 *			 Web Service Callbacks
	 * 
	 ********************************************************/
		
	/*
	 * Registration callback
	 */
	
	public synchronized void onSpotRegistered(RegisterSpotResponse answer) {
		WifiSpotInfo wsi;
		
		/* sanity check -> server error */
		if (answer == null)
		{
			Log.e("No3g.Service","Response was not parsed correctly :(");
			return;
		}
		
		/* if answer is not null, then process */
		if (answer.getAnswer())
		{
			Log.i("No3g.Service", "Spot successfully registered");
						
			/* Add the returned spots to known spots */
			for(WifiSpot spot : answer.getSpots())
			{
				/* If spot was registered as "unknown", delete it */
				if (m_spots.containsKey(spot.getBSSID()))
				{
					if (!m_spots.get(spot.getBSSID()).isKnown())
					{
						if ((spot.getEnc()!=CIPHER.OPEN) && (spot.getKey().length()>0))
							m_spots.get(spot.getBSSID()).markAsKnown();
						if ((spot.getEnc()==CIPHER.OPEN) && (spot.getKey().length()==0))
							m_spots.get(spot.getBSSID()).markAsKnown();
					}
					this.requestSpotsUpdate();
				}
				
				//Log.i("No3g.Service","Answer contains:"+spot.getSSID());
				wsi = m_spots.get(spot.getBSSID());
				if (wsi != null)
				{
					wsi.getSpot().setKey(spot.getKey()); /* saves the key */
					wsi.getSpot().setEnc(spot.getEnc()); /* saves the enc */
					if ((!this.m_curr_spots.containsKey(spot.getBSSID())) && wsi.getSignal()>WIFI_MIN_LVL)
					{
						Log.i("No3g.Service","New spot detected: "+wsi.getSpot().getSSID()+"("+new Integer(wsi.getSignal()).toString()+")");
						this.m_curr_spots.put(spot.getBSSID(), wsi);
						this.requestSpotsUpdate();
					}
					
					/* Make a 'beeep' if spot is available */
					if ( ((wsi.getSpot().getEnc()==CIPHER.OPEN) || (wsi.getSpot().getKey().length()>0 && wsi.getSpot().getEnc()!=CIPHER.OPEN)) && m_settings.shouldVibrate()) {
						/* Vibrates only if not in offline mode  nor suspended*/
						if (!bOfflineMode && !m_bSuspended)
							beeep();
					}
				}
			}
			
			/* Update known networks */
			if (this.needSpotsUpdate())
				notifySpotsUpdate();			
		}
		else
			Log.e("No3g.Service","Unable to register spot");
	}

	
	/********************************************************
	 * 
	 *			 Service management
	 * 
	 ********************************************************/

	public WebService getService() {
		return m_ws;
	}
	
	public synchronized void addListener(IScanner.INo3gServiceListener listener) {
		if (m_listeners != null)
			m_listeners.add(listener);
	}
	
	public synchronized void removeListener(IScanner.INo3gServiceListener listener) {
		if (m_listeners != null)
			m_listeners.remove(listener);
	}
	
	public synchronized void removeAllListeners() {
		if (m_listeners != null)
			for (IScanner.INo3gServiceListener listener : m_listeners)
				m_listeners.remove(listener);
	}

	public void setOfflineMode(boolean bOffline) {
		//Log.i("No3g.Service","Set offline mode: "+String.valueOf(bOffline));
		bOfflineMode = bOffline;
	}
	
	public boolean isOffline() {
		return bOfflineMode;
	}
	
	public void clearSpots() {
		m_curr_spots.clear();
		m_spots.clear();
	}
	
	public int getNbOfDetectedSpots() {
		return m_spots.size();
	}
	
}
