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

import static org.syncany.gui.util.I18n._;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.syncany.config.ConfigException;
import org.syncany.config.GuiConfigHelper;
import org.syncany.config.GuiEventBus;
import org.syncany.config.to.GuiConfigTO;
import org.syncany.gui.Panel;
import org.syncany.gui.util.WidgetDecorator;
import org.syncany.operations.daemon.messages.GuiConfigChangedGuiInternalEvent;

public class GeneralPanel extends Panel {
	private static final Logger logger = Logger.getLogger(GeneralPanel.class.getSimpleName());		

	private Button displayNotificationsButton;	
	private GuiConfigTO guiConfig;
	
	private GuiEventBus eventBus;
	
	public GeneralPanel(PreferencesDialog parentDialog, Composite composite, int style) {
		super(parentDialog, composite, style);
		
		initEventBus();
		loadConfig();
		
		createContents();			    
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
		titleLabel.setText(_("org.syncany.gui.preferences.GeneralPanel.title"));

		WidgetDecorator.title(titleLabel);

		// Startup
	    /*Button launchOnStartupButton = new Button(this, SWT.CHECK);
	    launchOnStartupButton.setText(_("org.syncany.gui.preferences.GeneralPanel.launchOnStartup"));*/
	    
	    // Notifications
	    displayNotificationsButton = new Button(this, SWT.CHECK);
	    displayNotificationsButton.setText(_("org.syncany.gui.preferences.GeneralPanel.displayNotifications"));
	    displayNotificationsButton.setSelection(guiConfig.isNotifications());	  
	    
	    displayNotificationsButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				saveConfig();
			}
		});	
	}

	private void loadConfig() {
		guiConfig = GuiConfigHelper.loadOrCreateGuiConfig();
	}

	private void saveConfig() {
		guiConfig.setNotifications(displayNotificationsButton.getSelection());
		
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
