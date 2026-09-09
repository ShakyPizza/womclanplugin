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

	int rateLimit;
	int rateRemaining;
	long rateResetAtMs;
	long serverBackoffRemainingMs;

	/** Keeps call sites and focused formatter tests concise when no API headers are involved. */
	WomSyncStatus(WomSyncState.Outcome outcome, boolean fetching, String errorMessage,
		long lastSuccessMs, long cooldownRemainingMs)
	{
		this(outcome, fetching, errorMessage, lastSuccessMs, cooldownRemainingMs, -1, -1, 0, 0);
	}

	WomSyncStatus(WomSyncState.Outcome outcome, boolean fetching, String errorMessage,
		long lastSuccessMs, long cooldownRemainingMs, int rateLimit, int rateRemaining,
		long rateResetAtMs, long serverBackoffRemainingMs)
	{
		this.outcome = outcome;
		this.fetching = fetching;
		this.errorMessage = errorMessage;
		this.lastSuccessMs = lastSuccessMs;
		this.cooldownRemainingMs = cooldownRemainingMs;
		this.rateLimit = rateLimit;
		this.rateRemaining = rateRemaining;
		this.rateResetAtMs = rateResetAtMs;
		this.serverBackoffRemainingMs = serverBackoffRemainingMs;
	}

	boolean isManualSyncAllowed()
	{
		return !fetching && cooldownRemainingMs == 0;
	}

	boolean hasSucceeded()
	{
		return lastSuccessMs > 0;
	}

	boolean hasRateLimit()
	{
		return rateLimit >= 0 && rateRemaining >= 0;
	}
}
