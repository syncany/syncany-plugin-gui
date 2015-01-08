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
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseTrackAdapter;
import org.eclipse.swt.events.MouseTrackListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.syncany.gui.util.SWTResourceManager;
import org.syncany.gui.util.WidgetDecorator;
import org.syncany.operations.log.LightweightDatabaseVersion;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class TabComposite extends Composite {
	private static final String IMAGE_RESOURCE_FORMAT = "/" + HistoryDialog.class.getPackage().getName().replace('.', '/') + "/%s.png";
	
	private MainPanel mainPanel;
	
	private LightweightDatabaseVersion databaseVersion;
	private boolean highlighted;
	private boolean mouseOver;
	
	public TabComposite(MainPanel mainPanel, Composite parent, LightweightDatabaseVersion databaseVersion) {
		super(parent, SWT.BORDER);	
		
		this.mainPanel = mainPanel;
		
		this.databaseVersion = databaseVersion;
		this.highlighted = false;
		this.mouseOver = false;
		
		this.createControls();
	}
				
	private void createControls() {		
		MouseTrackListener mouseTrackListener = new MouseTrackAdapter() {
			@Override
			public void mouseEnter(MouseEvent e) {
				mouseOver = true;
				updateHighlighted();
			}
			
			@Override
			public void mouseExit(MouseEvent e) {
				mouseOver = false;
				updateHighlighted();
			}
		};
		
		MouseListener mouseListener = new MouseAdapter() {
			@Override
			public void mouseUp(MouseEvent e) {
				mainPanel.updateDate(databaseVersion.getDate());
			}
			
			@Override
			public void mouseDoubleClick(MouseEvent e) {
				mainPanel.showTree();
			}
		};
		
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
		
		addMouseTrackListener(mouseTrackListener);
		addMouseListener(mouseListener);
		
		Label dateLabel = new Label(this, SWT.NONE);
		dateLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));		
		dateLabel.setText(databaseVersion.getDate().toString());		
		dateLabel.addMouseTrackListener(mouseTrackListener);
		dateLabel.addMouseListener(mouseListener);
		
		WidgetDecorator.bold(dateLabel);
		
		Label spacingLabel1 = new Label(this, SWT.NONE);
		spacingLabel1.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));		
		spacingLabel1.addMouseTrackListener(mouseTrackListener);
		spacingLabel1.addMouseListener(mouseListener);
		
		for (String file : databaseVersion.getChangeSet().getNewFiles()) {
			createEntryLabel(file, mouseTrackListener, mouseListener, "add");			
		}

		for (String file : databaseVersion.getChangeSet().getChangedFiles()) {
			createEntryLabel(file, mouseTrackListener, mouseListener, "edit");	
		}

		for (String file : databaseVersion.getChangeSet().getDeletedFiles()) {
			createEntryLabel(file, mouseTrackListener, mouseListener, "delete");	
		}
		
		Label spacingLabel2 = new Label(this, SWT.NONE);
		spacingLabel2.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
		spacingLabel2.addMouseTrackListener(mouseTrackListener);
		spacingLabel2.addMouseListener(mouseListener);
	}

	private void createEntryLabel(String file, MouseTrackListener mouseTrackListener, MouseListener mouseListener, String imageResourceName) {
		GridData imageLabelGridData = new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1);
		imageLabelGridData.horizontalIndent = 20;
		imageLabelGridData.verticalIndent = 2;
		
		Label imageLabel = new Label(this, SWT.NONE);
		imageLabel.setLayoutData(imageLabelGridData);		
		imageLabel.setImage(SWTResourceManager.getImage(String.format(IMAGE_RESOURCE_FORMAT, imageResourceName)));
		imageLabel.addMouseTrackListener(mouseTrackListener);
		imageLabel.addMouseListener(mouseListener);
		
		GridData fileLabelGridData = new GridData(SWT.LEFT, SWT.TOP, true, false, 1, 1);
		fileLabelGridData.horizontalIndent = 10;
		fileLabelGridData.verticalIndent = 2;

		Label fileLabel = new Label(this, SWT.NONE);
		fileLabel.setLayoutData(fileLabelGridData);		
		fileLabel.setText(file);
		fileLabel.addMouseTrackListener(mouseTrackListener);
		fileLabel.addMouseListener(mouseListener);
	}
	
	public LightweightDatabaseVersion getDatabaseVersion() {
		return databaseVersion;
	}

	public void setHighlighted(boolean highlighted) {
		this.highlighted = highlighted;						
		updateHighlighted();
	}

	private void updateHighlighted() {
		if (mouseOver || highlighted) {
			if (getBackground() != WidgetDecorator.LIGHT_GRAY) {
				setBackground(WidgetDecorator.LIGHT_GRAY);
			}
		}
		else {
			if (getBackground() != WidgetDecorator.WHITE) {
				setBackground(WidgetDecorator.WHITE);
			}
		}		
	}
}
