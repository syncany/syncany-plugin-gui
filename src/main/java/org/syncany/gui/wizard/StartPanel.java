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
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.syncany.gui.util.I18n;

/**
 * @author Vincent Wiencek <vwiencek@gmail.com>
 *
 */
public class StartPanel extends WizardPanelComposite {
	private Button createStorageRadio;
	private Button connectStorageRadio;
	private Button watchStorageRadio;
	private Button existingUrl;

	public StartPanel(WizardDialog wizardParentDialog, Composite composite, int style) {
		// Trick for windowBuilder to work
		super(wizardParentDialog, composite == null ? new Composite(new Shell(), SWT.NONE) : composite, style);
		initComposite();
	}

	private void initComposite() {
		GridLayout gridLayoutComposite = new GridLayout(1, false);
		gridLayoutComposite.marginRight = 30;

		setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));		
		setLayout(gridLayoutComposite);

		Label introductionTitleText = new Label(this, SWT.WRAP);
		introductionTitleText.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
		introductionTitleText.setText(I18n.getString("dialog.start.introductionText.title"));

		Label introductionText = new Label(this, SWT.WRAP);
		introductionText.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false, 1, 1));
		introductionText.setText(I18n.getString("dialog.start.introductionText"));

		GridData gridDataCreateStorageRadio = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
		gridDataCreateStorageRadio.verticalIndent = 20;
		gridDataCreateStorageRadio.horizontalIndent = 30;
		gridDataCreateStorageRadio.heightHint = 30;

		createStorageRadio = new Button(this, SWT.RADIO);
		createStorageRadio.setLayoutData(gridDataCreateStorageRadio);
		createStorageRadio.setBounds(0, 0, 90, 16);
		createStorageRadio.setText(I18n.getString("dialog.start.option.createOnlineStorage"));
		createStorageRadio.setSelection(true);
		createStorageRadio.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				toggleButtons();
			}
		});

		GridData gridDataCreateText = new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1);
		gridDataCreateText.horizontalIndent = 30;

		Label createText = new Label(this, SWT.WRAP);
		createText.setLayoutData(gridDataCreateText);
		createText.setText(I18n.getString("dialog.start.option.createOnlineStorage.helpText"));

		GridData gridDataConnectStorageRadio = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
		gridDataConnectStorageRadio.verticalIndent = 20;
		gridDataConnectStorageRadio.horizontalIndent = 30;
		gridDataConnectStorageRadio.heightHint = 30;

		connectStorageRadio = new Button(this, SWT.RADIO);
		connectStorageRadio.setLayoutData(gridDataConnectStorageRadio);
		connectStorageRadio.setBounds(0, 0, 90, 16);
		connectStorageRadio.setText(I18n.getString("dialog.start.option.connectExisting"));
		connectStorageRadio.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				toggleButtons();
			}
		});

		GridData gridDataConnectText = new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1);
		gridDataConnectText.horizontalIndent = 30;

		Label connectText = new Label(this, SWT.WRAP);
		connectText.setLayoutData(gridDataConnectText);
		connectText.setText(I18n.getString("dialog.start.option.connectExisting.helpText"));

		GridData gridDataExistingUrl = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gridDataExistingUrl.horizontalIndent = 50;

		existingUrl = new Button(this, SWT.CHECK);
		existingUrl.setEnabled(false);
		existingUrl.setLayoutData(gridDataExistingUrl);
		existingUrl.setText(I18n.getString("dialog.start.option.connectExisting.url"));

		GridData gridDataWatchStorageRadio = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
		gridDataWatchStorageRadio.verticalIndent = 20;
		gridDataWatchStorageRadio.horizontalIndent = 30;
		gridDataWatchStorageRadio.heightHint = 30;

		watchStorageRadio = new Button(this, SWT.RADIO);
		watchStorageRadio.setLayoutData(gridDataWatchStorageRadio);
		watchStorageRadio.setBounds(0, 0, 90, 16);
		watchStorageRadio.setText(I18n.getString("dialog.start.option.watchExisting"));
		watchStorageRadio.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				toggleButtons();
			}
		});

		Label watchText = new Label(this, SWT.WRAP);
		GridData gd_watchText = new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1);
		gd_watchText.horizontalIndent = 30;
		watchText.setLayoutData(gd_watchText);
		watchText.setText(I18n.getString("dialog.start.option.watchExisting.helpText"));

		WidgetDecorator.bold(introductionTitleText);
		WidgetDecorator.bold(createStorageRadio);
		WidgetDecorator.bold(connectStorageRadio);
		WidgetDecorator.bold(watchStorageRadio);

		WidgetDecorator.normal(introductionText);
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
	public UserInput getUserSelection() {
		UserInput userInput = new UserInput();
		if (createStorageRadio.getSelection()) {
			userInput.putCommonParameter(CommonParameters.COMMAND_ACTION, "create");
		}
		else if (connectStorageRadio.getSelection()) {
			userInput.putCommonParameter(CommonParameters.COMMAND_ACTION, "connect");
			userInput.putCommonParameter(CommonParameters.AVAILABLE_URL, existingUrl.getSelection() ? "yes" : "no");
		}
		else {
			userInput.putCommonParameter(CommonParameters.COMMAND_ACTION, "watch");
		}
		return userInput;
	}

	@Override
	public boolean hasNextButton() {
		return true;
	}

	@Override
	public boolean hasPreviousButton() {
		return false;
	}

	@Override
	public boolean hasFinishButton() {
		return false;
	}

	@Override
	public void updateData() {

	}
}
