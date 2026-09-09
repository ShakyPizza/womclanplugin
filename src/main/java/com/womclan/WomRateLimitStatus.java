package com.womclan;

import lombok.Value;

/** The latest request-budget information reported by the WOM API. */
@Value
class WomRateLimitStatus
{
	static final WomRateLimitStatus UNKNOWN = new WomRateLimitStatus(-1, -1, 0, 0);

	int limit;
	int remaining;
	long resetAtMs;
	long retryAtMs;

	boolean isKnown()
	{
		return limit >= 0 && remaining >= 0;
	}
}
