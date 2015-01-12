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

import java.io.File;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseTrackAdapter;
import org.eclipse.swt.events.MouseWheelListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.MessageBox;
import org.ocpsoft.prettytime.PrettyTime;
import org.syncany.gui.util.DesktopUtil;
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.SWTResourceManager;
import org.syncany.gui.util.WidgetDecorator;
import org.syncany.operations.log.LightweightDatabaseVersion;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class LogTabComposite extends Composite {
	private static final String IMAGE_RESOURCE_FORMAT = "/" + HistoryDialog.class.getPackage().getName().replace('.', '/') + "/%s.png";
	
	private LogCompositeListener listener;
	private LogComposite logComposite;
	
	private String root;
	private LightweightDatabaseVersion databaseVersion;
	private boolean highlighted;
	private boolean mouseOver;
	
	public LogTabComposite(LogComposite logComposite, Composite logMainComposite, String root, LightweightDatabaseVersion databaseVersion, LogCompositeListener listener) {
		super(logMainComposite, SWT.BORDER);	
		
		this.listener = listener;
		this.logComposite = logComposite;
		
		this.root = root;
		this.databaseVersion = databaseVersion;
		this.highlighted = false;
		this.mouseOver = false;
		
		this.createControls();
		this.addMouseListeners();
	}
				
	private void createControls() {		
		GridLayout mainCompositeGridLayout = new GridLayout(3, false);
		mainCompositeGridLayout.marginTop = 0;
		mainCompositeGridLayout.marginLeft = 0;
		mainCompositeGridLayout.marginRight = 0;
		mainCompositeGridLayout.marginBottom = 0;
		mainCompositeGridLayout.horizontalSpacing = 0;
		mainCompositeGridLayout.verticalSpacing = 0;

		setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1));		
		setLayout(mainCompositeGridLayout);		
		setBackground(WidgetDecorator.WHITE);	
		setBackgroundMode(SWT.INHERIT_FORCE);
				
		Label dateLabel = new Label(this, SWT.NONE);
		dateLabel.setText(databaseVersion.getDate().toString());		
		dateLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 2, 1));		
		
		WidgetDecorator.bold(dateLabel);
		
		GridData prettyDateLabelGriData = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
		prettyDateLabelGriData.horizontalIndent = 10;
		
		Label prettyDateLabel = new Label(this, SWT.NONE);
		prettyDateLabel.setLayoutData(prettyDateLabelGriData);		
		prettyDateLabel.setText(new PrettyTime().format(databaseVersion.getDate()));		
		prettyDateLabel.setForeground(WidgetDecorator.DARK_GRAY);
		
		WidgetDecorator.smaller(prettyDateLabel);
				
		Label spacingLabel1 = new Label(this, SWT.NONE);
		spacingLabel1.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 3, 1));		
		
		for (String file : databaseVersion.getChangeSet().getNewFiles()) {
			createEntryLabel(file, "add");			
		}

		for (String file : databaseVersion.getChangeSet().getChangedFiles()) {
			createEntryLabel(file, "edit");	
		}

		for (String file : databaseVersion.getChangeSet().getDeletedFiles()) {
			createEntryLabel(file, "delete");	
		}
		
		// Add 'more ...' entry if max. number reached
		int totalEntryCount = databaseVersion.getChangeSet().getNewFiles().size()
				+ databaseVersion.getChangeSet().getChangedFiles().size()
				+ databaseVersion.getChangeSet().getDeletedFiles().size();
		
		if (totalEntryCount == LogComposite.LOG_REQUEST_FILE_COUNT) {
			createEntryLabel(I18n.getText("org.syncany.gui.history.LogTabComposite.more"), "more");	
		}
		
		Label spacingLabel2 = new Label(this, SWT.NONE);
		spacingLabel2.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 3, 1));
	}

	private void createEntryLabel(String file, String imageResourceName) {		
		GridData imageLabelGridData = new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1);
		imageLabelGridData.horizontalIndent = 20;
		imageLabelGridData.verticalIndent = 2;
		
		Label imageLabel = new Label(this, SWT.NONE);
		imageLabel.setLayoutData(imageLabelGridData);		
		imageLabel.setImage(SWTResourceManager.getImage(String.format(IMAGE_RESOURCE_FORMAT, imageResourceName)));
		
		GridData fileLabelGridData = new GridData(SWT.LEFT, SWT.TOP, true, false, 2, 1);
		fileLabelGridData.horizontalIndent = 10;
		fileLabelGridData.verticalIndent = 2;

		Label fileLabel = new Label(this, SWT.NONE);
		fileLabel.setLayoutData(fileLabelGridData);		
		fileLabel.setText(file);
		fileLabel.setCursor(new Cursor(Display.getDefault(), SWT.CURSOR_HAND));
		
		addFileMenu(fileLabel, file);	
		addFileMouseListeners(fileLabel);			
	}

	private void addFileMenu(Label fileLabel, final String relativeFilePath) {
		final Menu fileMenu = new Menu(fileLabel);
		final File file = new File(root, relativeFilePath); 
		
		MenuItem jumpToDetailViewMenuItem = new MenuItem(fileMenu, SWT.NONE);
		jumpToDetailViewMenuItem.setText(I18n.getText("org.syncany.gui.history.LogTabComposite.jumpToDetail"));
		jumpToDetailViewMenuItem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				listener.onFileJumpToDetail(databaseVersion, relativeFilePath);
			}
		});
		
		MenuItem jumpToTreeMenuItem = new MenuItem(fileMenu, SWT.NONE);
		jumpToTreeMenuItem.setText(I18n.getText("org.syncany.gui.history.LogTabComposite.jumpToTree"));
		jumpToTreeMenuItem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				listener.onFileJumpToTree(databaseVersion, relativeFilePath);
			}
		});
		
		new MenuItem(fileMenu, SWT.SEPARATOR);
		
		MenuItem openFileMenuItem = new MenuItem(fileMenu, SWT.NONE);
		openFileMenuItem.setText(I18n.getText("org.syncany.gui.history.LogTabComposite.openFile"));
		openFileMenuItem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				listener.onFileOpen(databaseVersion, relativeFilePath);				
			}
		});
		
		MenuItem openFolderMenuItem = new MenuItem(fileMenu, SWT.NONE);
		openFolderMenuItem.setText(I18n.getText("org.syncany.gui.history.LogTabComposite.openContainingFolder"));				
		openFolderMenuItem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				listener.onFileOpenContainingFolder(databaseVersion, relativeFilePath);				
			}
		});
		
		new MenuItem(fileMenu, SWT.SEPARATOR);
		
		MenuItem copyPathMenuItem = new MenuItem(fileMenu, SWT.NONE);
		copyPathMenuItem.setText(I18n.getText("org.syncany.gui.history.LogTabComposite.copyPath"));		
		copyPathMenuItem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				listener.onFileCopytoClipboard(databaseVersion, relativeFilePath);				
			}
		});
		
		fileMenu.setDefaultItem(jumpToDetailViewMenuItem);	
		fileLabel.setMenu(fileMenu);
	}

	private void addFileMouseListeners(final Label fileLabel) {
		fileLabel.addMouseTrackListener(new MouseTrackAdapter() {
			@Override
			public void mouseEnter(MouseEvent e) {
				fileLabel.setForeground(WidgetDecorator.BLUE_LINK);
			}
			
			@Override
			public void mouseExit(MouseEvent e) {
				fileLabel.setForeground(WidgetDecorator.BLACK);
			}
		});
		
		fileLabel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseUp(MouseEvent e) {				
				// mainPanel.showDetails( ...			
			}
		});
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
	
	private void addMouseListeners() {
		addMouseListeners(this);
		
		for (Control control : getChildren()) {
			addMouseListeners(control);
		}
	}
	
	private void addMouseListeners(Control control) {
		control.addMouseTrackListener(new MouseTrackAdapter() {
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
		});
		
		control.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseUp(MouseEvent e) {
				listener.onSelectDatabaseVersion(databaseVersion);
			}
			
			@Override
			public void mouseDoubleClick(MouseEvent e) {
				listener.onDoubleClickDatabaseVesion(databaseVersion);
			}
		});
		
		control.addMouseWheelListener(new MouseWheelListener() {			
			@Override
			public void mouseScrolled(MouseEvent e) {
				// Pass on mouse events to parent control. 				
				logComposite.scrollBy(e.count);
			}
		});
	}
}
