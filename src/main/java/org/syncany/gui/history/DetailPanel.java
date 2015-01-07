package org.syncany.gui.history;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.TreeAdapter;
import org.eclipse.swt.events.TreeEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Slider;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.syncany.config.GuiEventBus;
import org.syncany.database.DatabaseVersionHeader;
import org.syncany.database.FileVersion;
import org.syncany.database.FileVersion.FileType;
import org.syncany.database.PartialFileHistory.FileHistoryId;
import org.syncany.gui.Panel;
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
import org.syncany.operations.plugin.ExtendedPluginInfo;
import org.syncany.util.EnvironmentUtil;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.eventbus.Subscribe;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class DetailPanel extends Panel {
	private static final String TREE_ICON_RESOURCE_FORMAT = "/" + DetailPanel.class.getPackage().getName().replace('.', '/') + "/%s.png";
	private static final Object RETRIEVING_LIST_IDENTIFIER = new Object();
	
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
		
	    TableColumn pluginTableColumnImage = new TableColumn(historyTable, SWT.CENTER);
	    pluginTableColumnImage.setWidth(30);
	    pluginTableColumnImage.setResizable(false);

	    TableColumn pluginTableColumnText = new TableColumn(historyTable, SWT.LEFT | SWT.FILL);
	    pluginTableColumnText.setText(I18n.getText("org.syncany.gui.preferences.PluginsPanel.table.plugin"));
	    pluginTableColumnText.setWidth(110);	    

	    TableColumn pluginTableColumnLocalVersion = new TableColumn(historyTable, SWT.LEFT);
	    pluginTableColumnLocalVersion.setText(I18n.getText("org.syncany.gui.preferences.PluginsPanel.table.localVersion"));
	    pluginTableColumnLocalVersion.setWidth(90);	    

	    TableColumn pluginTableColumnType = new TableColumn(historyTable, SWT.LEFT);
	    pluginTableColumnType.setText(I18n.getText("org.syncany.gui.preferences.PluginsPanel.table.type"));
	    pluginTableColumnType.setWidth(50);	    

	    TableColumn pluginTableColumnRemoteVersion = new TableColumn(historyTable, SWT.LEFT);
	    pluginTableColumnRemoteVersion.setText(I18n.getText("org.syncany.gui.preferences.PluginsPanel.table.remoteVersion"));
	    pluginTableColumnRemoteVersion.setWidth(90);
	    
	    TableColumn pluginTableColumnStatus = new TableColumn(historyTable, SWT.LEFT);
	    pluginTableColumnStatus.setText(I18n.getText("org.syncany.gui.preferences.PluginsPanel.table.status"));
	    pluginTableColumnStatus.setWidth(60);	    
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
		
		lsOptions.setPathExpression(fileHistoryId.toString());
		lsOptions.setDate(new Date());
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
				/*fileTree.setEnabled(true);
				
				LsFolderRequest lsRequest = pendingLsFolderRequests.get(lsResponse.getRequestId());
				
				if (lsRequest != null) {
					updateTree(lsRequest, lsResponse);
				}*/
			}
		});		
	}

	@Override
	public boolean validatePanel() {
		return true;
	}
}
