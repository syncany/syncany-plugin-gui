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
import org.syncany.gui.history.events.ModelSelectedRootUpdatedEvent;
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.SWTResourceManager;
import org.syncany.operations.daemon.messages.LsFolderRequest;
import org.syncany.operations.daemon.messages.LsFolderResponse;
import org.syncany.operations.ls.LsOperationOptions;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.eventbus.Subscribe;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class FileTreeComposite extends Composite {
	private static final Logger logger = Logger.getLogger(FileTreeComposite.class.getSimpleName());		

	private static final String TREE_ICON_RESOURCE_FORMAT = "/" + FileTreeComposite.class.getPackage().getName().replace('.', '/') + "/%s.png";
	private static final Object RETRIEVING_LIST_IDENTIFIER = new Object();
	
	private HistoryModel model;
	private Tree fileTree;

	private HistoryDialog historyDialog;
	private FileTreeComposite fileTreeComposite;
	
	private Map<Integer, LsFolderRequest> pendingLsFolderRequests;	
	
	private GuiEventBus eventBus;	
	
	public FileTreeComposite(Composite parent, int style) {
		super(parent, style);
		
		this.eventBus = GuiEventBus.getAndRegister(this);
		
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
				selectItem(treeItem);
			}
		});
		
		fileTree.addMouseListener(new MouseAdapter() {			
			@Override
			public void mouseDoubleClick(MouseEvent e) {
				doubleClickSelectedItem();
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

	protected void doubleClickSelectedItem() {
		TreeItem[] selectedTreeItems = fileTree.getSelection();
		
		if (selectedTreeItems != null && selectedTreeItems.length > 0) {
			TreeItem selectedItem = selectedTreeItems[0];

			if (!isRetrievingItem(selectedItem)) {
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
					onDoubleClickItem(fileVersion);
				}
			}
		}
	}

	private void selectItem(TreeItem treeItem) {
		if (!isRetrievingItem(treeItem)) {
			FileVersion fileVersion = (FileVersion) treeItem.getData();
			onSelectItem(fileVersion);			
		}
	}

	private void expandTreeItem(TreeItem treeItem) {
		if (hasRetrievingChildItem(treeItem)) {
			FileVersion fileVersion = (FileVersion) treeItem.getData();		
			onExpandItem(fileVersion);
		}
	}
	
	private void collapseTreeItem(TreeItem treeItem) {
		final FileVersion fileVersion = (FileVersion) treeItem.getData();
		onCollapseItem(fileVersion);		
	}

	public void updateTree(LsFolderRequest lsRequest, LsFolderResponse lsResponse) {
		List<FileVersion> fileVersions = lsResponse.getResult().getFileList();
		
		// Find parent path (where to attach new items)
		TreeItem parentTreeItem = findItemByPath(lsRequest.getOptions().getPathExpression());
		
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
		selectItemIfSelectedPathOrFileVersion();
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
				
				if (model.getExpandedFilePaths().contains(fileVersion.getPath())) {
					treeItem.setExpanded(true);
					refreshTree(fileVersion.getPath());
				}
				
				selectItemIfSelectedPathOrFileVersion(treeItem, fileVersion);
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
				
				selectItemIfSelectedPathOrFileVersion(treeItem, fileVersion);
			}
		}
	}		

	private void selectItemIfSelectedPathOrFileVersion(TreeItem treeItem, FileVersion fileVersion) {
		if (model.getSelectedFileHistoryId() != null && model.getSelectedFileHistoryId().equals(fileVersion.getFileHistoryId())) {
			fileTree.setSelection(treeItem);
		}
		else if (model.getSelectedFilePath() != null && model.getSelectedFilePath().equals(fileVersion.getPath())) {
			fileTree.setSelection(treeItem);
		}
	}	

	private void selectItemIfSelectedPathOrFileVersion() {
		if (model.getSelectedFilePath() != null) {
			selectItemByPath(model.getSelectedFilePath());
		}		
	}

	private void selectItemByPath(String searchPath) {
		TreeItem treeItem = findItemByPath(searchPath);
		
		if (treeItem != null) {
			fileTree.setSelection(treeItem);
		}
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
		if ("".equals(searchPath)) {
			return null;
		}
		else {
			if (searchPath.endsWith("/")) {
				searchPath = searchPath.substring(0, searchPath.length() - 1);
			}
			
			TreeItem[] treeItems = fileTree.getItems();
			return findItemByPath(searchPath, treeItems);			
		}		
	}

	private TreeItem findItemByPath(String searchPath, TreeItem[] treeItems) {
		for (int i = 0; i < treeItems.length; i++) {
			TreeItem treeItem = treeItems[i];
			
			if (!isRetrievingItem(treeItem)) {
				FileVersion fileVersion = (FileVersion) treeItem.getData();
								
				if (fileVersion.getPath().equals(searchPath)) {
					return treeItem;
				}
				else if (treeItem.getItemCount() > 0) {
					TreeItem searchItem = findItemByPath(searchPath, treeItem.getItems());
					
					if (searchItem != null) {
						return searchItem;
					}
				}
			}
		}			
			
		return null;
	}
	
	private boolean isRetrievingItem(TreeItem treeItem) {
		return RETRIEVING_LIST_IDENTIFIER.equals(treeItem.getData());		
	}
	
	public boolean hasRetrievingChildItem(TreeItem treeItem) {
		return treeItem.getItemCount() == 1 && isRetrievingItem(treeItem.getItems()[0]);
	}

	public void setTreeEnabled(boolean enabled) {
		fileTree.setEnabled(enabled);		
	}
	
	private void sendLsRequest(String pathExpression) {
		// Date
		Date browseDate = (model.getSelectedDate() != null) ? model.getSelectedDate() : new Date();
		
		// Create list request
		LsOperationOptions lsOptions = new LsOperationOptions();
		
		lsOptions.setPathExpression(pathExpression);
		lsOptions.setDate(browseDate);
		lsOptions.setRecursive(false);
		lsOptions.setFetchHistories(false);
		lsOptions.setFileTypes(Sets.newHashSet(FileType.FILE, FileType.FOLDER, FileType.SYMLINK));
		
		LsFolderRequest lsRequest = new LsFolderRequest();
		
		lsRequest.setRoot(model.getSelectedRoot());
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
				fileTreeComposite.setTreeEnabled(true);
				
				LsFolderRequest lsRequest = pendingLsFolderRequests.remove(lsResponse.getRequestId());
				
				if (lsRequest != null) {
					fileTreeComposite.updateTree(lsRequest, lsResponse);
				}
			}
		});		
	}

	@Subscribe
	public void onModelSelectedRootUpdatedEvent(ModelSelectedRootUpdatedEvent event) {
		fileTree.removeAll();	
		sendLsRequest("");
	}

	public void refreshTree(String pathExpression) {
		if ("".equals(pathExpression)) {
			throw new IllegalArgumentException();
		}
				
		logger.log(Level.INFO, "Refreshing tree at " + pathExpression + " ...");	
		
		// Remember this as expanded path
		model.getExpandedFilePaths().add(pathExpression);
		
		// Find all sub-paths, a/b/c/ -> [a, a/b, a/b/c]
		List<String> allPaths = getPaths(pathExpression + "/");
		List<String> notLoadedPaths = new ArrayList<>();
		
		for (String path : allPaths) {
			TreeItem treeItem = fileTreeComposite.findItemByPath(path);
			
			if (!notLoadedPaths.isEmpty()) {
				notLoadedPaths.add(path);
				logger.log(Level.INFO, "- Item '" + path + "' has not been loaded (2).");					
			}
			else if (treeItem != null) {			
				if (fileTreeComposite.hasRetrievingChildItem(treeItem)) {
					notLoadedPaths.add(path);
					logger.log(Level.INFO, "- Item '" + path + "' has not been loaded (1).");					
				}
			}
		}
		
		// If items unloaded: set 'select after load' item, and send load requests
		if (!notLoadedPaths.isEmpty()) {
			for (String path : notLoadedPaths) {
				sendLsRequest(path + "/");
			}			
		}
		else {
			selectItemByPath(pathExpression);
		}
	}
	
	public void onDoubleClickItem(FileVersion fileVersion) {
		historyDialog.showDetails(model.getSelectedRoot(), fileVersion.getFileHistoryId());
	}

	public void onSelectItem(FileVersion fileVersion) {
		model.setSelectedFileHistoryId(fileVersion.getFileHistoryId());
	}

	public void onExpandItem(FileVersion fileVersion) {
		refreshTree(fileVersion.getPath());
	}

	public void onCollapseItem(final FileVersion fileVersion) {
		// Remove all children items from saved expanded paths
		Iterables.removeIf(model.getExpandedFilePaths(), new Predicate<String>() {
			@Override
			public boolean apply(String expandedPath) {				
				return expandedPath.startsWith(fileVersion.getPath());
			}			
		});		
	}
		
	private List<String> getPaths(String pathExpression) {
		List<String> paths = new ArrayList<>();
		int previousIndexOf = -1;
		
		while (-1 != (previousIndexOf = pathExpression.indexOf('/', previousIndexOf + 1))) {
			paths.add(pathExpression.substring(0, previousIndexOf));
		}
		
		return paths;
	}	

	
}
