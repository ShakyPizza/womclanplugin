package com.womclan;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WomHistoryTest
{
	@Test
	public void loadedCarriesTheEntries()
	{
		WomHistory<String> history = WomHistory.loaded(Arrays.asList("a", "b"));

		assertTrue(history.isAvailable());
		assertEquals(2, history.getEntries().size());
		assertNull(history.getError());
	}

	@Test
	public void anEmptySuccessIsStillAvailable()
	{
		WomHistory<String> history = WomHistory.loaded(Arrays.asList());

		assertTrue("an empty result means no recent events, not a failed fetch", history.isAvailable());
		assertTrue(history.getEntries().isEmpty());
	}

	@Test
	public void unavailableKeepsTheReason()
	{
		WomHistory<String> history = WomHistory.unavailable("WOM API error 503");

		assertFalse(history.isAvailable());
		assertTrue(history.getEntries().isEmpty());
		assertEquals("WOM API error 503", history.getError());
	}

	@Test
	public void pendingIsNeitherLoadedNorFailed()
	{
		WomHistory<String> history = WomHistory.pending();

		assertEquals(WomHistory.Status.PENDING, history.getStatus());
		assertFalse(history.isAvailable());
	}
}
