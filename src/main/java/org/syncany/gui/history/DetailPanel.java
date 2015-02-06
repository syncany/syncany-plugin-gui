package org.syncany.gui.history;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.syncany.config.GuiEventBus;
import org.syncany.database.FileVersion;
import org.syncany.database.FileVersion.FileStatus;
import org.syncany.database.FileVersion.FileType;
import org.syncany.database.PartialFileHistory;
import org.syncany.database.PartialFileHistory.FileHistoryId;
import org.syncany.gui.Panel;
import org.syncany.gui.util.DesktopUtil;
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.SWTResourceManager;
import org.syncany.gui.util.WidgetDecorator;
import org.syncany.operations.daemon.messages.LsFolderRequest;
import org.syncany.operations.daemon.messages.LsFolderResponse;
import org.syncany.operations.daemon.messages.RestoreFolderRequest;
import org.syncany.operations.daemon.messages.RestoreFolderResponse;
import org.syncany.operations.ls.LsOperationOptions;
import org.syncany.operations.restore.RestoreOperationOptions;
import org.syncany.operations.restore.RestoreOperationResult;
import org.syncany.operations.restore.RestoreOperationResult.RestoreResultCode;
import org.syncany.util.EnvironmentUtil;
import org.syncany.util.FileUtil;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.eventbus.Subscribe;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class DetailPanel extends Panel {
	private static final Logger logger = Logger.getLogger(DetailPanel.class.getSimpleName());		
	private static final String IMAGE_RESOURCE_FORMAT = "/" + DetailPanel.class.getPackage().getName().replace('.', '/') + "/%s.png";
	private static final String IMAGE_LOADING_SPINNER_RESOURCE = "/" + DetailPanel.class.getPackage().getName().replace('.', '/') + "/loading-spinner.gif";
	private static final int IMAGE_LOADING_SPINNER_FRAME_RATE = 90; // ms per image
	
	private static final int RESTORE_FILENAME_SHORTENED_LENGTH = 50;
	
	private static final int COLUMN_INDEX_STATUS = 0;
	private static final int COLUMN_INDEX_PATH = 1;
	private static final int COLUMN_INDEX_VERSION = 2;
	private static final int COLUMN_INDEX_TYPE = 3;
	private static final int COLUMN_INDEX_SIZE = 4;
	private static final int COLUMN_INDEX_POSIX_PERMS = 5;
	private static final int COLUMN_INDEX_DOS_ATTRS = 6;
	private static final int COLUMN_INDEX_CHECKSUM = 7;
	private static final int COLUMN_INDEX_LAST_MODIFIED = 8;
	private static final int COLUMN_INDEX_UPDATED = 9;
	
	private HistoryModel historyModel;
	private HistoryDialog historyDialog;
	
	private Map<Integer, LsFolderRequest> pendingLsFolderRequests;
	private RestoreFolderRequest pendingRestoreRequest;	
	private GuiEventBus eventBus; 
	
	private Composite restoreStatusComposite;
	private ImageComposite restoreStatusIconComposite;
	private Label restoreStatusTextLabel;
	private Button restoreButton;		
	private Table historyTable;	
	
	private File restoredFile;
	private PartialFileHistory selectedFileHistory;

	public DetailPanel(Composite composite, int style, HistoryModel historyModel, HistoryDialog historyDialog) {
		super(composite, style);

		this.setBackgroundImage(null);
		this.setBackgroundMode(SWT.INHERIT_DEFAULT);
		
		this.historyModel = historyModel;
		this.historyDialog = historyDialog;
		
		this.pendingRestoreRequest = null;
		this.pendingLsFolderRequests = Maps.newConcurrentMap();
		this.eventBus = GuiEventBus.getAndRegister(this);
		
		this.restoreStatusIconComposite = null;
		this.restoreStatusTextLabel = null;
		this.restoreButton = null;			
		this.historyTable = null;
		
		this.createContents();
	}

	private void createContents() {
		createMainComposite();
		createButtons();
		createHistoryTable();
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
	
	private void createButtons() {
		Button backButton = new Button(this, SWT.NONE);
		backButton.setText(I18n.getText("org.syncany.gui.history.DetailPanel.button.back"));
		backButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));
		backButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				onClickBackButton();				
			}
		});
		
		GridData restoreStatusCompositeGridData = new GridData(SWT.CENTER, SWT.CENTER, true, false, 1, 1);
		restoreStatusCompositeGridData.verticalIndent = 3;
		
		restoreStatusComposite = new Composite(this, SWT.NONE);
		restoreStatusComposite.setLayout(new GridLayout(2, false));
		restoreStatusComposite.setLayoutData(restoreStatusCompositeGridData);
		
		restoreStatusIconComposite = new ImageComposite(restoreStatusComposite, SWT.NONE);
		restoreStatusIconComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		
		restoreStatusTextLabel = new Label(restoreStatusComposite, SWT.NONE);
		restoreStatusTextLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false, 1, 1));		
		restoreStatusTextLabel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseUp(MouseEvent e) {
				if (restoredFile != null) {
					DesktopUtil.launch(restoredFile.getAbsolutePath());
				}
			}
		});

		restoreButton = new Button(this, SWT.NONE);
		restoreButton.setEnabled(false);
		restoreButton.setText(I18n.getText("org.syncany.gui.history.DetailPanel.button.restore"));
		restoreButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		restoreButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				restoreSelectedFile();
			}
		});
	}

	private void createHistoryTable() {
		// Plugin list
		GridData pluginTableGridData = new GridData(SWT.FILL, SWT.FILL, true, true);
		pluginTableGridData.verticalIndent = 5;
		pluginTableGridData.horizontalIndent = 0;
		pluginTableGridData.horizontalSpan = 3;
		
	    historyTable = new Table(this, SWT.BORDER | SWT.FULL_SELECTION);
		historyTable.setHeaderVisible(true);
		historyTable.setLayoutData(pluginTableGridData);
		
		if (EnvironmentUtil.isWindows()) {
			historyTable.setBackground(WidgetDecorator.WHITE);
		}
		
		historyTable.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				TableItem tableItem = (TableItem) e.item;
				FileVersion fileVersion = (FileVersion) tableItem.getData();
				
				boolean isLastVersion = fileVersion.equals(selectedFileHistory.getLastVersion());				
				restoreButton.setEnabled(!isLastVersion);
			}
		});
		
		historyTable.addControlListener(new ControlAdapter() {			
			@Override
			public void controlResized(ControlEvent e) {
				resizeColumns();
			}			
		});
		
		// When reordering/adding columns, make sure to adjust the constants!
		// e.g TABLE_COLUMN_REMOTE_VERSION, ...

		TableColumn columnStatus = new TableColumn(historyTable, SWT.LEFT);
		columnStatus.setWidth(30);

		TableColumn columnPath = new TableColumn(historyTable, SWT.NONE);
		columnPath.setText(I18n.getText("org.syncany.gui.history.DetailPanel.table.path"));
		columnPath.setWidth(210);

		TableColumn columnVersion = new TableColumn(historyTable, SWT.NONE);
		columnVersion.setText(I18n.getText("org.syncany.gui.history.DetailPanel.table.version"));
		columnVersion.setWidth(30);

		TableColumn columnType = new TableColumn(historyTable, SWT.LEFT);
		columnType.setText(I18n.getText("org.syncany.gui.history.DetailPanel.table.type"));
		columnType.setWidth(60);

		TableColumn columnSize = new TableColumn(historyTable, SWT.LEFT);
		columnSize.setText(I18n.getText("org.syncany.gui.history.DetailPanel.table.size"));
		columnSize.setWidth(70);

		TableColumn columnPosixPermissions = new TableColumn(historyTable, SWT.LEFT);
		columnPosixPermissions.setText(I18n.getText("org.syncany.gui.history.DetailPanel.table.posixPermissions"));
		columnPosixPermissions.setWidth(70);

		TableColumn columnDosAttributes = new TableColumn(historyTable, SWT.LEFT);
		columnDosAttributes.setText(I18n.getText("org.syncany.gui.history.DetailPanel.table.dosAttributes"));
		columnDosAttributes.setWidth(70);

		TableColumn columnChecksum = new TableColumn(historyTable, SWT.LEFT);
		columnChecksum.setText(I18n.getText("org.syncany.gui.history.DetailPanel.table.checksum"));
		columnChecksum.setWidth(200);

		TableColumn columnLastModified = new TableColumn(historyTable, SWT.LEFT);
		columnLastModified.setText(I18n.getText("org.syncany.gui.history.DetailPanel.table.lastModified"));
		columnLastModified.setWidth(130);

		TableColumn columnUpdated = new TableColumn(historyTable, SWT.LEFT);
		columnUpdated.setText(I18n.getText("org.syncany.gui.history.DetailPanel.table.updated"));
		columnUpdated.setWidth(130);
	}
	
	public void updateTable(LsFolderRequest lsRequest, LsFolderResponse lsResponse) {
		logger.log(Level.INFO, "Updating detail panel table with " + lsResponse.getResult().getFileVersions().size() + " file versions ...");
		
		historyTable.removeAll();
		
		List<PartialFileHistory> fileVersions = new ArrayList<>(lsResponse.getResult().getFileVersions().values());
		selectedFileHistory = fileVersions.get(0);
		
		for (FileVersion fileVersion : selectedFileHistory.getFileVersions().values()) {
			String checksumStr = (fileVersion.getChecksum() != null) ? fileVersion.getChecksum().toString() : "";
			
			TableItem tableItem = new TableItem(historyTable, SWT.NONE);
			
			tableItem.setData(fileVersion);			
			tableItem.setImage(COLUMN_INDEX_STATUS, getStatusImage(fileVersion.getStatus()));
			tableItem.setText(COLUMN_INDEX_PATH, fileVersion.getPath());
			tableItem.setText(COLUMN_INDEX_VERSION, Long.toString(fileVersion.getVersion()));
			tableItem.setText(COLUMN_INDEX_TYPE, fileVersion.getType().toString());
			tableItem.setText(COLUMN_INDEX_SIZE, FileUtil.formatFileSize(fileVersion.getSize()));
			tableItem.setText(COLUMN_INDEX_POSIX_PERMS, fileVersion.getPosixPermissions());
			tableItem.setText(COLUMN_INDEX_DOS_ATTRS, fileVersion.getDosAttributes());
			tableItem.setText(COLUMN_INDEX_CHECKSUM, checksumStr);
			tableItem.setText(COLUMN_INDEX_LAST_MODIFIED, ""+fileVersion.getLastModified());
			tableItem.setText(COLUMN_INDEX_UPDATED, ""+fileVersion.getUpdated());		
		}
		
		if (historyTable.getItemCount() > 0) {
			restoreButton.setEnabled(false);
			historyTable.select(historyTable.getItemCount()-1);
		}
		
		resizeColumns();		
	}
	
	private void resizeColumns() {
		for (TableColumn tableColumn : historyTable.getColumns()) {
			tableColumn.pack();
		}
		
		historyTable.layout();
	}
	
	private Image getStatusImage(FileStatus status) {
		switch (status) {
		case NEW:
			return SWTResourceManager.getImage(String.format(IMAGE_RESOURCE_FORMAT, "add"));
			
		case CHANGED:
		case RENAMED:
			return SWTResourceManager.getImage(String.format(IMAGE_RESOURCE_FORMAT, "edit"));

		case DELETED:
			return SWTResourceManager.getImage(String.format(IMAGE_RESOURCE_FORMAT, "delete"));

		default:
			return null;				
		}
	}

	private void restoreSelectedFile() {
		TableItem[] selectedItems = historyTable.getSelection();
		
		if (selectedItems.length > 0) {
			TableItem tableItem = selectedItems[0];
			FileVersion fileVersion = (FileVersion) tableItem.getData();
			
			onClickRestoreButton(fileVersion);			
		}		
	}	

	@Override
	public boolean validatePanel() {
		return true;
	}
	
	public void sendLsFolderRequest(String root, FileHistoryId fileHistoryId) {
		// Create list request
		LsOperationOptions lsOptions = new LsOperationOptions();
		
		lsOptions.setPathExpression(fileHistoryId.toString());
		lsOptions.setFileHistoryId(true);
		lsOptions.setRecursive(false);
		lsOptions.setDeleted(true);
		lsOptions.setFetchHistories(true);
		lsOptions.setFileTypes(Sets.newHashSet(FileType.FILE, FileType.SYMLINK));
		
		LsFolderRequest lsRequest = new LsFolderRequest();
		
		lsRequest.setRoot(root);
		lsRequest.setOptions(lsOptions);
		
		logger.log(Level.INFO, "History detail panel: Sending LsRequest with ID #" + lsRequest.getId() + " for " + root + " ...");

		// Send request
		pendingLsFolderRequests.put(lsRequest.getId(), lsRequest);
		eventBus.post(lsRequest);
	}
	
	@Subscribe
	public void onLsFolderResponse(final LsFolderResponse lsResponse) {
		logger.log(Level.INFO, "History detail panel: LsResponse received for request #" + lsResponse.getRequestId() + ".");

		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				LsFolderRequest lsRequest = pendingLsFolderRequests.remove(lsResponse.getRequestId());
				
				if (lsRequest != null) {
					updateTable(lsRequest, lsResponse);
				}
			}
		});		
	}
	
	public void onClickBackButton() {
		historyDialog.showMainPanel();
	}

	public void onClickRestoreButton(FileVersion fileVersion) {
		// Set labels/status
		String shortFileName = shortenFileName(fileVersion.getPath());
		String versionStr = Long.toString(fileVersion.getVersion());
		
		restoreButton.setEnabled(false);
		
		restoreStatusIconComposite.setAnimatedImage(IMAGE_LOADING_SPINNER_RESOURCE, IMAGE_LOADING_SPINNER_FRAME_RATE);
		restoreStatusTextLabel.setText(I18n.getText("org.syncany.gui.history.DetailPanel.label.fileRestoreOngoing", shortFileName, versionStr));
		restoreStatusTextLabel.setCursor(new Cursor(Display.getDefault(), SWT.CURSOR_ARROW));
		restoreStatusTextLabel.setToolTipText("");
		
		restoredFile = null;
		
		layout();
		
		// Send restore request
		RestoreOperationOptions restoreOptions = new RestoreOperationOptions();
		restoreOptions.setFileHistoryId(fileVersion.getFileHistoryId());
		restoreOptions.setFileVersion(fileVersion.getVersion().intValue());
		
		pendingRestoreRequest = new RestoreFolderRequest();
		pendingRestoreRequest.setRoot(historyModel.getSelectedRoot());
		pendingRestoreRequest.setOptions(restoreOptions);
		
		eventBus.post(pendingRestoreRequest);
	}
	
	private String shortenFileName(String path) {
		String baseName = new File(path).getName();
		return (baseName.length() >= RESTORE_FILENAME_SHORTENED_LENGTH) ? baseName.substring(0, RESTORE_FILENAME_SHORTENED_LENGTH-3) + "..." : baseName;
	}

	@Subscribe
	public void onRestoreResponseReceived(final RestoreFolderResponse restoreResponse) {
		logger.log(Level.INFO, "History detail panel: RestoreResponse received for request #" + restoreResponse.getRequestId() + ".");

		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				boolean restoreRequestMatches = pendingRestoreRequest != null 
						&& pendingRestoreRequest.getId() == restoreResponse.getRequestId();
				
				if (restoreRequestMatches) {
					updateRestoreStatus(pendingRestoreRequest, restoreResponse);
					pendingRestoreRequest = null;
				}
			}
		});		
	}
	
	private void updateRestoreStatus(RestoreFolderRequest restoreRequest, RestoreFolderResponse restoreResponse) {
		RestoreOperationResult restoreResult = restoreResponse.getResult(); 
		RestoreResultCode restoreResultCode = restoreResult.getResultCode();
		
		// Set labels/status
		restoreButton.setEnabled(true);
		
		if (restoreResultCode == RestoreResultCode.ACK) {
			String shortFileName = shortenFileName(restoreResult.getTargetFile().getAbsolutePath());			
			logger.log(Level.INFO, "History detail panel: Restore successful, file restored to " + restoreResult.getTargetFile().toString());
			
			restoreStatusIconComposite.setImage(SWTResourceManager.getImage(String.format(IMAGE_RESOURCE_FORMAT, "success")));
			restoreStatusTextLabel.setText(I18n.getText("org.syncany.gui.history.DetailPanel.label.fileRestoreSuccess", shortFileName));
			restoreStatusTextLabel.setCursor(new Cursor(Display.getDefault(), SWT.CURSOR_HAND));
			restoreStatusTextLabel.setToolTipText(restoreResult.getTargetFile().toString());
			
			restoredFile = restoreResult.getTargetFile();
		}
		else {
			logger.log(Level.WARNING, "History detail panel: Restore FAILED, error code " + restoreResultCode);
			
			restoreStatusIconComposite.setImage(SWTResourceManager.getImage(String.format(IMAGE_RESOURCE_FORMAT, "failure")));
			restoreStatusTextLabel.setText(I18n.getText("org.syncany.gui.history.DetailPanel.label.fileRestoreFailure"));
			restoreStatusTextLabel.setCursor(new Cursor(Display.getDefault(), SWT.CURSOR_ARROW));
			restoreStatusTextLabel.setToolTipText("");
			
			restoredFile = null;
		}
		
		layout();
	}

	public void dispose() {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {	
				eventBus.unregister(DetailPanel.this);								
			}
		});
	}
}
