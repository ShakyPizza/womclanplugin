package com.womclan;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * A single row in the member list, displaying one clan member's stats.
 */
class WomMemberPanel extends JPanel
{
	WomMemberPanel(WomMember member, boolean odd)
	{
		setLayout(new BorderLayout(6, 0));
		setBorder(new EmptyBorder(5, 8, 5, 8));
		setBackground(odd ? ColorScheme.DARKER_GRAY_COLOR : ColorScheme.DARK_GRAY_COLOR);

		JLabel nameLabel = new JLabel(member.getDisplayName());
		nameLabel.setFont(FontManager.getRunescapeBoldFont());
		nameLabel.setForeground(Color.WHITE);
		add(nameLabel, BorderLayout.WEST);

		JLabel roleLabel = new JLabel(WomFormat.role(member.getRole()), SwingConstants.RIGHT);
		roleLabel.setFont(FontManager.getRunescapeSmallFont());
		roleLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		add(roleLabel, BorderLayout.EAST);
	}
}
