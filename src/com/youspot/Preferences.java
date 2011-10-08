package com.youspot;

import com.youspot.service.ScannerHandler;
import com.youspot.storage.Settings;
import com.youspot.storage.StorageManager;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;

public class Preferences extends Activity {

	private CheckBox m_chk_reportown = null;
	private CheckBox m_chk_offline = null;
	private CheckBox m_chk_vibrate = null;
	private Button m_btn_cancel = null;
	private Button m_btn_save = null;
	private Settings m_settings = null;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		this.setContentView(R.layout.settings);
		
		/* Set callbacks */
		m_btn_cancel = (Button)findViewById(R.id.settings_cancel);
		m_btn_cancel.setOnClickListener(new OnClickListener(){
			public void onClick(View v) {
				Preferences.this.onCancel();
			}
		});
		
		m_btn_save = (Button)findViewById(R.id.settings_save);
		m_btn_save.setOnClickListener(new OnClickListener(){
			public void onClick(View v) {
				Preferences.this.onSave();
			}
		});
		
		/* Get checkboxes */
		m_chk_reportown = (CheckBox)findViewById(R.id.settings_own);
		m_chk_offline = (CheckBox)findViewById(R.id.settings_offline);
		m_chk_vibrate = (CheckBox)findViewById(R.id.settings_vibrate);
		
		/* Load settings if any */
		m_settings = StorageManager.getSettings(getApplicationContext());
		if (m_settings.isOfflineModeSet())
			m_chk_offline.setChecked(true);
		else
			m_chk_offline.setChecked(false);
		
		if (!m_settings.shouldReportOwn())
			m_chk_reportown.setChecked(true);
		else
			m_chk_reportown.setChecked(false);
		
		if (m_settings.shouldVibrate())
			m_chk_vibrate.setChecked(true);
		else
			m_chk_vibrate.setChecked(false);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
	}
	
	public void onCancel() {
		finish();
	}
	
	public void onSave() {
		if (m_chk_offline.isChecked())
		{
			m_settings.setOfflineMode(true);
			ScannerHandler.getInstance().setOfflineMode(true);
			ScannerHandler.getInstance().clearSpots();
		}
		else
		{
			if (m_settings.isOfflineModeSet())
			{
				Intent synchro_int = new Intent(getApplicationContext(), Synchro.class);
				startActivity(synchro_int);
			}
		}
		if (m_chk_reportown.isChecked())
			m_settings.setReportOwn(false);
		else
			m_settings.setReportOwn(true);

		if (m_chk_vibrate.isChecked())
			m_settings.setVibrate(true);
		else
			m_settings.setVibrate(false);
		
		/* Save changes to database */
		m_settings.commitChanges();
		
		/* Close activity */
		finish();
	}
	
}
