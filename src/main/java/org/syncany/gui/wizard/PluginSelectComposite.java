package org.syncany.gui.wizard;

import java.util.List;

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
import org.syncany.plugins.transfer.TransferPlugin;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class PluginSelectComposite extends Composite {
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
					selectedPlugin = plugins.get(pluginTable.getSelectionIndex());
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
	    	String pluginImageResource = "/org/syncany/plugins/" + plugin.getId() + "/icon24.png";
		    Image image = SWTResourceManager.getImage(pluginImageResource);

		    TableItem tableItem = new TableItem(pluginTable, SWT.NONE);		    
		    tableItem.setText(1, plugin.getName());		    
		    tableItem.setImage(0, image);			    		    
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
