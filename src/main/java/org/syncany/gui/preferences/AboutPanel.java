/*
 * Syncany, www.syncany.org
 * Copyright (C) 2011-2013 Philipp C. Heckel <philipp.heckel@gmail.com> 
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.syncany.gui.preferences;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Text;
import org.syncany.Client;
import org.syncany.gui.Panel;
import org.syncany.gui.util.DesktopUtil;
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.WidgetDecorator;
import org.syncany.plugins.Plugin;
import org.syncany.plugins.Plugins;
import org.syncany.plugins.gui.GuiPlugin;
import org.syncany.plugins.local.LocalTransferPlugin;

public class AboutPanel extends Panel {
	private static String URL_AUTHOR = "http://www.philippheckel.com/";
	private static String URL_TEAM = "https://www.syncany.org/r/team";
	private static String URL_DONATE = "https://www.syncany.org/r/donate";
	
	public AboutPanel(PreferencesDialog parentDialog, Composite composite, int style) {
		super(parentDialog, composite, style);
		createContents();			
	}

	private void createContents() {
		// Main composite
		GridLayout mainCompositeGridLayout = new GridLayout(1, false);
		mainCompositeGridLayout.marginTop = 15;
		mainCompositeGridLayout.marginLeft = 10;
		mainCompositeGridLayout.marginRight = 20;

		setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		setLayout(mainCompositeGridLayout);

		// Title and welcome text
		Label titleLabel = new Label(this, SWT.WRAP);
		titleLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
		titleLabel.setText(I18n.getText("org.syncany.gui.preferences.AboutPanel.title"));

		WidgetDecorator.title(titleLabel);	

	 	// License text
	 	final String authorName = I18n.getText("org.syncany.gui.preferences.AboutPanel.about.author");
	 	final String teamName = I18n.getText("org.syncany.gui.preferences.AboutPanel.about.team");
	 	final String donateLinkName = I18n.getText("org.syncany.gui.preferences.AboutPanel.about.donate");

	 	Link licenseDescriptionLink = new Link(this, SWT.WRAP);
	 	licenseDescriptionLink.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
	 	licenseDescriptionLink.setText(I18n.getText("org.syncany.gui.preferences.AboutPanel.about.description", authorName, teamName, donateLinkName));
	 	licenseDescriptionLink.addSelectionListener(new SelectionAdapter() {
	 		@Override
	 		public void widgetSelected(SelectionEvent e) {
	 			if (authorName.equals(e.text)) {
	 				DesktopUtil.launch(URL_AUTHOR);
	 			}
	 			else if (teamName.equals(e.text)) {
	 				DesktopUtil.launch(URL_TEAM);
	 			}
	 			else if (donateLinkName.equals(e.text)) {
	 				DesktopUtil.launch(URL_DONATE);
	 			}
	 		}
		});	 
	 	
	 	// Spacing
	    new Label(this, SWT.NONE);
	    
	    // About title
 		Label aboutTitleLabel = new Label(this, SWT.WRAP);
 		aboutTitleLabel.setText(I18n.getText("org.syncany.gui.preferences.AboutPanel.version.title"));

	 	WidgetDecorator.bold(aboutTitleLabel);
	 	
	 	// About text
	 	final String appVersion = Client.getApplicationVersionFull();	 	
	 	final String guiPluginVersion = Plugins.get(GuiPlugin.ID).getVersion();	 	
	 		 	
	 	final Text aboutDescriptionText = new Text(this, SWT.WRAP);
		aboutDescriptionText.setEditable(false);	
		aboutDescriptionText.setLayoutData(new GridData(SWT.LEFT, SWT.FILL, true, true, 1, 1));
		aboutDescriptionText.setText(I18n.getText("org.syncany.gui.preferences.AboutPanel.version.description.short", appVersion, guiPluginVersion));
		
		String pluginVersions = "";
	 	
	 	for (Plugin plugin : Plugins.list()) {
	 		if (!LocalTransferPlugin.ID.equals(plugin.getId())) {
	 			pluginVersions += I18n.getText("org.syncany.gui.preferences.AboutPanel.version.description.plugin", plugin.getName(), plugin.getVersion()) + "\n";
	 		}
	 	}
	 	
	 	aboutDescriptionText.setText(I18n.getText("org.syncany.gui.preferences.AboutPanel.version.description.full", appVersion, pluginVersions));
	}

	@Override
	public boolean validatePanel() {
		return true;
	}
}
