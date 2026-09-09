package com.womclan;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@PluginDescriptor(
	name = "WOM Clan Stats",
	description = "Displays Wise Old Man clan member stats (Total XP, EHP, EHB) in a sidebar panel",
	tags = {"wom", "clan", "xp", "ehb", "ehp", "wise old man", "hiscores"}
)
public class WomClanPlugin extends Plugin
{
	private static final int AUTO_REFRESH_MINUTES = 60;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private WomClanConfig config;

	@Inject
	private WomApiClient apiClient;

	private WomClanPanel panel;
	private NavigationButton navButton;
	private ScheduledExecutorService executor;
	private ScheduledFuture<?> autoRefreshTask;

	@Override
	protected void startUp() throws Exception
	{
		panel = new WomClanPanel(this);

		navButton = NavigationButton.builder()
			.tooltip("WOM Clan Stats")
			.icon(buildIcon())
			.priority(7)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);

		executor = Executors.newSingleThreadScheduledExecutor();
		scheduleAutoRefresh();
	}

	@Override
	protected void shutDown() throws Exception
	{
		cancelAutoRefresh();

		if (executor != null)
		{
			executor.shutdownNow();
			executor = null;
		}

		clientToolbar.removeNavigation(navButton);

		if (panel != null)
		{
			panel.shutdown();
			panel = null;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"womclan".equals(event.getGroup()))
		{
			return;
		}

		// Re-schedule whenever groupId or autoRefresh toggle changes
		cancelAutoRefresh();
		scheduleAutoRefresh();
	}

	/** Called by the panel's Sync Now button. */
	void manualSync()
	{
		if (executor != null && !executor.isShutdown())
		{
			executor.submit(this::fetchAndUpdate);
		}
	}

	// ── Private helpers ────────────────────────────────────────────────────────

	private void scheduleAutoRefresh()
	{
		if (!config.autoRefresh() || executor == null || executor.isShutdown())
		{
			return;
		}

		// Fetch immediately on startup / config change, then every 60 min
		autoRefreshTask = executor.scheduleAtFixedRate(
			this::fetchAndUpdate,
			0, AUTO_REFRESH_MINUTES, TimeUnit.MINUTES
		);
	}

	private void cancelAutoRefresh()
	{
		if (autoRefreshTask != null)
		{
			autoRefreshTask.cancel(false);
			autoRefreshTask = null;
		}
	}

	private void fetchAndUpdate()
	{
		int groupId = config.groupId();

		if (groupId <= 0)
		{
			SwingUtilities.invokeLater(() -> panel.setSyncStatus("Set Group ID in config"));
			return;
		}

		SwingUtilities.invokeLater(() -> panel.setSyncStatus("Syncing…"));

		try
		{
			WomClanData clanData = apiClient.fetchClanData(groupId);
			SwingUtilities.invokeLater(() -> panel.updateClanData(clanData));
		}
		catch (IOException e)
		{
			log.warn("WOM Clan Stats: failed to fetch group {}: {}", groupId, e.getMessage());
			SwingUtilities.invokeLater(() -> panel.showError(e.getMessage()));
		}
	}

	/** Creates a small 16×16 icon programmatically (blue rounded square with "W"). */
	private BufferedImage buildIcon()
	{
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// Background
		g.setColor(new Color(30, 130, 200));
		g.fillRoundRect(0, 0, 15, 15, 5, 5);

		// "W" letter
		g.setColor(Color.WHITE);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
		FontMetrics fm = g.getFontMetrics();
		String letter = "W";
		int x = (16 - fm.stringWidth(letter)) / 2;
		int y = (16 - fm.getHeight()) / 2 + fm.getAscent();
		g.drawString(letter, x, y);
		g.dispose();

		return img;
	}

	@Provides
	WomClanConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(WomClanConfig.class);
	}
}
