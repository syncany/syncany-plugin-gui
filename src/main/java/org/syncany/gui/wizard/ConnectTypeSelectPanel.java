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
import org.eclipse.swt.widgets.Text;
import org.syncany.gui.util.I18n;

/**
 * @author Vincent Wiencek <vwiencek@gmail.com>
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class ConnectTypeSelectPanel extends Panel {
	public enum ConnectPanelSelection {
		LINK, MANUAL
	}

	private Button connectLinkRadio;
	private Button connectManuallyRadio;

	public ConnectTypeSelectPanel(WizardDialog parentDialog, Composite composite, int style) {
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
		titleLabel.setText("Connect to storage");

		WidgetDecorator.title(titleLabel);

		Label descriptionLabel = new Label(this, SWT.WRAP);
		descriptionLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false, 1, 1));
		descriptionLabel.setText("Choose whether to connect via a syncany:// link or by manually entering the connection details.");
		
		WidgetDecorator.normal(descriptionLabel);

		// Radio button "Create new repo"
		GridData connectLinkRadioGridData = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
		connectLinkRadioGridData.verticalIndent = 23;
		connectLinkRadioGridData.horizontalIndent = 0;
		connectLinkRadioGridData.heightHint = 20;

		connectLinkRadio = new Button(this, SWT.RADIO);
		connectLinkRadio.setLayoutData(connectLinkRadioGridData);
		connectLinkRadio.setBounds(0, 0, 90, 16);
		connectLinkRadio.setText("Connect via syncany:// link");
		connectLinkRadio.setSelection(true);
		
		WidgetDecorator.bigger(connectLinkRadio);

		GridData connectLinkTextGridData = new GridData(GridData.FILL_BOTH);
		connectLinkTextGridData.horizontalIndent = 25;

		Text createStorageText = new Text(this, SWT.MULTI | SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
		createStorageText.setLayoutData(connectLinkTextGridData);
		createStorageText.setText("(Paste syncany:// link)");
		createStorageText.setForeground(WidgetDecorator.GRAY);		


		// Radio button "Connect to existing repo"
		GridData connectManuallyRadioGridData = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
		connectManuallyRadioGridData.verticalIndent = 23;
		connectManuallyRadioGridData.horizontalIndent = 0;
		connectManuallyRadioGridData.heightHint = 20;

		connectManuallyRadio = new Button(this, SWT.RADIO);
		connectManuallyRadio.setLayoutData(connectManuallyRadioGridData);
		connectManuallyRadio.setBounds(0, 0, 90, 16);
		connectManuallyRadio.setText(I18n.getString("dialog.start.option.connectExisting"));
		
		WidgetDecorator.bigger(connectManuallyRadio);
	}

	@Override
	public boolean validatePanel() {
		return connectLinkRadio.getSelection() || connectManuallyRadio.getSelection();
	}

	public ConnectPanelSelection getSelection() {
		if (connectLinkRadio.getSelection()) {
			return ConnectPanelSelection.LINK;
		}
		else {
			return ConnectPanelSelection.MANUAL;
		}
	}
}
