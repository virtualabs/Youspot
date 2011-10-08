package com.youspot;

import android.app.Service;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

public class ShakeToShare implements SensorEventListener {

	private SensorManager m_sensor;
	private long lastUpdate = -1;
	private float x, y, z;
	private float last_x, last_y, last_z, last_dir=0, prev_dir=0;
	private static final int SHAKE_THRESHOLD = 600;
	private static ShakeToShare m_instance = null;
	
	private ShakeToShare(Context context) {
		m_sensor = (SensorManager) context.getSystemService(Service.SENSOR_SERVICE);
        boolean accelSupported = m_sensor.registerListener(
        		this,
        		m_sensor.getDefaultSensor(SensorManager.SENSOR_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME);
        if (!accelSupported)
        	m_sensor.unregisterListener(this);
	}
	
	public static ShakeToShare getInstance(Context context) {
		if (m_instance == null)
			m_instance = new ShakeToShare(context);
		return m_instance;
	}
	
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		
		
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event.sensor.getType() == SensorManager.SENSOR_ACCELEROMETER) {
			long curTime = System.currentTimeMillis();
			//only allow one update every 100ms.
			if ((curTime - lastUpdate) > 100) {
				long diffTime = (curTime - lastUpdate);
				lastUpdate = curTime;

				x = event.values[SensorManager.DATA_X];
				y = event.values[SensorManager.DATA_Y];
				z = event.values[SensorManager.DATA_Z];

				float speed = Math.abs(x+y+z - last_x - last_y - last_z) / diffTime * 10000;
				if (last_y!=y)
				{
					last_dir = (y-last_y)/Math.abs(y - last_y);
					if ((prev_dir != 0.0) && (last_dir == -prev_dir) && (speed>SHAKE_THRESHOLD))
					{
						Log.d("sensor", "shake detected w/ speed: " + speed);
						//Toast.makeText(m_context, "shake detected w/ speed: " + speed, Toast.LENGTH_SHORT).show();						
					}
					else if (prev_dir == 0.0)
						prev_dir = last_dir;
				}
				else
					last_dir = 0;

				last_x = x;
				last_y = y;
				last_z = z;
			}
		}		
	}
}
