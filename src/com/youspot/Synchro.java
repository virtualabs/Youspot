package com.youspot;

import java.util.ArrayList;

import com.youspot.service.ScannerHandler;
import com.youspot.storage.Settings;
import com.youspot.storage.StorageManager;
import com.youspot.ws.WebService;
import com.youspot.ws.WebService.RegisterSpotResponse;

import android.app.Activity;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class Synchro extends Activity {

	private ProgressBar m_progress = null;
	private LinearLayout m_summup = null;
	private TextView m_summup_total = null;
	private Button m_summup_ok = null;
	private LinearLayout m_working = null;
	private TextView m_status = null;
	private ArrayList<WifiSpot> m_pending_spots = null;
	private ArrayList<ArrayList<WifiSpot>> m_chunks = null;
	private WebService m_ws = null;
	private WSClientBinder m_binder = null;
	private String m_token = null;
	private Settings m_settings = null;
	private int m_curr_chunk = 0;
	private int m_errors = 0;
	private AnimationDrawable m_wait_anim;
	
	
	private class LoadingAnimation implements Runnable {
		public void run() {
			m_wait_anim.start();
		}
	}
	
	private class WSClientBinder extends WebService.WebServiceClient {
		private Synchro m_parent;
		
		public WSClientBinder(Synchro parent) {
			m_parent = parent;
		}
		
		@Override
		public void onSpotRegistered(RegisterSpotResponse response) {
			m_parent.onSpotRegistered(response);
		}
		
		@Override
		public void onCannotRegisterSpot() {
			m_parent.onCannotRegisterSpot();
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
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		int i,j,max,nchunks = 0;
		
		super.onCreate(savedInstanceState);		
		this.setContentView(R.layout.synchro);

        /* Create the web service client */
		m_ws = WebService.getInstance();
		
		/* Get progress bar */
		m_progress = (ProgressBar)findViewById(R.id.synchro_progress);
		m_progress.setProgress(0);
		
		/* Get summup widgets */
		m_summup = (LinearLayout)findViewById(R.id.synchro_summup);
		m_summup_total = (TextView)findViewById(R.id.synchro_total_spots);
		m_summup_ok = (Button)findViewById(R.id.synchro_ok_btn);
		if (m_summup_ok != null) {
			m_summup_ok.setOnClickListener(new OnClickListener(){
				public void onClick(View v) {
					Synchro.this.terminate(false);
				}
			});
		}
		m_working = (LinearLayout)findViewById(R.id.content);
		m_status = (TextView)findViewById(R.id.synchro_status);
		
		ImageView wait_anim = (ImageView)findViewById(R.id.synchro_waiting_ani);
		m_wait_anim = (AnimationDrawable)this.getResources().getDrawable(R.drawable.wait);
		wait_anim.setImageDrawable(m_wait_anim);
		wait_anim.post(new LoadingAnimation());

		
		/* Get spots */
		m_chunks = new ArrayList<ArrayList<WifiSpot>>();
		m_pending_spots = StorageManager.getOfflineSpots(getApplicationContext()).loadSpots();
		nchunks = m_pending_spots.size()/20;
		if (m_pending_spots.size()%20 > 0)
			nchunks++;
		
		if (m_pending_spots != null) {
			Log.i("No3g.Synchro","Cutting stored spots in chunks");
			for (i=0; i<nchunks; i++) {
				if ((m_pending_spots.size()%20)>0)
				{
					if (i==(nchunks-1))
						max = m_pending_spots.size()%20;
					else
						max = 20;
				}
				else
					max = 20;
				Log.i("No3g.Synchro","Chunk size: "+String.valueOf(max));
				ArrayList<WifiSpot> chunk = new ArrayList<WifiSpot>();
				for (j=0;j<max;j++) {
					chunk.add(m_pending_spots.get(i*20+j));
				}
				m_chunks.add(chunk);
			}
		}
		
		/* Create web service binder */
		m_binder = new WSClientBinder(this);
	
		/* get settings instance */
		m_settings = StorageManager.getSettings(getApplicationContext());
		
		/* Suspend service */
		ScannerHandler.getInstance().suspend();
		
		/* Submit chunks to webservice */
		login();
	}
	
	private void login() {
		m_status.setText(getString(R.string.synchro_connecting));
		m_ws.userLogin(StorageManager.getSettings(getApplicationContext()).getUserId(), m_binder);
	}
	
	private void onLoginError() {
		/* Failed, stay offline */
		m_settings.setOfflineMode(true);
		m_settings.commitChanges();
	}
	
	private void onClientLoggedIn(String userid, String token) {
		m_token = token;
		m_settings.setUserToken(m_token);
		m_curr_chunk = 0;
		m_errors = 0;
		processChunk();
	}
	
	private void processChunk() {
		int percent;
		m_progress.setIndeterminate(false);
		Log.i("No3g.Synchro","Process chunk #"+String.valueOf(m_curr_chunk+1)+"/"+String.valueOf(m_chunks.size()));
		m_status.setText(getString(R.string.synchro_sent)+" "+String.valueOf(m_curr_chunk*20) +"/"+String.valueOf(m_pending_spots.size()));
		percent = (int)((((float)m_curr_chunk)/m_chunks.size())*100.0);
		m_progress.setProgress(percent);

		if (m_curr_chunk==m_chunks.size())
		{
			Log.d("No3g.Synchro","All pending spots sent ! Exiting synchro ...");
			displaySummup();
		}
		else
		{
			Log.d("No3g.Synchro","Sending chunk  #"+String.valueOf(m_curr_chunk+1));
			m_ws.registerSpot(m_chunks.get(m_curr_chunk), m_token, m_binder);
		}
	}
	
	private void onSpotRegistered(RegisterSpotResponse response) {
		m_errors = 0;
		Log.d("No3g.Synchro","Chunk #"+String.valueOf(m_curr_chunk)+" successfully sent");
		m_curr_chunk++;
		processChunk();
	}
	
	private void onCannotRegisterSpot() {
		Log.e("No3g.Synchro","Error while registering spots");
		m_errors++;
		if (m_errors==3)
		{
			Log.d("No3g.Synchro","Too many errors. Forgive.");
			Toast t = Toast.makeText(getApplicationContext(),getString(R.string.synchro_error),Toast.LENGTH_LONG);
			t.show();
			terminate(true);
		}
		else
			processChunk();
	}
	
	private void displaySummup() {
		m_working.setVisibility(View.GONE);
		m_summup_total.setText(getString(R.string.synchro_sent)+" "+String.valueOf(m_pending_spots.size()));
		m_summup.setVisibility(View.VISIBLE);
	}
	
	private void terminate(boolean bOfflineMode) {
		Log.d("No3g.Synchro","Set offline mode and return to normal mode");
		StorageManager.getOfflineSpots(getApplicationContext()).clearSpots();
		ScannerHandler.getInstance().setOfflineMode(bOfflineMode);
		StorageManager.getSettings(getApplicationContext()).setOfflineMode(bOfflineMode);
		StorageManager.getSettings(getApplicationContext()).commitChanges();
		ScannerHandler.getInstance().clearSpots();
		ScannerHandler.getInstance().resume();
		finish();
	}
}
