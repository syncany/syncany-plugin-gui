package org.syncany.gui.history;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.TreeAdapter;
import org.eclipse.swt.events.TreeEvent;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;
import org.ocpsoft.prettytime.PrettyTime;
import org.syncany.config.GuiEventBus;
import org.syncany.database.FileVersion;
import org.syncany.database.FileVersion.FileType;
import org.syncany.database.PartialFileHistory.FileHistoryId;
import org.syncany.gui.history.events.ModelSelectedDateUpdatedEvent;
import org.syncany.gui.history.events.ModelSelectedFilePathUpdatedEvent;
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
	
	private Tree fileTree;
	
	private HistoryModel historyModel;
	private HistoryDialog historyDialog;
	
	private Map<Integer, LsFolderRequest> pendingLsFolderRequests;		
	private GuiEventBus eventBus;	
	
	private Map<String, TreeItem> pathTreeItemCache;
	private Map<FileHistoryId, TreeItem> fileHistoryIdTreeItemCache;
	
	private TreeSet<String> expandedFilePaths;
	
	public FileTreeComposite(Composite parent, int style, HistoryModel historyModel, HistoryDialog historyDialog) {
		super(parent, style);
		
		this.fileTree = null;
		
		this.historyModel = historyModel;
		this.historyDialog = historyDialog;
		
		this.pendingLsFolderRequests = Maps.newConcurrentMap();
		this.eventBus = GuiEventBus.getAndRegister(this);
		
		this.pathTreeItemCache = Maps.newConcurrentMap();
		this.fileHistoryIdTreeItemCache = Maps.newConcurrentMap();
		
		this.expandedFilePaths = Sets.newTreeSet();	
		
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
		fileTree = new Tree(this, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL | SWT.DOUBLE_BUFFERED | SWT.SINGLE | SWT.FULL_SELECTION);

		fileTree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		fileTree.setEnabled(false);		

		createTreeListeners();
		createTreeColumns();						
	}		

	private void createTreeListeners() {		
		fileTree.addMouseListener(new MouseAdapter() {	
			@Override
			public void mouseUp(MouseEvent e) {
				clickItem(getSelectedItem());
			}
			
			@Override
			public void mouseDoubleClick(MouseEvent e) {
				doubleClickItem(getSelectedItem());
			}
		});
		
		fileTree.addKeyListener(new KeyAdapter() {			
			@Override
			public void keyReleased(KeyEvent e) {
				clickItem(getSelectedItem());
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
	
	private void createTreeColumns() {
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

	@Subscribe
	public void onModelSelectedDateUpdatedEvent(ModelSelectedDateUpdatedEvent event) {
		logger.log(Level.INFO, "Tree: Model DATE changed event received (" + event.getSelectedDate() + "); resetting tree ...");
		resetAndSendRootLsRequest();
	}	
	
	private void resetAndSendRootLsRequest() {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {	
				logger.log(Level.INFO, "Tree: Reset: Remove all tree items; and resending LsRequest ...");
				sendLsRequest("");		

				for (String expandedPath : expandedFilePaths) {
					sendLsRequest(expandedPath + "/");
				}
			}
		});
	}

	private void sendLsRequest(String pathExpression) {
		// Date
		Date browseDate = (historyModel.getSelectedDate() != null) ? historyModel.getSelectedDate() : new Date();
		
		// Create list request
		LsOperationOptions lsOptions = new LsOperationOptions();
		
		lsOptions.setPathExpression(pathExpression);
		lsOptions.setDate(browseDate);
		lsOptions.setRecursive(false);
		lsOptions.setFetchHistories(false);
		lsOptions.setFileTypes(Sets.newHashSet(FileType.FILE, FileType.FOLDER, FileType.SYMLINK));
		
		LsFolderRequest lsRequest = new LsFolderRequest();
		
		lsRequest.setRoot(historyModel.getSelectedRoot());
		lsRequest.setOptions(lsOptions);

		logger.log(Level.INFO, "Tree: Sending LsRequest #" + lsRequest.getId() + ", date: " + browseDate + ", root: "
				+ historyModel.getSelectedRoot() + ", path: "
				+ pathExpression + " ...");

		// Send request
		pendingLsFolderRequests.put(lsRequest.getId(), lsRequest);
		eventBus.post(lsRequest);		
	}
	
	private void sendLsRequestsWithChildren(String pathExpression) {
		logger.log(Level.INFO, "Tree: Refreshing at " + pathExpression + " ...");	
		
		// Add to expanded paths
		addToExpandedPathsIncludingChildPaths(pathExpression);		
		
		// Find all sub-paths, a/b/c/ -> [a, a/b, a/b/c]
		List<String> notLoadedPaths = findUnloadedPaths(pathExpression);
				
		// If items unloaded: set 'select after load' item, and send load requests
		if (!notLoadedPaths.isEmpty()) {
			logger.log(Level.INFO, "Tree: Sending LsRequests for " + notLoadedPaths.size() + " not-yet-loaded-path(s) ...");					
			
			for (String path : notLoadedPaths) {
				sendLsRequest(path + "/");
			}			
		}	
		else {
			selectItemByPath(pathExpression);
		}
	}
		
	private List<String> findUnloadedPaths(String pathExpression) {
		List<String> allPaths = getPaths(pathExpression + "/");
		List<String> notLoadedPaths = new ArrayList<>();
		
		for (String path : allPaths) {			
			TreeItem treeItem = findItemByPath(path);
			
			boolean noTreeItem = treeItem == null;
			boolean treeItemWithRetrievingChild = treeItem != null && hasRetrievingChildItem(treeItem);
			
			if (noTreeItem || treeItemWithRetrievingChild) {
				notLoadedPaths.add(path);
				logger.log(Level.INFO, "- Item '" + path + "' has not been loaded.");					
			}
		}
		
		return notLoadedPaths;
	}

	@Subscribe
	public void onLsFolderResponse(final LsFolderResponse lsResponse) {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				fileTree.setEnabled(true);
				
				LsFolderRequest lsRequest = pendingLsFolderRequests.remove(lsResponse.getRequestId());
				
				if (lsRequest != null) {
					logger.log(Level.INFO, "Tree: Received LsResponse for request #" + lsResponse.getRequestId() + "; updating tree at path " + lsRequest.getOptions().getPathExpression() + " ...");					
					createTreeItems(lsRequest, lsResponse);
				}
			}
		});		
	}	

	private void createTreeItems(LsFolderRequest lsRequest, LsFolderResponse lsResponse) {
		logger.log(Level.INFO, "Tree: Updating with LsResponse " + lsResponse.getResult().getFileList().size() + " versions ...");

		List<FileVersion> fileVersions = lsResponse.getResult().getFileList();
		
		// Clear entire tree if '/' request
		String pathExpression = lsRequest.getOptions().getPathExpression();
		boolean isRootRefresh = "".equals(pathExpression);
		
		if (isRootRefresh) {
			fileTree.removeAll();	
			
			pathTreeItemCache.clear();
			fileHistoryIdTreeItemCache.clear();
		}
		
		// Find parent path (where to attach new items)
		TreeItem parentTreeItem = findItemByPath(pathExpression);
		
		if (parentTreeItem != null) {
			parentTreeItem.removeAll(); // removes 'Retrieving ...'
		}
		
		// Create new items
		createFolderItems(parentTreeItem, fileVersions);
		createFileItems(parentTreeItem, fileVersions);				
		
		// Expand parent path
		if (parentTreeItem != null) {
			parentTreeItem.setExpanded(true);			
		}
				
		addToExpandedPathsIncludingChildPaths(pathExpression);		
		
		// Select item
		selectItemIfSelectedPathOrFileVersion();		
	}

	private void addToExpandedPathsIncludingChildPaths(String pathExpression) {
		List<String> allPaths = getPaths(pathExpression + "/");

		for (String path : allPaths) {
			if (path.endsWith("/")) {
				path = path.substring(0, path.length() - 1);
			}
			
			if (!path.isEmpty()) {			
				expandedFilePaths.add(path);
			}
		}
		
		logExpandedPaths();		
	}

	private void createFolderItems(TreeItem parentTreeItem, List<FileVersion> fileVersions) {
		for (FileVersion fileVersion : fileVersions) {
			if (fileVersion.getType() == FileType.FOLDER) {
				TreeItem treeItem = createItem(parentTreeItem);
				treeItem.setData(fileVersion);
				treeItem.setText(fileVersion.getName());	
				treeItem.setImage(SWTResourceManager.getImage(String.format(TREE_ICON_RESOURCE_FORMAT, "folder")));
				
				TreeItem retrieveListTreeItem = new TreeItem(treeItem, 0);
				retrieveListTreeItem.setData(RETRIEVING_LIST_IDENTIFIER);
				retrieveListTreeItem.setText(I18n.getText("org.syncany.gui.history.HistoryDialog.retrievingList"));
				
				if (expandedFilePaths.contains(fileVersion.getPath())) {
					treeItem.setExpanded(true);
				}
				
				pathTreeItemCache.put(fileVersion.getPath(), treeItem);
				fileHistoryIdTreeItemCache.put(fileVersion.getFileHistoryId(), treeItem);
			}
		}
	}
	
	private void createFileItems(TreeItem parentTreeItem, List<FileVersion> fileVersions) {
		for (FileVersion fileVersion : fileVersions) {
			if (fileVersion.getType() != FileType.FOLDER) {
				TreeItem treeItem = createItem(parentTreeItem);
				treeItem.setData(fileVersion);
				treeItem.setText(new String[] { fileVersion.getName(), new PrettyTime().format(fileVersion.getLastModified())});			
				treeItem.setImage(SWTResourceManager.getImage(String.format(TREE_ICON_RESOURCE_FORMAT, "file")));
				
				pathTreeItemCache.put(fileVersion.getPath(), treeItem);
				fileHistoryIdTreeItemCache.put(fileVersion.getFileHistoryId(), treeItem);
			}
		}
	}		

	private List<String> getPaths(String pathExpression) {
		List<String> paths = new ArrayList<>();
		int previousIndexOf = -1;
		
		while (-1 != (previousIndexOf = pathExpression.indexOf('/', previousIndexOf + 1))) {
			paths.add(pathExpression.substring(0, previousIndexOf));
		}
		
		return paths;
	}	
	
	private void clickItem(TreeItem treeItem) {		
		if (treeItem != null && !isRetrievingItem(treeItem)) {
			logger.log(Level.INFO, "Tree: Clicked: " + treeItem);
			selectItem(treeItem);
		}
	}
	
	private void doubleClickItem(TreeItem treeItem) {
		if (treeItem != null && !isRetrievingItem(treeItem)) {
			FileVersion fileVersion = (FileVersion) treeItem.getData();
			logger.log(Level.INFO, "Tree: Double clicking item " + fileVersion.getPath() + " ...");					
			
			if (fileVersion.getType() == FileType.FOLDER) {					
				if (treeItem.getExpanded()) {
					logger.log(Level.INFO, "- Is expanded folder: Collapsing ...");					
					collapseTreeItem(treeItem);
				}
				else {
					logger.log(Level.INFO, "- Is collapsed folder: Expanding ...");					
					expandTreeItem(treeItem);
				}
				
				treeItem.setExpanded(!treeItem.getExpanded());
			}
			else {
				logger.log(Level.INFO, "- Is file: Showing details ...");					
				historyDialog.showDetailsPanel(historyModel.getSelectedRoot(), fileVersion.getFileHistoryId());
			}
		}
	}

	private TreeItem getSelectedItem() {
		TreeItem[] selectedTreeItems = fileTree.getSelection();
		
		if (selectedTreeItems != null && selectedTreeItems.length > 0) {
			return selectedTreeItems[0];
		}
		else {
			return null;
		}
	}
	
	private void selectItem(TreeItem treeItem) {
		if (!isRetrievingItem(treeItem)) {
			FileVersion fileVersion = (FileVersion) treeItem.getData();
			
			logger.log(Level.INFO, "Tree: Selected item with history ID " + fileVersion.getFileHistoryId());							
			historyModel.setSelectedFileHistoryId(fileVersion.getFileHistoryId());		
		}
	}

	private void expandTreeItem(TreeItem treeItem) {
		FileVersion fileVersion = (FileVersion) treeItem.getData();
		
		// Add to expanded paths
		addToExpandedPathsIncludingChildPaths(fileVersion.getPath());

		// Send 'load' request (or not)
		if (hasRetrievingChildItem(treeItem)) {
			logger.log(Level.INFO, "Tree: Expand item; Sending LsRequest for path " + fileVersion.getPath() + " ...");							
			sendLsRequest(fileVersion.getPath() + "/");
		}
		else {
			logger.log(Level.INFO, "Tree: Expand item; Not loading item, because no 'retrieving ..' child: " + treeItem.getText());
		}
	}
	
	private void collapseTreeItem(TreeItem treeItem) {
		final FileVersion fileVersion = (FileVersion) treeItem.getData();
		logger.log(Level.INFO, "Tree: Collapsing item with history ID #" + fileVersion.getFileHistoryId() + ", with text " + treeItem.getText());							
		
		// Remove from expanded paths
		removeFromExpandedPathsIncludingChildPaths(fileVersion.getPath());
	}

	private void removeFromExpandedPathsIncludingChildPaths(final String path) {
		Iterables.removeIf(expandedFilePaths, new Predicate<String>() {
			@Override
			public boolean apply(String expandedPath) {				
				return expandedPath.startsWith(path);
			}			
		});		
		
		logExpandedPaths();
	}

	private void selectItemIfSelectedPathOrFileVersion() {
		if (historyModel.getSelectedFileHistoryId() != null) {
			selectItemByFileHistoryId(historyModel.getSelectedFileHistoryId());			
		}
		else if (historyModel.getSelectedFilePath() != null) {
			selectItemByPath(historyModel.getSelectedFilePath());
		}		
	}

	private void selectItemByFileHistoryId(final FileHistoryId fileHistoryId) {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {	
				TreeItem treeItem = fileHistoryIdTreeItemCache.get(fileHistoryId);
				
				if (treeItem != null) {
					logger.log(Level.INFO, "Tree: Selecting file by file history ID #" + fileHistoryId + "; tree item " + treeItem);
					fileTree.setSelection(treeItem); 
					
					// Note: Tree.setSelection() must be called within asyncExec, not syncExec.
					//       It took me 2-3 hours to figure this out. Don't delete this comment!
				}
			}
		});
	}

	private void selectItemByPath(final String searchPath) {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {	
				TreeItem treeItem = findItemByPath(searchPath);
				
				if (treeItem != null) {
					logger.log(Level.INFO, "Tree: Selecting file by path " + searchPath + "; tree item " + treeItem);							
					fileTree.setSelection(treeItem);					
					
					// Note: Tree.setSelection() must be called within asyncExec, not syncExec.
					//       It took me 2-3 hours to figure this out. Don't delete this comment!					
				}
			}
		});
	}

	private TreeItem createItem(TreeItem parentItem) {
		if (parentItem != null) {
			return new TreeItem(parentItem, SWT.NONE);
		}
		else {
			return new TreeItem(fileTree, SWT.NONE);
		}
	}

	public TreeItem findItemByPath(String searchPath) {				
		if (searchPath == null || "".equals(searchPath)) {
			return null;
		}
		else {
			if (searchPath.endsWith("/")) {
				searchPath = searchPath.substring(0, searchPath.length() - 1);
			}
			
			return pathTreeItemCache.get(searchPath);		
		}		
	}

	private boolean isRetrievingItem(TreeItem treeItem) {
		return RETRIEVING_LIST_IDENTIFIER.equals(treeItem.getData());		
	}
	
	public boolean hasRetrievingChildItem(TreeItem treeItem) {
		return treeItem.getItemCount() == 1 && isRetrievingItem(treeItem.getItems()[0]);
	}
	
	@Subscribe
	public void onModelSelectedFilePathUpdatedEvent(ModelSelectedFilePathUpdatedEvent event) {
		logger.log(Level.INFO, "Tree: Model FILE PATH changed event received; refreshing tree for " + event.getSelectedFilePath() + " ...");
		sendLsRequestsWithChildren(event.getSelectedFilePath());
	}	
	
	public void dispose() {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {	
				eventBus.unregister(FileTreeComposite.this);									
			}
		});
	}
	
	private void logExpandedPaths() {
		if (logger.isLoggable(Level.INFO)) {
			logger.log(Level.INFO, "Tree: Updated expanded paths; full list:");
			
			for (String path : expandedFilePaths) {
				logger.log(Level.INFO, " - " + path);
			}
		}
	}
}
