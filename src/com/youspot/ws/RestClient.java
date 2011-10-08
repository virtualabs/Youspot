package com.youspot.ws;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

import android.os.Handler;
import android.util.Log;

import com.youspot.ws.AsyncHTTPClient;

public class RestClient {


	/*
	 * Available HTTP methods
	 */
	
	public enum RequestMethod
	{
	GET,
	POST
	};
		
	
	/*
	 * Worker wrapper
	 */
	
	private class HTTPWorker {
		private AsyncHTTPClient m_client;
		public HTTPWorker(AsyncHTTPClient client) {
			this.m_client = client;
		}
		public boolean isDone() {
			return this.m_client.isDone();
		}
		public void close() {
			this.m_client.closeConnection();
		}
	}
	
	
	public class RequestParams {
		
		private ArrayList <NameValuePair>		m_params;
	    private ArrayList <NameValuePair>		m_headers;
		
	    public RequestParams() {
	        m_params = new ArrayList<NameValuePair>();
	        m_headers = new ArrayList<NameValuePair>();
	    }
	    
	    /*
	     * Add a parameter
	     */
	    
	    public void AddParam(String name, String value)
	    {
	        m_params.add(new BasicNameValuePair(name, value));
	    }

	    
	    /*
	     * Add an HTTP header
	     */
	    
	    public void AddHeader(String name, String value)
	    {
	        m_headers.add(new BasicNameValuePair(name, value));
	    }
	    
	    public ArrayList <NameValuePair> getParams() {
	    	return m_params;
	    }
	    
	    public ArrayList <NameValuePair> getHeaders() {
	    	return m_headers;
	    }
	    
	    public String toString() {
	    	try
	    	{
	            String combinedParams = "";
	            if(!m_params.isEmpty()){
	                combinedParams += "?";
	                for(NameValuePair p : m_params)
	                {
	                    String paramString = p.getName() + "=" + URLEncoder.encode(p.getValue(),"UTF-8");
	                    if(combinedParams.length() > 1)
	                    {
	                        combinedParams  +=  "&" + paramString;
	                    }
	                    else
	                    {
	                        combinedParams += paramString;
	                    }
	                }
	            }
	            return combinedParams;
	    	}
	    	catch(Exception e)
	    	{
	    		Log.e("RestClient","Unable to serialize request parameters");
	    		return new String("");
	    	}
	    }
	}
	
	/*
	 * Properties
	 */
	
	private ConcurrentHashMap <UUID, HTTPWorker>	 	m_workers;
    private String 										m_url;
    private int 										m_responseCode;
    private String 										m_message;
    private String 										m_response;
    private int 										MAX_WORKERS=4;

    
    /*
     * Constructor
     */
    
    public RestClient(String url)
    {
        this.m_url = url;
        m_workers = new ConcurrentHashMap<UUID, HTTPWorker>();
    }


    
    /*
     * Execute a request
     */
    
    public boolean Execute(RequestMethod method, RequestParams params, UUID uuid, IRestClientResponseListener listener) throws Exception
    {
        switch(method) {
            case GET:
            {

                HttpGet request = new HttpGet(m_url + params.toString());

                //add headers
                for(NameValuePair h : params.getHeaders())
                {
                    request.addHeader(h.getName(), h.getValue());
                }

                return executeAsyncRequest(request, m_url, uuid, listener);
            }
            case POST:
            {
                HttpPost request = new HttpPost(m_url);

                //add headers
                for(NameValuePair h : params.getHeaders())
                {
                    request.addHeader(h.getName(), h.getValue());
                }

                if(!params.getParams().isEmpty()){
                	Log.i("RestClient","Generate http request parameters string");
                    request.setEntity(new UrlEncodedFormEntity(params.getParams(), HTTP.UTF_8));
                }
                Log.i("RestClient","Execute async request");
                return executeAsyncRequest(request, m_url, uuid, listener);
            }
            default:
            {
            	return false;
            }
        }
    }

    /*
     * Asynchronous HTTP Request
     */
    
    private boolean executeAsyncRequest(HttpUriRequest request, String url, UUID uuid, IRestClientResponseListener listener)
    {
    	/* Check if some workers are done ... */
    	synchronized (m_workers) {
	    	for (UUID worker_uuid : m_workers.keySet())
	    		if (m_workers.get(worker_uuid).isDone())
	    			m_workers.remove(worker_uuid);
	    	
	    	if (m_workers.size()<MAX_WORKERS)
	    	{
	    		/* Create a working thread */
	    		AsyncHTTPClient client = new AsyncHTTPClient(request, uuid, new Handler(), new AsyncListener(listener));
	    		m_workers.put(uuid, new HTTPWorker(client));
	    		client.start();
	    	}
	    	else
	    		return false;
    	}
    	
    	return true;
    }
     
    
    /*
     * Request termination
     */
    
    public void closeAllWorkers() {
    	for (HTTPWorker worker : m_workers.values())
    		worker.close();
    }

    /*
     * Getters
     */

    public String getResponse() {
        return m_response;
    }

    public String getErrorMessage() {
        return m_message;
    }

    public int getResponseCode() {
        return m_responseCode;
    }

}