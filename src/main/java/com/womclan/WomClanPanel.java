package com.womclan;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Main sidebar panel for the WOM Clan Stats plugin.
 * Shows a searchable, sorted list of clan members with their XP, EHP, and EHB.
 */
class WomClanPanel extends PluginPanel
{
	private static final int STATUS_REFRESH_MS = 1_000;
	private static final int SCROLLBAR_WIDTH = 8;
	private static final int SCROLL_UNIT_INCREMENT = 16;
	private static final NumberFormat INTEGER_FORMAT = NumberFormat.getIntegerInstance(Locale.US);

	private final WomClanPlugin plugin;

	private final JLabel statusLabel;
	private final JLabel clanNameLabel;
	private final JLabel clanChatLabel;
	private final JLabel memberCountLabel;
	private final JLabel totalXpLabel;
	private final JLabel totalEhpLabel;
	private final JLabel totalEhbLabel;
	private final JButton syncButton;
	private final JTextField searchField;
	private final JPanel memberListPanel;

	/** The last successfully fetched data, kept so a failed refresh does not blank the panel. */
	private WomClanData currentData;
	private List<WomMember> allMembers = new ArrayList<>();

	/** Repaints the elapsed-time and cooldown text so it stays true between fetches. */
	private final Timer statusTimer;

	private WomExpandedWindow expandedWindow;

	WomClanPanel(WomClanPlugin plugin)
	{
		super(false);
		this.plugin = plugin;

		setLayout(new BorderLayout(0, 0));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		// ── Header bar (Sync button + status) ─────────────────────────────────
		syncButton = new JButton("Sync Now");
		syncButton.setFont(FontManager.getRunescapeSmallFont());
		syncButton.setFocusPainted(false);
		styleHeaderButton(syncButton);
		syncButton.setToolTipText("Fetch latest clan stats from WOM API");
		syncButton.addActionListener(e -> onSyncClicked());

		statusLabel = new JLabel();
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
		statusLabel.setBorder(new EmptyBorder(6, 0, 0, 0));

		JButton expandButton = new JButton("Open GUI");
		expandButton.setFont(FontManager.getRunescapeSmallFont());
		expandButton.setFocusPainted(false);
		styleHeaderButton(expandButton);
		expandButton.setToolTipText("Open GUI in a separate window");
		expandButton.addActionListener(e -> openExpandedWindow());

		JPanel buttonRow = new JPanel(new GridLayout(1, 2, 8, 0));
		buttonRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		buttonRow.add(syncButton);
		buttonRow.add(expandButton);

		JPanel topBar = new JPanel(new BorderLayout());
		topBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		topBar.setBorder(new EmptyBorder(8, 8, 6, 8));
		topBar.add(buttonRow, BorderLayout.NORTH);
		topBar.add(statusLabel, BorderLayout.SOUTH);

		// ── Clan summary ───────────────────────────────────────────────────────
		clanNameLabel = new JLabel("Clan");
		clanNameLabel.setFont(FontManager.getRunescapeBoldFont());
		clanNameLabel.setForeground(Color.YELLOW);

		clanChatLabel = new JLabel("No clan data loaded");
		clanChatLabel.setFont(FontManager.getRunescapeSmallFont());
		clanChatLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JPanel clanTitlePanel = new JPanel();
		clanTitlePanel.setLayout(new BoxLayout(clanTitlePanel, BoxLayout.Y_AXIS));
		clanTitlePanel.setOpaque(false);
		clanTitlePanel.add(clanNameLabel);
		clanTitlePanel.add(clanChatLabel);

		memberCountLabel = createClanStatLabel();
		totalXpLabel = createClanStatLabel();
		totalEhpLabel = createClanStatLabel();
		totalEhbLabel = createClanStatLabel();

		JPanel statsPanel = new JPanel(new GridLayout(2, 2, 8, 2));
		statsPanel.setOpaque(false);
		statsPanel.add(memberCountLabel);
		statsPanel.add(totalXpLabel);
		statsPanel.add(totalEhpLabel);
		statsPanel.add(totalEhbLabel);
		

		JPanel clanInfoPanel = new JPanel(new BorderLayout(0, 6));
		clanInfoPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		clanInfoPanel.setBorder(new EmptyBorder(0, 8, 8, 8));
		clanInfoPanel.add(clanTitlePanel, BorderLayout.NORTH);
		clanInfoPanel.add(statsPanel, BorderLayout.CENTER);
		updateClanInfo(null);

		// ── Search field ───────────────────────────────────────────────────────
		searchField = new JTextField();
		searchField.setFont(FontManager.getRunescapeSmallFont());
		searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchField.setForeground(Color.WHITE);
		searchField.setCaretColor(Color.WHITE);
		searchField.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(4, 6, 4, 6)));
		searchField.setToolTipText("Filter members by name…");
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			public void insertUpdate(DocumentEvent e)
			{
				filterMembers();
			}

			public void removeUpdate(DocumentEvent e)
			{
				filterMembers();
			}

			public void changedUpdate(DocumentEvent e)
			{
				filterMembers();
			}
		});

		JPanel searchWrapper = new JPanel(new BorderLayout());
		searchWrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchWrapper.setBorder(new EmptyBorder(0, 8, 8, 8));
		searchWrapper.add(searchField, BorderLayout.CENTER);

		JPanel headerPanel = new JPanel(new BorderLayout());
		headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		headerPanel.add(topBar, BorderLayout.NORTH);
		headerPanel.add(clanInfoPanel, BorderLayout.CENTER);
		headerPanel.add(searchWrapper, BorderLayout.SOUTH);

		add(headerPanel, BorderLayout.NORTH);

		// ── Member list ────────────────────────────────────────────────────────
		memberListPanel = new JPanel();
		memberListPanel.setLayout(new BoxLayout(memberListPanel, BoxLayout.Y_AXIS));
		memberListPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JScrollPane scrollPane = new JScrollPane(memberListPanel);
		scrollPane.setBorder(null);
		scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(SCROLLBAR_WIDTH, 0));
		scrollPane.getVerticalScrollBar().setUnitIncrement(SCROLL_UNIT_INCREMENT);

		add(scrollPane, BorderLayout.CENTER);

		showPlaceholder("Set your WOM Group ID in the\nplugin config, then hit Sync Now.");

		statusTimer = new Timer(STATUS_REFRESH_MS, e -> refreshSyncStatus());
		statusTimer.start();
		refreshSyncStatus();
	}

	// ── Sync button handler ────────────────────────────────────────────────────

	private void styleHeaderButton(JButton button)
	{
		button.setBackground(ColorScheme.DARK_GRAY_COLOR);
		button.setForeground(Color.WHITE);
		button.setOpaque(true);
		button.setContentAreaFilled(true);
		button.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(4, 10, 4, 10)));
	}

	private String describeSyncButton(WomSyncStatus status)
	{
		if (status.isFetching())
		{
			return "Syncing…";
		}
		// The countdown lives on the button because Swing hides tooltips on disabled components,
		// and an enabled-looking button that refuses to sync is worse than a labelled wait.
		return status.getCooldownRemainingMs() > 0
			? "Sync in " + WomFormat.countdown(status.getCooldownRemainingMs())
			: "Sync Now";
	}

	private String describeStatus(WomSyncStatus status, long now)
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
				if (currentData != null && status.hasSucceeded())
				{
					return "Sync failed · showing data " + WomFormat.since(status.getLastSuccessMs(), now);
				}
				return status.getCooldownRemainingMs() > 0
					? "Sync failed · retry in " + WomFormat.countdown(status.getCooldownRemainingMs())
					: "Sync failed · retry available";
			case SUCCESS:
				return "Synced " + WomFormat.since(status.getLastSuccessMs(), now);
			default:
				return "Not synced yet";
		}
	}

	private JLabel createClanStatLabel()
	{
		JLabel label = new JLabel();
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return label;
	}

	private void onSyncClicked()
	{
		plugin.requestManualSync();
		refreshSyncStatus();
	}

	// ── Public API called by WomClanPlugin ─────────────────────────────────────

	/** Called on the EDT after a successful fetch. */
	void updateClanData(WomClanData clanData)
	{
		currentData = clanData;
		allMembers = new ArrayList<>(clanData.getMembers());
		allMembers.sort(Comparator.comparingLong(WomMember::getTotalXp).reversed());
		updateClanInfo(clanData.getInfo() == null ? buildClanInfo(null, allMembers) : clanData.getInfo());
		refreshSyncStatus();
		filterMembers();

		if (expandedWindow != null && expandedWindow.isVisible())
		{
			expandedWindow.setClanData(currentData);
		}
	}

	/**
	 * Drops everything on screen. Called when the configured group changes, so the previous clan's
	 * members are never presented as belonging to the newly selected one.
	 */
	void clearClanData()
	{
		currentData = null;
		allMembers = new ArrayList<>();
		searchField.setText("");
		updateClanInfo(null);
		refreshSyncStatus();
		filterMembers();

		if (expandedWindow != null)
		{
			expandedWindow.setClanData(null);
		}
	}

	/**
	 * Redraws the sync button and status line from the plugin's refresh state. Safe to call as often
	 * as wanted; the timer calls it once a second so elapsed times and the cooldown stay honest.
	 */
	void refreshSyncStatus()
	{
		WomSyncStatus status = plugin.syncStatus();
		long now = System.currentTimeMillis();

		syncButton.setEnabled(status.isManualSyncAllowed());
		syncButton.setText(describeSyncButton(status));

		statusLabel.setText(describeStatus(status, now));
		statusLabel.setToolTipText(status.hasSucceeded()
			? "Last successful sync: " + WomFormat.timestamp(status.getLastSuccessMs())
			: null);
	}

	/**
	 * Reports a failed fetch (call via SwingUtilities.invokeLater). Data already on screen survives:
	 * a clan list that is a few minutes stale beats an error page where the clan list used to be.
	 * The status line says how old it is.
	 */
	void showError(String msg)
	{
		refreshSyncStatus();

		if (currentData == null)
		{
			updateClanInfo(null);
			showPlaceholder("Could not load clan data.\n\n" + msg + "\n\nCheck your Group ID and connection, then sync again.");
		}
	}

	void shutdown()
	{
		statusTimer.stop();
		if (expandedWindow != null)
		{
			expandedWindow.dispose();
		}
	}

	// ── Private helpers ────────────────────────────────────────────────────────

	private void openExpandedWindow()
	{
		if (expandedWindow == null || !expandedWindow.isDisplayable())
		{
			expandedWindow = new WomExpandedWindow();
		}
		expandedWindow.setClanData(currentData);
		expandedWindow.setVisible(true);
		expandedWindow.toFront();
	}

	private void filterMembers()
	{
		String query = searchField.getText().trim().toLowerCase();
		List<WomMember> filtered = new ArrayList<>();

		for (WomMember m : allMembers)
		{
			if (query.isEmpty() || m.getDisplayName().toLowerCase().contains(query))
			{
				filtered.add(m);
			}
		}

		rebuildList(filtered);
	}

	private void updateClanInfo(WomClanInfo info)
	{
		if (info == null)
		{
			clanNameLabel.setText("Clan");
			clanChatLabel.setText("No clan data loaded");
			memberCountLabel.setText(formatStatLabel("Members", "-"));
			totalXpLabel.setText(formatStatLabel("XP", "-"));
			totalEhpLabel.setText(formatStatLabel("EHP", "-"));
			totalEhbLabel.setText(formatStatLabel("EHB", "-"));
			return;
		}

		clanNameLabel.setText(info.getName());
		clanChatLabel.setText(info.getClanChat().isEmpty() ? "Clan chat: -" : "Clan chat: " + info.getClanChat());
		memberCountLabel.setText(formatStatLabel("Members", INTEGER_FORMAT.format(info.getMemberCount())));
		totalXpLabel.setText(formatStatLabel("XP", INTEGER_FORMAT.format(info.getTotalXp())));
		totalEhpLabel.setText(formatStatLabel("EHP", formatDecimal(info.getTotalEhp())));
		totalEhbLabel.setText(formatStatLabel("EHB", formatDecimal(info.getTotalEhb())));
	}

	private WomClanInfo buildClanInfo(String name, List<WomMember> members)
	{
		long totalXp = 0L;
		double totalEhp = 0.0;
		double totalEhb = 0.0;
		for (WomMember member : members)
		{
			totalXp += member.getTotalXp();
			totalEhp += member.getEhp();
			totalEhb += member.getEhb();
		}

		return new WomClanInfo(name == null ? "Clan" : name, "", members.size(), totalXp, totalEhp, totalEhb);
	}

	private String formatDecimal(double value)
	{
		return INTEGER_FORMAT.format(Math.round(value));
	}

	private String formatStatLabel(String label, String value)
	{
		return "<html><b>" + label + ":</b> " + value + "</html>";
	}

	private void rebuildList(List<WomMember> members)
	{
		memberListPanel.removeAll();

		if (members.isEmpty())
		{
			String msg = allMembers.isEmpty()
				? "No data yet.\nHit Sync Now to load members."
				: "No members match \"" + searchField.getText().trim() + "\".";
			showPlaceholder(msg);
		}
		else
		{
			for (int i = 0; i < members.size(); i++)
			{
				WomMemberPanel row = new WomMemberPanel(members.get(i), i % 2 == 0);
				row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
				memberListPanel.add(row);
			}
		}

		memberListPanel.revalidate();
		memberListPanel.repaint();
	}

	private void showPlaceholder(String text)
	{
		memberListPanel.removeAll();

		JLabel label = new JLabel("<html><center>" + text.replace("\n", "<br>") + "</center></html>");
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setBorder(new EmptyBorder(24, 12, 12, 12));
		label.setAlignmentX(Component.CENTER_ALIGNMENT);

		memberListPanel.add(label);
		memberListPanel.revalidate();
		memberListPanel.repaint();
	}
}
