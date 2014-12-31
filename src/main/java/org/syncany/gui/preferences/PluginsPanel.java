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

import static org.syncany.gui.util.I18n._;

import java.io.File;
import java.util.Arrays;
import java.util.List;
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

import com.google.common.eventbus.Subscribe;

public class PluginsPanel extends Panel {
	private static final Logger logger = Logger.getLogger(PluginsPanel.class.getSimpleName());		
	private static final String PLUGIN_ICON_RESOURCE_FORMAT = "/" + Plugin.class.getPackage().getName().replace('.', '/') + "/%s/icon24.png";

	private enum ActionButton {
		INSTALL, UPDATE, REMOVE
	}
	
	private Table pluginTable;
	private Label statusLabel;	
	private Composite actionButtonComposite;
	
	private ExtendedPluginInfo selectedPlugin;
	private AtomicBoolean requestRunning;	
	
	private GuiEventBus eventBus;
	
	public PluginsPanel(PreferencesDialog parentDialog, Composite composite, int style) {
		super(parentDialog, composite, style | SWT.DOUBLE_BUFFERED);
		
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
		titleLabel.setText(_("org.syncany.gui.preferences.PluginsPanel.title"));

		WidgetDecorator.title(titleLabel);
	}

	private void createPluginTable() {
		// Plugin list
		GridData pluginTableGridData = new GridData(SWT.FILL, SWT.FILL, true, true);
		pluginTableGridData.verticalIndent = 5;
		pluginTableGridData.horizontalIndent = 0;
		pluginTableGridData.horizontalSpan = 2;
		
	    pluginTable = new Table(this, SWT.BORDER | SWT.V_SCROLL | SWT.DOUBLE_BUFFERED);
		pluginTable.setHeaderVisible(true);
		pluginTable.setBackground(WidgetDecorator.WHITE);
		pluginTable.setLayoutData(pluginTableGridData);
		
		pluginTable.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (pluginTable.getSelectionIndex() >= 0) {
					TableItem tableItem = pluginTable.getItem(pluginTable.getSelectionIndex());
					ExtendedPluginInfo extPluginInfo = (ExtendedPluginInfo) tableItem.getData();
					
					selectPlugin(extPluginInfo);
				}
				else {
					selectPlugin(null);
				}
			}
		});			
		
		pluginTable.addListener(SWT.MeasureItem, new Listener() {
			public void handleEvent(Event event) {				
				event.height = 30; // Row height workaround
			}
		});
		
	    TableColumn pluginTableColumnImage = new TableColumn(pluginTable, SWT.CENTER);
	    pluginTableColumnImage.setWidth(30);

	    TableColumn pluginTableColumnText = new TableColumn(pluginTable,  SWT.LEFT);
	    pluginTableColumnText.setText(_("org.syncany.gui.preferences.PluginsPanel.table.plugin"));
	    pluginTableColumnText.setWidth(100);	    

	    TableColumn pluginTableColumnLocalVersion = new TableColumn(pluginTable,  SWT.LEFT);
	    pluginTableColumnLocalVersion.setText(_("org.syncany.gui.preferences.PluginsPanel.table.localVersion"));
	    pluginTableColumnLocalVersion.setWidth(75);	    

	    TableColumn pluginTableColumnType = new TableColumn(pluginTable,  SWT.LEFT);
	    pluginTableColumnType.setText(_("org.syncany.gui.preferences.PluginsPanel.table.type"));
	    pluginTableColumnType.setWidth(30);	    

	    TableColumn pluginTableColumnRemoteVersion = new TableColumn(pluginTable,  SWT.LEFT);
	    pluginTableColumnRemoteVersion.setText(_("org.syncany.gui.preferences.PluginsPanel.table.remoteVersion"));
	    pluginTableColumnRemoteVersion.setWidth(75);
	    
	    TableColumn pluginTableColumnStatus = new TableColumn(pluginTable,  SWT.LEFT);
	    pluginTableColumnStatus.setText(_("org.syncany.gui.preferences.PluginsPanel.table.status"));
	    pluginTableColumnStatus.setWidth(30);	    
	}

	private void createStatusLabel() {
		RowLayout statusLabelRowLayout = new RowLayout(SWT.HORIZONTAL);
		statusLabelRowLayout.marginTop = 20;
		statusLabelRowLayout.marginBottom = 15;

		GridData statusLabelGridData = new GridData(SWT.FILL, SWT.FILL, true, false);
		statusLabelGridData.horizontalSpan = 1;
		statusLabelGridData.verticalSpan = 1;
		statusLabelGridData.minimumWidth = 90;
		statusLabelGridData.minimumHeight = 20;

		Composite statusLabelComposite = new Composite(this, SWT.NONE);
		statusLabelComposite.setLayout(statusLabelRowLayout);
		statusLabelComposite.setLayoutData(statusLabelGridData);		
		
		statusLabel = new Label(statusLabelComposite, SWT.NONE);
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

	private void createActionButtons(ActionButton... buttons) {
		while (actionButtonComposite.getChildren().length > 0) {
			actionButtonComposite.getChildren()[0].dispose();
		}		
		
		List<ActionButton> buttonList = Arrays.asList(buttons);
		
		if (buttonList.contains(ActionButton.UPDATE)) {			
			Button updatePluginButton = new Button(actionButtonComposite, SWT.NONE);
			updatePluginButton.setText(_("org.syncany.gui.preferences.PluginsPanel.button.update"));

			updatePluginButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					handleUpdatePlugin();
				}
			});
		}	   
		
		if (buttonList.contains(ActionButton.REMOVE)) {
		    Button removePluginButton = new Button(actionButtonComposite, SWT.NONE);
		    removePluginButton.setText(_("org.syncany.gui.preferences.PluginsPanel.button.remove"));
		    
		    removePluginButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					handleRemovePlugin();
				}
			});	
		}
		
		if (buttonList.contains(ActionButton.INSTALL)) {
			Button installPluginButton = new Button(actionButtonComposite, SWT.NONE);
			installPluginButton.setText(_("org.syncany.gui.preferences.PluginsPanel.button.install"));
		    
		    installPluginButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					handleInstallPlugin();
				}
			});
		}
		   
	    Button installFromFilePluginButton = new Button(actionButtonComposite, SWT.NONE);
	    installFromFilePluginButton.setText(_("org.syncany.gui.preferences.PluginsPanel.button.installFromFile"));
	    
	    installFromFilePluginButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				handleInstallFromFilePlugin();
			}
		});
	    
	    actionButtonComposite.getParent().layout();
	    actionButtonComposite.layout();
	    actionButtonComposite.redraw();
	}

	protected void selectPlugin(ExtendedPluginInfo extPluginInfo) {
		selectedPlugin = extPluginInfo;
		
		if (selectedPlugin != null) {
			PluginInfo pluginInfo = (extPluginInfo.isInstalled()) ? extPluginInfo.getLocalPluginInfo() : extPluginInfo.getRemotePluginInfo();
			
			if (selectedPlugin.isInstalled()) {
				if (selectedPlugin.canUninstall()) {
					if (false /* Can update */) { 
						logger.log(Level.FINE, "Plugin '" + pluginInfo.getPluginId() + "' can be updated and removed.");
						createActionButtons(ActionButton.UPDATE, ActionButton.REMOVE);
					}
					else {
						logger.log(Level.FINE, "Plugin '" + pluginInfo.getPluginId() + "' can be removed (not updated).");
						createActionButtons(ActionButton.REMOVE);
					}
				}
				else {
					logger.log(Level.FINE, "Plugin '" + pluginInfo.getPluginId() + "' cannot be uninstalled (or updated).");
					createActionButtons();
				}
			}
			else {
				logger.log(Level.FINE, "Plugin '" + pluginInfo.getPluginId() + "' is not installed, it can be installed..");
				createActionButtons(ActionButton.INSTALL);
			}			
		}
		else {
			logger.log(Level.FINE, "No plugin selected.");
			createActionButtons();
		}		
	}
	
	protected void handleInstallPlugin() {
		if (!requestRunning.get()) {
			requestRunning.set(true);
			
			setStatusText("Installing plugin " + selectedPlugin.getRemotePluginInfo().getPluginName() + " ...");
			
			PluginOperationOptions pluginOperationOptions = new PluginOperationOptions();
			pluginOperationOptions.setAction(PluginOperationAction.INSTALL);
			pluginOperationOptions.setPluginId(selectedPlugin.getRemotePluginInfo().getPluginId());
			
		    eventBus.post(new PluginManagementRequest(pluginOperationOptions));			
		}
	}

	protected void handleInstallFromFilePlugin() {				
		if (!requestRunning.get()) {
			FileDialog fileDialog = new FileDialog(getShell(), SWT.OPEN);
			fileDialog.setFilterExtensions(new String[] { "*.jar" });
			
			String selectedFilePath = fileDialog.open();
			
			if (selectedFilePath != null && selectedFilePath.length() > 0) {
				File selectedFile = new File(selectedFilePath);
				
				requestRunning.set(true);
				setStatusText("Installing plugin from file '" + selectedFile.getName() + "' ...");

				PluginOperationOptions pluginOperationOptions = new PluginOperationOptions();
				pluginOperationOptions.setAction(PluginOperationAction.INSTALL);
				pluginOperationOptions.setPluginId(selectedFilePath);
				
			    eventBus.post(new PluginManagementRequest(pluginOperationOptions));					
			}			
		}		
	}
	
	protected void handleUpdatePlugin() {
		// TODO [medium] Waiting for cr0's issue
	}
	
	protected void handleRemovePlugin() {
		if (!requestRunning.get()) {
			requestRunning.set(true);
			setStatusText("Removing plugin " + selectedPlugin.getRemotePluginInfo().getPluginName() + " ...");

			PluginOperationOptions pluginOperationOptions = new PluginOperationOptions();
			pluginOperationOptions.setAction(PluginOperationAction.REMOVE);
			pluginOperationOptions.setPluginId(selectedPlugin.getRemotePluginInfo().getPluginId());
			
		    eventBus.post(new PluginManagementRequest(pluginOperationOptions));
		}		
	}

	private void refreshPluginList() {
		requestRunning.set(true);
		setStatusText("Retrieving plugin list ...");

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
				String pluginName = pluginResponse.getResult().getAffectedPluginInfo().getPluginName();					

				if (pluginResponse.getResult().getResultCode() == PluginResultCode.OK) {
					setStatusText(_("org.syncany.gui.preferences.PluginsPanel.status.pluginInstalled", pluginName));
				}
				else {
					setStatusText(_("org.syncany.gui.preferences.PluginsPanel.status.pluginNotInstalled", pluginName));					
				}
				
				requestRunning.set(false);
			}			
		});		
	}

	private void onPluginRemoveResponseReceived(final PluginManagementResponse pluginResponse) {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {		
				if (pluginResponse.getResult().getResultCode() == PluginResultCode.OK) {
					setStatusText("Plugin removed.");
				}
				else {
					setStatusText("Plugin not removed.");					
				}
				
				requestRunning.set(false);
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
					
					// Get cell labels
					String localVersionStr = (extPluginInfo.isInstalled()) ? extPluginInfo.getLocalPluginInfo().getPluginVersion() : "";
					String typeStr = extPluginInfo.isInstalled() ? (extPluginInfo.canUninstall() ? "User" : "Global") : "";
					String remoteVersionStr = (extPluginInfo.isRemoteAvailable()) ? extPluginInfo.getRemotePluginInfo().getPluginVersion() : "";
					String installedStr = extPluginInfo.isInstalled() ? "Installed" : "";
					
					// Find plugin image (if installed)
					Image pluginImage = null;
					
					if (extPluginInfo.isInstalled()) {
						String pluginImageResource = String.format(PLUGIN_ICON_RESOURCE_FORMAT, pluginInfo.getPluginId());
						pluginImage = SWTResourceManager.getImage(pluginImageResource);
					}

					// Create table item
				    TableItem tableItem = new TableItem(pluginTable, SWT.DOUBLE_BUFFERED);
				    tableItem.setData(extPluginInfo);
				    
				    if (pluginImage != null) {
				    	tableItem.setImage(0, pluginImage);
				    }
				    
				    tableItem.setText(1, pluginInfo.getPluginName());		    
				    tableItem.setText(2, localVersionStr);		    
				    tableItem.setText(3, typeStr);		    
				    tableItem.setText(4, remoteVersionStr);		
				    tableItem.setText(5, installedStr);						    
			    }	
				
				// Reset status text
				setStatusText("");
				requestRunning.set(false);
			}
		});		
	}	
	
	private void setStatusText(String status) {
		statusLabel.setText(status);

		statusLabel.getParent().layout();
		statusLabel.redraw();
	}
}
