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

import static org.syncany.gui.util.I18n._;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.File;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.syncany.gui.util.DesktopUtil;
import org.syncany.gui.util.WidgetDecorator;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class InitSuccessPanel extends Panel {
	private String applicationLinkText;
	private File localDir;
	
	public InitSuccessPanel(WizardDialog parentDialog, Composite composite, int style) {
		super(parentDialog, composite, style);
		this.createControls();
	}
	
	private void createControls() {
		// Main composite
		GridLayout mainCompositeGridLayout = new GridLayout(2, false);
		mainCompositeGridLayout.marginTop = 15;
		mainCompositeGridLayout.marginLeft = 10;
		mainCompositeGridLayout.marginRight = 20;
		mainCompositeGridLayout.marginBottom = 10;

		setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		setLayout(mainCompositeGridLayout);

		// Title and welcome text
		Label titleLabel = new Label(this, SWT.WRAP);
		titleLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 2, 1));
		titleLabel.setText(_("org.syncany.gui.wizard.InitSuccessPanel.title"));

		WidgetDecorator.title(titleLabel);

		Label descriptionLabel = new Label(this, SWT.WRAP);
		descriptionLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false, 2, 1));
		descriptionLabel.setText(_("org.syncany.gui.wizard.InitSuccessPanel.description"));
		
		WidgetDecorator.normal(descriptionLabel);

		// Button grid data
		GridData buttonGridData = new GridData(SWT.CENTER, SWT.BOTTOM, true, false, 1, 1);
		buttonGridData.minimumHeight = 80;		
		buttonGridData.verticalIndent = 20;
		buttonGridData.widthHint = 150;
		buttonGridData.heightHint = 35;
		
		// Button "Copy"
		Button copyLinkButton = new Button(this, SWT.FLAT);
		copyLinkButton.setText(_("org.syncany.gui.wizard.InitSuccessPanel.button.copyLink"));
		copyLinkButton.setLayoutData(buttonGridData);
		copyLinkButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				StringSelection applicationLinkStringSelection = new StringSelection(applicationLinkText);
				
			    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			    clipboard.setContents(applicationLinkStringSelection, applicationLinkStringSelection);
			}
		});
		
		// Button "Open folder"
		Button openFolderButton = new Button(this, SWT.FLAT);
		openFolderButton.setText(_("org.syncany.gui.wizard.InitSuccessPanel.button.openFolder"));
		openFolderButton.setLayoutData(buttonGridData);
		openFolderButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				DesktopUtil.launch(localDir.getAbsolutePath());
			}
		});		
		
		// Label for syncany:// link help 
		GridData linkHelpLabelGridData = new GridData(SWT.LEFT, SWT.TOP, true, false, 2, 1);
		linkHelpLabelGridData.verticalIndent = 20;
		
		Label linkHelpLabel = new Label(this, SWT.WRAP);
		linkHelpLabel.setLayoutData(linkHelpLabelGridData);
		linkHelpLabel.setText(_("org.syncany.gui.wizard.InitSuccessPanel.linkHelp"));
		
		WidgetDecorator.normal(linkHelpLabel);
		
		layout();
	}

	@Override
	public boolean validatePanel() {
		return true;
	}
	
	public void setApplicationLinkText(String applicationLinkText) {
		this.applicationLinkText = applicationLinkText;
	}
	
	public void setLocalDir(File localDir) {
		this.localDir = localDir;
	}
}
