package com.youspot.ws;

import java.util.UUID;

import com.youspot.ws.IRestClientResponseListener;

public class AsyncListener implements Runnable {

	private IRestClientResponseListener m_listener;
	private String m_response;
	private int m_status;
	private UUID m_uuid;
	private boolean m_failed = false;
	
	public AsyncListener(IRestClientResponseListener listener) {
		this.m_listener = listener;
	}
	
	public void run() {
		if (!m_failed)
			this.m_listener.onResponseReceived(m_uuid, m_status, m_response);
		else
			this.m_listener.onResponseError(m_uuid);
	}
	
	public void setStatus(int status) {
		this.m_status = status;
	}
	
	public void setUUID(UUID uuid) {
		this.m_uuid = uuid;
	}
	
	public void setResponse(String response) {
		this.m_response = response;
	}
	
	public void notifyError(UUID uuid) {
		this.m_uuid = uuid;
		this.m_failed = true;
	}
	
}
