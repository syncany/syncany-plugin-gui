package org.syncany.gui.history;

import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.syncany.config.GuiEventBus;
import org.syncany.database.FileVersion;
import org.syncany.database.FileVersion.FileType;
import org.syncany.database.PartialFileHistory;
import org.syncany.database.PartialFileHistory.FileHistoryId;
import org.syncany.gui.Panel;
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.WidgetDecorator;
import org.syncany.operations.daemon.messages.LsFolderRequest;
import org.syncany.operations.daemon.messages.LsFolderResponse;
import org.syncany.operations.ls.LsOperationOptions;
import org.syncany.util.EnvironmentUtil;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.eventbus.Subscribe;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class DetailPanel extends Panel {
	private static final String TREE_ICON_RESOURCE_FORMAT = "/" + DetailPanel.class.getPackage().getName().replace('.', '/') + "/%s.png";
	
	private static final int COLUMN_INDEX_VERSION = 0;
	private static final int COLUMN_INDEX_PATH = 1;
	private static final int COLUMN_INDEX_TYPE = 2;
	private static final int COLUMN_INDEX_SIZE = 3;
	private static final int COLUMN_INDEX_POSIX_PERMS = 4;
	private static final int COLUMN_INDEX_DOS_ATTRS = 5;
	private static final int COLUMN_INDEX_CHECKSUM = 6;
	private static final int COLUMN_INDEX_LAST_MODIFIED = 7;
	private static final int COLUMN_INDEX_UPDATED = 8;
	
	private Table historyTable;	
	private Map<Integer, LsFolderRequest> pendingLsFolderRequests;

	private GuiEventBus eventBus;

	public DetailPanel(HistoryDialog parentDialog, Composite composite, int style) {
		super(parentDialog, composite, style);

		this.setBackgroundImage(null);
		this.setBackgroundMode(SWT.INHERIT_DEFAULT);
		
		this.historyTable = null;
		
		this.pendingLsFolderRequests = Maps.newConcurrentMap();

		this.eventBus = GuiEventBus.getInstance();
		this.eventBus.register(this);	
		
		this.createContents();
	}
	
	protected HistoryDialog getParentDialog() {
		return (HistoryDialog) parentDialog;
	}

	private void createContents() {
		createMainComposite();
		createBackButton();
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
	
	private void createBackButton() {
		Button backButton = new Button(this, SWT.NONE);
		backButton.setText("< Back");
		backButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1));
		backButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getParentDialog().showTree();
			}
		});
	}
	
	private void createHistoryTable() {
		// Plugin list
		GridData pluginTableGridData = new GridData(SWT.FILL, SWT.FILL, true, true);
		pluginTableGridData.verticalIndent = 5;
		pluginTableGridData.horizontalIndent = 0;
		pluginTableGridData.horizontalSpan = 2;
		
	    historyTable = new Table(this, SWT.BORDER | SWT.V_SCROLL | SWT.FULL_SELECTION);
		historyTable.setHeaderVisible(true);
		historyTable.setLayoutData(pluginTableGridData);
		
		if (EnvironmentUtil.isWindows()) {
			historyTable.setBackground(WidgetDecorator.WHITE);
		}
				
		// When reordering/adding columns, make sure to adjust the constants!
		// e.g TABLE_COLUMN_REMOTE_VERSION, ...
		
	    TableColumn columnVersion = new TableColumn(historyTable, SWT.CENTER);
	    columnVersion.setWidth(30);
	    columnVersion.setResizable(false);

	    TableColumn columnPath = new TableColumn(historyTable, SWT.LEFT | SWT.FILL);
	    columnPath.setText(I18n.getText("org.syncany.gui.history.DetailPanel.table.path"));
	    columnPath.setWidth(210);	    

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
	    columnChecksum.setWidth(180);

	    TableColumn columnLastModified = new TableColumn(historyTable, SWT.LEFT);
	    columnLastModified.setText(I18n.getText("org.syncany.gui.history.DetailPanel.table.lastModified"));
	    columnLastModified.setWidth(100);	    

	    TableColumn columnUpdated = new TableColumn(historyTable, SWT.LEFT);
	    columnUpdated.setText(I18n.getText("org.syncany.gui.history.DetailPanel.table.updated"));
	    columnUpdated.setWidth(100);	    
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
		// Create list request
		LsOperationOptions lsOptions = new LsOperationOptions();
		
		lsOptions.setFileHistoryPrefix(fileHistoryId.toString());
		lsOptions.setRecursive(false);
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
		
		for (PartialFileHistory partialFileHistory : lsResponse.getResult().getFileVersions().values()) {
			for (FileVersion fileVersion : partialFileHistory.getFileVersions().values()) {
				TableItem tableItem = new TableItem(historyTable, SWT.NONE);
				tableItem.setText(COLUMN_INDEX_VERSION, Long.toString(fileVersion.getVersion()));
				tableItem.setText(COLUMN_INDEX_PATH, fileVersion.getPath());
				tableItem.setText(COLUMN_INDEX_TYPE, fileVersion.getType().toString());
				tableItem.setText(COLUMN_INDEX_SIZE, ""+fileVersion.getSize());
				tableItem.setText(COLUMN_INDEX_POSIX_PERMS, fileVersion.getPosixPermissions());
				tableItem.setText(COLUMN_INDEX_DOS_ATTRS, fileVersion.getDosAttributes());
				tableItem.setText(COLUMN_INDEX_CHECKSUM, fileVersion.getChecksum().toString());
				tableItem.setText(COLUMN_INDEX_LAST_MODIFIED, ""+fileVersion.getLastModified());
				tableItem.setText(COLUMN_INDEX_UPDATED, ""+fileVersion.getUpdated());
			}
		}
	}

	@Override
	public boolean validatePanel() {
		return true;
	}
}
