package com.womclan;

import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.border.Border;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A resizable standalone window showing clan data in sortable tables.
 */
class WomExpandedWindow extends JFrame
{
	private static final NumberFormat INTEGER_FORMAT = NumberFormat.getIntegerInstance(Locale.US);
	private static final String WINDOW_TITLE = "WiseOldMan Clan Stats";
	private static final int SYNC_REFRESH_MS = 1_000;
	private static final int ACHIEVEMENT_DETAIL_COLUMN = 3;
	private static final int EHB_COLUMN = 5;
	private static final int RANK_COLUMN_WIDTH = 70;
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
		.withZone(ZoneId.systemDefault());

	private final DefaultTableModel memberTableModel;
	private final DefaultTableModel achievementTableModel;
	private final DefaultTableModel activityTableModel;
	private final DefaultTableModel nameChangeTableModel;
	private final TableRowSorter<DefaultTableModel> memberSorter;
	private final JLabel achievementStatusLabel = createStatusLabel();
	private final JLabel activityStatusLabel = createStatusLabel();
	private final JLabel nameChangeStatusLabel = createStatusLabel();

	private final WomClanPlugin plugin;
	private final JLabel clanLabel = new JLabel();
	private final JLabel syncLabel = new JLabel();
	private final JButton refreshButton = new JButton();

	/** Keeps the header's elapsed time and cooldown honest while the window sits open. */
	private final Timer syncTimer;

	private boolean hasData;

	WomExpandedWindow(WomClanPlugin plugin)
	{
		super(WINDOW_TITLE);
		this.plugin = plugin;
		setSize(850, 560);
		setMinimumSize(new Dimension(620, 380));
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setLayout(new BorderLayout(0, 0));

		memberTableModel = createMemberTableModel();
		achievementTableModel = createAchievementTableModel();
		activityTableModel = createActivityTableModel();
		nameChangeTableModel = createNameChangeTableModel();

		JTable memberTable = createTable(memberTableModel);
		memberSorter = new TableRowSorter<>(memberTableModel);
		memberTable.setRowSorter(memberSorter);
		// Sort through the sorter rather than pre-sorting the rows, so the header carries the
		// indicator that says what the initial order is. Set once: a refresh must not stomp on
		// whatever column the user has since chosen.
		memberSorter.setSortKeys(Collections.singletonList(new RowSorter.SortKey(EHB_COLUMN, SortOrder.DESCENDING)));
		// The rank only ever holds a member ordinal, so it is capped rather than left to share the
		// extra width evenly with the name and role columns.
		memberTable.getColumnModel().getColumn(0).setPreferredWidth(RANK_COLUMN_WIDTH);
		memberTable.getColumnModel().getColumn(0).setMaxWidth(RANK_COLUMN_WIDTH + 20);
		memberTable.getColumnModel().getColumn(1).setPreferredWidth(190);
		memberTable.getColumnModel().getColumn(2).setPreferredWidth(130);
		memberTable.getColumnModel().getColumn(3).setPreferredWidth(140);
		memberTable.getColumnModel().getColumn(4).setPreferredWidth(80);
		memberTable.getColumnModel().getColumn(5).setPreferredWidth(80);
		memberTable.getColumnModel().getColumn(0).setCellRenderer(new IntegerRenderer());
		memberTable.getColumnModel().getColumn(3).setCellRenderer(new IntegerRenderer());
		memberTable.getColumnModel().getColumn(4).setCellRenderer(new DecimalRenderer());
		memberTable.getColumnModel().getColumn(5).setCellRenderer(new DecimalRenderer());

		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab("Members", buildMembersTab(memberTable));
		tabs.addTab("Achievements", buildAchievementTab());
		tabs.addTab("Activity", buildActivityTab());
		tabs.addTab("Name Changes", buildNameChangeTab());
		add(buildHeader(), BorderLayout.NORTH);
		add(tabs, BorderLayout.CENTER);

		setClanData(null);

		syncTimer = new Timer(SYNC_REFRESH_MS, e -> refreshSyncStatus());
	}

	/**
	 * Replaces the window's contents. A null {@code data} empties every table, which is what a group
	 * change needs: the previous group's rows must never sit under the new group's name.
	 *
	 * <p>Only the table rows are replaced, so the selected tab, the active search filter and the
	 * user's chosen column sort all survive a refresh.</p>
	 */
	void setClanData(WomClanData data)
	{
		hasData = data != null;
		updateHeader(data == null ? null : data.getInfo());
		refreshSyncStatus();

		if (data == null)
		{
			clearTables();
			return;
		}

		memberTableModel.setRowCount(0);
		List<WomMember> sortedMembers = new ArrayList<>(data.getMembers());
		sortedMembers.sort(Comparator.comparingDouble(WomMember::getEhb).reversed());
		for (int i = 0; i < sortedMembers.size(); i++)
		{
			WomMember m = sortedMembers.get(i);
			memberTableModel.addRow(new Object[]{
				// The rank is the player's standing by EHB, computed once and carried with them, so
				// it keeps meaning the same thing after the table is re-sorted or filtered.
				i + 1,
				m.getDisplayName(),
				formatRole(m.getRole()),
				m.getTotalXp(),
				m.getEhp(),
				m.getEhb()
			});
		}

		WomHistory<WomAchievement> achievements = data.getAchievements();
		achievementTableModel.setRowCount(0);
		for (WomAchievement achievement : achievements.getEntries())
		{
			achievementTableModel.addRow(new Object[]{
				formatInstant(achievement.getCreatedAt()),
				achievement.getDisplayName(),
				achievement.getName(),
				WomFormat.achievementDetail(achievement)
			});
		}

		WomHistory<WomGroupActivity> activity = data.getActivity();
		activityTableModel.setRowCount(0);
		for (WomGroupActivity entry : activity.getEntries())
		{
			if (!entry.isMembershipChange())
			{
				continue;
			}

			activityTableModel.addRow(new Object[]{
				formatInstant(entry.getCreatedAt()),
				entry.getDisplayName(),
				formatActivityType(entry.getType()),
				formatRole(entry.getRole())
			});
		}

		WomHistory<WomNameChange> nameChanges = data.getNameChanges();
		nameChangeTableModel.setRowCount(0);
		for (WomNameChange nameChange : nameChanges.getEntries())
		{
			nameChangeTableModel.addRow(new Object[]{
				formatInstant(nameChange.getResolvedAt() == null ? nameChange.getCreatedAt() : nameChange.getResolvedAt()),
				nameChange.getDisplayName(),
				nameChange.getOldName(),
				nameChange.getNewName(),
				formatNameChangeStatus(nameChange.getStatus())
			});
		}

		// Row counts rather than entry counts: the activity tab shows only membership changes.
		applyHistoryStatus(achievementStatusLabel, achievements, achievementTableModel.getRowCount(), "achievements");
		applyHistoryStatus(activityStatusLabel, activity, activityTableModel.getRowCount(), "activity");
		applyHistoryStatus(nameChangeStatusLabel, nameChanges, nameChangeTableModel.getRowCount(), "name changes");
	}

	private void clearTables()
	{
		memberTableModel.setRowCount(0);
		achievementTableModel.setRowCount(0);
		activityTableModel.setRowCount(0);
		nameChangeTableModel.setRowCount(0);
		applyHistoryStatus(achievementStatusLabel, WomHistory.pending(), 0, "achievements");
		applyHistoryStatus(activityStatusLabel, WomHistory.pending(), 0, "activity");
		applyHistoryStatus(nameChangeStatusLabel, WomHistory.pending(), 0, "name changes");
	}

	private void applyHistoryStatus(JLabel label, WomHistory<?> history, int displayedRows, String noun)
	{
		label.setText(WomFormat.historyStatus(history.getStatus(), displayedRows, noun));
		label.setToolTipText(history.getError());
	}

	private static JLabel createStatusLabel()
	{
		JLabel label = new JLabel();
		label.setBorder(BorderFactory.createEmptyBorder(0, 8, 6, 8));
		return label;
	}

	/**
	 * A compact bar shared by every tab: which clan is on screen, how fresh it is, and one refresh
	 * that goes through the plugin's cooldown rather than inventing a second one.
	 */
	private JPanel buildHeader()
	{
		clanLabel.setFont(FontManager.getRunescapeBoldFont());

		syncLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		syncLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));

		refreshButton.setFocusPainted(false);
		refreshButton.addActionListener(e ->
		{
			plugin.requestManualSync();
			refreshSyncStatus();
		});

		JPanel header = new JPanel(new BorderLayout(8, 0));
		header.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		header.add(clanLabel, BorderLayout.WEST);
		header.add(syncLabel, BorderLayout.CENTER);
		header.add(refreshButton, BorderLayout.EAST);
		return header;
	}

	private void updateHeader(WomClanInfo info)
	{
		if (info == null)
		{
			setTitle(WINDOW_TITLE);
			clanLabel.setText("No clan loaded");
			return;
		}

		int members = info.getMemberCount();
		setTitle(WINDOW_TITLE + " — " + info.getName());
		clanLabel.setText(info.getName() + "  ·  " + WomFormat.integer(members) + (members == 1 ? " member" : " members"));
	}

	/** Redraws the header's freshness line and refresh button from the plugin's shared sync state. */
	private void refreshSyncStatus()
	{
		WomSyncStatus status = plugin.syncStatus();
		syncLabel.setText(WomFormat.syncSummary(status, hasData, System.currentTimeMillis()));
		syncLabel.setToolTipText(status.hasSucceeded()
			? "Last successful sync: " + WomFormat.timestamp(status.getLastSuccessMs())
			: null);
		refreshButton.setEnabled(status.isManualSyncAllowed());
		refreshButton.setText(WomFormat.syncButtonText(status, "Refresh"));
	}

	@Override
	public void setVisible(boolean visible)
	{
		super.setVisible(visible);

		if (visible)
		{
			refreshSyncStatus();
			syncTimer.start();
		}
		else
		{
			syncTimer.stop();
		}
	}

	@Override
	public void dispose()
	{
		syncTimer.stop();
		super.dispose();
	}

	private JPanel buildMembersTab(JTable memberTable)
	{
		JTextField searchField = new JTextField();
		searchField.setFont(FontManager.getRunescapeSmallFont());
		searchField.setToolTipText("Filter members by name...");

		JPanel searchWrapper = new JPanel(new BorderLayout(6, 0));
		searchWrapper.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		searchWrapper.add(new JLabel("Search:"), BorderLayout.WEST);
		searchWrapper.add(searchField, BorderLayout.CENTER);

		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			public void insertUpdate(DocumentEvent e) { applyFilter(); }
			public void removeUpdate(DocumentEvent e) { applyFilter(); }
			public void changedUpdate(DocumentEvent e) { applyFilter(); }

			private void applyFilter()
			{
				String text = searchField.getText().trim();
				memberSorter.setRowFilter(text.isEmpty() ? null : RowFilter.regexFilter("(?i)" + Pattern.quote(text), 1));
			}
		});

		JPanel panel = new JPanel(new BorderLayout(0, 0));
		panel.add(searchWrapper, BorderLayout.NORTH);
		panel.add(new JScrollPane(memberTable), BorderLayout.CENTER);
		return panel;
	}

	private JPanel buildActivityTab()
	{
		JTable activityTable = createTable(activityTableModel);
		activityTable.setRowSorter(new TableRowSorter<>(activityTableModel));
		activityTable.getColumnModel().getColumn(0).setPreferredWidth(115);
		activityTable.getColumnModel().getColumn(1).setPreferredWidth(180);
		activityTable.getColumnModel().getColumn(2).setPreferredWidth(100);
		activityTable.getColumnModel().getColumn(3).setPreferredWidth(110);

		JPanel panel = new JPanel(new BorderLayout(0, 0));
		panel.add(wrapTable("Recent Activity", activityTable, activityStatusLabel), BorderLayout.CENTER);
		return panel;
	}

	private JPanel buildAchievementTab()
	{
		// The metric and threshold live in a hidden model column and surface as a tooltip: showing
		// them as their own columns repeated the milestone the achievement name already states.
		JTable achievementTable = new WomTable(achievementTableModel, ACHIEVEMENT_DETAIL_COLUMN);
		achievementTable.setRowSorter(new TableRowSorter<>(achievementTableModel));
		achievementTable.removeColumn(achievementTable.getColumnModel().getColumn(ACHIEVEMENT_DETAIL_COLUMN));
		achievementTable.getColumnModel().getColumn(0).setPreferredWidth(115);
		achievementTable.getColumnModel().getColumn(1).setPreferredWidth(140);
		achievementTable.getColumnModel().getColumn(2).setPreferredWidth(330);

		JPanel panel = new JPanel(new BorderLayout(0, 0));
		panel.add(wrapTable("Recent Achievements", achievementTable, achievementStatusLabel), BorderLayout.CENTER);
		return panel;
	}

	private JPanel buildNameChangeTab()
	{
		JTable nameChangeTable = createTable(nameChangeTableModel);
		nameChangeTable.setRowSorter(new TableRowSorter<>(nameChangeTableModel));
		nameChangeTable.getColumnModel().getColumn(0).setPreferredWidth(115);
		nameChangeTable.getColumnModel().getColumn(1).setPreferredWidth(140);
		nameChangeTable.getColumnModel().getColumn(2).setPreferredWidth(140);
		nameChangeTable.getColumnModel().getColumn(3).setPreferredWidth(140);
		nameChangeTable.getColumnModel().getColumn(4).setPreferredWidth(90);

		JPanel panel = new JPanel(new BorderLayout(0, 0));
		panel.add(wrapTable("Recent Name Changes", nameChangeTable, nameChangeStatusLabel), BorderLayout.CENTER);
		return panel;
	}

	private JPanel wrapTable(String title, JTable table, JLabel statusLabel)
	{
		JLabel label = new JLabel(title);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setBorder(BorderFactory.createEmptyBorder(8, 8, 2, 8));

		JPanel heading = new JPanel(new BorderLayout(0, 0));
		heading.add(label, BorderLayout.NORTH);
		heading.add(statusLabel, BorderLayout.SOUTH);

		JPanel panel = new JPanel(new BorderLayout(0, 0));
		panel.add(heading, BorderLayout.NORTH);
		panel.add(new JScrollPane(table), BorderLayout.CENTER);
		return panel;
	}

	private JTable createTable(DefaultTableModel tableModel)
	{
		return new WomTable(tableModel, -1);
	}

	private DefaultTableModel createMemberTableModel()
	{
		return new DefaultTableModel(new String[]{"EHB Rank", "Name", "Role", "Total XP", "EHP", "EHB"}, 0)
		{
			@Override
			public boolean isCellEditable(int row, int col)
			{
				return false;
			}

			@Override
			public Class<?> getColumnClass(int col)
			{
				switch (col)
				{
					case 0: return Integer.class;
					case 3: return Long.class;
					case 4:
					case 5: return Double.class;
					default: return String.class;
				}
			}
		};
	}

	private DefaultTableModel createAchievementTableModel()
	{
		return new DefaultTableModel(new String[]{"When", "Player", "Achievement", "Detail"}, 0)
		{
			@Override
			public boolean isCellEditable(int row, int col)
			{
				return false;
			}
		};
	}

	private DefaultTableModel createActivityTableModel()
	{
		return new DefaultTableModel(new String[]{"Time", "Player", "Action", "Role"}, 0)
		{
			@Override
			public boolean isCellEditable(int row, int col)
			{
				return false;
			}
		};
	}

	private DefaultTableModel createNameChangeTableModel()
	{
		return new DefaultTableModel(new String[]{"Resolved", "Player", "Old Name", "New Name", "Status"}, 0)
		{
			@Override
			public boolean isCellEditable(int row, int col)
			{
				return false;
			}
		};
	}

	private String formatInstant(Instant instant)
	{
		return instant == null ? "" : DATE_FORMAT.format(instant);
	}

	private String formatActivityType(String type)
	{
		if ("joined".equals(type))
		{
			return "Joined";
		}
		if ("left".equals(type))
		{
			return "Left";
		}
		if ("changed_role".equals(type))
		{
			return "Changed Role";
		}
		return WomFormat.titleCase(type);
	}

	private String formatNameChangeStatus(String status)
	{
		return WomFormat.titleCase(status);
	}

	private String formatRole(String role)
	{
		return WomFormat.role(role);
	}

	/**
	 * The table styling shared by every tab: subtle row striping, roomy rows, and a tooltip that
	 * explains the row it is hovering over — the full cell text when the column is too narrow to
	 * show it, plus an optional detail held in a hidden model column.
	 */
	private static class WomTable extends JTable
	{
		private static final int ROW_PADDING = 6;
		private static final Border CELL_PADDING = BorderFactory.createEmptyBorder(0, 6, 0, 6);

		private final int detailModelColumn;
		private Color stripeBase;
		private Color stripeColor;

		WomTable(DefaultTableModel model, int detailModelColumn)
		{
			super(model);
			this.detailModelColumn = detailModelColumn;

			setFillsViewportHeight(true);
			getTableHeader().setReorderingAllowed(false);
			setAutoResizeMode(AUTO_RESIZE_ALL_COLUMNS);
			// Striping carries the row boundaries, so grid lines would just add noise.
			setShowGrid(false);
			setIntercellSpacing(new Dimension(0, 0));
			setRowHeight(getRowHeight() + ROW_PADDING);
			ToolTipManager.sharedInstance().registerComponent(this);
		}

		@Override
		public Component prepareRenderer(TableCellRenderer renderer, int row, int column)
		{
			Component c = super.prepareRenderer(renderer, row, column);

			if (!isRowSelected(row))
			{
				// Selection and focus keep the look and feel's own colours; only unselected rows
				// are striped, so a selected row still stands out against either stripe.
				c.setBackground(row % 2 == 0 ? getBackground() : stripeColor());
			}

			if (c instanceof JComponent)
			{
				JComponent cell = (JComponent) c;
				// Inside the renderer's own border, so the focus rectangle stays visible.
				cell.setBorder(BorderFactory.createCompoundBorder(cell.getBorder(), CELL_PADDING));
			}

			return c;
		}

		/** A stripe derived from the current background, so it follows whatever theme is in use. */
		private Color stripeColor()
		{
			Color base = getBackground();
			if (!base.equals(stripeBase))
			{
				int shift = luminance(base) < 128 ? 12 : -12;
				stripeBase = base;
				stripeColor = new Color(
					clamp(base.getRed() + shift),
					clamp(base.getGreen() + shift),
					clamp(base.getBlue() + shift));
			}
			return stripeColor;
		}

		private static int luminance(Color color)
		{
			return (color.getRed() * 299 + color.getGreen() * 587 + color.getBlue() * 114) / 1000;
		}

		private static int clamp(int channel)
		{
			return Math.max(0, Math.min(255, channel));
		}

		@Override
		public String getToolTipText(MouseEvent event)
		{
			int viewRow = rowAtPoint(event.getPoint());
			int viewColumn = columnAtPoint(event.getPoint());
			if (viewRow < 0 || viewColumn < 0)
			{
				return null;
			}

			StringBuilder tooltip = new StringBuilder();
			String cellText = renderedText(viewRow, viewColumn);
			if (!cellText.isEmpty() && isClipped(cellText, viewColumn))
			{
				tooltip.append(cellText);
			}

			if (detailModelColumn >= 0)
			{
				Object detail = getModel().getValueAt(convertRowIndexToModel(viewRow), detailModelColumn);
				if (detail != null && !detail.toString().isEmpty())
				{
					if (tooltip.length() > 0)
					{
						tooltip.append(" — ");
					}
					tooltip.append(detail);
				}
			}

			return tooltip.length() == 0 ? null : tooltip.toString();
		}

		/** The text as the user sees it, so a formatted number tooltips as the formatted number. */
		private String renderedText(int viewRow, int viewColumn)
		{
			Component c = prepareRenderer(getCellRenderer(viewRow, viewColumn), viewRow, viewColumn);
			if (c instanceof JLabel)
			{
				String text = ((JLabel) c).getText();
				return text == null ? "" : text;
			}

			Object value = getValueAt(viewRow, viewColumn);
			return value == null ? "" : value.toString();
		}

		private boolean isClipped(String text, int viewColumn)
		{
			int available = getColumnModel().getColumn(viewColumn).getWidth() - 2 * ROW_PADDING;
			return getFontMetrics(getFont()).stringWidth(text) > available;
		}
	}

	private static class IntegerRenderer extends DefaultTableCellRenderer
	{
		IntegerRenderer()
		{
			setHorizontalAlignment(SwingConstants.RIGHT);
		}

		@Override
		protected void setValue(Object value)
		{
			setText(value instanceof Number
				? INTEGER_FORMAT.format(((Number) value).longValue())
				: "");
		}
	}

	/**
	 * Renders EHP and EHB at a fixed two decimal places. The model still holds the Double, so the
	 * column continues to sort numerically rather than by the formatted text.
	 */
	private static class DecimalRenderer extends DefaultTableCellRenderer
	{
		private static final NumberFormat DECIMAL_FORMAT = NumberFormat.getNumberInstance(Locale.US);

		static
		{
			DECIMAL_FORMAT.setMinimumFractionDigits(2);
			DECIMAL_FORMAT.setMaximumFractionDigits(2);
		}

		DecimalRenderer()
		{
			setHorizontalAlignment(SwingConstants.RIGHT);
		}

		@Override
		protected void setValue(Object value)
		{
			setText(value instanceof Number
				? DECIMAL_FORMAT.format(((Number) value).doubleValue())
				: "");
		}
	}
}
