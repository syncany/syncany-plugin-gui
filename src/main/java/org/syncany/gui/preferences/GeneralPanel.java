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

import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Text;
import org.syncany.Client;
import org.syncany.config.ConfigException;
import org.syncany.config.GuiConfigHelper;
import org.syncany.config.GuiEventBus;
import org.syncany.config.to.GuiConfigTO;
import org.syncany.gui.Panel;
import org.syncany.gui.util.DesktopUtil;
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.WidgetDecorator;
import org.syncany.operations.daemon.messages.GuiConfigChangedGuiInternalEvent;
import org.syncany.plugins.Plugin;
import org.syncany.plugins.Plugins;
import org.syncany.plugins.gui.GuiPlugin;
import org.syncany.util.EnvironmentUtil;

public class GeneralPanel extends Panel {
	private static final Logger logger = Logger.getLogger(GeneralPanel.class.getSimpleName());		
	private static String URL_AUTHOR = "http://www.philippheckel.com/";
	private static String URL_TEAM = "https://syncany.org/r/team";
	private static String URL_DONATE = "https://www.syncany.org/donate.html";
	
	private Button launchAtStartupButton;
	private Button displayNotificationsButton;
	
	private GuiConfigTO guiConfig;
	
	private GuiEventBus eventBus;
	
	public GeneralPanel(PreferencesDialog parentDialog, Composite composite, int style) {
		super(parentDialog, composite, style);
		
		initEventBus();
		loadConfig();
		
		createContents();			

		// Persist changes loaded from gui.xml to the operating 
		// system. The gui.xml is the master config file. This
		// makes sure that the OS settings are consistent with it.
		
		writeOrDeleteStartupScriptFile();
	}

	private void initEventBus() {
		this.eventBus = GuiEventBus.getInstance();
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
		titleLabel.setText(I18n.getText("org.syncany.gui.preferences.GeneralPanel.title"));

		WidgetDecorator.title(titleLabel);

		// Common selection listener
		SelectionListener commonSelectionListener = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				saveConfig();
			}
		};
		
		// Startup
	    launchAtStartupButton = new Button(this, SWT.CHECK);
	    
	    if (EnvironmentUtil.isUnixLikeOperatingSystem() || EnvironmentUtil.isWindows()) {
		    launchAtStartupButton.setText(I18n.getText("org.syncany.gui.preferences.GeneralPanel.launchAtStartup"));
		    launchAtStartupButton.setSelection(guiConfig.isStartup());
		    launchAtStartupButton.addSelectionListener(commonSelectionListener);
	    }
	    else {
		    launchAtStartupButton.setText(I18n.getText("org.syncany.gui.preferences.GeneralPanel.launchAtStartupNotSupported"));
		    launchAtStartupButton.setSelection(false);
		    launchAtStartupButton.setEnabled(false);
	    }
	    
	    // Notifications
	    displayNotificationsButton = new Button(this, SWT.CHECK);
	    displayNotificationsButton.setText(I18n.getText("org.syncany.gui.preferences.GeneralPanel.displayNotifications"));
	    displayNotificationsButton.setSelection(guiConfig.isNotifications());	  	    
	    displayNotificationsButton.addSelectionListener(commonSelectionListener);	
	    
	    // Spacing
	    new Label(this, SWT.NONE);
	    
	    // License title
	    Label licenseTitleLabel = new Label(this, SWT.WRAP);
 		licenseTitleLabel.setText(I18n.getText("org.syncany.gui.preferences.GeneralPanel.about.title"));

	 	WidgetDecorator.bold(licenseTitleLabel);

	 	// License text
	 	final String authorName = I18n.getText("org.syncany.gui.preferences.GeneralPanel.about.author");
	 	final String teamName = I18n.getText("org.syncany.gui.preferences.GeneralPanel.about.team");
	 	final String donateLinkName = I18n.getText("org.syncany.gui.preferences.GeneralPanel.about.donate");

	 	Link licenseDescriptionLink = new Link(this, SWT.WRAP);
	 	licenseDescriptionLink.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
	 	licenseDescriptionLink.setText(I18n.getText("org.syncany.gui.preferences.GeneralPanel.about.description", authorName, teamName, donateLinkName));
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
 		aboutTitleLabel.setText(I18n.getText("org.syncany.gui.preferences.GeneralPanel.version.title"));

	 	WidgetDecorator.bold(aboutTitleLabel);
	 	
	 	// About text
	 	String appVersion = Client.getApplicationVersionFull();
	 	String appDate = Client.getApplicationDate().toString();
	 	String appRevision = Client.getApplicationRevision();
	 	
	 	Plugin guiPlugin = Plugins.get(GuiPlugin.ID);
	 	String guiPluginVersion = guiPlugin.getVersion();
	 	
	 	Text aboutDescriptionText = new Text(this, SWT.WRAP);
	 	aboutDescriptionText.setEditable(false);
	 	aboutDescriptionText.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
	 	aboutDescriptionText.setText(I18n.getText("org.syncany.gui.preferences.GeneralPanel.version.description", appVersion, appDate, appRevision, guiPluginVersion));
	 	
	    
	}

	private void loadConfig() {
		guiConfig = GuiConfigHelper.loadOrCreateGuiConfig();		
	}

	private void saveConfig() {
		guiConfig.setStartup(launchAtStartupButton.getSelection());
		guiConfig.setNotifications(displayNotificationsButton.getSelection());
		
		writeOrDeleteStartupScriptFile();
		saveGuiConfigFile();		
	}

	private void writeOrDeleteStartupScriptFile() {
		DesktopUtil.writeAutostart(launchAtStartupButton.getSelection());
	}

	private void saveGuiConfigFile() {
		try {
			GuiConfigHelper.saveGuiConfig(guiConfig);
			eventBus.post(new GuiConfigChangedGuiInternalEvent(guiConfig));
		}
		catch (ConfigException e) {
			logger.log(Level.WARNING, "Unable to save GUI config.", e);
		}
	}

	@Override
	public boolean validatePanel() {
		return true;
	}
}
