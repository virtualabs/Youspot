package com.youspot.ws;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import android.location.Location;
import android.util.Log;


import com.youspot.WifiSpot;
import com.youspot.WifiSpotLocation;
import com.youspot.ws.IJsonWSClient;
import com.youspot.ws.types.Request;
import com.youspot.ws.types.RequestEnveloppe;
import com.youspot.ws.types.Response;

public class WebService implements IJsonWSClient {

	private final String WS_URL = "http://www.youspot.org/ws3.php";
	
	enum ACTION {
		WS_REGISTER,
		WS_MAP,
		WS_LOGIN,
		WS_STATS
	}
	
	/*
	 * Requests & Responses (Serializable)
	 */
	
	
	/* WS stats request */
	public class StatsRequest extends Request {
		private String userid;
	
		public StatsRequest(String userid) {
			super("stats");
			this.setUserid(userid);
		}

		public void setUserid(String userid) {
			this.userid = userid;
		}

		public String getUserid() {
			return userid;
		}
	}
	
	/* WS Stats response */
	public static class StatsResponse extends Response {
		public int registered;
		public int total;
	}
	
	public class LoginRequest extends Request {
		private String userid;
		
		public LoginRequest(String userid) {
			super("login");
			this.setUserid(userid);
		}

		public void setUserid(String userid) {
			this.userid = userid;
		}

		public String getUserid() {
			return userid;
		}
	}
	
	public static class LoginResponse extends Response {
		private String token = "";
		private String userid= "";
		
		public LoginResponse() {
		}
				
		public String getToken() {
			return token;
		}
		
		public String getUserId() {
			return userid;
		}
	}
	
	/* WS spot registering request */
	public class RegisterSpotRequest extends Request {
		
		private ArrayList<WifiSpot> spot;
		private String token;
		
		public RegisterSpotRequest(ArrayList<WifiSpot> spot, String token) {
			super("register_spot");
			this.spot = spot;
			this.setToken(token);
		}
		
		public ArrayList<WifiSpot> getSpot() {
			return this.spot;
		}

		public void setToken(String token) {
			this.token = token;
		}

		public String getToken() {
			return token;
		}
	}
	
	
	/* WS spot registering response */
	public static class RegisterSpotResponse extends Response{
		private boolean answer;
		private WifiSpot[] spots;
		
		public RegisterSpotResponse() {
		}
		
		public boolean getAnswer() {
			return this.answer;
		}
		
		public WifiSpot[] getSpots() {
			return this.spots;
		}
	}
	
	/* WS spot map request */
	public class MapSpotsRequest extends Request {
		double latitude;
		double longitude;
		
		public MapSpotsRequest(Location location) {
			super("map_spots");
			
			this.latitude = location.getLatitude();
			this.longitude = location.getLongitude();
		}
	}
	
	/* WS spot map response */
	public static class MapSpotsResponse extends Response {
		private boolean answer;
		private double latitude;
		private double longitude;
		private WifiSpotLocation[] spots;

		public boolean getAnswer() {
			return this.answer;
		}
		
		public WifiSpotLocation[] getSpots() {
			return this.spots;
		}
		
		public double getLatitude() {
			return latitude;
		}
		
		public double getLongitude() {
			return longitude;
		}
	}
	
	
	public static class WebServiceClient {
		public void onSpotRegistered(RegisterSpotResponse response){};
		public void onCannotRegisterSpot(){};
		public void onUpdateMapSpots(MapSpotsResponse response){};
		public void onCannotUpdateMapSpots() {};
		public void onUpgradeRequired(String uri, String changelog){};
		public void onCannotCheckUpgrade(){};
		public void onClientLoggedIn(String userid, String token){};
		public void onCannotLogIn(){};
		public void onStatsReceived(int registered, int total){};
	}

	
	private class WebServiceIntent {
		
		private ACTION m_action;
		private WebServiceClient m_client;
		
		public WebServiceIntent(ACTION action, WebServiceClient client) {
			this.m_action = action;
			this.m_client = client;
		}
		
		public ACTION getAction() {
			return this.m_action;
		}
		
		public WebServiceClient getClient() {
			return this.m_client;
		}
	}

	
	/*
	 * Properties
	 */
	
	private JsonWSClient client;
	private HashMap<UUID, WebServiceIntent> m_requests;
	private static WebService m_instance = null;
	
	public static WebService getInstance() {
		if (m_instance == null)
			m_instance = new WebService();
		return m_instance;
	}
	
	private WebService() {
		this.client = new JsonWSClient(WS_URL);
		this.m_requests = new HashMap<UUID, WebServiceIntent>();
	}
	
	/*
	 * WebService Async HTTP requests dispatcher
	 */
	
	public void onWSResponse(UUID uuid, int status, Object response) {
		WebServiceIntent intent;
		Response resp;
		if (m_requests.containsKey(uuid))
		{
			intent = m_requests.get(uuid);
			switch(intent.getAction())
			{
				case WS_REGISTER:
					{
						resp = (RegisterSpotResponse)response;
						intent.getClient().onSpotRegistered((RegisterSpotResponse)resp);
					}
					break;
				
				case WS_MAP:
					{
						resp = (MapSpotsResponse)response;
						if (((MapSpotsResponse)resp).spots.length>0)
							intent.getClient().onUpdateMapSpots((MapSpotsResponse)resp);
					}
					break;
										
				case WS_LOGIN:
					{
						LoginResponse r = (LoginResponse)response;
						intent.getClient().onClientLoggedIn(r.getUserId(),r.getToken());
					}
					break;
					
				case WS_STATS:
					{
						StatsResponse r = (StatsResponse)response;
						intent.getClient().onStatsReceived(r.registered, r.total);
					}
					break;
				
				default:
					break;
			}
		}
	}
	
	public void onWSError(UUID uuid) {
		WebServiceIntent intent;
		if (m_requests.containsKey(uuid))
		{
			intent = m_requests.get(uuid);
			switch(intent.getAction())
			{
				case WS_REGISTER:
					intent.getClient().onCannotRegisterSpot();
					break;
				
				case WS_MAP:
					intent.getClient().onCannotUpdateMapSpots();
					break;
					
				case WS_LOGIN:
					intent.getClient().onCannotLogIn();
					break;
				
				default:
					break;
			}
		}
	}
	
	
	private boolean sendRequest(RequestEnveloppe env, ACTION action, WebServiceClient listener)
	{
		try
		{
			/* Save the task */
			UUID uuid = UUID.randomUUID();
			m_requests.put(uuid, new WebServiceIntent(action, listener));
			Log.i("WifiService","Querying remote ws ...");
			return this.client.Execute(uuid, env, this);
		}
		catch(Exception e)
		{
			Log.e("WebService", "Unable to query remote ws !");
			e.printStackTrace();
			return false;
		}
	}
	
	/*
	 * WebService interface
	 */
	
	public boolean registerSpot(ArrayList<WifiSpot> spot, String token, WebServiceClient listener) {	
		Log.i("WifiService","Registering new spots");
		RegisterSpotRequest req = new RegisterSpotRequest(spot, token);
		RegisterSpotResponse resp = new RegisterSpotResponse();
		RequestEnveloppe env = new RequestEnveloppe (req, resp);
		return sendRequest(env, ACTION.WS_REGISTER, listener);
	}
	
	public boolean findSpots(Location location, WebServiceClient listener) {
		Log.i("WifiService","Finding spots based on location");
		MapSpotsRequest req = new MapSpotsRequest(location);
		MapSpotsResponse resp = new MapSpotsResponse();
		RequestEnveloppe env = new RequestEnveloppe(req, resp);
		return sendRequest(env, ACTION.WS_MAP, listener);
	}
	

	public boolean userLogin(String userid, WebServiceClient listener) {
		Log.i("WebService","Checking current version");
		LoginRequest req = new LoginRequest(userid);
		LoginResponse resp = new LoginResponse();
		RequestEnveloppe env = new RequestEnveloppe(req,resp);
		return sendRequest(env, ACTION.WS_LOGIN, listener);
	}
	
	public boolean getUserStats(String userid, WebServiceClient listener) {
		Log.i("WebService","Checking current version");
		StatsRequest req = new StatsRequest(userid);
		StatsResponse resp = new StatsResponse();
		RequestEnveloppe env = new RequestEnveloppe(req,resp);
		return sendRequest(env, ACTION.WS_STATS, listener);
	}
	
	public void close() {
		this.client.closeAllWorkers();
	}
}
