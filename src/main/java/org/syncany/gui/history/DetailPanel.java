package org.syncany.gui.history;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
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
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.SWTResourceManager;
import org.syncany.gui.util.WidgetDecorator;
import org.syncany.operations.daemon.messages.LsFolderRequest;
import org.syncany.operations.daemon.messages.LsFolderResponse;
import org.syncany.operations.daemon.messages.RestoreFolderRequest;
import org.syncany.operations.daemon.messages.RestoreFolderResponse;
import org.syncany.operations.ls.LsOperationOptions;
import org.syncany.operations.restore.RestoreOperationOptions;
import org.syncany.util.EnvironmentUtil;
import org.syncany.util.FileUtil;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.eventbus.Subscribe;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class DetailPanel extends Panel {
	private static final String IMAGE_RESOURCE_FORMAT = "/" + DetailPanel.class.getPackage().getName().replace('.', '/') + "/%s.png";

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
	
	private DetailPanelListener listener;
	
	private Table historyTable;	
	private Button restoreButton;	

	public DetailPanel(Composite composite, int style, DetailPanelListener listener) {
		super(composite, style);

		this.setBackgroundImage(null);
		this.setBackgroundMode(SWT.INHERIT_DEFAULT);
		
		this.listener = listener;
		
		this.historyTable = null;
		this.restoreButton = null;			
		
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
		backButton.setText(I18n.getText("org.syncany.gui.history.DetailPanel.back"));
		backButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));
		backButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				listener.onClickBackButton();
			}
		});
		
		new Label(this, SWT.NONE);
		
		restoreButton = new Button(this, SWT.NONE);
		restoreButton.setEnabled(false);
		restoreButton.setText(I18n.getText("org.syncany.gui.history.DetailPanel.restore"));
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
	
	public void safeDispose() {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {	
				eventBus.unregister(DetailPanel.this);
			}
		});
	}		
	
	public void showDetails(String root, FileHistoryId fileHistoryId) {
		selectedRoot = root;
		
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
		
		// Send request
		pendingLsFolderRequests.put(lsRequest.getId(), lsRequest);
		eventBus.post(lsRequest);
	}
	
	@Subscribe
	public void onLsFolderResponse(final LsFolderResponse lsResponse) {
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

	private void updateTable(LsFolderRequest lsRequest, LsFolderResponse lsResponse) {
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
			
			listener.onClickRestoreButton(fileVersion);			
		}		
	}	

	@Override
	public boolean validatePanel() {
		return true;
	}
}
