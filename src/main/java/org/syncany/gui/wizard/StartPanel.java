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
package org.syncany.gui.wizard;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.SWTResourceManager;

/**
 * @author Vincent Wiencek <vwiencek@gmail.com>
 *
 */
public class StartPanel extends WizardPanel {
	private Button createStorageRadio;
	private Button connectStorageRadio;
	private Button watchStorageRadio;
	private Button existingUrl;

	public StartPanel(WizardDialog parentDialog, Composite composite, int style) {
		super(parentDialog, composite, style);
		
		// Main composite
		GridLayout mainCompositeGridLayout = new GridLayout(1, false);
		mainCompositeGridLayout.marginTop = 15;
		mainCompositeGridLayout.marginLeft = 10;
		mainCompositeGridLayout.marginRight = 20;

		setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));		
		setLayout(mainCompositeGridLayout);
		setBackground(SWTResourceManager.getColor(236, 236, 236));

		// Title and welcome text
		Label titleLabel = new Label(this, SWT.WRAP);
		titleLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
		titleLabel.setText(I18n.getString("dialog.start.introductionText.title"));

		Label welcomeTextLabel = new Label(this, SWT.WRAP);
		welcomeTextLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false, 1, 1));
		welcomeTextLabel.setText(I18n.getString("dialog.start.introductionText"));

		// Toggle checkboxes/buttons
		SelectionAdapter toggleButtonsSelectionAdapter = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				toggleButtons();
			}
		};
		
		// Radio button "Create new repo"
		GridData gridDataCreateStorageRadio = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
		gridDataCreateStorageRadio.verticalIndent = 20;
		gridDataCreateStorageRadio.horizontalIndent = 0;
		gridDataCreateStorageRadio.heightHint = 20;

		createStorageRadio = new Button(this, SWT.RADIO);
		createStorageRadio.setLayoutData(gridDataCreateStorageRadio);
		createStorageRadio.setBounds(0, 0, 90, 16);
		createStorageRadio.setText(I18n.getString("dialog.start.option.createOnlineStorage"));
		createStorageRadio.setSelection(true);
		createStorageRadio.addSelectionListener(toggleButtonsSelectionAdapter);

		GridData gridDataCreateText = new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1);
		gridDataCreateText.horizontalIndent = 0;

		Label createText = new Label(this, SWT.WRAP);
		createText.setLayoutData(gridDataCreateText);
		createText.setText(I18n.getString("dialog.start.option.createOnlineStorage.helpText"));

		// Radio button "Connect to existing repo"
		GridData gridDataConnectStorageRadio = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
		gridDataConnectStorageRadio.verticalIndent = 20;
		gridDataConnectStorageRadio.horizontalIndent = 0;
		gridDataConnectStorageRadio.heightHint = 20;

		connectStorageRadio = new Button(this, SWT.RADIO);
		connectStorageRadio.setLayoutData(gridDataConnectStorageRadio);
		connectStorageRadio.setBounds(0, 0, 90, 16);
		connectStorageRadio.setText(I18n.getString("dialog.start.option.connectExisting"));
		connectStorageRadio.addSelectionListener(toggleButtonsSelectionAdapter);

		GridData gridDataConnectText = new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1);
		gridDataConnectText.horizontalIndent = 0;

		Label connectText = new Label(this, SWT.WRAP);
		connectText.setLayoutData(gridDataConnectText);
		connectText.setText(I18n.getString("dialog.start.option.connectExisting.helpText"));

		GridData gridDataExistingUrl = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gridDataExistingUrl.horizontalIndent = 0;

		existingUrl = new Button(this, SWT.CHECK);
		existingUrl.setEnabled(false);
		existingUrl.setLayoutData(gridDataExistingUrl);
		existingUrl.setText(I18n.getString("dialog.start.option.connectExisting.url"));

		// Radio button "Add existing folder"
		GridData gridDataWatchStorageRadio = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
		gridDataWatchStorageRadio.verticalIndent = 20;
		gridDataWatchStorageRadio.horizontalIndent = 0;
		gridDataWatchStorageRadio.heightHint = 20;

		watchStorageRadio = new Button(this, SWT.RADIO);
		watchStorageRadio.setLayoutData(gridDataWatchStorageRadio);
		watchStorageRadio.setBounds(0, 0, 90, 16);
		watchStorageRadio.setText(I18n.getString("dialog.start.option.watchExisting"));
		watchStorageRadio.addSelectionListener(toggleButtonsSelectionAdapter);

		Label watchText = new Label(this, SWT.WRAP);
		GridData gridDataWatchText = new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1);
		gridDataWatchText.horizontalIndent = 0;
		watchText.setLayoutData(gridDataWatchText);
		watchText.setText(I18n.getString("dialog.start.option.watchExisting.helpText"));

		WidgetDecorator.title(titleLabel);
		WidgetDecorator.bold(createStorageRadio);
		WidgetDecorator.bold(connectStorageRadio);
		WidgetDecorator.bold(watchStorageRadio);

		WidgetDecorator.normal(welcomeTextLabel);
		WidgetDecorator.normal(existingUrl);
		WidgetDecorator.normal(createText);
		WidgetDecorator.normal(connectText);
		WidgetDecorator.normal(watchText);
	}

	protected void toggleButtons() {
		existingUrl.setEnabled(connectStorageRadio.getSelection());
	}

	@Override
	public boolean isValid() {
		return createStorageRadio.getSelection() || connectStorageRadio.getSelection() || watchStorageRadio.getSelection();
	}

	@Override
	public StartPanelState getState() {		
		if (createStorageRadio.getSelection()) {
			return new StartPanelState(StartPanelSelection.INIT);
		}
		else if (connectStorageRadio.getSelection()) {
			if (existingUrl.getSelection()) {
				return new StartPanelState(StartPanelSelection.CONNECT_URL);
			}
			else {
				return new StartPanelState(StartPanelSelection.CONNECT_MANUAL);
			}
		}
		else {
			return new StartPanelState(StartPanelSelection.ADD_EXISTING);
		}
	}
	
	public enum StartPanelSelection {
		INIT, CONNECT_MANUAL, CONNECT_URL, ADD_EXISTING
	}
	
	public class StartPanelState implements PanelState {
		private StartPanelSelection selection;

		public StartPanelState(StartPanelSelection selection) {
			this.selection = selection;
		}
		
		public StartPanelSelection getSelection() {
			return selection;
		}

		public void setSelection(StartPanelSelection selection) {
			this.selection = selection;
		}			
	}
}
