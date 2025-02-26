package cuchaz.enigma.gui.dialog;

import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import cuchaz.enigma.Enigma;
import cuchaz.enigma.gui.config.Decompiler;
import cuchaz.enigma.gui.util.GridBagConstraintsBuilder;
import cuchaz.enigma.gui.util.GuiUtil;
import cuchaz.enigma.gui.util.ScaleUtil;
import cuchaz.enigma.utils.I18n;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.WindowConstants;

public class AboutDialog {
	public static void show(JFrame parent) {
		JDialog frame = new JDialog(parent, String.format(I18n.translate("menu.help.about.title"), Enigma.NAME), true);
		Container pane = frame.getContentPane();
		pane.setLayout(new GridBagLayout());

		GridBagConstraintsBuilder cb = GridBagConstraintsBuilder.create()
				.insets(2)
				.weight(1.0, 0.0)
				.anchor(GridBagConstraints.WEST);

		JLabel title = new JLabel(Enigma.NAME);
		title.setFont(title.getFont().deriveFont(title.getFont().getSize2D() * 1.5f));

		JButton okButton = new JButton(I18n.translate("prompt.ok"));
		okButton.addActionListener(e -> frame.dispose());

		pane.add(title, cb.pos(0, 0).build());
		pane.add(new JLabel(I18n.translate("menu.help.about.description")), cb.pos(0, 1).width(2).build());
		pane.add(new JLabel(I18n.translateFormatted("menu.help.about.version", Enigma.VERSION)), cb.pos(0, 2).width(2).build());
		pane.add(new JLabel(I18n.translateFormatted("menu.help.about.version.external", Decompiler.QUILTFLOWER.name, Enigma.QUILTFLOWER_VERSION)), cb.pos(0, 3).width(2).build());
		pane.add(new JLabel(I18n.translateFormatted("menu.help.about.version.external", Decompiler.CFR.name, Enigma.CFR_VERSION)), cb.pos(0, 4).width(2).build());
		pane.add(new JLabel(I18n.translateFormatted("menu.help.about.version.external", Decompiler.PROCYON.name, Enigma.PROCYON_VERSION)), cb.pos(0, 5).width(2).build());
		pane.add(GuiUtil.createLink(Enigma.URL, () -> GuiUtil.openUrl(Enigma.URL)), cb.pos(0, 6).build());
		pane.add(okButton, cb.pos(1, 6).anchor(GridBagConstraints.SOUTHEAST).build());

		frame.pack();
		frame.setResizable(false);
		frame.setLocationRelativeTo(parent);
		frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		frame.setVisible(true);
	}
}
