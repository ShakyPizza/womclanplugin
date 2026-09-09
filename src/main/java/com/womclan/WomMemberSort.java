package com.womclan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The orderings offered for the sidebar member list.
 *
 * <p>The sidebar shows only names and roles, so any ordering it applies is invisible unless the
 * user is told which one it is — hence a labelled selector rather than a silent default.</p>
 */
enum WomMemberSort
{
	TOTAL_XP("Total XP", Comparator.comparingLong(WomMember::getTotalXp).reversed()),
	EHP("EHP", Comparator.comparingDouble(WomMember::getEhp).reversed()),
	EHB("EHB", Comparator.comparingDouble(WomMember::getEhb).reversed()),
	NAME("Name (A-Z)", Comparator.<WomMember, String>comparing(WomMember::getDisplayName, String.CASE_INSENSITIVE_ORDER));

	private final String label;
	private final Comparator<WomMember> comparator;

	WomMemberSort(String label, Comparator<WomMember> comparator)
	{
		this.label = label;
		// Name breaks every tie, so equal stats never shuffle between refreshes.
		this.comparator = comparator.thenComparing(WomMember::getDisplayName, String.CASE_INSENSITIVE_ORDER);
	}

	/** Returns a sorted copy, leaving the caller's list alone. */
	List<WomMember> sort(List<WomMember> members)
	{
		List<WomMember> sorted = new ArrayList<>(members);
		sorted.sort(comparator);
		return sorted;
	}

	/** Drives the text shown in the sidebar's sort selector. */
	@Override
	public String toString()
	{
		return label;
	}
}
