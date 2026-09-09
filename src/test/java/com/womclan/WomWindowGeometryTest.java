package com.womclan;

import org.junit.Test;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WomWindowGeometryTest
{
	private static final Dimension MINIMUM = new Dimension(620, 380);

	@Test
	public void preservesBoundsOnMonitorToLeftOfPrimary()
	{
		Rectangle saved = new Rectangle(-1600, 120, 850, 560);
		assertEquals(saved, WomWindowGeometry.fitToScreens(saved, MINIMUM, Arrays.asList(
			new Rectangle(0, 30, 1920, 1050), new Rectangle(-1920, 0, 1920, 1080))));
		assertEquals(saved, WomWindowGeometry.decode(WomWindowGeometry.encode(saved)));
	}

	@Test
	public void bringsWindowBackAfterMonitorIsDisconnected()
	{
		assertEquals(new Rectangle(1070, 100, 850, 560), WomWindowGeometry.fitToScreens(
			new Rectangle(2400, 100, 850, 560), MINIMUM,
			Collections.singletonList(new Rectangle(0, 30, 1920, 1050))));
	}

	@Test
	public void fitsOversizedWindowToSmallWorkAreaIncludingReservedBar()
	{
		Rectangle workArea = new Rectangle(0, 40, 500, 300);
		assertEquals(workArea, WomWindowGeometry.fitToScreens(
			new Rectangle(-100, -100, 1600, 1200), MINIMUM, Collections.singletonList(workArea)));
	}

	@Test
	public void selectsMonitorWithLargestOverlap()
	{
		assertEquals(new Rectangle(1920, 100, 850, 560), WomWindowGeometry.fitToScreens(
			new Rectangle(1800, 100, 850, 560), MINIMUM, Arrays.asList(
				new Rectangle(0, 0, 1920, 1080), new Rectangle(1920, 0, 1920, 1080))));
	}

	@Test
	public void selectsNearestMonitorWhenSavedWindowIsInGap()
	{
		assertEquals(new Rectangle(1100, 40, 620, 380), WomWindowGeometry.fitToScreens(
			new Rectangle(950, 40, 100, 100), MINIMUM, Arrays.asList(
				new Rectangle(0, 0, 800, 600), new Rectangle(1100, 0, 800, 600))));
	}

	@Test
	public void restoresMinimumSizeAndKeepsTitleBarBelowReservedArea()
	{
		assertEquals(new Rectangle(100, 40, 620, 380), WomWindowGeometry.fitToScreens(
			new Rectangle(100, -50, 100, 100), MINIMUM,
			Collections.singletonList(new Rectangle(0, 40, 1920, 1040))));
	}

	@Test
	public void rejectsMalformedOrUnusableSavedBounds()
	{
		for (String value : Arrays.asList(null, "", "1,2,3", "1,2,3,4,", "a,2,3,4",
			"0,0,0,560", "0,0,850,-1", "999999999999,0,850,560"))
		{
			assertNull(value, WomWindowGeometry.decode(value));
		}
	}
}
