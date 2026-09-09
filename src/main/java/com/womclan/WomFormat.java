package com.womclan;

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

	/**
	 * Highest total level reachable in game. A "levels" threshold larger than this cannot be a
	 * level count, which is how the WOM API's base-stat achievements give themselves away: they
	 * carry an experience threshold while reporting a level measure.
	 */
	private static final long MAX_TOTAL_LEVEL = 2376L;

	private static final DateTimeFormatter TIME_OF_DAY = DateTimeFormatter.ofPattern("HH:mm", Locale.US);
	private static final DateTimeFormatter DATE_AND_TIME = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.US);
	private static final DateTimeFormatter FULL_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US);

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
}
