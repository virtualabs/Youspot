package com.youspot.service;


import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;

import com.youspot.WifiSpotInfo;
import com.youspot.service.IScanner.INo3gServiceListener;
import com.youspot.service.Scanner.No3gServiceBinder;

public class ScannerHandler {
	
	public static ScannerHandler m_instance = null;
	private No3gServiceBinder m_service = null;
	private ArrayList<INo3gServiceListener> m_pending_listeners;
	private Activity m_activity = null;
	private ServiceConnection m_conn;
	private boolean m_bound = false;
	
	public static ScannerHandler getInstance() {
		if (m_instance == null) {
			m_instance = new ScannerHandler();
		}
		return m_instance;
	}
	
	private ScannerHandler() {
		/* Create the service instance from an Activity */
		m_pending_listeners = new ArrayList<INo3gServiceListener>();
	}
	
	public void bindToActivity(Activity activity) {
		m_activity = activity;
		Intent intent = new Intent(activity, Scanner.class);
		
		Log.i("No3g.ServiceHandler","Binding to activity ...");
		
		m_conn = new ServiceConnection() {
			public synchronized void onServiceConnected(ComponentName name, IBinder service) {
				m_bound = true;
				Log.i("No3g.ServiceHandler","Bound to activity");
				
				/* Retrieve service binder instance */
				m_service = (No3gServiceBinder)service;

				/* Install pending listeners */
				Log.i("No3g.ServiceHandler","Installing pending listeners ("+m_pending_listeners.toString()+")" );
				for (INo3gServiceListener listener : m_pending_listeners) {
					ScannerHandler.this.addListener(listener);
					
				/* Clear all pending listeners */
				m_pending_listeners.clear();
				
				/* Start scanning */
				m_service.startScan();
				}
			}

			public void onServiceDisconnected(ComponentName name) {
				m_bound = false;
				Log.i("No3g.ServiceHandler","Activity unbound");
				ScannerHandler.this.onServiceDisconnected();
			}
		};

		if (m_activity.bindService(intent, m_conn, Context.BIND_AUTO_CREATE))
			Log.i("No3g.ServiceHandler","Binding was successful");
		else
			Log.e("No3g.ServiceHandler","Could not bind to activity");
	}
	
	private boolean isBound() {
		return m_bound;
	}
	
	public void unbind() {
		Log.i("No3g.ServiceHandler","Trying to unbind");
		if (isBound())
		{
			/* Try to shutdown the service */
			try {
				m_service.removeAllListeners();
				m_activity.unbindService(m_conn);
				m_bound = false;
				Log.i("No3g.ServiceHandler","Activity is now unbound");
			}
			catch(IllegalArgumentException e)
			{
				Log.e("No3g.ServiceHandler","Error while destroying service");
				e.printStackTrace();
			}			
		}
	}
	
	
	public Collection<WifiSpotInfo> getCurrentSpots() {
		if (isBound())
		{
			Log.i("No3g.ServiceHandler","Querying current spots");
			return m_service.getCurrentSpots();
		}
		else
			return null;
	}
	
	public void refresh() {
		if (isBound())
			m_service.refresh();
	}
		
	public void startScan() {
		if (isBound())
			m_service.startScan();
	}
	
	public void suspend() {
		if (isBound())
			m_service.suspend();
	}
	
	public void resume() {
		if (isBound())
			m_service.resume();
	}
	
	public void setOfflineMode(boolean bOffline) {
		if (isBound())
			m_service.setOfflineMode(bOffline);
	}
	
	public boolean isOffline() {
		if (isBound())
			return m_service.isOffline();
		return false;
	}
	
	public synchronized void addListener(INo3gServiceListener listener) {
		if (isBound())
		{
			Log.i("No3g.ServiceHandler","register listener ...");
			m_service.addListener(listener);
		}
		else
		{
			Log.i("No3g.ServiceHandler","Service is not bound, register listener as pending ...");
			m_pending_listeners.add(listener);
		}
	}

	public synchronized void removeListener(INo3gServiceListener listener) {
		if (isBound())
			m_service.removeListener(listener);
		else
			if (m_pending_listeners.contains(listener))
				m_pending_listeners.remove(listener);
	}
	
	public void onServiceDisconnected() {
		Log.e("No3gServiceHandler","Service disconnected !");
	}
	
	public void clearSpots() {
		if (isBound())
			m_service.clearSpots();
	}
	
	public int getNbOfDetectedSpots() {
		if (isBound())
			return m_service.getNbOfDetectedSpots();
		return 0;
	}
}