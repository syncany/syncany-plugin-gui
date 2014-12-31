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

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
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
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.syncany.config.GuiEventBus;
import org.syncany.gui.util.DesktopUtil;
import org.syncany.gui.util.SWTResourceManager;
import org.syncany.gui.util.WidgetDecorator;
import org.syncany.gui.wizard.PluginSelectComposite;
import org.syncany.operations.plugin.ExtendedPluginInfo;
import org.syncany.operations.plugin.PluginInfo;
import org.syncany.operations.plugin.PluginOperation;
import org.syncany.operations.plugin.PluginOperationOptions;
import org.syncany.operations.plugin.PluginOperationOptions.PluginAction;
import org.syncany.operations.plugin.PluginOperationOptions.PluginListMode;
import org.syncany.operations.plugin.PluginOperationResult;
import org.syncany.plugins.Plugin;
import org.syncany.plugins.Plugins;
import org.syncany.plugins.transfer.TransferPlugin;

import com.google.common.eventbus.Subscribe;

public class PluginsPanel extends Panel {
	private static final String PLUGIN_ICON_RESOURCE_FORMAT = "/" + Plugin.class.getPackage().getName().replace('.', '/') + "/%s/icon24.png";

	private Table pluginTable;
	private Button installPluginButton;
	private Button updatePluginButton;
	private Button removePluginButton;
	
	private ExtendedPluginInfo selectedPlugin;
	
	private GuiEventBus eventBus;
	
	public PluginsPanel(PreferencesDialog parentDialog, Composite composite, int style) {
		super(parentDialog, composite, style | SWT.DOUBLE_BUFFERED);
		
		this.selectedPlugin = null;
		
		this.eventBus = GuiEventBus.getInstance();
		this.eventBus.register(this);
		
		this.createControls();
	}
	
	private void createControls() {	
		createMainCompositeAndTitle();		

		createPluginTable();
	    createActionButtons();
	    
	    refreshPluginList();	      	  
	}

	private void createMainCompositeAndTitle() {
		// Main composite
		GridLayout mainCompositeGridLayout = new GridLayout(1, false);
		mainCompositeGridLayout.marginTop = 15;
		mainCompositeGridLayout.marginLeft = 10;
		mainCompositeGridLayout.marginRight = 20;

		setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		setLayout(mainCompositeGridLayout);

		// Title and welcome text
		Label titleLabel = new Label(this, SWT.WRAP);
		titleLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
		titleLabel.setText(_("org.syncany.gui.preferences.PluginsPanel.title"));

		WidgetDecorator.title(titleLabel);
	}

	private void createPluginTable() {
		// Plugin list
		GridData pluginTableGridData = new GridData(SWT.FILL, SWT.TOP, true, true);
		pluginTableGridData.verticalIndent = 0;
		pluginTableGridData.horizontalIndent = 0;
		
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
	    pluginTableColumnText.setText("Plugin");
	    pluginTableColumnText.setWidth(100);	    

	    TableColumn pluginTableColumnLocalVersion = new TableColumn(pluginTable,  SWT.LEFT);
	    pluginTableColumnLocalVersion.setText("Local Version");
	    pluginTableColumnLocalVersion.setWidth(70);	    

	    TableColumn pluginTableColumnType = new TableColumn(pluginTable,  SWT.LEFT);
	    pluginTableColumnType.setText("Type");
	    pluginTableColumnType.setWidth(45);	    

	    TableColumn pluginTableColumnRemoteVersion = new TableColumn(pluginTable,  SWT.LEFT);
	    pluginTableColumnRemoteVersion.setText("Remote Version");
	    pluginTableColumnRemoteVersion.setWidth(30);
	}

	protected void selectPlugin(ExtendedPluginInfo extPluginInfo) {
		selectedPlugin = extPluginInfo;
		
		if (selectedPlugin.isInstalled()) {
			if (selectedPlugin.canUninstall()) {
				if (false) { // 
					updatePluginButton.setEnabled(true);
				}
				
				installPluginButton.setEnabled(false);
				removePluginButton.setEnabled(true);
			}
			else {
				installPluginButton.setEnabled(false);
				updatePluginButton.setEnabled(false);
				removePluginButton.setEnabled(false);
			}
		}
		else {
			installPluginButton.setEnabled(true);
			updatePluginButton.setEnabled(false);
			removePluginButton.setEnabled(false);
		}
	}

	private void createActionButtons() {
		RowLayout buttonCompositeRowLayout = new RowLayout(SWT.HORIZONTAL);
		buttonCompositeRowLayout.marginTop = 15;
		buttonCompositeRowLayout.marginBottom = 15;
		buttonCompositeRowLayout.marginRight = 10;
		
		GridData buttonCompositeGridData = new GridData(SWT.RIGHT, SWT.FILL, false, false);
		buttonCompositeGridData.horizontalSpan = 2;
		buttonCompositeGridData.verticalSpan = 1;

		Composite buttonComposite = new Composite(this, SWT.NONE);
		buttonComposite.setLayout(buttonCompositeRowLayout);
		buttonComposite.setLayoutData(buttonCompositeGridData);
		
		installPluginButton = new Button(buttonComposite, SWT.NONE);
		installPluginButton.setText("Install");
	    installPluginButton.setEnabled(false);
	    
	    installPluginButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				handleInstallPlugin();
			}
		});
	    
	    updatePluginButton = new Button(buttonComposite, SWT.NONE);
	    updatePluginButton.setText("Update");
	    updatePluginButton.setEnabled(false);
	    
	    updatePluginButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				handleUpdatePlugin();
			}
		});
	    
	    removePluginButton = new Button(buttonComposite, SWT.NONE);
	    removePluginButton.setText("Remove");
	    removePluginButton.setEnabled(false);
	    
	    removePluginButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				handleRemovePlugin();
			}
		});				
	}	

	protected void handleInstallPlugin() {
		
	}

	protected void handleUpdatePlugin() {
		
	}
	
	protected void handleRemovePlugin() {
		
	}

	private void refreshPluginList() {
		pluginTable.clearAll();
			
		TableItem tableItem = new TableItem(pluginTable, SWT.DOUBLE_BUFFERED);
	    tableItem.setData(null);
	   
	    tableItem.setText(1, "Updating ...");		    

		PluginOperationOptions pluginOptions = new PluginOperationOptions();
		pluginOptions.setAction(PluginAction.LIST);
		pluginOptions.setListMode(PluginListMode.ALL);
		
	    eventBus.post(new PluginManagementRequest());
	    
		try {

			
			PluginOperation pluginOperation = new PluginOperation(null, pluginOptions);
			PluginOperationResult pluginResult = pluginOperation.execute();
			
		}
		catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}						
	}
	
	@Subscribe
	public void onPluginResultReceived() { // TODO [high] Add result object
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				PluginOperationResult pluginResult = null; // ...
				
				for (ExtendedPluginInfo extPluginInfo : pluginResult.getPluginList()) {	   	    	
					PluginInfo pluginInfo = (extPluginInfo.isInstalled()) ? extPluginInfo.getLocalPluginInfo() : extPluginInfo.getRemotePluginInfo();

					String localVersionStr = (extPluginInfo.isInstalled()) ? extPluginInfo.getLocalPluginInfo().getPluginVersion() : "";
					String installedStr = extPluginInfo.isInstalled() ? (extPluginInfo.canUninstall() ? "User" : "Global") : "";
					String remoteVersionStr = (extPluginInfo.isRemoteAvailable()) ? extPluginInfo.getRemotePluginInfo().getPluginVersion() : "";
					
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
				    tableItem.setText(3, installedStr);		    
				    tableItem.setText(4, remoteVersionStr);		    
			    }	 
			}
		});		
	}
}
