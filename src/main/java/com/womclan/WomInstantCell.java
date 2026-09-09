package com.womclan;

import lombok.Value;

import java.time.Instant;

/**
 * A timestamp held in a table model as the instant itself rather than as formatted text.
 *
 * <p>History tables show compact relative dates ("3d ago"), which sort alphabetically into
 * nonsense. Keeping the instant and comparing on it means the column keeps sorting chronologically
 * no matter how it is rendered.</p>
 */
@Value
class WomInstantCell implements Comparable<WomInstantCell>
{
	/** May be null when the API omitted the timestamp. */
	Instant instant;

	@Override
	public int compareTo(WomInstantCell other)
	{
		if (instant == null)
		{
			return other.instant == null ? 0 : -1;
		}
		if (other.instant == null)
		{
			return 1;
		}
		return instant.compareTo(other.instant);
	}
}
