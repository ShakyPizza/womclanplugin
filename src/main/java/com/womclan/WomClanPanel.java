package com.womclan;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Main sidebar panel for the WOM Clan Stats plugin.
 * Shows a searchable, sorted list of clan members with their XP, EHP, and EHB.
 */
class WomClanPanel extends PluginPanel
{
	private static final String SETUP_CARD = "setup";
	private static final String CLAN_CARD = "clan";
	private static final String WOM_GROUPS_URL = "https://wiseoldman.net/groups";
	private static final String DETAILS_TOOLTIP =
		"Open the full member, achievement, activity and name-change tables in a separate window";
	/** Width hint for wrapped HTML text: without it a label reports a single-line preferred size. */
	private static final String WRAP_STYLE = "<html><body style='width:160px'>";
	private static final WomMemberSort DEFAULT_SORT = WomMemberSort.TOTAL_XP;
	private static final int STATUS_REFRESH_MS = 1_000;
	private static final int SCROLLBAR_WIDTH = 8;
	private static final int SCROLL_UNIT_INCREMENT = 16;

	private final WomClanPlugin plugin;

	private final JLabel statusLabel;
	private final JLabel clanNameLabel;
	private final JLabel clanChatLabel;
	private final JLabel memberCountLabel;
	private final JLabel totalXpLabel;
	private final JLabel totalEhpLabel;
	private final JLabel totalEhbLabel;
	private final JButton syncButton;
	private final JButton detailsButton;
	private final JLabel resultCountLabel;
	private final CardLayout cards = new CardLayout();
	private final JPanel cardHolder = new JPanel(cards);
	private final JTextField groupIdField = new JTextField();
	private final JLabel setupErrorLabel = new JLabel();
	private final JComboBox<WomMemberSort> sortCombo;
	private final JTextField searchField;
	private final JPanel memberListPanel;

	/** The last successfully fetched data, kept so a failed refresh does not blank the panel. */
	private WomClanData currentData;
	private List<WomMember> allMembers = new ArrayList<>();

	/** Repaints the elapsed-time and cooldown text so it stays true between fetches. */
	private final Timer statusTimer;

	private WomExpandedWindow expandedWindow;

	/** Flushes the expanded window's bounds; see WomWindowGeometry.restoreAndTrack. */
	private Runnable saveExpandedGeometry;

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

		detailsButton = new JButton("Clan Details");
		detailsButton.setFont(FontManager.getRunescapeSmallFont());
		detailsButton.setFocusPainted(false);
		styleHeaderButton(detailsButton);
		detailsButton.setToolTipText(DETAILS_TOOLTIP);
		detailsButton.addActionListener(e -> openExpandedWindow());

		JPanel buttonRow = new JPanel(new GridLayout(1, 2, 8, 0));
		buttonRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		buttonRow.add(syncButton);
		buttonRow.add(detailsButton);

		JPanel topBar = new JPanel(new BorderLayout());
		topBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		topBar.setBorder(new EmptyBorder(8, 8, 6, 8));
		topBar.add(buttonRow, BorderLayout.NORTH);
		topBar.add(statusLabel, BorderLayout.SOUTH);

		// ── Clan identity ──────────────────────────────────────────────────────
		// Nested BorderLayouts rather than a BoxLayout: each row then gets the panel's full width,
		// instead of being squeezed to its own preferred width and ellipsised.
		clanNameLabel = new JLabel("Clan");
		clanNameLabel.setFont(FontManager.getRunescapeBoldFont());
		clanNameLabel.setForeground(Color.YELLOW);

		memberCountLabel = createClanStatLabel();
		memberCountLabel.setHorizontalAlignment(SwingConstants.RIGHT);

		clanChatLabel = new JLabel("No clan data loaded");
		clanChatLabel.setFont(FontManager.getRunescapeSmallFont());
		clanChatLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JPanel nameRow = new JPanel(new BorderLayout(6, 0));
		nameRow.setOpaque(false);
		nameRow.add(clanNameLabel, BorderLayout.CENTER);
		nameRow.add(memberCountLabel, BorderLayout.EAST);

		totalXpLabel = createClanStatLabel();
		totalEhpLabel = createClanStatLabel();
		totalEhbLabel = createClanStatLabel();

		// One compact row of abbreviated totals; the exact figures live in each label's tooltip.
		JPanel statsPanel = new JPanel(new GridLayout(1, 3, 6, 0));
		statsPanel.setOpaque(false);
		statsPanel.setBorder(new EmptyBorder(6, 0, 0, 0));
		statsPanel.add(totalXpLabel);
		statsPanel.add(totalEhpLabel);
		statsPanel.add(totalEhbLabel);

		JPanel clanInfoPanel = new JPanel(new BorderLayout(0, 2));
		clanInfoPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		clanInfoPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
		clanInfoPanel.add(nameRow, BorderLayout.NORTH);
		clanInfoPanel.add(clanChatLabel, BorderLayout.CENTER);
		clanInfoPanel.add(statsPanel, BorderLayout.SOUTH);
		updateClanInfo(null);

		// ── Sort selector ──────────────────────────────────────────────────────
		// The list shows names and roles only, so the ordering has to be stated rather than implied.
		sortCombo = new JComboBox<>(WomMemberSort.values());
		sortCombo.setSelectedItem(DEFAULT_SORT);
		sortCombo.setFont(FontManager.getRunescapeSmallFont());
		sortCombo.setToolTipText("Choose how the member list is ordered");
		sortCombo.addActionListener(e -> rebuildFromMembers());

		JLabel sortLabel = new JLabel("Sort by");
		sortLabel.setFont(FontManager.getRunescapeSmallFont());
		sortLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		sortLabel.setBorder(new EmptyBorder(0, 0, 0, 6));

		JPanel sortWrapper = new JPanel(new BorderLayout());
		sortWrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		sortWrapper.setBorder(new EmptyBorder(0, 8, 6, 8));
		sortWrapper.add(sortLabel, BorderLayout.WEST);
		sortWrapper.add(sortCombo, BorderLayout.CENTER);

		// ── Search field ───────────────────────────────────────────────────────
		searchField = new JTextField();
		searchField.setFont(FontManager.getRunescapeSmallFont());
		searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchField.setForeground(Color.WHITE);
		searchField.setCaretColor(Color.WHITE);
		searchField.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(4, 6, 4, 6)));
		searchField.setToolTipText("Type part of a member's name");
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			public void insertUpdate(DocumentEvent e)
			{
				rebuildFromMembers();
			}

			public void removeUpdate(DocumentEvent e)
			{
				rebuildFromMembers();
			}

			public void changedUpdate(DocumentEvent e)
			{
				rebuildFromMembers();
			}
		});

		// A visible label rather than a placeholder tooltip: the field's purpose should not need a
		// hover to discover, and the count answers "did that match anything?" on the spot.
		JLabel searchLabel = new JLabel("Search members");
		searchLabel.setFont(FontManager.getRunescapeSmallFont());
		searchLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		searchLabel.setLabelFor(searchField);
		searchLabel.setBorder(new EmptyBorder(0, 0, 2, 0));

		resultCountLabel = createClanStatLabel();
		resultCountLabel.setBorder(new EmptyBorder(2, 0, 0, 0));

		JPanel searchWrapper = new JPanel(new BorderLayout());
		searchWrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchWrapper.setBorder(new EmptyBorder(0, 8, 8, 8));
		searchWrapper.add(searchLabel, BorderLayout.NORTH);
		searchWrapper.add(searchField, BorderLayout.CENTER);
		searchWrapper.add(resultCountLabel, BorderLayout.SOUTH);

		JPanel listControls = new JPanel(new BorderLayout());
		listControls.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		listControls.add(sortWrapper, BorderLayout.NORTH);
		listControls.add(searchWrapper, BorderLayout.SOUTH);

		JPanel headerPanel = new JPanel(new BorderLayout());
		headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		// Clan identity first: the sidebar is about a clan, not about its two buttons.
		headerPanel.add(clanInfoPanel, BorderLayout.NORTH);
		headerPanel.add(topBar, BorderLayout.CENTER);
		headerPanel.add(listControls, BorderLayout.SOUTH);

		JPanel clanCard = new JPanel(new BorderLayout(0, 0));
		clanCard.setBackground(ColorScheme.DARK_GRAY_COLOR);
		clanCard.add(headerPanel, BorderLayout.NORTH);

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

		clanCard.add(scrollPane, BorderLayout.CENTER);

		// Two states, not one panel with everything greyed out: with no group configured there is
		// nothing to summarise, search or expand, and offering those controls only buries the one
		// action that matters.
		cardHolder.setBackground(ColorScheme.DARK_GRAY_COLOR);
		cardHolder.add(buildSetupCard(), SETUP_CARD);
		cardHolder.add(clanCard, CLAN_CARD);
		add(cardHolder, BorderLayout.CENTER);

		setGroupConfigured(plugin.isGroupConfigured());
		showPlaceholder("No data yet.\nHit Sync Now to load members.");

		statusTimer = new Timer(STATUS_REFRESH_MS, e -> refreshSyncStatus());
		statusTimer.start();
		refreshSyncStatus();
	}

	/**
	 * The state shown before a clan is chosen: what the plugin needs, where to find it, and a field
	 * to enter it. RuneLite exposes no supported way for a Plugin Hub plugin to open another
	 * plugin's settings panel, so the setting is offered here rather than pointed at.
	 */
	private JPanel buildSetupCard()
	{
		JLabel title = new JLabel("Connect your clan");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.YELLOW);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel explanation = new JLabel(WRAP_STYLE + "This plugin reads your clan from Wise Old Man."
			+ "<br><br>Open your clan on wiseoldman.net and copy the number from the address bar:"
			+ "<br>wiseoldman.net/groups/<b>2300</b>"
			+ "<br><br>You can change it again later in the plugin's settings.</body></html>");
		explanation.setFont(FontManager.getRunescapeSmallFont());
		explanation.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		explanation.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel fieldLabel = new JLabel("WOM Group ID");
		fieldLabel.setFont(FontManager.getRunescapeSmallFont());
		fieldLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		fieldLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		groupIdField.setFont(FontManager.getRunescapeSmallFont());
		groupIdField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		groupIdField.setForeground(Color.WHITE);
		groupIdField.setCaretColor(Color.WHITE);
		groupIdField.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(4, 6, 4, 6)));
		groupIdField.setAlignmentX(Component.LEFT_ALIGNMENT);
		groupIdField.addActionListener(e -> onConnectClicked());

		setupErrorLabel.setFont(FontManager.getRunescapeSmallFont());
		setupErrorLabel.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
		setupErrorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton connectButton = new JButton("Connect clan");
		connectButton.setFont(FontManager.getRunescapeSmallFont());
		connectButton.setFocusPainted(false);
		styleHeaderButton(connectButton);
		connectButton.setToolTipText("Save this group ID and load the clan");
		connectButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		connectButton.addActionListener(e -> onConnectClicked());

		JButton findButton = new JButton("Find my group ID");
		findButton.setFont(FontManager.getRunescapeSmallFont());
		findButton.setFocusPainted(false);
		styleHeaderButton(findButton);
		findButton.setToolTipText("Open wiseoldman.net/groups in your browser");
		findButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		findButton.addActionListener(e -> LinkBrowser.browse(WOM_GROUPS_URL));

		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARK_GRAY_COLOR);
		card.setBorder(new EmptyBorder(12, 10, 12, 10));
		card.add(title);
		card.add(Box.createVerticalStrut(8));
		card.add(explanation);
		card.add(Box.createVerticalStrut(12));
		card.add(fieldLabel);
		card.add(Box.createVerticalStrut(4));
		card.add(groupIdField);
		card.add(Box.createVerticalStrut(4));
		card.add(setupErrorLabel);
		card.add(Box.createVerticalStrut(8));
		card.add(connectButton);
		card.add(Box.createVerticalStrut(6));
		card.add(findButton);
		card.add(Box.createVerticalGlue());

		// A text field in a BoxLayout would otherwise stretch to fill the column.
		groupIdField.setMaximumSize(new Dimension(Integer.MAX_VALUE, groupIdField.getPreferredSize().height));

		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.add(card, BorderLayout.NORTH);
		return wrapper;
	}

	private void onConnectClicked()
	{
		int groupId = parseGroupId(groupIdField.getText().trim());

		if (groupId <= 0)
		{
			setupErrorLabel.setText(WRAP_STYLE + "Enter the number from the wiseoldman.net/groups/… address.</body></html>");
			return;
		}

		setupErrorLabel.setText("");
		plugin.setGroupId(groupId);
	}

	private static int parseGroupId(String text)
	{
		try
		{
			return Integer.parseInt(text);
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}

	/** Switches between the setup state and the clan view. */
	void setGroupConfigured(boolean configured)
	{
		cards.show(cardHolder, configured ? CLAN_CARD : SETUP_CARD);

		if (!configured)
		{
			groupIdField.setText("");
			setupErrorLabel.setText("");
		}
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
		updateClanInfo(clanData.getInfo() == null ? buildClanInfo(null, allMembers) : clanData.getInfo());
		refreshSyncStatus();
		rebuildFromMembers();

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
		rebuildFromMembers();

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
		syncButton.setText(WomFormat.syncButtonText(status, "Sync Now"));

		statusLabel.setText(WomFormat.syncSummary(status, currentData != null, now));
		statusLabel.setToolTipText(status.hasSucceeded()
			? "Last successful sync: " + WomFormat.timestamp(status.getLastSuccessMs())
			: null);

		// Nothing to expand into a details window until a fetch has actually produced something.
		detailsButton.setEnabled(currentData != null);
		detailsButton.setToolTipText(currentData == null
			? "Sync the clan first — there is nothing to show yet"
			: DETAILS_TOOLTIP);
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
			if (saveExpandedGeometry != null)
			{
				// Synchronously, because dispose()'s WINDOW_CLOSED may never be dispatched if the
				// client is on its way out.
				saveExpandedGeometry.run();
			}
			expandedWindow.dispose();
		}
	}

	// ── Private helpers ────────────────────────────────────────────────────────

	private void openExpandedWindow()
	{
		if (expandedWindow == null || !expandedWindow.isDisplayable())
		{
			expandedWindow = new WomExpandedWindow(plugin);
			saveExpandedGeometry = plugin.windowGeometry().restoreAndTrack(expandedWindow, this);
		}
		expandedWindow.setClanData(currentData);
		expandedWindow.setVisible(true);
		expandedWindow.toFront();
	}

	/** Applies the selected ordering and the search filter, in that order, and redraws the list. */
	private void rebuildFromMembers()
	{
		String query = searchField.getText().trim();
		String needle = query.toLowerCase(Locale.US);
		List<WomMember> filtered = new ArrayList<>();

		for (WomMember m : selectedSort().sort(allMembers))
		{
			if (needle.isEmpty() || m.getDisplayName().toLowerCase(Locale.US).contains(needle))
			{
				filtered.add(m);
			}
		}

		resultCountLabel.setText(WomFormat.memberCount(filtered.size(), allMembers.size(), query));
		rebuildList(filtered);
	}

	private WomMemberSort selectedSort()
	{
		WomMemberSort selected = (WomMemberSort) sortCombo.getSelectedItem();
		return selected == null ? DEFAULT_SORT : selected;
	}

	private void updateClanInfo(WomClanInfo info)
	{
		if (info == null)
		{
			clanNameLabel.setText("Clan");
			clanNameLabel.setToolTipText(null);
			clanChatLabel.setText("No clan data loaded");
			memberCountLabel.setText("");
			setStat(totalXpLabel, "XP", "-", null);
			setStat(totalEhpLabel, "EHP", "-", null);
			setStat(totalEhbLabel, "EHB", "-", null);
			return;
		}

		clanNameLabel.setText(info.getName());
		clanNameLabel.setToolTipText(info.getName());
		memberCountLabel.setText(WomFormat.memberCount(info.getMemberCount(), info.getMemberCount(), ""));
		clanChatLabel.setText(info.getClanChat().isEmpty() ? "Clan chat: -" : "Clan chat: " + info.getClanChat());
		setStat(totalXpLabel, "XP", WomFormat.abbreviate(info.getTotalXp()), WomFormat.integer(info.getTotalXp()));
		setStat(totalEhpLabel, "EHP", WomFormat.abbreviate(info.getTotalEhp()), WomFormat.integer(info.getTotalEhp()));
		setStat(totalEhbLabel, "EHB", WomFormat.abbreviate(info.getTotalEhb()), WomFormat.integer(info.getTotalEhb()));
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

	/** Shows an abbreviated total, keeping the exact figure one hover away. */
	private void setStat(JLabel label, String name, String abbreviated, String exact)
	{
		label.setText("<html><b>" + name + "</b> " + abbreviated + "</html>");
		label.setToolTipText(exact == null ? null : name + ": " + exact);
	}

	private void rebuildList(List<WomMember> members)
	{
		memberListPanel.removeAll();

		if (members.isEmpty())
		{
			showPlaceholder(describeEmptyList());
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

	/**
	 * Distinguishes the three ways the list can come up empty. None of them is "no group
	 * configured": that state never reaches this card.
	 */
	private String describeEmptyList()
	{
		if (currentData == null)
		{
			return "No data yet.\nHit Sync Now to load members.";
		}
		if (allMembers.isEmpty())
		{
			return "This clan has no members on\nWise Old Man yet.";
		}
		// The count above the list already states the miss, so this says what to do about it.
		return "No members match \"" + searchField.getText().trim() + "\".\n\nClear the search to see all members.";
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
