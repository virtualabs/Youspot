package com.youspot.ws;

import java.util.UUID;

public interface IRestClientResponseListener {
	public void onResponseReceived(UUID uuid, int status, String response);
	public void onResponseError(UUID uuid);
}
