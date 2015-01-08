/*
 * Syncany, www.syncany.org
 * Copyright (C) 2011-2015 Philipp C. Heckel <philipp.heckel@gmail.com> 
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
package org.syncany.gui.history;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.syncany.gui.preferences.PreferencesDialog;
import org.syncany.gui.util.SWTResourceManager;
import org.syncany.gui.util.WidgetDecorator;
import org.syncany.operations.log.LightweightDatabaseVersion;

/**
 * @author pheckel
 *
 */
public class TabComposite extends Composite {
	private static final String IMAGE_RESOURCE_FORMAT = "/" + HistoryDialog.class.getPackage().getName().replace('.', '/') + "/%s.png";
	private LightweightDatabaseVersion databaseVersion;
	
	public TabComposite(Composite parent, LightweightDatabaseVersion databaseVersion) {
		super(parent, SWT.BORDER);	
		
		this.databaseVersion = databaseVersion;
		this.createControls();
	}
				
	private void createControls() {		
		// Main composite
		GridLayout mainCompositeGridLayout = new GridLayout(2, false);
		mainCompositeGridLayout.marginTop = 0;
		mainCompositeGridLayout.marginLeft = 0;
		mainCompositeGridLayout.marginRight = 0;
		mainCompositeGridLayout.marginBottom = 0;
		mainCompositeGridLayout.horizontalSpacing = 0;
		mainCompositeGridLayout.verticalSpacing = 0;

		setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));		
		setLayout(mainCompositeGridLayout);		
		setBackground(WidgetDecorator.WHITE);				
		
		Label dateLabel = new Label(this, SWT.NONE);
		dateLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));		
		dateLabel.setText(databaseVersion.getDate().toString());		
		
		for (String file : databaseVersion.getChangeSet().getNewFiles()) {
			createEntryLabel(file, "add");			
		}

		for (String file : databaseVersion.getChangeSet().getChangedFiles()) {
			createEntryLabel(file, "edit");	
		}

		for (String file : databaseVersion.getChangeSet().getDeletedFiles()) {
			createEntryLabel(file, "delete");	
		}
	}

	private void createEntryLabel(String file, String imageResourceName) {
		GridData imageLabelGridData = new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1);
		imageLabelGridData.horizontalIndent = 20;
		imageLabelGridData.verticalIndent = 2;
		
		Label imageLabel = new Label(this, SWT.NONE);
		imageLabel.setLayoutData(imageLabelGridData);		
		imageLabel.setImage(SWTResourceManager.getImage(String.format(IMAGE_RESOURCE_FORMAT, imageResourceName)));
		
		GridData fileLabelGridData = new GridData(SWT.LEFT, SWT.TOP, true, false, 1, 1);
		fileLabelGridData.horizontalIndent = 10;
		fileLabelGridData.verticalIndent = 2;

		Label fileLabel = new Label(this, SWT.NONE);
		fileLabel.setLayoutData(fileLabelGridData);		
		fileLabel.setText(file);
	}
}
