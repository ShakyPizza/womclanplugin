package com.womclan;

import org.junit.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

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

	private static final ZoneId UTC = ZoneId.of("UTC");

	private static long utc(int year, int month, int day, int hour, int minute)
	{
		return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, UTC).toInstant().toEpochMilli();
	}

	@Test
	public void countdownRoundsUpSoItNeverReadsZeroWhileWaiting()
	{
		assertEquals("5:00", WomFormat.countdown(5 * 60 * 1_000L));
		assertEquals("4:32", WomFormat.countdown(4 * 60_000L + 31_500L));
		assertEquals("0:01", WomFormat.countdown(1));
		assertEquals("0:00", WomFormat.countdown(0));
	}

	@Test
	public void sinceReportsElapsedMinutesWhileRecent()
	{
		long now = utc(2026, 9, 9, 14, 30);
		assertEquals("just now", WomFormat.since(now - 59_000, now, UTC));
		assertEquals("1 min ago", WomFormat.since(now - 60_000, now, UTC));
		assertEquals("23 mins ago", WomFormat.since(now - 23 * 60_000, now, UTC));
	}

	@Test
	public void sinceFallsBackToAClockTimeOnceElapsedStopsHelping()
	{
		long now = utc(2026, 9, 9, 14, 30);
		assertEquals("at 12:15", WomFormat.since(utc(2026, 9, 9, 12, 15), now, UTC));
		assertEquals("on 8 Sep 23:05", WomFormat.since(utc(2026, 9, 8, 23, 5), now, UTC));
	}

	@Test
	public void sinceHandlesNeverSynced()
	{
		assertEquals("never", WomFormat.since(0, utc(2026, 9, 9, 14, 30), UTC));
	}

	@Test
	public void timestampKeepsTheExactValueDiscoverable()
	{
		assertEquals("2026-09-09 14:30:00", WomFormat.timestamp(utc(2026, 9, 9, 14, 30), UTC));
		assertEquals("", WomFormat.timestamp(0, UTC));
	}
}
