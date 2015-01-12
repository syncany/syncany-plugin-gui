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
	
	private FileTreeCompositeListener listener;	
	private Tree fileTree;
	
	public FileTreeComposite(Composite parent, int style, FileTreeCompositeListener fileTreeListener) {
		super(parent, style);

		this.listener = fileTreeListener;				
		
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
					listener.onDoubleClickItem(fileVersion);
				}
			}
		}
	}

	private void selectItem(TreeItem treeItem) {
		if (!isRetrievingItem(treeItem)) {
			FileVersion fileVersion = (FileVersion) treeItem.getData();
			listener.onSelectItem(fileVersion);			
		}
	}

	private void expandTreeItem(TreeItem treeItem) {
		if (hasRetrievingChildItem(treeItem)) {
			FileVersion fileVersion = (FileVersion) treeItem.getData();		
			listener.onExpandItem(fileVersion);
		}
	}
	
	private void collapseTreeItem(TreeItem treeItem) {
		final FileVersion fileVersion = (FileVersion) treeItem.getData();
		listener.onCollapseItem(fileVersion);		
	}

	

	private void updateTree(LsFolderRequest lsRequest, LsFolderResponse lsResponse) {
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
				
				if (state.getExpandedFilePaths().contains(fileVersion.getPath())) {
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
		if (state.getSelectedFileHistoryId() != null && state.getSelectedFileHistoryId().equals(fileVersion.getFileHistoryId())) {
			fileTree.setSelection(treeItem);
		}
		else if (state.getSelectedFilePath() != null && state.getSelectedFilePath().equals(fileVersion.getPath())) {
			fileTree.setSelection(treeItem);
		}
	}	

	private void selectItemIfSelectedPathOrFileVersion() {
		if (state.getSelectedFilePath() != null) {
			selectItemByPath(state.getSelectedFilePath());
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

	private TreeItem findItemByPath(String searchPath) {				
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
	
	private boolean hasRetrievingChildItem(TreeItem treeItem) {
		return treeItem.getItemCount() == 1 && isRetrievingItem(treeItem.getItems()[0]);
	}
}
