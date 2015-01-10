package org.syncany.gui.history;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.TreeAdapter;
import org.eclipse.swt.events.TreeEvent;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;
import org.ocpsoft.prettytime.PrettyTime;
import org.syncany.config.GuiEventBus;
import org.syncany.database.FileVersion;
import org.syncany.database.FileVersion.FileType;
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.SWTResourceManager;
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
public class FileTreeComposite extends Composite {
	private static final Logger logger = Logger.getLogger(FileTreeComposite.class.getSimpleName());		

	private static final String TREE_ICON_RESOURCE_FORMAT = "/" + FileTreeComposite.class.getPackage().getName().replace('.', '/') + "/%s.png";
	private static final Object RETRIEVING_LIST_IDENTIFIER = new Object();
	
	private MainPanel mainPanel;
	private MainPanelState state;
	
	private Map<Integer, LsFolderRequest> pendingLsFolderRequests;
	private String selectAfterLoadFilePath;
	
	private Tree fileTree;
	
	private GuiEventBus eventBus;

	public FileTreeComposite(MainPanel mainPanel, MainPanelState state, Composite parent, int style) {
		super(parent, style);

		this.mainPanel = mainPanel;
		this.state = state;		
				
		this.pendingLsFolderRequests = Maps.newConcurrentMap();
		this.selectAfterLoadFilePath = null;
		
		this.eventBus = GuiEventBus.getInstance();
		this.eventBus.register(this);	
		
		this.createContents();
	}	

	private void createContents() {
		createMainComposite();
		createFileTree();
	}	

	private void createMainComposite() {
		// Main composite
		GridLayout mainCompositeGridLayout = new GridLayout(1, false);
		mainCompositeGridLayout.marginTop = 0;
		mainCompositeGridLayout.marginLeft = 0;
		mainCompositeGridLayout.marginRight = 0;
		mainCompositeGridLayout.verticalSpacing = 0;
		mainCompositeGridLayout.horizontalSpacing = 0;

		setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		setLayout(mainCompositeGridLayout);
	}	
	
	private void createFileTree() {
		fileTree = new Tree(this, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		
		fileTree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
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
		
		final TreeColumn columnFile = new TreeColumn(fileTree, SWT.LEFT);
	    columnFile.setWidth(400);
	    
	    final TreeColumn columnLastModified = new TreeColumn(fileTree, SWT.LEFT);
	    columnLastModified.setWidth(150);	    
		
		fileTree.addControlListener(new ControlAdapter() {
			@Override
			public void controlResized(ControlEvent e) {
				Rectangle area = fileTree.getClientArea();

				int newFileColumnWidth = area.width - columnLastModified.getWidth() - 20;
				columnFile.setWidth(newFileColumnWidth);
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
					mainPanel.showDetails(fileVersion);
				}
			}
		}
	}

	private void selectTreeItem(TreeItem treeItem) {
		boolean isRetrievingItem = RETRIEVING_LIST_IDENTIFIER.equals(treeItem.getData());
		
		if (!isRetrievingItem) {
			FileVersion fileVersion = (FileVersion) treeItem.getData();
			state.setSelectedFileVersion(fileVersion);			
		}
	}

	private void expandTreeItem(TreeItem treeItem) {
		if (treeItem.getItemCount() > 0) {
			TreeItem firstChildItem = treeItem.getItem(0);
			boolean isRetrievingItem = RETRIEVING_LIST_IDENTIFIER.equals(firstChildItem.getData());
			
			if (isRetrievingItem) {
				FileVersion fileVersion = (FileVersion) treeItem.getData();
				
				state.getExpandedFilePaths().add(fileVersion.getPath());
				refreshTree(fileVersion.getPath());
			}
		}
	}
	
	private void collapseTreeItem(TreeItem treeItem) {
		final FileVersion fileVersion = (FileVersion) treeItem.getData();
		
		// Remove all children items from saved expanded paths
		Iterables.removeIf(state.getExpandedFilePaths(), new Predicate<String>() {
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
				eventBus.unregister(FileTreeComposite.this);
			}
		});
	}		

	public void resetAndRefresh() {
		fileTree.removeAll();
		refreshRoot();
	}
	
	public void refreshTree(String pathExpression) {
		if ("".equals(pathExpression)) {
			throw new IllegalArgumentException();
		}
		
		logger.log(Level.INFO, "Refreshing tree at " + pathExpression + " ...");	
		
		// Find all sub-paths, a/b/c/ -> [a, a/b, a/b/c]
		List<String> allPaths = getPaths(pathExpression + "/");
		List<String> notLoadedPaths = new ArrayList<>();
		
		for (String path : allPaths) {
			TreeItem treeItem = findTreeItemByPath(path);
			
			if (!notLoadedPaths.isEmpty()) {
				notLoadedPaths.add(path);
				logger.log(Level.INFO, "- Item '" + path + "' has not been loaded (2).");					
			}
			else if (treeItem != null) {
				boolean hasRetrievingChildItem = treeItem.getItemCount() == 1 && RETRIEVING_LIST_IDENTIFIER.equals(treeItem.getItems()[0].getData());
			
				if (hasRetrievingChildItem) {
					notLoadedPaths.add(path);
					logger.log(Level.INFO, "- Item '" + path + "' has not been loaded (1).");					
				}
			}
		}
		
		// If items unloaded: set 'select after load' item, and send load requests
		if (!notLoadedPaths.isEmpty()) {
			selectAfterLoadFilePath = pathExpression;
						
			for (String path : notLoadedPaths) {
				sendLsRequest(path + "/");
			}			
		}
		else {
			selectItemByPath(pathExpression);
		}
	}
	
	private void refreshRoot() {
		sendLsRequest("");
	}
	
	private void sendLsRequest(String pathExpression) {
		// Date
		Date browseDate = (state.getSelectedDate() != null) ? state.getSelectedDate() : new Date();
		
		// Create list request
		LsOperationOptions lsOptions = new LsOperationOptions();
		
		lsOptions.setPathExpression(pathExpression);
		lsOptions.setDate(browseDate);
		lsOptions.setRecursive(false);
		lsOptions.setFetchHistories(false);
		lsOptions.setFileTypes(Sets.newHashSet(FileType.FILE, FileType.FOLDER, FileType.SYMLINK));
		
		LsFolderRequest lsRequest = new LsFolderRequest();
		
		lsRequest.setRoot(state.getSelectedRoot());
		lsRequest.setOptions(lsOptions);
		
		// Send request
		pendingLsFolderRequests.put(lsRequest.getId(), lsRequest);
		eventBus.post(lsRequest);		
	}
	
	private List<String> getPaths(String pathExpression) {
		List<String> paths = new ArrayList<>();
		int previousIndexOf = -1;
		
		while (-1 != (previousIndexOf = pathExpression.indexOf('/', previousIndexOf + 1))) {
			paths.add(pathExpression.substring(0, previousIndexOf));
		}
		
		return paths;
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
		List<FileVersion> fileVersions = lsResponse.getResult().getFileList();
		
		// Find parent path (where to attach new items)
		TreeItem parentTreeItem = findTreeItemByPath(lsRequest.getOptions().getPathExpression());
		
		if (parentTreeItem != null) {
			parentTreeItem.removeAll();
		}
		
		// Create new items
		createFolderItems(parentTreeItem, fileVersions);
		createFileItems(parentTreeItem, fileVersions);				
		
		// Expand parent path
		if (parentTreeItem != null) {
			parentTreeItem.setExpanded(true);
		}
		
		// Select item
		selectAfterLoadItem();
	}

	private void createFolderItems(TreeItem parentTreeItem, List<FileVersion> fileVersions) {
		for (FileVersion fileVersion : fileVersions) {
			if (fileVersion.getType() == FileType.FOLDER) {
				TreeItem treeItem = createTreeItem(parentTreeItem);
				treeItem.setData(fileVersion);
				treeItem.setText(fileVersion.getName());	
				treeItem.setImage(SWTResourceManager.getImage(String.format(TREE_ICON_RESOURCE_FORMAT, "folder")));
				
				TreeItem retrieveListTreeItem = new TreeItem(treeItem, 0);
				retrieveListTreeItem.setData(RETRIEVING_LIST_IDENTIFIER);
				retrieveListTreeItem.setText(I18n.getText("org.syncany.gui.history.HistoryDialog.retrievingList"));
				
				if (state.getExpandedFilePaths().contains(fileVersion.getPath())) {
					treeItem.setExpanded(true);
					refreshTree(fileVersion.getPath());
				}
				
				if (state.getSelectedFileVersion() != null && state.getSelectedFileVersion().getFileHistoryId().equals(fileVersion.getFileHistoryId())) {
					fileTree.setSelection(treeItem);
				}
			}
		}
	}
	
	private void createFileItems(TreeItem parentTreeItem, List<FileVersion> fileVersions) {
		for (FileVersion fileVersion : fileVersions) {
			if (fileVersion.getType() != FileType.FOLDER) {
				TreeItem treeItem = createTreeItem(parentTreeItem);
				treeItem.setData(fileVersion);
				treeItem.setText(new String[] { fileVersion.getName(), new PrettyTime().format(fileVersion.getLastModified())});			
				treeItem.setImage(SWTResourceManager.getImage(String.format(TREE_ICON_RESOURCE_FORMAT, "file")));
				
				if (state.getSelectedFileVersion() != null && state.getSelectedFileVersion().getFileHistoryId().equals(fileVersion.getFileHistoryId())) {
					fileTree.setSelection(treeItem);
				}
			}
		}
	}	

	private void selectAfterLoadItem() {
		if (selectAfterLoadFilePath != null) {
			selectItemByPath(selectAfterLoadFilePath);
		}
	}
	
	private void selectItemByPath(String searchPath) {
		TreeItem treeItem = findTreeItemByPath(searchPath);
		
		if (treeItem != null) {
			fileTree.setSelection(treeItem);
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

	private TreeItem findTreeItemByPath(String searchPath) {				
		if ("".equals(searchPath)) {
			return null;
		}
		else {
			if (searchPath.endsWith("/")) {
				searchPath = searchPath.substring(0, searchPath.length() - 1);
			}
			
			TreeItem[] treeItems = fileTree.getItems();
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
}
