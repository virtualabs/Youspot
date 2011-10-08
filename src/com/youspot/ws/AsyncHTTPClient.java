package com.youspot.ws;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.UUID;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import android.os.Handler;
import android.util.Log;
import com.youspot.ws.AsyncListener;

/*
 * Asynchronous working thread (used to send HTTP requests)
 */

public class AsyncHTTPClient extends Thread {
	
	private AsyncListener m_listener;
	private HttpUriRequest m_request = null;
	private HttpClient m_client = new DefaultHttpClient();
	private UUID m_uuid = null;
	private int m_responseCode;
	private Handler m_handler;
	private boolean finished = false;
	
	public AsyncHTTPClient(HttpUriRequest request, UUID reqid, Handler handler, AsyncListener listener) {
		this.m_listener = listener;
		this.m_request = request;
		this.m_uuid = reqid;
		this.m_handler = handler;
	}
	
	public void run() {
		String szResponse = new String();
		try
		{
			final HttpResponse response;
			synchronized (m_client) {
				Log.i("AsyncHTTPClient","Executing request");
				response = getClient().execute(m_request);
			}

            m_responseCode = response.getStatusLine().getStatusCode();
            //message = response.getStatusLine().getReasonPhrase();

            HttpEntity entity = response.getEntity();

            if (entity != null) {

                InputStream instream = entity.getContent();
                szResponse = convertStreamToString(instream);

                // Closing the input stream will trigger connection release
                instream.close();
            }
			Log.i("AsyncHTTPClient","Got answer: "+szResponse);
			m_listener.setResponse(szResponse);
			m_listener.setUUID(m_uuid);
			m_listener.setStatus(m_responseCode);
			Log.i("AsyncHTTPClient","Notify listener");
			m_handler.post(m_listener);
		}
		catch (ClientProtocolException e)
		{
			Log.e("AsyncRestClient","Protocol Exception");
			m_listener.notifyError(m_uuid);
		}
		catch (IOException e)
		{
			Log.e("AsyncRestClient","IOException");
			e.printStackTrace();
			m_listener.notifyError(m_uuid);
			Log.i("AsyncHTTPClient","Notify listener");
			m_handler.post(m_listener);
		}
		/* Worker is done */
		finished = true;
	}
	
	public void closeConnection() {
		try
		{
			m_client.getConnectionManager().closeExpiredConnections();
			m_client.getConnectionManager().shutdown();
		}
		catch(IllegalStateException e)
		{
			Log.e("No3g.AsyncHTTPClient","Could not close connections");
		}
	}
    
    private static String convertStreamToString(InputStream is) {

        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();

        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }
	
	
	private HttpClient getClient() {
		return this.m_client;
	}
	
	public UUID getUUID() {
		return this.m_uuid;
	}
	
	public boolean isDone() {
		return this.finished;
	}
}
