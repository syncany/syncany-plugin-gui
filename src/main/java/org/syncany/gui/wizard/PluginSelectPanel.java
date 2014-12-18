package org.syncany.gui.wizard;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
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
public class PluginSelectPanel extends Panel {
	private Label descriptionLabel;

	private List<TransferPlugin> plugins;
	private TransferPlugin selectedPlugin;
	
	public PluginSelectPanel(WizardDialog wizardParentDialog, Composite parent, int style) {
		super(wizardParentDialog, parent, style);	
		
		this.plugins = Plugins.list(TransferPlugin.class);
		this.selectedPlugin = null;
		
		this.createControls();
	}
				
	private void createControls() {		
		// Main composite
		GridLayout mainCompositeGridLayout = new GridLayout(1, true);
		mainCompositeGridLayout.marginTop = 15;
		mainCompositeGridLayout.marginLeft = 10;
		mainCompositeGridLayout.marginRight = 20;
		mainCompositeGridLayout.marginBottom = 25;

		setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));		
		setLayout(mainCompositeGridLayout);
		
		// Title and description
		Label titleLabel = new Label(this, SWT.WRAP);
		titleLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
		titleLabel.setText("Select storage backend");
		
		WidgetDecorator.title(titleLabel);

		descriptionLabel = new Label(this, SWT.WRAP);
		descriptionLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false, 1, 1));
		descriptionLabel.setText("Please choose where to store your files online. Syncany can use any of the following storage backends:");

		WidgetDecorator.normal(descriptionLabel);
		
		// Plugin list
		GridData pluginTableGridData = new GridData(SWT.FILL, SWT.TOP, true, true);
		pluginTableGridData.verticalIndent = WidgetDecorator.VERTICAL_INDENT;
		pluginTableGridData.horizontalIndent = 5;
		
	    final Table pluginTable = new Table(this, SWT.BORDER |  SWT.V_SCROLL);
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

		pluginTable.select(0);
		selectedPlugin = plugins.get(0);
	}

	public TransferPlugin getSelectedPlugin() {
		return selectedPlugin;
	}
	
	@Override
	public boolean validatePanel() {
		return selectedPlugin != null;
	}	
}
