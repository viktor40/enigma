package cuchaz.enigma.gui.dialog;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Window;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

import cuchaz.enigma.gui.config.keybind.KeyBinds;
import cuchaz.enigma.gui.util.GuiUtil;
import cuchaz.enigma.utils.I18n;

public class ChangeDialog {
	public static void show(Window parent) {
		// init frame
		JDialog frame = new JDialog(parent, I18n.translate("menu.view.change.title"), Dialog.DEFAULT_MODALITY_TYPE);
		JPanel textPanel = new JPanel();
		JPanel buttonPanel = new JPanel();
		frame.setLayout(new BorderLayout());
		frame.add(BorderLayout.NORTH, textPanel);
		frame.add(BorderLayout.SOUTH, buttonPanel);

		// show text
		JLabel text = new JLabel((I18n.translate("menu.view.change.summary")));
		text.setHorizontalAlignment(JLabel.CENTER);
		textPanel.add(text);

		// show ok button
		JButton okButton = new JButton(I18n.translate("prompt.ok"));
		buttonPanel.add(okButton);
		okButton.addActionListener(event -> frame.dispose());
		okButton.addKeyListener(GuiUtil.onKeyPress(e -> {
			if (KeyBinds.EXIT.matches(e)) {
				frame.dispose();
			}
		}));

		// show the frame
		frame.pack();
		frame.setResizable(false);
		frame.setLocationRelativeTo(parent);
		frame.setVisible(true);
	}
}
