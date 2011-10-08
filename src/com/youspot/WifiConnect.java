package com.youspot;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.AnimationDrawable;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.youspot.WifiSpot.CIPHER;
import com.youspot.service.ScannerHandler;

public class WifiConnect extends Activity {
	
	private enum STATE {
		DISCONNECTED,
		DISCONNECTING,
		CONNECTED,
		CONNECTING,
		AUTH_FAILED,
		IDLING
	};
	
	private class WifiMonitor extends BroadcastReceiver {
		
		WifiConnect m_wc = null;
		
		public WifiMonitor(WifiConnect wc) {
			this.m_wc = wc;
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
					this.m_wc.onConnectionFailed();
				}
				break;
				
				case CONNECTED:
				{
					this.m_wc.onConnected();
				}
				break;
				
				case AUTHENTICATING:
				{
					this.m_wc.onAuthenticating();
				}
				break;
				
				case CONNECTING:
				{
					this.m_wc.onConnecting();
				}
				break;
				
				case OBTAINING_IPADDR:
				{
					this.m_wc.onDhcpSolving();
				}
				break;
				
				case DISCONNECTED:
				{
					this.m_wc.onDisconnected();
				}
				break;
				
				case SCANNING:
				{
					this.m_wc.onScanning();
				}
				break;
			}
		}
	}
	
	Button m_cancel = null;
	Button m_connect = null;
	TextView m_key = null;
	TextView m_action = null;
	String m_essid = null;
	String m_sskey = null;
	CIPHER m_spotenc = CIPHER.OPEN;
	int m_nid = 0;
	WifiMonitor m_mon = null;
	WifiManager  m_wifi = null;
	STATE m_state = STATE.IDLING;
	ScannerHandler m_service = null;
	AnimationDrawable m_ani_status = null;
	ImageView m_iv_status = null;
	
	boolean bConnected = false;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		this.setContentView(R.layout.spotinfo);
		
		this.setTitle("Network: "+this.getIntent().getStringExtra("SPOT_ESSID"));
		
		
		m_wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);

		/* setup the cancel button */
		m_cancel = (Button)this.findViewById(R.id.btnCancel);
		m_cancel.setOnClickListener(new OnClickListener(){
			public void onClick(View v) {
				WifiConnect.this.finish();
			}
		});
		
		/* setup the ok button */
		m_connect = (Button)this.findViewById(R.id.btnConnect);
		m_connect.setOnClickListener(new OnClickListener(){
			public void onClick(View v) {
				WifiConnect.this.onConnect();
			}
		});
		
		m_action = (TextView)this.findViewById(R.id.currentAction);
		m_action.setText("Disconnected");
		
		/* setup the key textview */
		m_key = (TextView)this.findViewById(R.id.spotkey);
		m_key.setText(this.getIntent().getStringExtra("SPOT_KEY"));
		m_essid = this.getIntent().getStringExtra("SPOT_ESSID");
		m_sskey = this.getIntent().getStringExtra("SPOT_KEY");
		Log.i("WifiConnect","Spot key: "+this.getIntent().getStringExtra("SPOT_KEY"));
		m_spotenc = CIPHER.values()[this.getIntent().getIntExtra("SPOT_CIPHER",0)];
		Log.i("No3g","Spot cipher: "+ m_spotenc.toString());
		
		((TextView)this.findViewById(R.id.lblSSID)).setText(m_essid);
				
		/* enable the button based on spot info */
		if (!(m_spotenc==CIPHER.OPEN || (m_spotenc!=CIPHER.OPEN && m_sskey.length()>0)))
		{
			m_connect.setVisibility(View.INVISIBLE);
			m_action.setVisibility(View.INVISIBLE);
		}
		
		/* get the service handler */
		m_service = ScannerHandler.getInstance();
		m_service.suspend();
		
		/* Start the animation */
		m_iv_status = (ImageView)this.findViewById(R.id.status);
		m_ani_status = (AnimationDrawable)this.getResources().getDrawable(R.drawable.wait);
		displayDisconnect();
		
		/* register the wifi monitor */
		m_mon = new WifiMonitor(this);
		registerReceiver(this.m_mon,
				new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION)
		);

		
	}
	
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		unregisterReceiver(this.m_mon);
		m_service.resume();
	}
	
	public void onConnect() {
		if (!bConnected)
		{
			displayWait();
			Log.i("WifiConnect","spot key:" + m_sskey);
			Log.i("No3g","Start connect ("+m_spotenc.toString()+")");
			WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
			WifiConfiguration wc = new WifiConfiguration();
			wc.SSID = "\""+this.m_essid+"\"";
			//wc.hiddenSSID = true;
			
			/* Configure network options based on selected cipher */
			if (m_spotenc == CIPHER.OPEN)
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
			else if (m_spotenc == CIPHER.WEP)
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

				wc.wepKeys[0] = "\"".concat(this.m_sskey).concat("\"");
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
				wc.preSharedKey = "\"".concat(this.m_sskey).concat("\"");

			}

			this.m_nid = wifi.addNetwork(wc);
			//Log.i("WifiPreference", "add Network returned " + m_nid );
			boolean b = wifi.enableNetwork(m_nid, true);
			Log.i("WifiPreference", "enableNetwork returned " + b );
			this.m_state = STATE.CONNECTING;
		}
		else
		{
			this.m_state = STATE.DISCONNECTING;
			m_action.setText("Disconnecting ...");
			WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
			wifi.disableNetwork(m_nid);
			//wifi.disconnect();
		}
	}
	
	public void onConnectionFailed() {
		m_action.setText("Connection failed");
		Log.i("WifiConnect", "Authentication failed");
		this.m_state = STATE.AUTH_FAILED;
		bConnected = false;
		m_connect.setText("Connect");
		stopWait();
		displayDisconnect();
	}

	public void onConnected() {
		stopWait();
		displayConnect();
		m_action.setText("Connected.");
		this.m_state = STATE.CONNECTED;
		bConnected = true;
		m_connect.setText("Disconnect");
	}
	
	public void onAuthenticating() {
		m_action.setText("Authenticating ...");
	}
	
	public void onDisconnected() {
		/* If we were previously connceting, then something went wrong */
		if (this.m_state == STATE.CONNECTING)
			this.onConnectionFailed();
		
		/* Otherwise, the user pressed the 'disconnect' button */
		m_action.setText("Disconnected");
		displayDisconnect();
		WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		//wifi.disableNetwork(m_nid);
		wifi.removeNetwork(m_nid);
		m_nid = -1;
		this.m_state = STATE.DISCONNECTED;
		bConnected = false;
		m_connect.setText("Connect");
	}
	
	public void onDhcpSolving() {
		m_action.setText("Retrieving IP address ...");
	}
	
	public void onConnecting() {
		m_action.setText("Connecting ...");
	}
	
	public void onScanning() {}

	/* UI routines */
	
	private void displayDisconnect() {
		m_ani_status.stop();
		m_iv_status.setImageResource(R.drawable.disconnect);
	}
	
	private void displayConnect() {
		m_iv_status.setImageResource(R.drawable.connect);
	}
	
	private void displayWait() {
		m_iv_status.setImageDrawable(m_ani_status);
		m_ani_status.start();
	}
	
	private void stopWait() {
		m_ani_status.stop();
	}
	
}
