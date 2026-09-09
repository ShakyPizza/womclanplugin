package com.womclan;

import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
	private static final int ACHIEVEMENT_DETAIL_COLUMN = 3;
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
		.withZone(ZoneId.systemDefault());

	private final DefaultTableModel memberTableModel;
	private final DefaultTableModel achievementTableModel;
	private final DefaultTableModel activityTableModel;
	private final DefaultTableModel nameChangeTableModel;
	private final TableRowSorter<DefaultTableModel> memberSorter;

	WomExpandedWindow()
	{
		super("WiseOldMan Clan Stats");
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
		memberTable.getColumnModel().getColumn(0).setPreferredWidth(20);
		memberTable.getColumnModel().getColumn(1).setPreferredWidth(170);
		memberTable.getColumnModel().getColumn(2).setPreferredWidth(120);
		memberTable.getColumnModel().getColumn(3).setPreferredWidth(130);
		memberTable.getColumnModel().getColumn(4).setPreferredWidth(70);
		memberTable.getColumnModel().getColumn(5).setPreferredWidth(70);
		memberTable.getColumnModel().getColumn(3).setCellRenderer(new IntegerRenderer());

		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab("Members", buildMembersTab(memberTable));
		tabs.addTab("Achievements", buildAchievementTab());
		tabs.addTab("Activity", buildActivityTab());
		tabs.addTab("Name Changes", buildNameChangeTab());
		add(tabs, BorderLayout.CENTER);
	}

	/** Replaces the table contents with the given member list. */
	void setMembers(List<WomMember> members)
	{
		setClanData(members, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
	}

	void setClanData(List<WomMember> members, List<WomAchievement> achievements, List<WomGroupActivity> activity)
	{
		setClanData(members, achievements, activity, new ArrayList<>());
	}

	void setClanData(List<WomMember> members, List<WomAchievement> achievements, List<WomGroupActivity> activity, List<WomNameChange> nameChanges)
	{
		memberTableModel.setRowCount(0);
		List<WomMember> sortedMembers = new ArrayList<>(members);
		sortedMembers.sort(Comparator.comparingDouble(WomMember::getEhb).reversed());
		for (int i = 0; i < sortedMembers.size(); i++)
		{
			WomMember m = sortedMembers.get(i);
			memberTableModel.addRow(new Object[]{
				i + 1,
				m.getDisplayName(),
				formatRole(m.getRole()),
				m.getTotalXp(),
				m.getEhp(),
				m.getEhb()
			});
		}

		achievementTableModel.setRowCount(0);
		for (WomAchievement achievement : achievements)
		{
			achievementTableModel.addRow(new Object[]{
				formatInstant(achievement.getCreatedAt()),
				achievement.getDisplayName(),
				achievement.getName(),
				WomFormat.achievementDetail(achievement)
			});
		}

		activityTableModel.setRowCount(0);
		for (WomGroupActivity entry : activity)
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

		nameChangeTableModel.setRowCount(0);
		for (WomNameChange nameChange : nameChanges)
		{
			nameChangeTableModel.addRow(new Object[]{
				formatInstant(nameChange.getResolvedAt() == null ? nameChange.getCreatedAt() : nameChange.getResolvedAt()),
				nameChange.getDisplayName(),
				nameChange.getOldName(),
				nameChange.getNewName(),
				formatNameChangeStatus(nameChange.getStatus())
			});
		}
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
		panel.add(wrapTable("Recent Activity", activityTable), BorderLayout.CENTER);
		return panel;
	}

	private JPanel buildAchievementTab()
	{
		// The metric and threshold live in a hidden model column and surface as a tooltip: showing
		// them as their own columns repeated the milestone the achievement name already states.
		JTable achievementTable = new TooltipTable(achievementTableModel, ACHIEVEMENT_DETAIL_COLUMN);
		styleTable(achievementTable);
		achievementTable.setRowSorter(new TableRowSorter<>(achievementTableModel));
		achievementTable.removeColumn(achievementTable.getColumnModel().getColumn(ACHIEVEMENT_DETAIL_COLUMN));
		achievementTable.getColumnModel().getColumn(0).setPreferredWidth(115);
		achievementTable.getColumnModel().getColumn(1).setPreferredWidth(140);
		achievementTable.getColumnModel().getColumn(2).setPreferredWidth(330);

		JPanel panel = new JPanel(new BorderLayout(0, 0));
		panel.add(wrapTable("Recent Achievements", achievementTable), BorderLayout.CENTER);
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
		panel.add(wrapTable("Recent Name Changes", nameChangeTable), BorderLayout.CENTER);
		return panel;
	}

	private JPanel wrapTable(String title, JTable table)
	{
		JLabel label = new JLabel(title);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setBorder(BorderFactory.createEmptyBorder(8, 8, 6, 8));

		JPanel panel = new JPanel(new BorderLayout(0, 0));
		panel.add(label, BorderLayout.NORTH);
		panel.add(new JScrollPane(table), BorderLayout.CENTER);
		return panel;
	}

	private JTable createTable(DefaultTableModel tableModel)
	{
		JTable table = new JTable(tableModel);
		styleTable(table);
		return table;
	}

	private void styleTable(JTable table)
	{
		table.setFillsViewportHeight(true);
		table.getTableHeader().setReorderingAllowed(false);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
	}

	private DefaultTableModel createMemberTableModel()
	{
		return new DefaultTableModel(new String[]{"#", "Name", "Role", "Total XP", "EHP", "EHB"}, 0)
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
	 * A table that explains the row it is hovering over: the full cell text when the column is too
	 * narrow to show it, plus an optional detail held in a hidden model column.
	 */
	private static class TooltipTable extends JTable
	{
		private final int detailModelColumn;

		TooltipTable(DefaultTableModel model, int detailModelColumn)
		{
			super(model);
			this.detailModelColumn = detailModelColumn;
			ToolTipManager.sharedInstance().registerComponent(this);
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
			Object cellValue = getValueAt(viewRow, viewColumn);
			String cellText = cellValue == null ? "" : cellValue.toString();
			if (!cellText.isEmpty() && isClipped(cellText, viewColumn))
			{
				tooltip.append(cellText);
			}

			Object detail = getModel().getValueAt(convertRowIndexToModel(viewRow), detailModelColumn);
			if (detail != null && !detail.toString().isEmpty())
			{
				if (tooltip.length() > 0)
				{
					tooltip.append(" — ");
				}
				tooltip.append(detail);
			}

			return tooltip.length() == 0 ? null : tooltip.toString();
		}

		private boolean isClipped(String text, int viewColumn)
		{
			int available = getColumnModel().getColumn(viewColumn).getWidth() - getIntercellSpacing().width - 4;
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
}
