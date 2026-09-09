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
import java.util.function.Consumer;
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

	@Inject
	private ConfigManager configManager;

	private final WomSyncState syncState = new WomSyncState();

	private volatile WomClanPanel panel;
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

		// A different group invalidates everything on screen, including the "last synced" time:
		// leaving the old clan's members visible under the new group's name would be a lie.
		boolean groupChanged = "groupId".equals(event.getKey());
		if (groupChanged)
		{
			syncState.reset();
			onPanel(panel ->
			{
				panel.clearClanData();
				panel.setGroupConfigured(isGroupConfigured());
			});
		}

		// Re-schedule whenever groupId or autoRefresh toggle changes
		cancelAutoRefresh();
		scheduleAutoRefresh();

		// scheduleAutoRefresh fetches straight away when it is enabled. With it turned off, a group
		// change still deserves one fetch, so setup does not hand back an empty panel.
		if (groupChanged && isGroupConfigured() && !config.autoRefresh())
		{
			requestManualSync();
		}
	}

	/**
	 * Called by the panel's Sync Now button. Does nothing when a fetch is already running or the
	 * manual cooldown has not elapsed; callers read {@link #syncStatus()} to explain why.
	 */
	void requestManualSync()
	{
		if (executor == null || executor.isShutdown())
		{
			return;
		}

		if (syncState.beginManualFetch(System.currentTimeMillis()))
		{
			executor.submit(this::fetchAndUpdate);
		}
	}

	boolean isGroupConfigured()
	{
		return config.groupId() > 0;
	}

	/**
	 * Stores a group id chosen from the panel's setup state. Writing through ConfigManager keeps the
	 * plugin's own settings the single source of truth, and the resulting ConfigChanged drives the
	 * panel out of setup and into its first fetch.
	 */
	void setGroupId(int groupId)
	{
		configManager.setConfiguration("womclan", "groupId", groupId);
	}

	/** The current refresh state, shared by every surface that offers a refresh. */
	WomSyncStatus syncStatus()
	{
		return syncState.snapshot(System.currentTimeMillis());
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
			this::autoFetch,
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

	private void autoFetch()
	{
		// A scheduled refresh that lands on top of a running one is simply skipped; it must not
		// touch the manual cooldown either way.
		if (syncState.beginAutoFetch())
		{
			fetchAndUpdate();
		}
	}

	private void fetchAndUpdate()
	{
		int groupId = config.groupId();

		if (groupId <= 0)
		{
			syncState.recordNotConfigured();
			onPanel(WomClanPanel::refreshSyncStatus);
			return;
		}

		onPanel(WomClanPanel::refreshSyncStatus);

		try
		{
			WomClanData clanData = apiClient.fetchClanData(groupId);
			syncState.recordSuccess(System.currentTimeMillis());
			onPanel(p -> p.updateClanData(clanData));
		}
		catch (IOException e)
		{
			log.warn("WOM Clan Stats: failed to fetch group {}: {}", groupId, e.getMessage());
			syncState.recordFailure(e.getMessage());
			onPanel(p -> p.showError(e.getMessage()));
		}
	}

	/** Runs a panel update on the EDT, skipping it if the plugin shut down in the meantime. */
	private void onPanel(Consumer<WomClanPanel> action)
	{
		SwingUtilities.invokeLater(() ->
		{
			WomClanPanel current = panel;
			if (current != null)
			{
				action.accept(current);
			}
		});
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
