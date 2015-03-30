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

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.syncany.config.ConfigException;
import org.syncany.config.DaemonConfigHelper;
import org.syncany.config.GuiConfigHelper;
import org.syncany.config.GuiEventBus;
import org.syncany.config.UserConfig;
import org.syncany.config.to.DaemonConfigTO;
import org.syncany.config.to.GuiConfigTO;
import org.syncany.config.to.UserConfigTO;
import org.syncany.gui.Panel;
import org.syncany.gui.tray.TrayIconFactory;
import org.syncany.gui.tray.TrayIconTheme;
import org.syncany.gui.tray.TrayIconType;
import org.syncany.gui.util.DesktopUtil;
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.WidgetDecorator;
import org.syncany.operations.daemon.messages.GuiConfigChangedGuiInternalEvent;
import org.syncany.util.EnvironmentUtil;

public class GeneralPanel extends Panel {
	private static final Logger logger = Logger.getLogger(GeneralPanel.class.getSimpleName());		
	
	private Button launchAtStartupButton;
	private Button displayNotificationsButton;
	private Button preventStandbyButton;
	private Combo themeCombo;
	private Combo trayTypeCombo;
	
	private GuiConfigTO guiConfig;	
	private DaemonConfigTO daemonConfig;
	
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
		GridLayout mainCompositeGridLayout = new GridLayout(3, false);
		mainCompositeGridLayout.marginTop = 15;
		mainCompositeGridLayout.marginLeft = 10;
		mainCompositeGridLayout.marginRight = 20;

		setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
		setLayout(mainCompositeGridLayout);

		// Title and welcome text
		Label titleLabel = new Label(this, SWT.WRAP);
		titleLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 3, 1));
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
	    launchAtStartupButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
	    
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

		// Theme
		Label themeLabel = new Label(this, SWT.NONE);
		themeLabel.setText(I18n.getText("org.syncany.gui.preferences.GeneralPanel.theme.title"));

		themeCombo = new Combo(this, SWT.DROP_DOWN | SWT.BORDER | SWT.READ_ONLY);
		themeCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		themeCombo.addSelectionListener(commonSelectionListener);

		fillTrayThemeCombo();
	    
	    // Notifications
	    displayNotificationsButton = new Button(this, SWT.CHECK);
	    displayNotificationsButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
	    displayNotificationsButton.setText(I18n.getText("org.syncany.gui.preferences.GeneralPanel.displayNotifications"));
		displayNotificationsButton.setSelection(guiConfig.isNotifications());
		displayNotificationsButton.addSelectionListener(commonSelectionListener);

		// Prevent standby
		preventStandbyButton = new Button(this, SWT.CHECK);
		preventStandbyButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
		preventStandbyButton.setText(I18n.getText("org.syncany.gui.preferences.GeneralPanel.preventStandby"));
		preventStandbyButton.setSelection(UserConfig.preventStandbyEnabled());
		preventStandbyButton.addSelectionListener(commonSelectionListener);	
	    
	    // Tray type
	    Label trayTypeLabel = new Label(this, SWT.NONE);
		trayTypeLabel.setText(I18n.getText("org.syncany.gui.preferences.GeneralPanel.trayType.title"));

	    trayTypeCombo = new Combo(this, SWT.DROP_DOWN | SWT.BORDER | SWT.READ_ONLY);
	    trayTypeCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
	    trayTypeCombo.addSelectionListener(commonSelectionListener);

	    fillTrayTypeCombo();
	
	 	// Spacing
	    Label spacingLabel = new Label(this, SWT.NONE);
	    spacingLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 3, 1));
	    
	    // Updates title
 		Label updatesTitleLabel = new Label(this, SWT.WRAP);
 		updatesTitleLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 3, 1));
 		updatesTitleLabel.setText(I18n.getText("org.syncany.gui.preferences.GeneralPanel.updates.title"));

	 	WidgetDecorator.bold(updatesTitleLabel);
	 	
	 	// Updates text
		Label updatesLabel = new Label(this, SWT.WRAP);
		updatesLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 3, 1));
		updatesLabel.setText(I18n.getText("org.syncany.gui.preferences.GeneralPanel.updates.upToDate"));	 	
	}

	private void fillTrayThemeCombo() {
		// Determine texts
		HashMap<TrayIconTheme, String> themeTexts = new LinkedHashMap<>();
		
		TrayIconTheme autoTheme = TrayIconFactory.detectThemeFromOS();		
		String autoThemeDescription = I18n.getText("org.syncany.gui.preferences.GeneralPanel.theme." + autoTheme.toString());
		String autoThemeFullDescription = String.format(I18n.getText("org.syncany.gui.preferences.GeneralPanel.theme.autoFormat"), autoThemeDescription);
		
		themeTexts.put(TrayIconTheme.AUTO, autoThemeFullDescription);
		themeTexts.put(TrayIconTheme.DEFAULT, I18n.getText("org.syncany.gui.preferences.GeneralPanel.theme." + TrayIconTheme.DEFAULT.toString()));
		themeTexts.put(TrayIconTheme.MONOCHROME, I18n.getText("org.syncany.gui.preferences.GeneralPanel.theme." + TrayIconTheme.MONOCHROME.toString()));
		
		// Add to combo box
		TrayIconTheme currentTheme = guiConfig.getTheme();

		for (Entry<TrayIconTheme, String> themeComboEntry : themeTexts.entrySet()) {
			themeCombo.add(themeComboEntry.getValue());
			themeCombo.setData(themeComboEntry.getValue(), themeComboEntry.getKey());		
			
			if (currentTheme == themeComboEntry.getKey()) {
				themeCombo.select(themeCombo.getItemCount() - 1);
			}
		}		
	}

	private void fillTrayTypeCombo() {
		// Determine texts
		HashMap<TrayIconType, String> trayTypeTexts = new LinkedHashMap<>();
		
		TrayIconType autoTrayType = TrayIconFactory.detectTypeFromOS();		
		String autoTrayTypeDescription = I18n.getText("org.syncany.gui.preferences.GeneralPanel.trayType." + autoTrayType.toString());
		String autoTrayTypeFullDescription = String.format(I18n.getText("org.syncany.gui.preferences.GeneralPanel.trayType.autoFormat"), autoTrayTypeDescription);
		
		trayTypeTexts.put(TrayIconType.AUTO, autoTrayTypeFullDescription);
		trayTypeTexts.put(TrayIconType.DEFAULT, I18n.getText("org.syncany.gui.preferences.GeneralPanel.trayType." + TrayIconType.DEFAULT.toString()));

		if (EnvironmentUtil.isMacOSX()) {
			trayTypeTexts.put(TrayIconType.OSX_NOTIFICATION_CENTER, I18n.getText("org.syncany.gui.preferences.GeneralPanel.trayType." + TrayIconType.OSX_NOTIFICATION_CENTER.toString()));			
		}
		else if (EnvironmentUtil.isUnixLikeOperatingSystem()) {
			trayTypeTexts.put(TrayIconType.APPINDICATOR, I18n.getText("org.syncany.gui.preferences.GeneralPanel.trayType." + TrayIconType.APPINDICATOR.toString()));			
		}
		
		// Add to combo box
		TrayIconType currentTrayType = guiConfig.getTray();

		for (Entry<TrayIconType, String> trayTypeComboEntry : trayTypeTexts.entrySet()) {
			trayTypeCombo.add(trayTypeComboEntry.getValue());
			trayTypeCombo.setData(trayTypeComboEntry.getValue(), trayTypeComboEntry.getKey());		
			
			if (currentTrayType == trayTypeComboEntry.getKey()) {
				trayTypeCombo.select(trayTypeCombo.getItemCount() - 1);
			}
		}
	}

	private void loadConfig() {
		guiConfig = GuiConfigHelper.loadOrCreateGuiConfig();	
	}

	private void saveConfig() {
		TrayIconTheme selectedTheme = (TrayIconTheme) themeCombo.getData(themeCombo.getItem(themeCombo.getSelectionIndex()));
		TrayIconType selectedTrayType = (TrayIconType) trayTypeCombo.getData(trayTypeCombo.getItem(trayTypeCombo.getSelectionIndex()));
		
		guiConfig.setStartup(launchAtStartupButton.getSelection());
		guiConfig.setNotifications(displayNotificationsButton.getSelection());
		guiConfig.setTheme(selectedTheme);
		guiConfig.setTray(selectedTrayType);
		
		writeOrDeleteStartupScriptFile();
		saveGuiConfigFile();
		saveUserConfigFile();
	}

	private void saveUserConfigFile() {
		boolean userConfigChanged = UserConfig.preventStandbyEnabled() != preventStandbyButton.getSelection();
		
		if (userConfigChanged) {
			try {
				UserConfigTO userConfigTO = UserConfigTO.load(UserConfig.getUserConfigFile());
				userConfigTO.
				
				if (userConfigFile.)
			}
			catch (ConfigException e) {
				
			}
		}
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
