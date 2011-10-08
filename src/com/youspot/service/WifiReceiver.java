package com.youspot.service;

import com.youspot.service.Scanner;

import android.content.BroadcastReceiver;
import android.net.wifi.WifiManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import android.content.Intent;
import android.content.Context;


public class WifiReceiver extends BroadcastReceiver{

	private Scanner service;
	ConnectivityManager connectivity;
	
	public WifiReceiver(Scanner service)
	{
		this.service = service;
	}
	
	@Override
	public void onReceive(Context c, Intent intent) {
		int wifi_state;
		NetworkInfo network;
		if (intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
		{
			Log.i("No3g.WifiReceiver","Received networks !");
			this.service.onNetworksFound();
		}
		else if (intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION))
		{
			wifi_state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE , WifiManager.WIFI_STATE_UNKNOWN);
			switch(wifi_state)
			{
			case WifiManager.WIFI_STATE_DISABLED:
				{
					Log.i("No3g.Service", "Wifi is disabled");
				}
				break;

			case WifiManager.WIFI_STATE_ENABLED:
				{
					Log.i("No3g.WifiReceiver", "Wifi is enabled");
					this.service.onWifiEnabled();
				}
				break;
			}
		}
		else if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION))
		{
			network = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
			Log.i("No3g.WifiReceiver","Network connectivity has changed");
			this.service.onNetworkStateChanged(network.isConnected());
		}
		else if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION))
		{
			network = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
			if (network.getDetailedState()==NetworkInfo.DetailedState.IDLE)
				this.service.onWifiIdle();
		}
	}
}
