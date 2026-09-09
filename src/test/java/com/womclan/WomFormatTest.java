package com.womclan;

import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WomFormatTest
{
	private static WomAchievement achievement(String name, String metric, String measure, long threshold)
	{
		return new WomAchievement("Player", name, metric, measure, threshold, Instant.EPOCH);
	}

	@Test
	public void achievementValueRendersExperienceThresholds()
	{
		assertEquals("13,034,431 XP",
			WomFormat.achievementValue(achievement("99 Attack", "attack", "experience", 13_034_431L)));
	}

	@Test
	public void achievementValueRendersKillCounts()
	{
		assertEquals("1,000 kills",
			WomFormat.achievementValue(achievement("1k Kraken kills", "kraken", "kills", 1_000L)));
		assertEquals("1 kill",
			WomFormat.achievementValue(achievement("First Kraken kill", "kraken", "kills", 1L)));
	}

	@Test
	public void achievementValueKeepsPlausibleLevelCounts()
	{
		assertEquals("1,250 levels",
			WomFormat.achievementValue(achievement("1250 Total", "overall", "levels", 1_250L)));
	}

	@Test
	public void achievementValueTreatsImplausibleLevelCountAsExperience()
	{
		// "Base 90 Stats" reports a levels measure against an experience threshold; rendering the
		// two together produced "128,311,968 levels".
		assertEquals("128,311,968 XP",
			WomFormat.achievementValue(achievement("Base 90 Stats", "overall", "levels", 128_311_968L)));
	}

	@Test
	public void achievementValueIsEmptyWithoutAThreshold()
	{
		assertEquals("", WomFormat.achievementValue(achievement("Something", "overall", "experience", 0L)));
	}

	@Test
	public void achievementDetailCombinesMetricAndValue()
	{
		assertEquals("Kraken — 1,000 kills",
			WomFormat.achievementDetail(achievement("1k Kraken kills", "kraken", "kills", 1_000L)));
		assertEquals("Overall",
			WomFormat.achievementDetail(achievement("Maxed", "overall", "experience", 0L)));
		assertNull(WomFormat.achievementDetail(achievement("Mystery", "", "", 0L)));
	}

	@Test
	public void titleCaseNormalisesApiTokens()
	{
		assertEquals("Deputy Owner", WomFormat.titleCase("deputy_owner"));
		assertEquals("Changed Role", WomFormat.titleCase("changed_role"));
		assertEquals("", WomFormat.titleCase(null));
	}

	@Test
	public void roleFallsBackToMember()
	{
		assertEquals("Member", WomFormat.role(null));
		assertEquals("Member", WomFormat.role(""));
		assertEquals("Gold", WomFormat.role("gold"));
	}
}
