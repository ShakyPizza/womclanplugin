package com.womclan;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * A single row in the member list, displaying one clan member's name and role.
 */
class WomMemberPanel extends JPanel
{
	private static final int HORIZONTAL_PADDING = 8;
	private static final int LABEL_GAP = 6;

	/** Width a row actually gets: the panel, less the scrollbar, its border and this row's padding. */
	private static final int AVAILABLE_WIDTH = PluginPanel.PANEL_WIDTH
		- PluginPanel.SCROLLBAR_WIDTH
		- 2 * PluginPanel.BORDER_OFFSET
		- 2 * HORIZONTAL_PADDING
		- LABEL_GAP;

	WomMemberPanel(WomMember member, boolean odd)
	{
		setBorder(new EmptyBorder(5, HORIZONTAL_PADDING, 5, HORIZONTAL_PADDING));
		setBackground(odd ? ColorScheme.DARKER_GRAY_COLOR : ColorScheme.DARK_GRAY_COLOR);

		JLabel nameLabel = new JLabel(member.getDisplayName());
		nameLabel.setFont(FontManager.getRunescapeBoldFont());
		nameLabel.setForeground(Color.WHITE);

		JLabel roleLabel = new JLabel(WomFormat.role(member.getRole()));
		roleLabel.setFont(FontManager.getRunescapeSmallFont());
		roleLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		setToolTipText(member.getDisplayName() + " — " + WomFormat.role(member.getRole()));

		if (fitsOnOneLine(nameLabel, roleLabel))
		{
			setLayout(new BorderLayout(LABEL_GAP, 0));
			roleLabel.setHorizontalAlignment(SwingConstants.RIGHT);
			add(nameLabel, BorderLayout.CENTER);
			add(roleLabel, BorderLayout.EAST);
		}
		else
		{
			// A long name and a long role fight over one line and both end up ellipsised, so the
			// role drops underneath instead.
			setLayout(new BorderLayout(0, 1));
			add(nameLabel, BorderLayout.NORTH);
			add(roleLabel, BorderLayout.SOUTH);
		}
	}

	private static boolean fitsOnOneLine(JLabel nameLabel, JLabel roleLabel)
	{
		int width = nameLabel.getFontMetrics(nameLabel.getFont()).stringWidth(nameLabel.getText())
			+ roleLabel.getFontMetrics(roleLabel.getFont()).stringWidth(roleLabel.getText());
		return width <= AVAILABLE_WIDTH;
	}
}
