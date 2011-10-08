package com.youspot.ws.types;

import com.youspot.ws.types.Request;

public class RequestEnveloppe {
	
	private Class<? extends Object> m_oRespClass = null;
	private Request m_oContent = null;
	
	public RequestEnveloppe(Request oContent, Object oResponse) {
		this.m_oRespClass = oResponse.getClass();
		this.m_oContent = oContent;
	}
	
	public Class<? extends Object> getResponseClass() {
		return this.m_oRespClass;
	}
	
	public Request getRequest() {
		return this.m_oContent;
	}
}	
