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

import java.util.logging.Level;
import java.util.logging.Logger;

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
import org.ocpsoft.prettytime.PrettyTime;
import org.syncany.config.GuiEventBus;
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.SWTResourceManager;
import org.syncany.gui.util.WidgetDecorator;
import org.syncany.operations.daemon.messages.LogFolderRequest;
import org.syncany.operations.daemon.messages.LogFolderResponse;
import org.syncany.operations.log.LightweightDatabaseVersion;
import org.syncany.operations.log.LogOperationOptions;

import com.google.common.eventbus.Subscribe;

/**
 * The log tag composite is a visual representation of a single database
 * version and its changed file versions. Each tab lives inside a {@link LogComposite}
 * and displays multiple file entries (added/changed/removed files).
 * 
 * <p>The initial tab is created by the results of the {@link LogFolderResponse} received
 * by the {@link LogComposite}. If a certain number of entries is displayed, a "More ..."
 * link is shown that lets the user load more entries. This triggers a new {@link LogFolderRequest}
 * by this composite. Once the response is received, it composite updates itself.
 * 
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class LogTabComposite extends Composite {
	private static final Logger logger = Logger.getLogger(LogTabComposite.class.getSimpleName());		
	private static final String IMAGE_RESOURCE_FORMAT = "/" + HistoryDialog.class.getPackage().getName().replace('.', '/') + "/%s.png";
	private static final int LOG_REQUEST_FILE_COUNT_STEP = 25;
	
	private LogComposite logComposite;
	
	private String root;
	private int databaseVersionIndex;
	private LightweightDatabaseVersion databaseVersion;
	
	private boolean highlighted;
	private boolean mouseOver;
	private int showMoreLabelEntryLimit;
	
	private GuiEventBus eventBus;	
	private LogFolderRequest pendingLogFolderRequest;
	
	public LogTabComposite(LogComposite logComposite, Composite logMainComposite, String root, int databaseVersionIndex, LightweightDatabaseVersion databaseVersion) {
		super(logMainComposite, SWT.BORDER);	
		
		this.logComposite = logComposite;
		
		this.root = root;
		this.databaseVersionIndex = databaseVersionIndex;
		this.databaseVersion = databaseVersion;
		
		this.highlighted = false;
		this.mouseOver = false;
		this.showMoreLabelEntryLimit = LogComposite.LOG_REQUEST_FILE_COUNT;
		
		this.eventBus = null; // Register on demand!
		this.pendingLogFolderRequest = null;
		
		this.createControls();
		this.addMouseListeners();
	}
				
	private void createControls() {		
		createMainComposite();
		createDateLabels();
		createEntryLabels();		
	}

	private void createMainComposite() {
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
	}

	private void createDateLabels() {
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
	}

	private void createEntryLabels() {
		Label spacingLabel1 = new Label(this, SWT.NONE);
		spacingLabel1.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 3, 1));		
		
		for (String file : databaseVersion.getChangeSet().getNewFiles()) {
			createFileEntryLabel(file, "add", true);			
		}

		for (String file : databaseVersion.getChangeSet().getChangedFiles()) {
			createFileEntryLabel(file, "edit", true);	
		}

		for (String file : databaseVersion.getChangeSet().getDeletedFiles()) {
			createFileEntryLabel(file, "delete", false);	
		}
		
		// Add 'more ...' entry if max. number reached
		int totalEntryCount = databaseVersion.getChangeSet().getNewFiles().size()
				+ databaseVersion.getChangeSet().getChangedFiles().size()
				+ databaseVersion.getChangeSet().getDeletedFiles().size();
		
		if (totalEntryCount == showMoreLabelEntryLimit) {
			createMoreEntryLabel();	
		}
		
		Label spacingLabel2 = new Label(this, SWT.NONE);
		spacingLabel2.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 3, 1));
	}

	private void createFileEntryLabel(String relativeFilePath, String imageResourceName, boolean link) {		
		Label fileLabel = createEntryLabel(relativeFilePath, imageResourceName);
		
		if (link) {
			fileLabel.setCursor(new Cursor(Display.getDefault(), SWT.CURSOR_HAND));
			
			addFileEntryMenu(fileLabel, relativeFilePath);	
			addFileEntryMouseClickListener(fileLabel, relativeFilePath);
	
			addEntryMouseMoveListener(fileLabel);
		}
	}
	
	private void createMoreEntryLabel() {
		Label moreLabel = createEntryLabel(I18n.getText("org.syncany.gui.history.LogTabComposite.more"), "more");
		moreLabel.setCursor(new Cursor(Display.getDefault(), SWT.CURSOR_HAND));

		addEntryMouseMoveListener(moreLabel);
		addMoreEntryClickListener(moreLabel);
	}	

	private Label createEntryLabel(String relativeFilePath, String imageResourceName) {
		GridData imageLabelGridData = new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1);
		imageLabelGridData.horizontalIndent = 20;
		imageLabelGridData.verticalIndent = 2;
		
		Label imageLabel = new Label(this, SWT.NONE);
		imageLabel.setLayoutData(imageLabelGridData);		
		imageLabel.setImage(SWTResourceManager.getImage(String.format(IMAGE_RESOURCE_FORMAT, imageResourceName)));
		
		GridData entryLabelGridData = new GridData(SWT.LEFT, SWT.TOP, true, false, 2, 1);
		entryLabelGridData.horizontalIndent = 10;
		entryLabelGridData.verticalIndent = 2;

		Label entryLabel = new Label(this, SWT.NONE);
		entryLabel.setLayoutData(entryLabelGridData);		
		entryLabel.setText(relativeFilePath.replaceAll("&", "&&"));		
		
		return entryLabel;
	}

	private void addFileEntryMenu(Label fileLabel, final String relativeFilePath) {
		final Menu fileMenu = new Menu(fileLabel);	
		
		MenuItem jumpToTreeMenuItem = new MenuItem(fileMenu, SWT.NONE);
		jumpToTreeMenuItem.setText(I18n.getText("org.syncany.gui.history.LogTabComposite.jumpToTree"));
		jumpToTreeMenuItem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				logComposite.onFileJumpToTree(databaseVersion, relativeFilePath);
			}
		});
		
		new MenuItem(fileMenu, SWT.SEPARATOR);
		
		MenuItem openFileMenuItem = new MenuItem(fileMenu, SWT.NONE);
		openFileMenuItem.setText(I18n.getText("org.syncany.gui.history.LogTabComposite.openFile"));
		openFileMenuItem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				logComposite.onFileOpen(databaseVersion, relativeFilePath);				
			}
		});
		
		MenuItem openFolderMenuItem = new MenuItem(fileMenu, SWT.NONE);
		openFolderMenuItem.setText(I18n.getText("org.syncany.gui.history.LogTabComposite.openContainingFolder"));				
		openFolderMenuItem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				logComposite.onFileOpenContainingFolder(databaseVersion, relativeFilePath);				
			}
		});
		
		new MenuItem(fileMenu, SWT.SEPARATOR);
		
		MenuItem copyPathMenuItem = new MenuItem(fileMenu, SWT.NONE);
		copyPathMenuItem.setText(I18n.getText("org.syncany.gui.history.LogTabComposite.copyPath"));		
		copyPathMenuItem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				logComposite.onFileCopytoClipboard(databaseVersion, relativeFilePath);				
			}
		});
		
		fileMenu.setDefaultItem(jumpToTreeMenuItem);	
		fileLabel.setMenu(fileMenu);
	}
	
	private void addMoreEntryClickListener(Label moreLabel) {
		moreLabel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseUp(MouseEvent e) {				
				logger.log(Level.INFO, "Reloading database version.");
				sendMoreLogFolderRequest();				
			}
		});
	}

	private void addFileEntryMouseClickListener(final Label fileLabel, final String relativeFilePath) {
		fileLabel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseUp(MouseEvent e) {				
				logComposite.onFileJumpToTree(databaseVersion, relativeFilePath);			
			}
		});
	}
	
	private void addEntryMouseMoveListener(final Label fileLabel) {
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
	}

	/**
	 * Highlights the tab or removes the highlight color
	 * from the tab. 
	 */
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
				logComposite.onSelectDatabaseVersion(databaseVersion);
			}
			
			@Override
			public void mouseDoubleClick(MouseEvent e) {
				logComposite.onDoubleClickDatabaseVersion(databaseVersion);
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
	
	private void sendMoreLogFolderRequest() {
		showMoreLabelEntryLimit += LOG_REQUEST_FILE_COUNT_STEP;

		LogOperationOptions logOptions = new LogOperationOptions();
		logOptions.setMaxDatabaseVersionCount(1);
		logOptions.setStartDatabaseVersionIndex(databaseVersionIndex);
		logOptions.setMaxFileHistoryCount(showMoreLabelEntryLimit);		
		
		pendingLogFolderRequest = new LogFolderRequest();
		pendingLogFolderRequest.setRoot(root);
		pendingLogFolderRequest.setOptions(logOptions);
		
		if (eventBus == null) {
			eventBus = GuiEventBus.getAndRegister(this);
		}
		
		eventBus.post(pendingLogFolderRequest);
	}
	
	@Subscribe
	public void onLogFolderResponse(final LogFolderResponse logResponse) {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				if (pendingLogFolderRequest != null && pendingLogFolderRequest.getId() == logResponse.getRequestId()) {
					databaseVersion = logResponse.getResult().getDatabaseVersions().get(0);
					
					disposeControls();
					createControls();
					
					redrawParents();
					
					pendingLogFolderRequest = null;
				}				
			}				
		});		
	}
	
	private void disposeControls() {
		for (Control control : getChildren()) {
			control.dispose();
		}
	}
	
	private void redrawParents() {
		LogTabComposite.this.layout();
		logComposite.redrawAll();
	}	
	
	@Override
	public void dispose() {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {	
				if (eventBus != null) {
					eventBus.unregister(LogTabComposite.this);
				}
			}
		});
		
		super.dispose();
	}
}
