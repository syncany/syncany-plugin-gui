/*
 * Syncany, www.syncany.org
 * Copyright (C) 2011-2013 Philipp C. Heckel <philipp.heckel@gmail.com> 
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.syncany.gui.preferences;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.syncany.config.GuiEventBus;
import org.syncany.gui.Panel;
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.SWTResourceManager;
import org.syncany.gui.util.WidgetDecorator;
import org.syncany.operations.daemon.messages.PluginManagementRequest;
import org.syncany.operations.daemon.messages.PluginManagementResponse;
import org.syncany.operations.plugin.ExtendedPluginInfo;
import org.syncany.operations.plugin.PluginInfo;
import org.syncany.operations.plugin.PluginOperationAction;
import org.syncany.operations.plugin.PluginOperationOptions;
import org.syncany.operations.plugin.PluginOperationOptions.PluginListMode;
import org.syncany.operations.plugin.PluginOperationResult;
import org.syncany.operations.plugin.PluginOperationResult.PluginResultCode;
import org.syncany.plugins.Plugin;
import org.syncany.plugins.gui.GuiPlugin;

import com.google.common.collect.Maps;
import com.google.common.eventbus.Subscribe;

public class PluginsPanel extends Panel {
	private static final Logger logger = Logger.getLogger(PluginsPanel.class.getSimpleName());		
	private static final String PLUGIN_ICON_RESOURCE_FORMAT = "/" + Plugin.class.getPackage().getName().replace('.', '/') + "/%s/icon24.png";
	private static final String PLUGIN_ACTION_RESOURCE_FORMAT = "/" + PreferencesDialog.class.getPackage().getName().replace('.', '/') + "/plugins-%s.png";
	
	private static final int TABLE_COLUMN_IMAGE = 0;
	private static final int TABLE_COLUMN_NAME = 1;
	private static final int TABLE_COLUMN_LOCAL_VERSION = 2;
	private static final int TABLE_COLUMN_TYPE = 3;
	private static final int TABLE_COLUMN_REMOTE_VERSION = 4;
	private static final int TABLE_COLUMN_STATUS = 5;
	
	private enum Action {
		INSTALL, UPDATE, REMOVE
	}	
	
	private static final Map<String, Action> pluginActions = Maps.newHashMap();
	private static final Map<String, String> pluginStatusTexts = Maps.newHashMap();
	
	private Table pluginTable;
	private Label statusLabel;	
	private Composite actionButtonComposite;
	
	private ExtendedPluginInfo selectedPlugin;
	private AtomicBoolean requestRunning;	
	
	private GuiEventBus eventBus;
	
	public PluginsPanel(PreferencesDialog parentDialog, Composite composite, int style) {
		super(parentDialog, composite, style);
		
		this.selectedPlugin = null;		
		this.requestRunning = new AtomicBoolean(false);

		this.eventBus = GuiEventBus.getInstance();
		this.eventBus.register(this);
		
		this.createControls();
	}
	
	private void createControls() {	
		createMainCompositeAndTitle();		

		createPluginTable();
		createStatusLabel();
		
	    createActionButtonComposite();
	    createActionButtons();
	    
	    refreshPluginList();	      	  
	}

	private void createMainCompositeAndTitle() {
		// Main composite
		GridLayout mainCompositeGridLayout = new GridLayout(2, false);
		mainCompositeGridLayout.marginTop = 15;
		mainCompositeGridLayout.marginLeft = 10;
		mainCompositeGridLayout.marginRight = 20;

		setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		setLayout(mainCompositeGridLayout);

		// Title and welcome text
		Label titleLabel = new Label(this, SWT.WRAP);
		titleLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 2, 1));
		titleLabel.setText(I18n.getText("org.syncany.gui.preferences.PluginsPanel.title"));

		WidgetDecorator.title(titleLabel);
	}

	private void createPluginTable() {
		// Plugin list
		GridData pluginTableGridData = new GridData(SWT.FILL, SWT.FILL, true, true);
		pluginTableGridData.verticalIndent = 5;
		pluginTableGridData.horizontalIndent = 0;
		pluginTableGridData.horizontalSpan = 2;
		
	    pluginTable = new Table(this, SWT.BORDER | SWT.V_SCROLL | SWT.FULL_SELECTION);
		pluginTable.setHeaderVisible(true);
		pluginTable.setBackground(WidgetDecorator.WHITE);
		pluginTable.setLayoutData(pluginTableGridData);
		
		pluginTable.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				// Update buttons and status text
				if (pluginTable.getSelectionIndex() >= 0) {
					TableItem tableItem = pluginTable.getItem(pluginTable.getSelectionIndex());
					ExtendedPluginInfo extPluginInfo = (ExtendedPluginInfo) tableItem.getData();
					
					updatePluginActionButtons(extPluginInfo);
					updateStatusText(extPluginInfo);
				}	
				
				// Fix flickering images
				pluginTable.redraw();
			}
		});			
		
		// Make table rows always '30 pixels' high
		pluginTable.addListener(SWT.MeasureItem, new Listener() {
			public void handleEvent(Event event) {				
				event.height = 30; 
			}
		});		
		
		// Fix flickering images (when scrolling)
		pluginTable.getVerticalBar().addSelectionListener(new SelectionAdapter() {			
			@Override
			public void widgetSelected(SelectionEvent e) {
				pluginTable.redraw();
			}			
		});
		
		// When reordering/adding columns, make sure to adjust the constants!
		// e.g TABLE_COLUMN_REMOTE_VERSION, ...
		
	    TableColumn pluginTableColumnImage = new TableColumn(pluginTable, SWT.CENTER);
	    pluginTableColumnImage.setWidth(30);
	    pluginTableColumnImage.setResizable(false);

	    TableColumn pluginTableColumnText = new TableColumn(pluginTable, SWT.LEFT | SWT.FILL);
	    pluginTableColumnText.setText(I18n.getText("org.syncany.gui.preferences.PluginsPanel.table.plugin"));
	    pluginTableColumnText.setWidth(110);	    

	    TableColumn pluginTableColumnLocalVersion = new TableColumn(pluginTable, SWT.LEFT);
	    pluginTableColumnLocalVersion.setText(I18n.getText("org.syncany.gui.preferences.PluginsPanel.table.localVersion"));
	    pluginTableColumnLocalVersion.setWidth(90);	    

	    TableColumn pluginTableColumnType = new TableColumn(pluginTable, SWT.LEFT);
	    pluginTableColumnType.setText(I18n.getText("org.syncany.gui.preferences.PluginsPanel.table.type"));
	    pluginTableColumnType.setWidth(50);	    

	    TableColumn pluginTableColumnRemoteVersion = new TableColumn(pluginTable, SWT.LEFT);
	    pluginTableColumnRemoteVersion.setText(I18n.getText("org.syncany.gui.preferences.PluginsPanel.table.remoteVersion"));
	    pluginTableColumnRemoteVersion.setWidth(90);
	    
	    TableColumn pluginTableColumnStatus = new TableColumn(pluginTable, SWT.LEFT);
	    pluginTableColumnStatus.setText(I18n.getText("org.syncany.gui.preferences.PluginsPanel.table.status"));
	    pluginTableColumnStatus.setWidth(60);	    
	}

	private void createStatusLabel() {
		GridLayout statusLabelRowLayout = new GridLayout();
		statusLabelRowLayout.marginTop = 9;
		statusLabelRowLayout.marginBottom = 6;

		GridData statusLabelGridData = new GridData(SWT.FILL, SWT.FILL, true, false);
		statusLabelGridData.horizontalSpan = 1;
		statusLabelGridData.verticalSpan = 1;
		statusLabelGridData.minimumWidth = 90;
		statusLabelGridData.minimumHeight = 38;

		Composite statusLabelComposite = new Composite(this, SWT.NONE);
		statusLabelComposite.setLayout(statusLabelRowLayout);
		statusLabelComposite.setLayoutData(statusLabelGridData);		
		
		statusLabel = new Label(statusLabelComposite, SWT.NONE);
		statusLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, true));
		
		setStatusText("");	
		
		statusLabelComposite.layout();
	}
	
	private void createActionButtonComposite() {		
		RowLayout buttonCompositeRowLayout = new RowLayout(SWT.HORIZONTAL);
		buttonCompositeRowLayout.marginTop = 15;
		buttonCompositeRowLayout.marginBottom = 15;
		
		GridData buttonCompositeGridData = new GridData(SWT.RIGHT, SWT.FILL, false, false);
		buttonCompositeGridData.horizontalSpan = 1;
		buttonCompositeGridData.verticalSpan = 1;

		actionButtonComposite = new Composite(this, SWT.NONE);
		actionButtonComposite.setLayout(buttonCompositeRowLayout);
		actionButtonComposite.setLayoutData(buttonCompositeGridData);					  		
	}	

	private void createActionButtons(Action... actions) {
		while (actionButtonComposite.getChildren().length > 0) {
			actionButtonComposite.getChildren()[0].dispose();
		}		
		
		List<Action> actionList = Arrays.asList(actions);
		
		if (actionList.contains(Action.UPDATE)) {			
			Button updatePluginButton = new Button(actionButtonComposite, SWT.NONE);
			updatePluginButton.setText(I18n.getText("org.syncany.gui.preferences.PluginsPanel.button.update"));

			updatePluginButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					clickUpdatePlugin();
				}
			});
		}	   
		
		if (actionList.contains(Action.REMOVE)) {
		    Button removePluginButton = new Button(actionButtonComposite, SWT.NONE);
		    removePluginButton.setText(I18n.getText("org.syncany.gui.preferences.PluginsPanel.button.remove"));
		    
		    removePluginButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					clickRemovePlugin();
				}
			});	
		}
		
		if (actionList.contains(Action.INSTALL)) {
			Button installPluginButton = new Button(actionButtonComposite, SWT.NONE);
			installPluginButton.setText(I18n.getText("org.syncany.gui.preferences.PluginsPanel.button.install"));
		    
		    installPluginButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					clickInstallPlugin();
				}
			});
		}
		   
	    Button installFromFilePluginButton = new Button(actionButtonComposite, SWT.NONE);
	    installFromFilePluginButton.setText(I18n.getText("org.syncany.gui.preferences.PluginsPanel.button.installFromFile"));
	    
	    installFromFilePluginButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				clickInstallFromFilePlugin();
			}
		});
	    
	    actionButtonComposite.getParent().layout();
	    actionButtonComposite.layout();
	    actionButtonComposite.redraw();
	}

	protected void clickInstallPlugin() {
		if (!requestRunning.get()) {
			requestRunning.set(true);
						
			PluginInfo pluginInfo = (selectedPlugin.isInstalled()) ? selectedPlugin.getLocalPluginInfo() : selectedPlugin.getRemotePluginInfo();			
			pluginStatusTexts.put(pluginInfo.getPluginId(), I18n.getText("org.syncany.gui.preferences.PluginsPanel.status.pluginInstalling", pluginInfo.getPluginName()));
			
			updatePluginActionButtons(selectedPlugin);
			updateStatusText(selectedPlugin);
			
			PluginOperationOptions pluginOperationOptions = new PluginOperationOptions();
			pluginOperationOptions.setAction(PluginOperationAction.INSTALL);
			pluginOperationOptions.setPluginId(selectedPlugin.getRemotePluginInfo().getPluginId());
			
		    eventBus.post(new PluginManagementRequest(pluginOperationOptions));			
		}
	}

	protected void clickInstallFromFilePlugin() {				
		if (!requestRunning.get()) {
			FileDialog fileDialog = new FileDialog(getShell(), SWT.OPEN);
			fileDialog.setFilterExtensions(new String[] { "*.jar" });
			
			String selectedFilePath = fileDialog.open();
			
			if (selectedFilePath != null && selectedFilePath.length() > 0) {
				requestRunning.set(true);

				File selectedFile = new File(selectedFilePath);		
				PluginInfo pluginInfo = (selectedPlugin.isInstalled()) ? selectedPlugin.getLocalPluginInfo() : selectedPlugin.getRemotePluginInfo();							
				pluginStatusTexts.put(pluginInfo.getPluginId(), I18n.getText("org.syncany.gui.preferences.PluginsPanel.status.pluginInstalling", selectedFile.getName()));

				updatePluginActionButtons(selectedPlugin);
				updateStatusText(selectedPlugin);
				
				PluginOperationOptions pluginOperationOptions = new PluginOperationOptions();
				pluginOperationOptions.setAction(PluginOperationAction.INSTALL);
				pluginOperationOptions.setPluginId(selectedFilePath);
				
			    eventBus.post(new PluginManagementRequest(pluginOperationOptions));					
			}			
		}		
	}
	
	protected void clickUpdatePlugin() {
		// TODO [medium] Waiting for cr0's issue
	}
	
	protected void clickRemovePlugin() {
		if (!requestRunning.get()) {
			requestRunning.set(true);
			
			PluginInfo pluginInfo = (selectedPlugin.isInstalled()) ? selectedPlugin.getLocalPluginInfo() : selectedPlugin.getRemotePluginInfo();			
			pluginStatusTexts.put(pluginInfo.getPluginId(), I18n.getText("org.syncany.gui.preferences.PluginsPanel.status.pluginRemoving", pluginInfo.getPluginName()));
			
			updatePluginActionButtons(selectedPlugin);
			updateStatusText(selectedPlugin);
			
			PluginOperationOptions pluginOperationOptions = new PluginOperationOptions();
			pluginOperationOptions.setAction(PluginOperationAction.REMOVE);
			pluginOperationOptions.setPluginId(selectedPlugin.getRemotePluginInfo().getPluginId());
			
		    eventBus.post(new PluginManagementRequest(pluginOperationOptions));
		}		
	}

	
	private void updatePluginActionButtons(ExtendedPluginInfo extPluginInfo) {
		selectedPlugin = extPluginInfo;
				
		if (requestRunning.get()) {
			logger.log(Level.FINE, "Request is in action, no buttons visible.");
			createActionButtons();
	    }
		else if (selectedPlugin != null) {
			PluginInfo pluginInfo = (extPluginInfo.isInstalled()) ? extPluginInfo.getLocalPluginInfo() : extPluginInfo.getRemotePluginInfo();
		    Action pluginUpdateAction = pluginActions.get(pluginInfo.getPluginId());	    	
			
		    if (pluginUpdateAction != null) {
				logger.log(Level.FINE, "Plugin '" + pluginInfo.getPluginId() + "' was changed. No actions possible.");
				createActionButtons();
		    }
		    else {
			    if (selectedPlugin.isInstalled()) {
					if (selectedPlugin.canUninstall()) {
						if (false /* Can update */) { 
							logger.log(Level.FINE, "Plugin '" + pluginInfo.getPluginId() + "' can be updated and removed.");
							createActionButtons(Action.UPDATE, Action.REMOVE);
						}
						else {
							logger.log(Level.FINE, "Plugin '" + pluginInfo.getPluginId() + "' can be removed (not updated).");
							createActionButtons(Action.REMOVE);
						}
					}
					else {
						logger.log(Level.FINE, "Plugin '" + pluginInfo.getPluginId() + "' cannot be uninstalled (or updated).");
						createActionButtons();
					}
				}
				else {
					logger.log(Level.FINE, "Plugin '" + pluginInfo.getPluginId() + "' is not installed, it can be installed..");
					createActionButtons(Action.INSTALL);
				}
		    }
		}
		else {
			logger.log(Level.FINE, "No plugin selected.");
			createActionButtons();
		}		
	}	
	
	protected void updateStatusText(ExtendedPluginInfo extPluginInfo) {
		PluginInfo pluginInfo = (extPluginInfo.isInstalled()) ? extPluginInfo.getLocalPluginInfo() : extPluginInfo.getRemotePluginInfo();
	    String pluginStatusText = pluginStatusTexts.get(pluginInfo.getPluginId());	    	
	    
	    if (pluginStatusText != null) {
	    	setStatusText(pluginStatusText);
	    }
	    else {
	    	setStatusText("");
	    }		
	}

	private void updatePluginTableEntry(PluginInfo updatedPluginInfo, Action updateAction) {
		// Update action log
		pluginActions.put(updatedPluginInfo.getPluginId(), updateAction);

		// Update table item
		for (TableItem tableItem : pluginTable.getItems()) {
			ExtendedPluginInfo extPluginInfo = (ExtendedPluginInfo) tableItem.getData();
			PluginInfo pluginInfo = (extPluginInfo.isInstalled()) ? extPluginInfo.getLocalPluginInfo() : extPluginInfo.getRemotePluginInfo();
			
			if (pluginInfo.getPluginId().equals(updatedPluginInfo.getPluginId())) {
				updateTableItemWithPluginAction(tableItem, updatedPluginInfo, extPluginInfo, updateAction);
			}
		}
	}
	
	private void updateTableItem(TableItem tableItem, ExtendedPluginInfo extPluginInfo) {
		PluginInfo pluginInfo = (extPluginInfo.isInstalled()) ? extPluginInfo.getLocalPluginInfo() : extPluginInfo.getRemotePluginInfo();		
	    Action pluginUpdateAction = pluginActions.get(pluginInfo.getPluginId());
	    	    
	    if (pluginUpdateAction != null) {
	    	updateTableItemWithPluginAction(tableItem, pluginInfo, extPluginInfo, pluginUpdateAction);	    	
	    }
	    else {
	    	updateTableItemFromExtPluginInfo(tableItem, pluginInfo, extPluginInfo);
	    }
	}

	private void updateTableItemWithPluginAction(TableItem tableItem, PluginInfo pluginInfo, ExtendedPluginInfo extPluginInfo,
			Action pluginUpdateAction) {
		
		// Get cell labels
		String remoteVersionStr = (extPluginInfo.isRemoteAvailable()) ? extPluginInfo.getRemotePluginInfo().getPluginVersion() : "";

		// Update cells
		tableItem.setImage(TABLE_COLUMN_IMAGE, getPluginImage(pluginInfo, extPluginInfo));
		tableItem.setText(TABLE_COLUMN_NAME, pluginInfo.getPluginName());		    
    	
    	switch (pluginUpdateAction) {
		case INSTALL:
		case UPDATE:			
			tableItem.setText(TABLE_COLUMN_LOCAL_VERSION, extPluginInfo.getRemotePluginInfo().getPluginVersion());
			tableItem.setText(TABLE_COLUMN_TYPE, I18n.getText("org.syncany.gui.preferences.PluginsPanel.table.pluginTypeUser"));
			tableItem.setText(TABLE_COLUMN_REMOTE_VERSION, remoteVersionStr);
			tableItem.setImage(TABLE_COLUMN_STATUS, SWTResourceManager.getImage(String.format(PLUGIN_ACTION_RESOURCE_FORMAT, "installed-restart-required")));
			
			break;

		case REMOVE:
			tableItem.setText(TABLE_COLUMN_LOCAL_VERSION, "");
			tableItem.setText(TABLE_COLUMN_TYPE, "");
			tableItem.setText(TABLE_COLUMN_REMOTE_VERSION, remoteVersionStr);
			tableItem.setImage(TABLE_COLUMN_STATUS, SWTResourceManager.getImage(String.format(PLUGIN_ACTION_RESOURCE_FORMAT, "removed-restart-required")));

			break;
		}    	
	}
	
	private void updateTableItemFromExtPluginInfo(TableItem tableItem, PluginInfo pluginInfo, ExtendedPluginInfo extPluginInfo) {
		// Get cell labels
		String localVersionStr = (extPluginInfo.isInstalled()) ? extPluginInfo.getLocalPluginInfo().getPluginVersion() : "";
		String remoteVersionStr = (extPluginInfo.isRemoteAvailable()) ? extPluginInfo.getRemotePluginInfo().getPluginVersion() : "";
		String typeStr = "";
		
		if (extPluginInfo.isInstalled()) {
			if (extPluginInfo.canUninstall()) {
				typeStr = I18n.getText("org.syncany.gui.preferences.PluginsPanel.table.pluginTypeUser");
			}
			else {
				typeStr = I18n.getText("org.syncany.gui.preferences.PluginsPanel.table.pluginTypeGlobal");
			}
		}
		
		// Update cells
		tableItem.setImage(TABLE_COLUMN_IMAGE, getPluginImage(pluginInfo, extPluginInfo));
		tableItem.setText(TABLE_COLUMN_NAME, pluginInfo.getPluginName());		    
	    tableItem.setText(TABLE_COLUMN_LOCAL_VERSION, localVersionStr);		    
	    tableItem.setText(TABLE_COLUMN_TYPE, typeStr);		    
	    tableItem.setText(TABLE_COLUMN_REMOTE_VERSION, remoteVersionStr);
	    
    	if (extPluginInfo.isInstalled()) {
    		if (false /*extPluginInfo.canUpdate()*/) {
    			tableItem.setImage(TABLE_COLUMN_STATUS, SWTResourceManager.getImage(String.format(PLUGIN_ACTION_RESOURCE_FORMAT, "updated")));
    		}
    		else {
    			tableItem.setImage(TABLE_COLUMN_STATUS, SWTResourceManager.getImage(String.format(PLUGIN_ACTION_RESOURCE_FORMAT, "installed")));
    		}
    	}
    	else {
    		tableItem.setImage(TABLE_COLUMN_STATUS, SWTResourceManager.getImage(String.format(PLUGIN_ACTION_RESOURCE_FORMAT, "removed")));   		
    	}    			   
	}

	private Image getPluginImage(PluginInfo pluginInfo, ExtendedPluginInfo extPluginInfo) {
		if (extPluginInfo.isInstalled()) {
			String pluginImageResource = String.format(PLUGIN_ICON_RESOURCE_FORMAT, pluginInfo.getPluginId());
			return SWTResourceManager.getImage(pluginImageResource);
		}
		else {
			return null;
		}
	}

	private void setStatusText(String status) {
		statusLabel.setText(status);

		statusLabel.getParent().layout();
		statusLabel.redraw();
	}
	

	private void refreshPluginList() {
		requestRunning.set(true);
		setStatusText(I18n.getText("org.syncany.gui.preferences.PluginsPanel.status.pluginRetrievingList"));

		while (pluginTable.getItemCount() > 0) {
			pluginTable.getItem(0).dispose();
		}				
	    
	    PluginOperationOptions pluginOperationOptions = new PluginOperationOptions();
		pluginOperationOptions.setAction(PluginOperationAction.LIST);
		pluginOperationOptions.setListMode(PluginListMode.ALL);
		
	    eventBus.post(new PluginManagementRequest(pluginOperationOptions));	    		  
	}
	
	@Subscribe
	public void onPluginResultReceived(PluginManagementResponse pluginResponse) {
		switch (pluginResponse.getResult().getAction()) {
		case LIST:
			onPluginListResponseReceived(pluginResponse);
			break;
			
		case INSTALL:
			onPluginInstallResponseReceived(pluginResponse);
			break;
			
		case REMOVE:
			onPluginRemoveResponseReceived(pluginResponse);
			break;				
		}
	}
	
	private void onPluginInstallResponseReceived(final PluginManagementResponse pluginResponse) {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {		
				PluginInfo pluginInfo = pluginResponse.getResult().getAffectedPluginInfo();				

				// Set status text
				if (pluginResponse.getResult().getResultCode() == PluginResultCode.OK) {
					pluginStatusTexts.put(pluginInfo.getPluginId(), I18n.getText("org.syncany.gui.preferences.PluginsPanel.status.pluginInstalled", pluginInfo.getPluginName()));
				}
				else {
					pluginStatusTexts.put(pluginInfo.getPluginId(), I18n.getText("org.syncany.gui.preferences.PluginsPanel.status.pluginNotInstalled", pluginInfo.getPluginName()));					
				}
				
				requestRunning.set(false);
				
				// Update table entry and buttons
				updatePluginTableEntry(pluginInfo, Action.INSTALL);
				updatePluginActionButtons(selectedPlugin);		
				updateStatusText(selectedPlugin);
			}			
		});		
	}

	private void onPluginRemoveResponseReceived(final PluginManagementResponse pluginResponse) {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				PluginInfo pluginInfo = pluginResponse.getResult().getAffectedPluginInfo();				
				
				// Set status text
				if (pluginResponse.getResult().getResultCode() == PluginResultCode.OK) {
					pluginStatusTexts.put(pluginInfo.getPluginId(), I18n.getText("org.syncany.gui.preferences.PluginsPanel.status.pluginRemoved", pluginInfo.getPluginName()));
				}
				else {
					pluginStatusTexts.put(pluginInfo.getPluginId(), I18n.getText("org.syncany.gui.preferences.PluginsPanel.status.pluginNotRemoved", pluginInfo.getPluginName()));					
				}				
				
				requestRunning.set(false);
				
				// Update table entry and buttons
				updatePluginTableEntry(pluginInfo, Action.REMOVE);
				updatePluginActionButtons(selectedPlugin);		
				updateStatusText(selectedPlugin);
			}
		});		
	}

	private void onPluginListResponseReceived(final PluginManagementResponse pluginResponse) {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				// Clear any items in there
				while (pluginTable.getItemCount() > 0) {
					pluginTable.getItem(0).dispose();
				}
				
				// Create new items
				PluginOperationResult pluginResult = pluginResponse.getResult();
				String guiPluginId = new GuiPlugin().getId();
				
				for (ExtendedPluginInfo extPluginInfo : pluginResult.getPluginList()) {						
					PluginInfo pluginInfo = (extPluginInfo.isInstalled()) ? extPluginInfo.getLocalPluginInfo() : extPluginInfo.getRemotePluginInfo();

					// Exclude GUI plugin
					if (guiPluginId.equals(pluginInfo.getPluginId())) {
						continue;
					}
										
					// Create table item
				    TableItem tableItem = new TableItem(pluginTable, SWT.NONE);
				    tableItem.setData(extPluginInfo);
				    
				    updateTableItem(tableItem, extPluginInfo);
			    }	
				
				// Reset status text
				setStatusText("");
				requestRunning.set(false);
			}
		});		
	}		
	
	@Override
	public void dispose() {
		eventBus.unregister(this);
		super.dispose();
	}

	@Override
	public boolean validatePanel() {
		return true;
	}
}
