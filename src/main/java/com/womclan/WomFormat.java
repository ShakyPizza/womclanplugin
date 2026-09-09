package com.womclan;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Pure formatting helpers shared by the sidebar panel and the expanded window.
 *
 * <p>Kept free of Swing so the display rules can be unit tested headlessly.</p>
 */
final class WomFormat
{
	private static final NumberFormat INTEGER_FORMAT = NumberFormat.getIntegerInstance(Locale.US);
	private static final DecimalFormat ABBREVIATED = new DecimalFormat("#.#");
	private static final String[] MAGNITUDES = {"", "K", "M", "B", "T"};

	/**
	 * Highest total level reachable in game. A "levels" threshold larger than this cannot be a
	 * level count, which is how the WOM API's base-stat achievements give themselves away: they
	 * carry an experience threshold while reporting a level measure.
	 */
	private static final long MAX_TOTAL_LEVEL = 2376L;

	private static final DateTimeFormatter TIME_OF_DAY = DateTimeFormatter.ofPattern("HH:mm", Locale.US);
	private static final DateTimeFormatter DATE_AND_TIME = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.US);
	private static final DateTimeFormatter FULL_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US);
	private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US);
	private static final DateTimeFormatter EXACT_MOMENT = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", Locale.US);

	private WomFormat()
	{
	}

	static String integer(long value)
	{
		synchronized (INTEGER_FORMAT)
		{
			return INTEGER_FORMAT.format(value);
		}
	}

	static String integer(double value)
	{
		return integer(Math.round(value));
	}

	/** Formats a snake_case or lowercase API token as Title Case, e.g. {@code deputy_owner} → Deputy Owner. */
	static String titleCase(String token)
	{
		if (token == null || token.isEmpty())
		{
			return "";
		}

		StringBuilder sb = new StringBuilder();
		for (String word : token.replace('_', ' ').split(" "))
		{
			if (word.isEmpty())
			{
				continue;
			}
			if (sb.length() > 0)
			{
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(word.charAt(0)));
			sb.append(word.substring(1).toLowerCase(Locale.US));
		}
		return sb.toString();
	}

	/** Clan roles fall back to "Member" when the API omits one. */
	static String role(String role)
	{
		String formatted = titleCase(role);
		return formatted.isEmpty() ? "Member" : formatted;
	}

	/**
	 * Describes an achievement's threshold with a unit that actually matches the threshold.
	 *
	 * <p>The WOM API reports a {@code measure} alongside the raw {@code threshold}, but the two do
	 * not always agree: base-stat achievements ("Base 90 Stats") report a level measure with an
	 * experience threshold, which rendered as an absurd "128,311,968 levels". Rather than
	 * concatenating the two fields, an implausible level count is treated as the experience value
	 * it really is.</p>
	 *
	 * @return a human-readable description, or an empty string when there is nothing meaningful to show
	 */
	static String achievementValue(WomAchievement achievement)
	{
		long threshold = achievement.getThreshold();
		if (threshold <= 0)
		{
			return "";
		}

		String measure = achievement.getMeasure() == null ? "" : achievement.getMeasure().toLowerCase(Locale.US);
		if ("levels".equals(measure) && threshold > MAX_TOTAL_LEVEL)
		{
			measure = "experience";
		}

		switch (measure)
		{
			case "experience":
				return integer(threshold) + " XP";
			case "levels":
				return integer(threshold) + (threshold == 1 ? " level" : " levels");
			case "kills":
				return integer(threshold) + (threshold == 1 ? " kill" : " kills");
			case "score":
				return integer(threshold) + " score";
			case "":
				return integer(threshold);
			default:
				return integer(threshold) + " " + measure;
		}
	}

	/**
	 * Builds the secondary detail for an achievement, used as a tooltip so the metric and threshold
	 * stay available without repeating the milestone across three columns.
	 *
	 * @return the detail text, or {@code null} when the achievement name already says everything
	 */
	static String achievementDetail(WomAchievement achievement)
	{
		String metric = titleCase(achievement.getMetric());
		String value = achievementValue(achievement);

		if (metric.isEmpty() && value.isEmpty())
		{
			return null;
		}
		if (metric.isEmpty())
		{
			return value;
		}
		if (value.isEmpty())
		{
			return metric;
		}
		return metric + " — " + value;
	}

	/** Formats a remaining duration as {@code m:ss}, rounded up so it never reads 0:00 while waiting. */
	static String countdown(long remainingMs)
	{
		long seconds = (Math.max(0, remainingMs) + 999) / 1_000;
		return seconds / 60 + String.format(Locale.US, ":%02d", seconds % 60);
	}

	/**
	 * Describes how long ago something happened, in a form that stays true as time passes:
	 * an elapsed count while it is recent, an absolute clock time once "N mins ago" stops helping.
	 */
	static String since(long timestampMs, long nowMs)
	{
		return since(timestampMs, nowMs, ZoneId.systemDefault());
	}

	static String since(long timestampMs, long nowMs, ZoneId zone)
	{
		if (timestampMs <= 0)
		{
			return "never";
		}

		long minutes = Math.max(0, nowMs - timestampMs) / 60_000;
		if (minutes < 1)
		{
			return "just now";
		}
		if (minutes < 60)
		{
			return minutes + (minutes == 1 ? " min ago" : " mins ago");
		}

		ZonedDateTime then = Instant.ofEpochMilli(timestampMs).atZone(zone);
		ZonedDateTime now = Instant.ofEpochMilli(nowMs).atZone(zone);
		return then.toLocalDate().equals(now.toLocalDate())
			? "at " + TIME_OF_DAY.format(then)
			: "on " + DATE_AND_TIME.format(then);
	}

	/** The unabbreviated timestamp, for tooltips where the exact value has to stay discoverable. */
	static String timestamp(long timestampMs)
	{
		return timestamp(timestampMs, ZoneId.systemDefault());
	}

	static String timestamp(long timestampMs, ZoneId zone)
	{
		return timestampMs <= 0 ? "" : FULL_TIMESTAMP.format(Instant.ofEpochMilli(timestampMs).atZone(zone));
	}

	/**
	 * Explains the state of a history section in one line, so an API failure never masquerades as a
	 * clan with nothing going on.
	 *
	 * @param displayedRows rows actually on screen, which can be fewer than the fetched entries
	 * @param noun          plural noun for the section, e.g. "name changes"
	 */
	static String historyStatus(WomHistory.Status status, int fetchedRows, int visibleRows, String query, String noun)
	{
		switch (status)
		{
			case UNAVAILABLE:
				return "Could not load " + noun + ".";
			case LOADED:
				if (fetchedRows == 0)
				{
					return "No recent " + noun + ".";
				}
				if (query.isEmpty())
				{
					return "Showing " + integer(fetchedRows) + " recent " + noun + ".";
				}
				// A search that matches nothing is a different situation from a clan with no recent
				// events, and both differ again from a fetch that failed.
				return visibleRows == 0
					? "No " + noun + " match \"" + query + "\"."
					: "Showing " + integer(visibleRows) + " of " + integer(fetchedRows) + " recent " + noun + ".";
			default:
				return "Loading " + noun + "…";
		}
	}

	/**
	 * The label for a manual refresh control. The wait goes on the control itself because Swing
	 * hides tooltips on disabled components, and an enabled-looking button that refuses to refresh
	 * is worse than a labelled wait.
	 */
	static String syncButtonText(WomSyncStatus status, String idleText)
	{
		if (status.isFetching())
		{
			return "Syncing…";
		}
		return status.getCooldownRemainingMs() > 0
			? idleText + " in " + countdown(status.getCooldownRemainingMs())
			: idleText;
	}

	/**
	 * One line describing the refresh state, shared by every surface so they cannot disagree about
	 * how fresh the data is.
	 *
	 * @param hasData whether there is loaded data still on screen behind a failure
	 */
	static String syncSummary(WomSyncStatus status, boolean hasData, long nowMs)
	{
		if (status.isFetching())
		{
			return "Syncing…";
		}

		switch (status.getOutcome())
		{
			case NOT_CONFIGURED:
				return "Set your Group ID in settings";
			case FAILURE:
				if (hasData && status.hasSucceeded())
				{
					return (status.getServerBackoffRemainingMs() > 0 ? "Rate limited" : "Sync failed")
						+ " · showing data " + since(status.getLastSuccessMs(), nowMs);
				}
				return status.getCooldownRemainingMs() > 0
					? (status.getServerBackoffRemainingMs() > 0 ? "Rate limited" : "Sync failed")
						+ " · retry in " + countdown(status.getCooldownRemainingMs())
					: "Sync failed · retry available";
			case CACHED:
				return "Cached · updated " + since(status.getLastSuccessMs(), nowMs);
			case SUCCESS:
				return "Synced " + since(status.getLastSuccessMs(), nowMs);
			default:
				return "Not synced yet";
		}
	}

	/** Advanced request-budget detail for the status tooltip. */
	static String rateLimitSummary(WomSyncStatus status, long nowMs)
	{
		if (!status.hasRateLimit() || status.getRateResetAtMs() <= nowMs)
		{
			return null;
		}
		long resetMs = Math.max(0, status.getRateResetAtMs() - nowMs);
		return "WOM API: " + status.getRateRemaining() + "/" + status.getRateLimit()
			+ " requests remaining · resets in " + countdown(resetMs);
	}

	/**
	 * A compact relative date for history rows, coarsening as events get older. The exact moment
	 * stays available through {@link #exactMoment}.
	 */
	static String relative(Instant instant, long nowMs)
	{
		return relative(instant, nowMs, ZoneId.systemDefault());
	}

	static String relative(Instant instant, long nowMs, ZoneId zone)
	{
		if (instant == null)
		{
			return "";
		}

		long minutes = Math.max(0, nowMs - instant.toEpochMilli()) / 60_000;
		if (minutes < 1)
		{
			return "just now";
		}
		if (minutes < 60)
		{
			return minutes + "m ago";
		}

		long hours = minutes / 60;
		if (hours < 24)
		{
			return hours + "h ago";
		}

		long days = hours / 24;
		return days < 7 ? days + "d ago" : SHORT_DATE.format(instant.atZone(zone));
	}

	/** The full moment behind a relative date, for the hover tooltip. */
	static String exactMoment(Instant instant)
	{
		return exactMoment(instant, ZoneId.systemDefault());
	}

	static String exactMoment(Instant instant, ZoneId zone)
	{
		return instant == null ? "" : EXACT_MOMENT.format(instant.atZone(zone));
	}

	/**
	 * Shortens a large total so it fits the sidebar's narrow column, e.g. 25,600,000,000 as 25.6B.
	 * Callers pair this with {@link #integer} in a tooltip so the exact value stays discoverable.
	 */
	static String abbreviate(long value)
	{
		double scaled = value;
		int magnitude = 0;
		while (Math.abs(scaled) >= 1_000 && magnitude < MAGNITUDES.length - 1)
		{
			scaled /= 1_000;
			magnitude++;
		}

		// Rounding to one decimal can push a value back up a magnitude: 999,960 is "1000K" unless
		// it is promoted to "1M" here.
		if (Math.abs(Math.round(scaled * 10) / 10.0) >= 1_000 && magnitude < MAGNITUDES.length - 1)
		{
			scaled /= 1_000;
			magnitude++;
		}

		if (magnitude == 0)
		{
			return integer(value);
		}

		synchronized (ABBREVIATED)
		{
			return ABBREVIATED.format(scaled) + MAGNITUDES[magnitude];
		}
	}

	static String abbreviate(double value)
	{
		return abbreviate(Math.round(value));
	}

	/**
	 * Describes how much of the member list is on screen, so the search reports its own result
	 * count rather than leaving the user to guess whether it matched anything.
	 */
	static String memberCount(int visible, int total, String query)
	{
		if (total == 0)
		{
			return "";
		}
		if (query.isEmpty())
		{
			return integer(total) + (total == 1 ? " member" : " members");
		}
		return visible == 0
			? "No members match \"" + query + "\""
			: integer(visible) + " of " + integer(total) + " members";
	}
}
