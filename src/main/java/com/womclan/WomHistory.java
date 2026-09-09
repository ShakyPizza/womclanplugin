package com.womclan;

import lombok.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One optional history section (achievements, activity, name changes) together with whether it
 * could actually be fetched.
 *
 * <p>These sections are fetched best-effort, so a failure used to be flattened into an empty list —
 * which made "the API did not answer" indistinguishable from "this clan has had no recent events".
 * Carrying the status alongside the entries lets each surface say which one it is.</p>
 */
@Value
class WomHistory<T>
{
	enum Status
	{
		/** Never fetched: no attempt has completed yet. */
		PENDING,
		/** Fetched successfully. The entries may still legitimately be empty. */
		LOADED,
		/** The fetch failed; {@link #error} says how. */
		UNAVAILABLE
	}

	Status status;
	List<T> entries;
	String error;

	static <T> WomHistory<T> pending()
	{
		return new WomHistory<>(Status.PENDING, Collections.emptyList(), null);
	}

	static <T> WomHistory<T> loaded(List<T> entries)
	{
		return new WomHistory<>(Status.LOADED, new ArrayList<>(entries), null);
	}

	static <T> WomHistory<T> unavailable(String error)
	{
		return new WomHistory<>(Status.UNAVAILABLE, Collections.emptyList(), error);
	}

	boolean isAvailable()
	{
		return status == Status.LOADED;
	}
}
