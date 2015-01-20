package org.syncany.gui.history;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TreeItem;
import org.syncany.config.GuiEventBus;
import org.syncany.config.Logging;
import org.syncany.database.DatabaseVersionHeader;
import org.syncany.database.FileVersion;
import org.syncany.database.FileVersion.FileType;
import org.syncany.database.PartialFileHistory.FileHistoryId;
import org.syncany.gui.Dialog;
import org.syncany.gui.Panel;
import org.syncany.gui.util.DesktopUtil;
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.WidgetDecorator;
import org.syncany.operations.daemon.Watch;
import org.syncany.operations.daemon.messages.GetDatabaseVersionHeadersFolderResponse;
import org.syncany.operations.daemon.messages.ListWatchesManagementRequest;
import org.syncany.operations.daemon.messages.ListWatchesManagementResponse;
import org.syncany.operations.daemon.messages.LogFolderRequest;
import org.syncany.operations.daemon.messages.LogFolderResponse;
import org.syncany.operations.daemon.messages.LsFolderRequest;
import org.syncany.operations.daemon.messages.LsFolderResponse;
import org.syncany.operations.daemon.messages.RestoreFolderRequest;
import org.syncany.operations.log.LightweightDatabaseVersion;
import org.syncany.operations.log.LogOperationOptions;
import org.syncany.operations.ls.LsOperationOptions;
import org.syncany.operations.restore.RestoreOperationOptions;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.eventbus.Subscribe;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class HistoryDialog extends Dialog implements MainPanelListener, FileTreeCompositeListener, LogCompositeListener, DetailPanelListener {
	// Model
	private HistoryModel model;	
		
	// View
	private Shell windowShell;	
	private Composite stackComposite;
	private StackLayout stackLayout;
			
	private MainPanel mainPanel;
	private DetailPanel detailPanel;
	
	private Panel currentPanel;
		
	// Controller
	private ListWatchesManagementRequest pendingListWatchesRequest;
	private Map<Integer, LsFolderRequest> pendingLsFolderRequests;	
	private LogFolderRequest pendingLogFolderRequest;

	private GuiEventBus eventBus;	
	
	public HistoryDialog() {		
		this.model = new HistoryModel();
		
		this.windowShell = null;	
		
		this.pendingLsFolderRequests = Maps.newConcurrentMap();

		this.eventBus = GuiEventBus.getInstance();
		this.eventBus.register(this);					
	}

	public void open() {
		// Create controls
		createContents();
		buildPanels();
		
		setCurrentPanel(mainPanel);

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
		GridLayout shellGridLayout = new GridLayout(1, false);
		shellGridLayout.marginTop = 0;
		shellGridLayout.marginLeft = 0;
		shellGridLayout.marginHeight = 0;
		shellGridLayout.marginWidth = 0;
		shellGridLayout.horizontalSpacing = 0;
		shellGridLayout.verticalSpacing = 0;

		windowShell = new Shell(Display.getDefault(), SWT.SHELL_TRIM | SWT.DOUBLE_BUFFERED);
		windowShell.setToolTipText("");
		windowShell.setBackground(WidgetDecorator.COLOR_WIDGET);
		windowShell.setSize(900, 560);
		windowShell.setText(I18n.getText("org.syncany.gui.history.HistoryDialog.title"));
		windowShell.setLayout(shellGridLayout);		
		windowShell.addListener(SWT.Close, new Listener() {
			public void handleEvent(Event event) {
				safeDispose();
			}
		});
		
		createStackComposite();
	}		

	private void createStackComposite() {
		stackLayout = new StackLayout();
		stackLayout.marginHeight = 0;
		stackLayout.marginWidth = 0;
		
		GridData stackCompositeGridData = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
		stackCompositeGridData.minimumWidth = 500;

		stackComposite = new Composite(windowShell, SWT.DOUBLE_BUFFERED);
		stackComposite.setLayout(stackLayout);
		stackComposite.setLayoutData(stackCompositeGridData);
	}

	private void buildPanels() {
		mainPanel = new MainPanel(stackComposite, SWT.NONE, model, this, this, this);
		detailPanel = new DetailPanel(stackComposite, SWT.NONE, model, this);
	}

	public Shell getWindowShell() {
		return windowShell;
	}

	public void setCurrentPanel(final Panel newPanel) {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				currentPanel = newPanel;
				
				stackLayout.topControl = currentPanel;
				stackComposite.layout();	
				
				currentPanel.setFocus();
			}
		});
	}
	
	public void safeDispose() {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {	
				eventBus.unregister(HistoryDialog.this);
				
				if (!mainPanel.isDisposed()) {
					mainPanel.safeDispose();
				}	
				
				if (!windowShell.isDisposed()) {
					windowShell.dispose();
				}									
			}
		});
	}
	
	public void showDetails(String root, FileHistoryId fileHistoryId) {
		detailPanel.showDetails(root, fileHistoryId);
		setCurrentPanel(detailPanel);
	}

	public void showTree() {
		setCurrentPanel(mainPanel);
	}
	
	
	
	
	

	private void refreshRoots() {
		pendingListWatchesRequest = new ListWatchesManagementRequest();
		eventBus.post(pendingListWatchesRequest);		
	}


	@Subscribe
	public void onGetDatabaseVersionHeadersFolderResponse(final GetDatabaseVersionHeadersFolderResponse getHeadersResponse) {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				List<DatabaseVersionHeader> headers = getHeadersResponse.getDatabaseVersionHeaders();
				
				if (headers.size() > 0) {
					int maxValue = headers.size() - 1;
					Date newSelectedDate = headers.get(headers.size()-1).getDate();
					
					dateSlider.setData(headers);
					dateSlider.setMinimum(0);
					dateSlider.setMaximum(maxValue);
					dateSlider.setSelection(maxValue);
					dateSlider.setEnabled(true);
					
					state.setSelectedDate(newSelectedDate);	
					setDateLabel(newSelectedDate);
				}
				else {
					dateSlider.setMinimum(0);
					dateSlider.setMaximum(0);
					dateSlider.setEnabled(false);	
					
					state.setSelectedDate(null);
				}				
			}
		});
	}


	
	
	public void refreshTree(String file) {
		fileTreeComposite.refreshTree(file);
	}


	

	public void refreshTree(String pathExpression) {
		if ("".equals(pathExpression)) {
			throw new IllegalArgumentException();
		}
				
		logger.log(Level.INFO, "Refreshing tree at " + pathExpression + " ...");	
		
		// Remember this as expanded path
		state.getExpandedFilePaths().add(pathExpression);
		
		// Find all sub-paths, a/b/c/ -> [a, a/b, a/b/c]
		List<String> allPaths = getPaths(pathExpression + "/");
		List<String> notLoadedPaths = new ArrayList<>();
		
		for (String path : allPaths) {
			TreeItem treeItem = findItemByPath(path);
			
			if (!notLoadedPaths.isEmpty()) {
				notLoadedPaths.add(path);
				logger.log(Level.INFO, "- Item '" + path + "' has not been loaded (2).");					
			}
			else if (treeItem != null) {			
				if (hasRetrievingChildItem(treeItem)) {
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
	
	private void refreshRoot() {
		sendLsRequest("");
	}

	public void resetAndRefresh() {
		fileTree.removeAll();
		refreshRoot();
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
	

	public void resetAndRefresh() {
		resetAndRefresh(0);
	}
	
	public void resetAndRefresh(int startIndex) {
		LogOperationOptions logOptions = new LogOperationOptions();
		logOptions.setMaxDatabaseVersionCount(LOG_REQUEST_DATABASE_COUNT);
		logOptions.setStartDatabaseVersionIndex(startIndex);
		logOptions.setMaxFileHistoryCount(LOG_REQUEST_FILE_COUNT);
		
		pendingLogFolderRequest = new LogFolderRequest();
		pendingLogFolderRequest.setRoot(state.getSelectedRoot());
		pendingLogFolderRequest.setOptions(logOptions);
		
		eventBus.post(pendingLogFolderRequest);
	}
	

	@Subscribe
	public void onLogFolderResponse(final LogFolderResponse logResponse) {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				if (pendingLogFolderRequest != null && pendingLogFolderRequest.getId() == logResponse.getRequestId()) {
					updateTabs(pendingLogFolderRequest, logResponse);
					pendingLogFolderRequest = null;
				}				
			}
		});		
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
						state.setSelectedRoot(watches.get(0).getFolder().getAbsolutePath());
						rootSelectCombo.select(0);
						
						refreshDateSlider();
						fileTreeComposite.resetAndRefresh();
						logComposite.resetAndRefresh();
					}
				}
			});
		}
	}
	
	

	@Override
	public void onClickBackButton() {
		setCurrentPanel(mainPanel);
	}

	@Override
	public void onClickRestoreButton(FileVersion fileVersion) {
		RestoreOperationOptions restoreOptions = new RestoreOperationOptions();
		restoreOptions.setFileHistoryId(fileVersion.getFileHistoryId());
		restoreOptions.setFileVersion(fileVersion.getVersion().intValue());
		
		RestoreFolderRequest restoreRequest = new RestoreFolderRequest();
		restoreRequest.setRoot(selectedRoot);
		restoreRequest.setOptions(restoreOptions);
		
		eventBus.post(restoreRequest);
	}

	@Override
	public void onSelectDatabaseVersion(LightweightDatabaseVersion databaseVersion) {
		mainPanel.setSelectedDate(databaseVersion.getDate());
	}
	
	@Override
	public void onDoubleClickDatabaseVesion(LightweightDatabaseVersion databaseVersion) {
		mainPanel.showTree();
	}

	@Override
	public void onFileJumpToDetail(LightweightDatabaseVersion databaseVersion, String relativeFilePath) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onFileJumpToTree(LightweightDatabaseVersion databaseVersion, String relativeFilePath) {
		mainPanelState.setSelectedFilePath(relativeFilePath);
		
		mainPanel.setSelectedDate(databaseVersion.getDate());
		mainPanel.refreshTree(relativeFilePath);
		
		mainPanel.showTree();				

	}

	@Override
	public void onFileOpen(LightweightDatabaseVersion databaseVersion, String relativeFilePath) {
		final File file = new File(root, relativeFilePath);
		launchOrDisplayError(file);
	}

	@Override
	public void onFileOpenContainingFolder(LightweightDatabaseVersion databaseVersion, String relativeFilePath) {
		final File file = new File(root, relativeFilePath);
		launchOrDisplayError(file.getParentFile());
	}

	@Override
	public void onFileCopytoClipboard(LightweightDatabaseVersion databaseVersion, String relativeFilePath) {
		final File file = new File(root, relativeFilePath);
		DesktopUtil.copyToClipboard(file.getAbsolutePath());
	}

	@Override
	public void onDoubleClickItem(FileVersion fileVersion) {
		showDetails(root, fileHistoryId);
	}

	@Override
	public void onSelectItem(FileVersion fileVersion) {
		state.setSelectedFileHistoryId(fileVersion.getFileHistoryId());
	}

	@Override
	public void onExpandItem(FileVersion fileVersion) {
		refreshTree(fileVersion.getPath());
	}

	@Override
	public void onCollapseItem(FileVersion fileVersion) {
		// Remove all children items from saved expanded paths
		Iterables.removeIf(state.getExpandedFilePaths(), new Predicate<String>() {
			@Override
			public boolean apply(String expandedPath) {				
				return expandedPath.startsWith(fileVersion.getPath());
			}			
		});		
	}

	@Override
	public void onDateChanged(Date newDate) {
		boolean listUpdateRequired = !newDate.equals(state.getSelectedDate());
			
		if (listUpdateRequired) {
			setSelectedDate(newDate);							
		}			
	}

	@Override
	public void onRootChanged(String newRoot) {

		state.setSelectedRoot(watches.get(selectionIndex).getFolder().getAbsolutePath());
		state.setSelectedDate(null);
		state.setSelectedFileHistoryId(null);
		state.getExpandedFilePaths().clear();
		
		refreshDateSlider();
		
		fileTreeComposite.resetAndRefresh();
		logComposite.resetAndRefresh();
	}
	
	
	private void launchOrDisplayError(File file) {
		if (file.exists()) {
			DesktopUtil.launch(file.getAbsolutePath());	
		}
		else {
			MessageBox messageBox = new MessageBox(getShell(), SWT.ICON_WARNING | SWT.OK);	        
	        messageBox.setText(I18n.getText("org.syncany.gui.history.LogTabComposite.warningNotExist.title"));
	        messageBox.setMessage(I18n.getText("org.syncany.gui.history.LogTabComposite.warningNotExist.description", file.getAbsolutePath()));
	        
	        messageBox.open();
		}
	}


}
