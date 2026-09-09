package com.womclan;

import lombok.Value;

/** An immutable view of {@link WomSyncState} taken at one instant, for the UI to render. */
@Value
class WomSyncStatus
{
	WomSyncState.Outcome outcome;
	boolean fetching;
	String errorMessage;

	/** Epoch millis of the last successful fetch, or 0 if there has never been one. */
	long lastSuccessMs;

	/** Milliseconds left on the manual cooldown, or 0 when a manual refresh is allowed. */
	long cooldownRemainingMs;

	boolean isManualSyncAllowed()
	{
		return !fetching && cooldownRemainingMs == 0;
	}

	boolean hasSucceeded()
	{
		return lastSuccessMs > 0;
	}
}
