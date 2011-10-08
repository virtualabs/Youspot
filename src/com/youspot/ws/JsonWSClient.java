package com.youspot.ws;

import java.util.HashMap;
import java.util.UUID;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.youspot.ws.IJsonWSClient;
import com.youspot.ws.IRestClientResponseListener;
import com.youspot.ws.types.RequestEnveloppe;

public class JsonWSClient extends RestClient implements IRestClientResponseListener {

	private class JsonWSIntent {
		private IJsonWSClient m_listener;
		private RequestEnveloppe m_request;
		
		public JsonWSIntent(RequestEnveloppe requestEnv, IJsonWSClient listener) {
			this.m_listener = listener;
			this.m_request = requestEnv;
		}
		
		public IJsonWSClient getClient() {
			return this.m_listener;
		}
		
		public RequestEnveloppe getEnveloppe() {
			return this.m_request;
		}
	}
	
	private Object m_Result;
	private HashMap<UUID, JsonWSIntent> m_requests;
			
	public JsonWSClient(String URL) {
		super(URL);
		this.m_requests = new HashMap<UUID, JsonWSIntent>(); 
	}
	
	
	/*
	 * Send an asynchronous request containing a single post param
	 * (named "object") serialized as JSON.
	 */
	
	public boolean Execute(UUID uuid, RequestEnveloppe request, IJsonWSClient listener) throws Exception {
		RequestParams params = new RequestParams();
		Gson gson = new Gson();
		
		Log.i("JsonWSClient","JSON: "+gson.toJson(request.getRequest()));
		params.AddParam("object",gson.toJson(request.getRequest()));
		
		/* Call the original method */
		try
		{
			JsonWSIntent intent = new JsonWSIntent(request, listener);
			m_requests.put(uuid, intent);
			return super.Execute(RequestMethod.POST, params, uuid, this);
		}
		catch (Exception e)
		{
			Log.e("Json WS client","Error while executing remote ws query !");
			e.printStackTrace();
		}
		return false;
	}
	
	
	public void onResponseReceived(UUID uuid, int status, String response) {
		/* Parse the JSON */
		Log.i("JsonWSClient","Got answer: "+response);
		GsonBuilder gsonb = new GsonBuilder();
		Gson gson = gsonb.create();
		JsonWSIntent intent;
		try
		{
			if (m_requests.containsKey(uuid))
			{
				intent = m_requests.get(uuid);
				intent.getClient().onWSResponse(uuid, status, gson.fromJson(response, intent.getEnveloppe().getResponseClass()));
			}
		}
		catch(Exception e)
		{
			Log.e("JsonWSClient","Error while processing HTTP response");
			e.printStackTrace();
		}
	}
	
	public void onResponseError(UUID uuid) {
		JsonWSIntent intent;
		Log.i("JsonWSClient","Got error");
		if (m_requests.containsKey(uuid))
		{
			intent = m_requests.get(uuid);
			if (intent != null)
				intent.getClient().onWSError(uuid);
		}
	}
	
	public Object getResult() {
		return this.m_Result;
	}
}
