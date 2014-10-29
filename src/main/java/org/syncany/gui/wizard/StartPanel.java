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
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.syncany.gui.util.I18n;

/**
 * @author Vincent Wiencek <vwiencek@gmail.com>
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class StartPanel extends Panel {
	public enum StartPanelSelection {
		INIT, CONNECT, ADD_EXISTING
	}

	private Button createStorageRadio;
	private Button connectStorageRadio;
	private Button addWatchStorageRadio;

	public StartPanel(WizardDialog parentDialog, Composite composite, int style) {
		super(parentDialog, composite, style);

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
		titleLabel.setText(I18n.getString("dialog.start.introductionText.title"));

		WidgetDecorator.title(titleLabel);

		Label descriptionLabel = new Label(this, SWT.WRAP);
		descriptionLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false, 1, 1));
		descriptionLabel.setText(I18n.getString("dialog.start.introductionText"));
		
		WidgetDecorator.normal(descriptionLabel);

		// Radio button "Create new repo"
		GridData createStorageRadioGridData = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
		createStorageRadioGridData.verticalIndent = 23;
		createStorageRadioGridData.horizontalIndent = 0;
		createStorageRadioGridData.heightHint = 20;

		createStorageRadio = new Button(this, SWT.RADIO);
		createStorageRadio.setLayoutData(createStorageRadioGridData);
		createStorageRadio.setBounds(0, 0, 90, 16);
		createStorageRadio.setText(I18n.getString("dialog.start.option.createOnlineStorage"));
		createStorageRadio.setForeground(WidgetDecorator.DARK_GRAY);
		createStorageRadio.setEnabled(false);
		
		WidgetDecorator.bigger(createStorageRadio);

		GridData createStorageTextGridData = new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1);
		createStorageTextGridData.horizontalIndent = 25;

		Label createStorageText = new Label(this, SWT.WRAP);
		createStorageText.setLayoutData(createStorageTextGridData);
		createStorageText.setForeground(WidgetDecorator.DARK_GRAY);
		createStorageText.setText(I18n.getString("dialog.start.option.createOnlineStorage.helpText"));

		WidgetDecorator.normal(createStorageText);

		// Radio button "Connect to existing repo"
		GridData connectStorageRadioGridData = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
		connectStorageRadioGridData.verticalIndent = 23;
		connectStorageRadioGridData.horizontalIndent = 0;
		connectStorageRadioGridData.heightHint = 20;

		connectStorageRadio = new Button(this, SWT.RADIO);
		connectStorageRadio.setLayoutData(connectStorageRadioGridData);
		connectStorageRadio.setBounds(0, 0, 90, 16);
		connectStorageRadio.setText(I18n.getString("dialog.start.option.connectExisting"));
		connectStorageRadio.setForeground(WidgetDecorator.DARK_GRAY);
		connectStorageRadio.setEnabled(false);
		
		WidgetDecorator.bigger(connectStorageRadio);

		GridData connectStorageTextGridData = new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1);
		connectStorageTextGridData.horizontalIndent = 25;

		Label connectStorageText = new Label(this, SWT.WRAP);
		connectStorageText.setLayoutData(connectStorageTextGridData);
		connectStorageText.setForeground(WidgetDecorator.DARK_GRAY);
		connectStorageText.setText(I18n.getString("dialog.start.option.connectExisting.helpText"));

		WidgetDecorator.normal(connectStorageText);

		// Radio button "Add existing folder"
		GridData addWatchStorageRadioGridData = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
		addWatchStorageRadioGridData.verticalIndent = 23;
		addWatchStorageRadioGridData.horizontalIndent = 0;
		addWatchStorageRadioGridData.heightHint = 20;

		addWatchStorageRadio = new Button(this, SWT.RADIO);
		addWatchStorageRadio.setLayoutData(addWatchStorageRadioGridData);
		addWatchStorageRadio.setBounds(0, 0, 90, 16);
		addWatchStorageRadio.setText(I18n.getString("dialog.start.option.watchExisting"));
		addWatchStorageRadio.setSelection(true);
		
		WidgetDecorator.bigger(addWatchStorageRadio);

		GridData addWatchTextGridData = new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1);
		addWatchTextGridData.horizontalIndent = 25;

		Label addWatchText = new Label(this, SWT.WRAP);
		addWatchText.setLayoutData(addWatchTextGridData);
		addWatchText.setText(I18n.getString("dialog.start.option.watchExisting.helpText"));

		WidgetDecorator.normal(addWatchText);
	}

	@Override
	public boolean validatePanel() {
		return createStorageRadio.getSelection() || connectStorageRadio.getSelection() || addWatchStorageRadio.getSelection();
	}

	public StartPanelSelection getSelection() {
		if (createStorageRadio.getSelection()) {
			return StartPanelSelection.INIT;
		}
		else if (connectStorageRadio.getSelection()) {
			return StartPanelSelection.CONNECT;
		}
		else {
			return StartPanelSelection.ADD_EXISTING;
		}
	}
}
