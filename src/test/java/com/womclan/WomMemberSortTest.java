package com.womclan;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class WomMemberSortTest
{
	private static final WomMember ALPHA = new WomMember("Alpha", "member", 300, 1.0, 30.0);
	private static final WomMember BRAVO = new WomMember("bravo", "member", 100, 3.0, 20.0);
	private static final WomMember CHARLIE = new WomMember("Charlie", "member", 200, 2.0, 10.0);

	private static List<String> names(List<WomMember> members)
	{
		List<String> names = new ArrayList<>();
		for (WomMember member : members)
		{
			names.add(member.getDisplayName());
		}
		return names;
	}

	private static final List<WomMember> MEMBERS = Arrays.asList(CHARLIE, ALPHA, BRAVO);

	@Test
	public void statSortsAreDescending()
	{
		assertEquals(Arrays.asList("Alpha", "Charlie", "bravo"), names(WomMemberSort.TOTAL_XP.sort(MEMBERS)));
		assertEquals(Arrays.asList("bravo", "Charlie", "Alpha"), names(WomMemberSort.EHP.sort(MEMBERS)));
		assertEquals(Arrays.asList("Alpha", "bravo", "Charlie"), names(WomMemberSort.EHB.sort(MEMBERS)));
	}

	@Test
	public void nameSortIgnoresCase()
	{
		assertEquals(Arrays.asList("Alpha", "bravo", "Charlie"), names(WomMemberSort.NAME.sort(MEMBERS)));
	}

	@Test
	public void tiesBreakByNameSoOrderIsStableAcrossRefreshes()
	{
		WomMember zulu = new WomMember("Zulu", "member", 500, 0.0, 0.0);
		WomMember delta = new WomMember("Delta", "member", 500, 0.0, 0.0);

		assertEquals(Arrays.asList("Delta", "Zulu"), names(WomMemberSort.TOTAL_XP.sort(Arrays.asList(zulu, delta))));
		assertEquals(Arrays.asList("Delta", "Zulu"), names(WomMemberSort.TOTAL_XP.sort(Arrays.asList(delta, zulu))));
	}

	@Test
	public void sortDoesNotMutateTheInputList()
	{
		List<WomMember> original = new ArrayList<>(MEMBERS);
		WomMemberSort.NAME.sort(original);
		assertEquals(names(MEMBERS), names(original));
	}

	@Test
	public void labelsAreShownInTheSelector()
	{
		assertEquals("Total XP", WomMemberSort.TOTAL_XP.toString());
		assertEquals("Name (A-Z)", WomMemberSort.NAME.toString());
	}
}
