package com.youspot.ws;

import java.util.UUID;

public interface IJsonWSClient {
	public void onWSResponse(UUID uuid, int status, Object response);
	public void onWSError(UUID uuid);
}
