package com.womclan;

import java.io.IOException;

/** An HTTP failure from WOM, retaining its status code for backoff decisions. */
class WomApiException extends IOException
{
	private final int statusCode;
	private final long retryAtMs;

	WomApiException(String message, int statusCode, long retryAtMs)
	{
		super(message);
		this.statusCode = statusCode;
		this.retryAtMs = retryAtMs;
	}

	int getStatusCode()
	{
		return statusCode;
	}

	long getRetryAtMs()
	{
		return retryAtMs;
	}
}
