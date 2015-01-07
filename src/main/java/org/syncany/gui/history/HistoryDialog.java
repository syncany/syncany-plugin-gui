package org.syncany.gui.history;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.TreeAdapter;
import org.eclipse.swt.events.TreeEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Slider;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.syncany.config.GuiEventBus;
import org.syncany.config.Logging;
import org.syncany.database.DatabaseVersionHeader;
import org.syncany.database.FileVersion;
import org.syncany.database.FileVersion.FileType;
import org.syncany.gui.Dialog;
import org.syncany.gui.util.DesktopUtil;
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.SWTResourceManager;
import org.syncany.gui.util.WidgetDecorator;
import org.syncany.operations.daemon.Watch;
import org.syncany.operations.daemon.messages.GetDatabaseVersionHeadersFolderRequest;
import org.syncany.operations.daemon.messages.GetDatabaseVersionHeadersFolderResponse;
import org.syncany.operations.daemon.messages.ListWatchesManagementRequest;
import org.syncany.operations.daemon.messages.ListWatchesManagementResponse;
import org.syncany.operations.daemon.messages.LsFolderRequest;
import org.syncany.operations.daemon.messages.LsFolderResponse;
import org.syncany.operations.ls.LsOperationOptions;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.eventbus.Subscribe;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class HistoryDialog extends Dialog {
	private static final String TREE_ICON_RESOURCE_FORMAT = "/" + HistoryDialog.class.getPackage().getName().replace('.', '/') + "/%s.png";
	private static final Object RETRIEVING_LIST_IDENTIFIER = new Object();
	
	private Shell trayShell;
	private Shell windowShell;
	
	private String selectedRoot;
	private Date selectedDate;
	
	private Combo rootSelectCombo;
	private Slider dateSlider;
	private Tree fileBrowserTree;
	
	private ListWatchesManagementRequest pendingListWatchesRequest;
	private Map<Integer, LsFolderRequest> pendingLsFolderRequests;

	private GuiEventBus eventBus;

	public static void main(String[] a) {
		Logging.init();
		
		String intlPackage = I18n.class.getPackage().getName().replace(".", "/");
		I18n.registerBundleName(intlPackage + "/i18n/messages");

		Shell shell = new Shell();

		HistoryDialog dialog = new HistoryDialog(shell);
		dialog.open();

		shell.dispose();
	}

	public HistoryDialog(Shell trayShell) {
		this.trayShell = trayShell;
		this.windowShell = null;
		
		this.selectedRoot = null;
		this.selectedDate = null;
		
		this.pendingListWatchesRequest = null;
		this.pendingLsFolderRequests = Maps.newConcurrentMap();
		
		this.eventBus = GuiEventBus.getInstance();
		this.eventBus.register(this);		
	}

	public void open() {
		// Create controls
		createContents();
		refreshRoots();
		
		// Open shell
		DesktopUtil.centerOnScreen(windowShell);

		windowShell.open();
		windowShell.layout();

		// Dispatch loop
		Display display = Display.getDefault();
	
		while (!windowShell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}		
	}

	/**
	 * Create contents of the dialog.
	 */
	private void createContents() {
		GridLayout shellGridLayout = new GridLayout(2, false);
		shellGridLayout.marginTop = 0;
		shellGridLayout.marginLeft = 0;
		shellGridLayout.marginHeight = 0;
		shellGridLayout.marginWidth = 0;
		shellGridLayout.horizontalSpacing = 0;
		shellGridLayout.verticalSpacing = 0;

		windowShell = new Shell(trayShell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.DOUBLE_BUFFERED);
		windowShell.setToolTipText("");
		windowShell.setBackground(WidgetDecorator.COLOR_WIDGET);
		windowShell.setSize(640, 480);
		windowShell.setText(I18n.getText("org.syncany.gui.history.HistoryDialog.title"));
		windowShell.setLayout(shellGridLayout);		
		windowShell.addListener(SWT.Close, new Listener() {
			public void handleEvent(Event event) {
				safeDispose();
			}
		});
		
		// Navigation table (row 1, column 1) and stack composite (row 1, column 2)
		createRootSelectionCombo();
		createDateSlider();
		createFileBrowserTree();
	}	

	private void createRootSelectionCombo() {
		rootSelectCombo = new Combo(windowShell, SWT.NONE);
		
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
						
						fileBrowserTree.removeAll();
						refreshTree("");
					}
				}
			}
		});
	}
	
	private void createDateSlider() {
		dateSlider = new Slider(windowShell, SWT.HORIZONTAL);
		
		dateSlider.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		dateSlider.setEnabled(false);
		
		dateSlider.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				System.out.println(dateSlider.getSelection());
			}
		});		
	}
	
	private void createFileBrowserTree() {
		fileBrowserTree = new Tree(windowShell, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		
		fileBrowserTree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
		fileBrowserTree.setEnabled(false);		

		fileBrowserTree.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				if (event.detail == SWT.CHECK) {
					System.out.println(event.item + " was checked.");
				}
				else {
					System.out.println(event.item + " was selected");
				}
			}
		});
		
		fileBrowserTree.addTreeListener(new TreeAdapter() {
			public void treeExpanded(TreeEvent e) {
				TreeItem treeItem = (TreeItem) e.item;
				expandTreeItem(treeItem);				
			}
		});
	}	
	
	private void expandTreeItem(TreeItem treeItem) {
		if (treeItem.getItemCount() > 0) {
			TreeItem firstChildItem = treeItem.getItem(0);
			boolean isRetrievingItem = RETRIEVING_LIST_IDENTIFIER.equals(firstChildItem.getData());
			
			if (isRetrievingItem) {
				FileVersion fileVersion = (FileVersion) treeItem.getData();
				refreshTree(fileVersion.getPath());
			}
		}
	}

	public Shell getTrayShell() {
		return trayShell;
	}

	public Shell getWindowShell() {
		return windowShell;
	}

	public void safeDispose() {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {	
				if (!windowShell.isDisposed()) {
					windowShell.dispose();
				}
				
				eventBus.unregister(HistoryDialog.this);
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
						refreshTree("");
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
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				List<DatabaseVersionHeader> headers = getHeadersResponse.getDatabaseVersionHeaders();
					
				dateSlider.setData(headers);
				dateSlider.setMinimum(0);
				dateSlider.setMaximum(headers.size()-1);
				dateSlider.setSelection(headers.size()-1);
				dateSlider.setEnabled(true);				
			}
		});
	}

	private void refreshTree(String pathExpression) {
		// Adjust path expression
		if (!"".equals(pathExpression)) {
			pathExpression += "/";
		}
		
		// Create list request
		LsOperationOptions lsOptions = new LsOperationOptions();
		
		lsOptions.setPathExpression(pathExpression);
		lsOptions.setDate(new Date());
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
				fileBrowserTree.setEnabled(true);
				
				LsFolderRequest lsRequest = pendingLsFolderRequests.get(lsResponse.getRequestId());
				
				if (lsRequest != null) {
					updateTree(lsRequest, lsResponse);
				}
			}
		});		
	}

	private void updateTree(LsFolderRequest lsRequest, LsFolderResponse lsResponse) {
		Map<String, FileVersion> fileTree = lsResponse.getResult().getFileTree();
		
		// Find parent path (where to attach new items)
		TreeItem parentTreeItem = findTreeItemByPath(lsRequest.getOptions().getPathExpression());
		
		if (parentTreeItem != null) {
			parentTreeItem.removeAll();
		}
		
		// Create new items
		for (FileVersion fileVersion : fileTree.values()) {
			if (fileVersion.getType() == FileType.FOLDER) {
				TreeItem treeItem = createTreeItem(parentTreeItem);
				treeItem.setData(fileVersion);
				treeItem.setText(fileVersion.getName());	
				treeItem.setImage(SWTResourceManager.getImage(String.format(TREE_ICON_RESOURCE_FORMAT, "folder")));
				
				TreeItem retrieveListTreeItem = new TreeItem(treeItem, 0);
				retrieveListTreeItem.setData(RETRIEVING_LIST_IDENTIFIER);
				retrieveListTreeItem.setText(I18n.getText("org.syncany.gui.history.HistoryDialog.retrievingList"));
			}
		}
		
		for (FileVersion fileVersion : fileTree.values()) {
			if (fileVersion.getType() != FileType.FOLDER) {
				TreeItem treeItem = createTreeItem(parentTreeItem);
				treeItem.setData(fileVersion);
				treeItem.setText(fileVersion.getName());			
				treeItem.setImage(SWTResourceManager.getImage(String.format(TREE_ICON_RESOURCE_FORMAT, "file")));
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
			return new TreeItem(fileBrowserTree, SWT.NONE);
		}
	}

	private TreeItem findTreeItemByPath(String pathExpression) {
		if ("".equals(pathExpression)) {
			return null;
		}
		else {
			TreeItem[] treeItems = fileBrowserTree.getItems();
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
}
