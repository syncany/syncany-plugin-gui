package org.syncany.gui.history;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.TreeAdapter;
import org.eclipse.swt.events.TreeEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.ocpsoft.prettytime.PrettyTime;
import org.syncany.config.GuiEventBus;
import org.syncany.database.DatabaseVersionHeader;
import org.syncany.database.FileVersion;
import org.syncany.database.FileVersion.FileType;
import org.syncany.gui.Panel;
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.SWTResourceManager;
import org.syncany.operations.daemon.Watch;
import org.syncany.operations.daemon.messages.GetDatabaseVersionHeadersFolderRequest;
import org.syncany.operations.daemon.messages.GetDatabaseVersionHeadersFolderResponse;
import org.syncany.operations.daemon.messages.ListWatchesManagementRequest;
import org.syncany.operations.daemon.messages.ListWatchesManagementResponse;
import org.syncany.operations.daemon.messages.LsFolderRequest;
import org.syncany.operations.daemon.messages.LsFolderResponse;
import org.syncany.operations.ls.LsOperationOptions;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.eventbus.Subscribe;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class TreePanel extends Panel {
	private static final String TREE_ICON_RESOURCE_FORMAT = "/" + TreePanel.class.getPackage().getName().replace('.', '/') + "/%s.png";
	private static final Object RETRIEVING_LIST_IDENTIFIER = new Object();
	
	private String selectedRoot;
	private Date selectedDate;
	private FileVersion selectedFileVersion;
	private Set<String> expandedFilePaths;
	
	private Combo rootSelectCombo;
	private Label dateLabel;
	private Scale dateSlider;
	private Tree fileTree;
	
	private boolean dateLabelPrettyTime;
	private Timer dateSliderChangeTimer;
	
	private ListWatchesManagementRequest pendingListWatchesRequest;
	private Map<Integer, LsFolderRequest> pendingLsFolderRequests;

	private GuiEventBus eventBus;

	public TreePanel(HistoryDialog parentDialog, Composite composite, int style) {
		super(parentDialog, composite, style);

		this.setBackgroundImage(null);
		this.setBackgroundMode(SWT.INHERIT_DEFAULT);
		
		this.selectedRoot = null;
		this.selectedDate = null;		
		this.selectedFileVersion = null;		
		this.expandedFilePaths = Sets.newConcurrentHashSet();
		
		this.dateLabelPrettyTime = true;
		this.dateSliderChangeTimer = null;
		
		this.pendingListWatchesRequest = null;
		this.pendingLsFolderRequests = Maps.newConcurrentMap();
		
		this.eventBus = GuiEventBus.getInstance();
		this.eventBus.register(this);	
		
		this.createContents();
	}
	
	public HistoryDialog getParentDialog() {
		return (HistoryDialog) parentDialog;
	}

	private void createContents() {
		refreshRoots();
		
		createMainComposite();
		createRootSelectionCombo();
		createDateSlider();
		createFileTree();
	}	

	private void createMainComposite() {
		// Main composite
		GridLayout mainCompositeGridLayout = new GridLayout(3, false);
		mainCompositeGridLayout.marginTop = 0;
		mainCompositeGridLayout.marginLeft = 0;
		mainCompositeGridLayout.marginRight = 0;

		setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1));
		setLayout(mainCompositeGridLayout);
	}
	
	private void createRootSelectionCombo() {
		rootSelectCombo = new Combo(this, SWT.DROP_DOWN | SWT.BORDER | SWT.READ_ONLY);
		
		rootSelectCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		rootSelectCombo.setText(I18n.getText("org.syncany.gui.history.HistoryDialog.retrievingList"));
		rootSelectCombo.setEnabled(false);
		
		rootSelectCombo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ListWatchesManagementResponse listWatchesResponse = (ListWatchesManagementResponse) rootSelectCombo.getData();				
				
				if (listWatchesResponse != null) {
					List<Watch> watches = listWatchesResponse.getWatches();
					int selectionIndex = rootSelectCombo.getSelectionIndex();

					if (selectionIndex >= 0 && selectionIndex < watches.size()) {						
						selectedRoot = watches.get(selectionIndex).getFolder().getAbsolutePath();
						selectedDate = null;
						selectedFileVersion = null;
						expandedFilePaths.clear();
						
						refreshDateSlider();
						resetAndRefreshTree();
					}
				}
			}
		});
	}
	
	private void createDateSlider() {
		// Label
		GridData dateLabelGridData = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
		dateLabelGridData.minimumWidth = 150;
		
		dateLabel = new Label(this, SWT.CENTER);
		dateLabel.setLayoutData(dateLabelGridData);
		
		dateLabel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseUp(MouseEvent e) {
				dateLabelPrettyTime = !dateLabelPrettyTime;
				
				if (dateLabel.getData() != null) {
					updateDateLabel((Date) dateLabel.getData());
				}
			}
		});
		
		// Slider
		dateSlider = new Scale(this, SWT.HORIZONTAL | SWT.BORDER);
		
		dateSlider.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		dateSlider.setEnabled(false);
		
		dateSlider.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				// Update label right away
				updateDateLabel(getDateSliderDate());
				
				// Update file tree after a while  
				synchronized (dateSlider) {		
					if (dateSliderChangeTimer != null) {
						dateSliderChangeTimer.cancel();
					}
					
					dateSliderChangeTimer = new Timer();
					dateSliderChangeTimer.schedule(createDateSliderTimerTask(), 800);
				}
			}
		});		
	}
	
	private void updateDateLabel(final Date dateSliderDate) {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {				
				String dateStrPretty = new PrettyTime().format(dateSliderDate);
				String dateStrExact = dateSliderDate.toString();
				
				dateLabel.setData(dateSliderDate);
				
				if (dateLabelPrettyTime) {
					dateLabel.setText(dateStrPretty);
					dateLabel.setToolTipText(dateStrExact);
				}
				else {
					dateLabel.setText(dateStrExact);
					dateLabel.setToolTipText(dateStrPretty);
				}
			}
		});
	}

	private TimerTask createDateSliderTimerTask() {
		return new TimerTask() {			
			@Override
			public void run() {		
				Display.getDefault().syncExec(new Runnable() {
					@Override
					public void run() {	
						Date newDate = getDateSliderDate();						
						boolean listUpdateRequired = !newDate.equals(selectedDate);
						
						if (listUpdateRequired) {
							selectedDate = newDate;
							resetAndRefreshTree();
						}
					}
				});
			}
		};
	}
	
	@SuppressWarnings("unchecked")
	private Date getDateSliderDate() {
		List<DatabaseVersionHeader> headers = (List<DatabaseVersionHeader>) dateSlider.getData();
		
		int dateSelectionIndex = dateSlider.getSelection();
		
		if (dateSelectionIndex >= 0 && dateSelectionIndex < headers.size()) {
			return headers.get(dateSelectionIndex).getDate();
		}
		else {
			return null;
		}
	}
	
	private void createFileTree() {
		fileTree = new Tree(this, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		
		fileTree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1));
		fileTree.setEnabled(false);		

		fileTree.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event e) {
				TreeItem treeItem = (TreeItem) e.item;
				selectTreeItem(treeItem);
			}
		});
		
		fileTree.addMouseListener(new MouseAdapter() {			
			@Override
			public void mouseDoubleClick(MouseEvent e) {
				doubleClickTreeItem();
			}
		});
		
		fileTree.addTreeListener(new TreeAdapter() {
			public void treeExpanded(TreeEvent e) {
				TreeItem treeItem = (TreeItem) e.item;
				expandTreeItem(treeItem);				
			}
			
			@Override
			public void treeCollapsed(TreeEvent e) {
				TreeItem treeItem = (TreeItem) e.item;
				collapseTreeItem(treeItem);
			}			
		});
	}	

	protected void doubleClickTreeItem() {
		TreeItem[] selectedTreeItems = fileTree.getSelection();
		
		if (selectedTreeItems != null && selectedTreeItems.length > 0) {
			TreeItem selectedItem = selectedTreeItems[0];
			boolean isRetrievingItem = RETRIEVING_LIST_IDENTIFIER.equals(selectedItem.getData());
			
			if (!isRetrievingItem) {
				FileVersion fileVersion = (FileVersion) selectedItem.getData();
				
				if (fileVersion.getType() == FileType.FOLDER) {					
					if (selectedItem.getExpanded()) {
						collapseTreeItem(selectedItem);
					}
					else {
						expandTreeItem(selectedItem);
					}
					
					selectedItem.setExpanded(!selectedItem.getExpanded());
				}
				else {
					showDetails(fileVersion);
				}
			}
		}
	}

	private void showDetails(FileVersion fileVersion) {
		getParentDialog().showDetails(selectedRoot, fileVersion.getFileHistoryId());
	}

	private void selectTreeItem(TreeItem treeItem) {
		boolean isRetrievingItem = RETRIEVING_LIST_IDENTIFIER.equals(treeItem.getData());
		
		if (!isRetrievingItem) {
			FileVersion fileVersion = (FileVersion) treeItem.getData();
			selectedFileVersion = fileVersion;			
		}
	}

	private void expandTreeItem(TreeItem treeItem) {
		if (treeItem.getItemCount() > 0) {
			TreeItem firstChildItem = treeItem.getItem(0);
			boolean isRetrievingItem = RETRIEVING_LIST_IDENTIFIER.equals(firstChildItem.getData());
			
			if (isRetrievingItem) {
				FileVersion fileVersion = (FileVersion) treeItem.getData();
				
				expandedFilePaths.add(fileVersion.getPath());
				refreshTree(fileVersion.getPath());
			}
		}
	}
	
	private void collapseTreeItem(TreeItem treeItem) {
		final FileVersion fileVersion = (FileVersion) treeItem.getData();
		
		// Remove all children items from saved expanded paths
		Iterables.removeIf(expandedFilePaths, new Predicate<String>() {
			@Override
			public boolean apply(String expandedPath) {				
				return expandedPath.startsWith(fileVersion.getPath());
			}			
		});		
	}

	public void safeDispose() {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {	
				eventBus.unregister(TreePanel.this);
			}
		});
	}	
	
	private void refreshRoots() {
		pendingListWatchesRequest = new ListWatchesManagementRequest();
		eventBus.post(pendingListWatchesRequest);		
	}

	@Subscribe
	public void onListWatchesManagementResponse(final ListWatchesManagementResponse listWatchesResponse) {
		if (pendingListWatchesRequest != null && pendingListWatchesRequest.getId() == listWatchesResponse.getRequestId()) {
			// Nullify pending request
			pendingListWatchesRequest = null;

			// Update combo box
			Display.getDefault().syncExec(new Runnable() {
				@Override
				public void run() {
					ArrayList<Watch> watches = listWatchesResponse.getWatches();
					
					rootSelectCombo.removeAll();
					
					for (Watch watch : watches) {
						rootSelectCombo.add(watch.getFolder().getName());
					}
					
					rootSelectCombo.setData(listWatchesResponse);
					rootSelectCombo.setEnabled(true);
					
					if (rootSelectCombo.getItemCount() > 0) {
						selectedRoot = watches.get(0).getFolder().getAbsolutePath();
						rootSelectCombo.select(0);
						
						refreshDateSlider();
						resetAndRefreshTree();
					}
				}
			});
		}
	}
	
	private void refreshDateSlider() {
		GetDatabaseVersionHeadersFolderRequest getHeadersRequest = new GetDatabaseVersionHeadersFolderRequest();
		getHeadersRequest.setRoot(selectedRoot);
		
		eventBus.post(getHeadersRequest);
	}
	
	@Subscribe
	public void onGetDatabaseVersionHeadersFolderResponse(final GetDatabaseVersionHeadersFolderResponse getHeadersResponse) {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				List<DatabaseVersionHeader> headers = getHeadersResponse.getDatabaseVersionHeaders();
				
				if (headers.size() > 0) {
					int maxValue = headers.size() - 1;
					
					dateSlider.setData(headers);
					dateSlider.setMinimum(0);
					dateSlider.setMaximum(maxValue);
					dateSlider.setSelection(maxValue);
					dateSlider.setEnabled(true);
					
					selectedDate = headers.get(headers.size()-1).getDate();	
					updateDateLabel(selectedDate);
				}
				else {
					dateSlider.setMinimum(0);
					dateSlider.setMaximum(0);
					dateSlider.setEnabled(false);	
					
					selectedDate = null;
				}				
			}
		});
	}

	private void resetAndRefreshTree() {
		fileTree.removeAll();
		refreshTree("");
	}
	
	private void refreshTree(String pathExpression) {
		// Adjust path expression
		if (!"".equals(pathExpression)) {
			pathExpression += "/";
		}
		
		// Date
		Date browseDate = (selectedDate != null) ? selectedDate : new Date();
		
		// Create list request
		LsOperationOptions lsOptions = new LsOperationOptions();
		
		lsOptions.setPathExpression(pathExpression);
		lsOptions.setDate(browseDate);
		lsOptions.setRecursive(false);
		lsOptions.setFetchHistories(false);
		lsOptions.setFileTypes(Sets.newHashSet(FileType.FILE, FileType.FOLDER, FileType.SYMLINK));
		
		LsFolderRequest lsRequest = new LsFolderRequest();
		
		lsRequest.setRoot(selectedRoot);
		lsRequest.setOptions(lsOptions);
		
		// Send request
		pendingLsFolderRequests.put(lsRequest.getId(), lsRequest);
		eventBus.post(lsRequest);
	}
	
	@Subscribe
	public void onLsFolderResponse(final LsFolderResponse lsResponse) {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				fileTree.setEnabled(true);
				
				LsFolderRequest lsRequest = pendingLsFolderRequests.remove(lsResponse.getRequestId());
				
				if (lsRequest != null) {
					updateTree(lsRequest, lsResponse);
				}
			}
		});		
	}

	private void updateTree(LsFolderRequest lsRequest, LsFolderResponse lsResponse) {
		Map<String, FileVersion> fileVersions = lsResponse.getResult().getFileTree();
		
		// Find parent path (where to attach new items)
		TreeItem parentTreeItem = findTreeItemByPath(lsRequest.getOptions().getPathExpression());
		
		if (parentTreeItem != null) {
			parentTreeItem.removeAll();
		}
		
		// Create new items
		for (FileVersion fileVersion : fileVersions.values()) {
			if (fileVersion.getType() == FileType.FOLDER) {
				TreeItem treeItem = createTreeItem(parentTreeItem);
				treeItem.setData(fileVersion);
				treeItem.setText(fileVersion.getName());	
				treeItem.setImage(SWTResourceManager.getImage(String.format(TREE_ICON_RESOURCE_FORMAT, "folder")));
				
				TreeItem retrieveListTreeItem = new TreeItem(treeItem, 0);
				retrieveListTreeItem.setData(RETRIEVING_LIST_IDENTIFIER);
				retrieveListTreeItem.setText(I18n.getText("org.syncany.gui.history.HistoryDialog.retrievingList"));
				
				if (expandedFilePaths.contains(fileVersion.getPath())) {
					treeItem.setExpanded(true);
					refreshTree(fileVersion.getPath());
				}
				
				if (selectedFileVersion != null && selectedFileVersion.getFileHistoryId().equals(fileVersion.getFileHistoryId())) {
					fileTree.setSelection(treeItem);
				}
			}
		}
		
		for (FileVersion fileVersion : fileVersions.values()) {
			if (fileVersion.getType() != FileType.FOLDER) {
				TreeItem treeItem = createTreeItem(parentTreeItem);
				treeItem.setData(fileVersion);
				treeItem.setText(fileVersion.getName());			
				treeItem.setImage(SWTResourceManager.getImage(String.format(TREE_ICON_RESOURCE_FORMAT, "file")));
				
				if (selectedFileVersion != null && selectedFileVersion.getFileHistoryId().equals(fileVersion.getFileHistoryId())) {
					fileTree.setSelection(treeItem);
				}
			}
		}
		
		if (parentTreeItem != null) {
			parentTreeItem.setExpanded(true);
		}
	}

	private TreeItem createTreeItem(TreeItem parentItem) {
		if (parentItem != null) {
			return new TreeItem(parentItem, SWT.NONE);
		}
		else {
			return new TreeItem(fileTree, SWT.NONE);
		}
	}

	private TreeItem findTreeItemByPath(String pathExpression) {
		if ("".equals(pathExpression)) {
			return null;
		}
		else {
			TreeItem[] treeItems = fileTree.getItems();
			String searchPath = pathExpression.substring(0, pathExpression.length()-1);
			
			return findTreeItem(searchPath, treeItems);			
		}		
	}

	private TreeItem findTreeItem(String searchPath, TreeItem[] treeItems) {
		for (int i = 0; i < treeItems.length; i++) {
			TreeItem treeItem = treeItems[i];
			boolean isRetrievingItem = RETRIEVING_LIST_IDENTIFIER.equals(treeItem.getData());
			
			if (!isRetrievingItem) {
				FileVersion fileVersion = (FileVersion) treeItem.getData();
								
				if (fileVersion.getPath().equals(searchPath)) {
					return treeItem;
				}
				else if (treeItem.getItemCount() > 0) {
					TreeItem searchItem = findTreeItem(searchPath, treeItem.getItems());
					
					if (searchItem != null) {
						return searchItem;
					}
				}
			}
		}			
			
		return null;
	}

	@Override
	public boolean validatePanel() {
		return true;
	}
}
