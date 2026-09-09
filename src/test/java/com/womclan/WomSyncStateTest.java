package com.womclan;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WomSyncStateTest
{
	private static final long T0 = 1_700_000_000_000L;

	@Test
	public void manualFetchStartsTheCooldown()
	{
		WomSyncState state = new WomSyncState();

		assertTrue(state.beginManualFetch(T0));
		state.recordSuccess(T0 + 500);

		WomSyncStatus status = state.snapshot(T0 + 500);
		assertFalse("a successful fetch must not end the cooldown early", status.isManualSyncAllowed());
		assertEquals(WomSyncState.MANUAL_COOLDOWN_MS - 500, status.getCooldownRemainingMs());
	}

	@Test
	public void manualFetchIsRefusedDuringTheCooldown()
	{
		WomSyncState state = new WomSyncState();
		assertTrue(state.beginManualFetch(T0));
		state.recordSuccess(T0);

		assertFalse(state.beginManualFetch(T0 + WomSyncState.MANUAL_COOLDOWN_MS - 1));
		assertTrue(state.beginManualFetch(T0 + WomSyncState.MANUAL_COOLDOWN_MS));
	}

	@Test
	public void manualFetchIsRefusedWhileAFetchIsRunning()
	{
		WomSyncState state = new WomSyncState();
		assertTrue(state.beginAutoFetch(T0));
		assertFalse(state.beginManualFetch(T0));
		assertFalse(state.snapshot(T0).isManualSyncAllowed());
	}

	@Test
	public void autoFetchDoesNotResetTheManualCooldown()
	{
		WomSyncState state = new WomSyncState();
		assertTrue(state.beginManualFetch(T0));
		state.recordSuccess(T0);

		long later = T0 + 60_000;
		assertTrue(state.beginAutoFetch(later));
		state.recordSuccess(later);

		WomSyncStatus status = state.snapshot(later);
		assertEquals(WomSyncState.MANUAL_COOLDOWN_MS - 60_000, status.getCooldownRemainingMs());
		assertEquals(later, status.getLastSuccessMs());
	}

	@Test
	public void concurrentAutoFetchesAreSkipped()
	{
		WomSyncState state = new WomSyncState();
		assertTrue(state.beginAutoFetch(T0));
		assertFalse(state.beginAutoFetch(T0));
		state.recordSuccess(T0);
		assertTrue(state.beginAutoFetch(T0));
	}

	@Test
	public void failureKeepsTheLastSuccessTimestamp()
	{
		WomSyncState state = new WomSyncState();
		state.beginAutoFetch(T0);
		state.recordSuccess(T0);

		state.beginAutoFetch(T0 + 1_000);
		state.recordFailure("boom");

		WomSyncStatus status = state.snapshot(T0 + 1_000);
		assertEquals(WomSyncState.Outcome.FAILURE, status.getOutcome());
		assertEquals("boom", status.getErrorMessage());
		assertEquals(T0, status.getLastSuccessMs());
		assertFalse(status.isFetching());
	}

	@Test
	public void aClockJumpCannotStrandTheButton()
	{
		WomSyncState state = new WomSyncState();
		assertTrue(state.beginManualFetch(T0));
		state.recordSuccess(T0);

		// System clock moves backwards a day.
		long remaining = state.snapshot(T0 - 86_400_000L).getCooldownRemainingMs();
		assertEquals(WomSyncState.MANUAL_COOLDOWN_MS, remaining);
	}

	@Test
	public void resetForgetsTheGroupButKeepsTheRateLimit()
	{
		WomSyncState state = new WomSyncState();
		assertTrue(state.beginManualFetch(T0));
		state.recordSuccess(T0);

		state.reset();

		WomSyncStatus status = state.snapshot(T0 + 1_000);
		assertEquals(WomSyncState.Outcome.NEVER, status.getOutcome());
		assertFalse(status.hasSucceeded());
		assertEquals(WomSyncState.MANUAL_COOLDOWN_MS - 1_000, status.getCooldownRemainingMs());
	}

	@Test
	public void restoredDeadlinesStillGateRequestsAfterRestart()
	{
		WomSyncState state = new WomSyncState();
		state.restoreDeadlines(T0 + 120_000, T0 + 45_000);

		assertFalse(state.beginManualFetch(T0));
		assertFalse(state.beginAutoFetch(T0));
		assertEquals(120_000, state.snapshot(T0).getCooldownRemainingMs());
		assertTrue(state.beginAutoFetch(T0 + 45_000));
	}

	@Test
	public void serverRetryAfterCanOutlastManualCooldown()
	{
		WomSyncState state = new WomSyncState();
		state.applyRateLimit(new WomRateLimitStatus(20, 0, T0 + 600_000, T0 + 600_000));

		WomSyncStatus status = state.snapshot(T0);
		assertEquals(600_000, status.getCooldownRemainingMs());
		assertEquals(600_000, status.getServerBackoffRemainingMs());
		assertFalse(status.isManualSyncAllowed());
	}

	@Test
	public void cachedDataHasItsOwnOutcome()
	{
		WomSyncState state = new WomSyncState();
		state.recordCached(T0);

		WomSyncStatus status = state.snapshot(T0 + 1_000);
		assertEquals(WomSyncState.Outcome.CACHED, status.getOutcome());
		assertEquals(T0, status.getLastSuccessMs());
	}
}
