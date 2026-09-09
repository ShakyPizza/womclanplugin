package com.womclan;

/**
 * The single source of truth for refresh state: whether a fetch is in flight, when the last one
 * succeeded, and how much of the manual cooldown is left.
 *
 * <p>Owned by {@link WomClanPlugin} rather than by a panel so every surface that offers a refresh
 * shares one cooldown instead of inventing its own. Mutated from the fetch executor and read from
 * the EDT, hence the synchronisation; readers take an immutable {@link WomSyncStatus} snapshot so
 * they cannot observe a half-updated state.</p>
 */
class WomSyncState
{
	/** Manual refreshes are limited to one per this interval, to stay friendly to the WOM API. */
	static final long MANUAL_COOLDOWN_MS = 5 * 60 * 1_000L;

	enum Outcome
	{
		NEVER,
		CACHED,
		SUCCESS,
		FAILURE,
		NOT_CONFIGURED
	}

	private long manualAllowedAtMs;
	private long serverRetryAtMs;
	private long lastSuccessMs;
	private int rateLimit = -1;
	private int rateRemaining = -1;
	private long rateResetAtMs;
	private boolean fetching;
	private Outcome outcome = Outcome.NEVER;
	private String errorMessage;

	/**
	 * Claims the right to start a user-requested fetch, starting the manual cooldown.
	 *
	 * @return false when a fetch is already running or the cooldown has not elapsed
	 */
	synchronized boolean beginManualFetch(long now)
	{
		if (fetching || cooldownRemaining(now) > 0)
		{
			return false;
		}

		manualAllowedAtMs = now + MANUAL_COOLDOWN_MS;
		fetching = true;
		return true;
	}

	/**
	 * Claims the right to start an automatic fetch. Deliberately leaves the manual cooldown alone:
	 * an hourly refresh should neither extend nor clear the user's own five-minute budget.
	 *
	 * @return false when a fetch is already running
	 */
	synchronized boolean beginAutoFetch(long now)
	{
		if (fetching || serverRetryAtMs > now)
		{
			return false;
		}

		fetching = true;
		return true;
	}

	/** Seeds the panel with disk-cached data while a network refresh is pending. */
	synchronized void recordCached(long cachedAtMs)
	{
		if (lastSuccessMs == 0)
		{
			lastSuccessMs = cachedAtMs;
			outcome = Outcome.CACHED;
		}
	}

	/** Restores gates that must survive a plugin or RuneLite restart. */
	synchronized void restoreDeadlines(long manualAllowedAtMs, long serverRetryAtMs)
	{
		this.manualAllowedAtMs = Math.max(0, manualAllowedAtMs);
		this.serverRetryAtMs = Math.max(0, serverRetryAtMs);
	}

	/** Applies the authoritative request budget and backoff returned by WOM. */
	synchronized void applyRateLimit(WomRateLimitStatus status)
	{
		if (status == null)
		{
			return;
		}
		rateLimit = status.getLimit();
		rateRemaining = status.getRemaining();
		rateResetAtMs = status.getResetAtMs();
		serverRetryAtMs = Math.max(serverRetryAtMs, status.getRetryAtMs());
	}

	synchronized void recordSuccess(long now)
	{
		fetching = false;
		lastSuccessMs = now;
		outcome = Outcome.SUCCESS;
		errorMessage = null;
	}

	synchronized void recordFailure(String error)
	{
		fetching = false;
		outcome = Outcome.FAILURE;
		errorMessage = error;
	}

	synchronized void recordNotConfigured()
	{
		fetching = false;
		outcome = Outcome.NOT_CONFIGURED;
		errorMessage = null;
	}

	/** Forgets everything but the manual cooldown, which is a rate limit rather than group state. */
	synchronized void reset()
	{
		lastSuccessMs = 0;
		outcome = Outcome.NEVER;
		errorMessage = null;
	}

	synchronized WomSyncStatus snapshot(long now)
	{
		return new WomSyncStatus(outcome, fetching, errorMessage, lastSuccessMs, cooldownRemaining(now),
			rateLimit, rateRemaining, rateResetAtMs, Math.max(0, serverRetryAtMs - now));
	}

	synchronized long getManualAllowedAtMs()
	{
		return manualAllowedAtMs;
	}

	synchronized long getServerRetryAtMs()
	{
		return serverRetryAtMs;
	}

	private long cooldownRemaining(long now)
	{
		// Clamped on both ends so a system clock that jumps cannot strand the button.
		long manualRemaining = Math.max(0, Math.min(MANUAL_COOLDOWN_MS, manualAllowedAtMs - now));
		long serverRemaining = Math.max(0, serverRetryAtMs - now);
		return Math.max(manualRemaining, serverRemaining);
	}
}
