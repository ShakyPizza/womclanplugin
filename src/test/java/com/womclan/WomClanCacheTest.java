package com.womclan;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WomClanCacheTest
{
	private static final int GROUP_ID = 2300;
	private static final String GROUP_JSON = "{\"name\":\"Cached Clan\",\"memberships\":["
		+ "{\"role\":\"member\",\"player\":{\"username\":\"alpha\",\"exp\":42}}]}";

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void groupCacheRestoresMembersWithoutInventingHistory() throws Exception
	{
		WomClanCache cache = new WomClanCache(temporaryFolder.newFolder().toPath());
		cache.storeGroup(GROUP_ID, GROUP_JSON, 1_000);

		WomClanCache.CachedClan cached = cache.load(GROUP_ID);
		assertNotNull(cached);
		assertEquals("Cached Clan", cached.getData().getInfo().getName());
		assertEquals("alpha", cached.getData().getMembers().get(0).getDisplayName());
		assertEquals(WomHistory.Status.PENDING, cached.getData().getAchievements().getStatus());
		assertEquals(1_000, cached.getGroupCachedAtMs());
		assertTrue(cached.historyNeedsRefresh(1_001));
	}

	@Test
	public void completeHistoryCacheHasAnIndependentFreshnessClock() throws Exception
	{
		WomClanCache cache = new WomClanCache(temporaryFolder.newFolder().toPath());
		cache.storeGroup(GROUP_ID, GROUP_JSON, 1_000);
		cache.storeAchievements(GROUP_ID, "[]", 2_000);
		cache.storeActivity(GROUP_ID, "[]", 2_100);
		cache.storeNameChanges(GROUP_ID, "[]", 2_200);

		WomClanCache.CachedClan cached = cache.load(GROUP_ID);
		assertEquals(WomHistory.Status.LOADED, cached.getData().getAchievements().getStatus());
		assertEquals(2_000, cached.getHistoryCachedAtMs());
		assertFalse(cached.historyNeedsRefresh(2_000 + WomClanCache.HISTORY_MAX_AGE_MS - 1));
		assertTrue(cached.historyNeedsRefresh(2_000 + WomClanCache.HISTORY_MAX_AGE_MS));
	}
}
