package com.womclan;

import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import javax.swing.JFrame;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Persists AWT user-space bounds; never resizes an already visible, WM-managed window. */
class WomWindowGeometry
{
	// Separate from data settings: saving window bounds must not schedule a clan refresh.
	private static final String CONFIG_GROUP = "womclan.window";
	private static final String CONFIG_KEY = "expandedBounds";
	private final Supplier<String> load;
	private final Consumer<String> save;

	@Inject
	WomWindowGeometry(ConfigManager configManager)
	{
		this(() -> configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY),
			value -> configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY, value));
	}

	WomWindowGeometry(Supplier<String> load, Consumer<String> save)
	{
		this.load = load;
		this.save = save;
	}

	/**
	 * Called on the EDT once, after constructing the frame and before showing it.
	 *
	 * @return an action that captures and persists the current bounds immediately. Closing the
	 *         window saves on its own, but {@code dispose()} only posts WINDOW_CLOSED to the event
	 *         queue; during plugin shutdown that event can go undispatched, so the caller flushes
	 *         explicitly rather than losing the position of a window left open at exit.
	 */
	Runnable restoreAndTrack(JFrame frame, Component anchor)
	{
		List<Rectangle> screens = new ArrayList<>();
		for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices())
		{
			GraphicsConfiguration configuration = device.getDefaultConfiguration();
			Rectangle bounds = new Rectangle(configuration.getBounds());
			Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
			bounds.x += insets.left;
			bounds.y += insets.top;
			bounds.width -= insets.left + insets.right;
			bounds.height -= insets.top + insets.bottom;
			if (bounds.width > 0 && bounds.height > 0)
			{
				screens.add(bounds);
			}
		}

		Rectangle requested = decode(load.get());
		if (requested == null)
		{
			frame.setLocationRelativeTo(anchor);
			requested = frame.getBounds();
		}
		Rectangle restored = fitToScreens(requested, frame.getMinimumSize(), screens);
		// A display can be smaller than the normal minimum after a monitor/scaling change.
		frame.setMinimumSize(new Dimension(Math.min(frame.getMinimumSize().width, restored.width),
			Math.min(frame.getMinimumSize().height, restored.height)));
		frame.setBounds(restored);

		class Tracker extends WindowAdapter
		{
			private Rectangle normalBounds = new Rectangle(restored);

			void capture()
			{
				if (frame.isShowing() && frame.getExtendedState() == Frame.NORMAL)
				{
					normalBounds = frame.getBounds();
				}
			}

			@Override
			public void windowClosing(WindowEvent event)
			{
				capture();
			}

			void saveNow()
			{
				capture();
				save.accept(encode(normalBounds));
			}

			@Override
			public void windowClosed(WindowEvent event)
			{
				// Also handles plugin shutdown's dispose(), not just the title-bar close button.
				saveNow();
			}
		}
		Tracker tracker = new Tracker();
		frame.addWindowListener(tracker);
		frame.addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentMoved(ComponentEvent event)
			{
				tracker.capture();
			}

			@Override
			public void componentResized(ComponentEvent event)
			{
				tracker.capture();
			}
		});

		return tracker::saveNow;
	}

	static Rectangle fitToScreens(Rectangle requested, Dimension minimum, List<Rectangle> screens)
	{
		if (screens.isEmpty())
		{
			return new Rectangle(requested);
		}
		Rectangle target = screens.get(0);
		double largestOverlap = -1;
		double nearestDistance = Double.POSITIVE_INFINITY;
		for (Rectangle screen : screens)
		{
			Rectangle intersection = requested.intersection(screen);
			double overlap = intersection.isEmpty() ? 0 : (double) intersection.width * intersection.height;
			double dx = requested.getCenterX() - screen.getCenterX();
			double dy = requested.getCenterY() - screen.getCenterY();
			double distance = dx * dx + dy * dy;
			if (overlap > largestOverlap || (overlap == largestOverlap && distance < nearestDistance))
			{
				target = screen;
				largestOverlap = overlap;
				nearestDistance = distance;
			}
		}
		int width = Math.min(target.width, Math.max(minimum.width, requested.width));
		int height = Math.min(target.height, Math.max(minimum.height, requested.height));
		int x = Math.max(target.x, Math.min(requested.x, target.x + target.width - width));
		int y = Math.max(target.y, Math.min(requested.y, target.y + target.height - height));
		return new Rectangle(x, y, width, height);
	}

	static String encode(Rectangle bounds)
	{
		return bounds.x + "," + bounds.y + "," + bounds.width + "," + bounds.height;
	}

	static Rectangle decode(String value)
	{
		if (value == null)
		{
			return null;
		}
		String[] parts = value.split(",", -1);
		if (parts.length != 4)
		{
			return null;
		}
		try
		{
			Rectangle bounds = new Rectangle(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
				Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
			return bounds.width > 0 && bounds.height > 0 ? bounds : null;
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}
}
