package org.syncany.gui.wizard;

import java.io.File;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.syncany.gui.util.SWTResourceManager;
import org.syncany.plugins.Plugins;
import org.syncany.plugins.transfer.StorageException;
import org.syncany.plugins.transfer.TransferPlugin;
import org.syncany.plugins.transfer.TransferPluginOption;
import org.syncany.plugins.transfer.TransferPluginOptions;
import org.syncany.plugins.transfer.TransferSettings;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class PluginSelectComposite extends Composite {
	private static final Logger logger = Logger.getLogger(PluginSelectComposite.class.getSimpleName());	

	private Table pluginTable;
	private List<TransferPlugin> plugins;
	private TransferPlugin selectedPlugin;
	
	public PluginSelectComposite(Composite parent, int style) {
		super(parent, style);	
		
		this.plugins = Plugins.list(TransferPlugin.class);
		this.selectedPlugin = null;
		
		this.createControls();
	}
				
	private void createControls() {		
		// Main composite
		GridLayout mainCompositeGridLayout = new GridLayout(1, true);
		mainCompositeGridLayout.marginTop = 0;
		mainCompositeGridLayout.marginLeft = 0;
		mainCompositeGridLayout.marginRight = 0;
		mainCompositeGridLayout.marginBottom = 0;
		mainCompositeGridLayout.horizontalSpacing = 0;
		mainCompositeGridLayout.verticalSpacing = 0;

		setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));		
		setLayout(mainCompositeGridLayout);		
		
		// Plugin list
		GridData pluginTableGridData = new GridData(SWT.FILL, SWT.TOP, true, true);
		pluginTableGridData.verticalIndent = 0;
		pluginTableGridData.horizontalIndent = 0;
		
	    pluginTable = new Table(this, SWT.BORDER |  SWT.V_SCROLL);
		pluginTable.setHeaderVisible(false);
		pluginTable.setBackground(WidgetDecorator.WHITE);
		pluginTable.setLayoutData(pluginTableGridData);
		
		pluginTable.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (pluginTable.getSelectionIndex() >= 0) {
					TableItem tableItem = pluginTable.getItem(pluginTable.getSelectionIndex());
					selectedPlugin = (TransferPlugin) tableItem.getData();
				}
				else {
					selectedPlugin = null;
				}
			}
		});	
		
		pluginTable.addFocusListener(new FocusAdapter() {
			@Override
			public void focusGained(FocusEvent e) {
				PluginSelectComposite.this.notifyListeners(SWT.FocusIn, new Event());
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
	    pluginTableColumnText.setWidth(300);	    

	    for (TransferPlugin plugin : plugins) {	   	    	
		    if (isSupportedPlugin(plugin)) {
		    	String pluginImageResource = "/org/syncany/plugins/" + plugin.getId() + "/icon24.png";
			    Image image = SWTResourceManager.getImage(pluginImageResource);
	
			    TableItem tableItem = new TableItem(pluginTable, SWT.NONE);		    
			    tableItem.setImage(0, image);
			    tableItem.setText(1, plugin.getName());		    
			    tableItem.setData(plugin);
		    }
	    }	    	   
	}

	private boolean isSupportedPlugin(TransferPlugin plugin) {
		try {
			TransferSettings pluginSettings = plugin.createEmptySettings();			
			List<TransferPluginOption> pluginOptions = TransferPluginOptions.getOrderedOptions(pluginSettings.getClass());
			
			for (TransferPluginOption pluginOption : pluginOptions) {
				if (pluginOption.isVisible()) {
					boolean optionSupported = pluginOption.getType() == String.class
							|| pluginOption.getType() == int.class
							|| pluginOption.getType() == File.class;
					
					if (!optionSupported) {					
						logger.log(Level.FINE, "- Plugin '" + plugin.getId() + "' is NOT supported by the GUI; reason is option '" + pluginOption.getName() + "' of type '" + pluginOption.getType() + "'.");
						return false;
					}
				}
			}
			
			logger.log(Level.FINE, "- Plugin '" + plugin.getId() + "' is supported by the GUI.");
			return true;
		}
		catch (StorageException e) {
			logger.log(Level.FINE, "- Plugin '" + plugin.getId() + "' is NOT supported by the GUI; reason is an exception.", e);
			return false;
		}
	}

	public TransferPlugin getSelectedPlugin() {
		return selectedPlugin;
	}

	public void clearSelection() {
		pluginTable.deselectAll();
		selectedPlugin = null;
	}		
}
