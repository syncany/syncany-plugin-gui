package org.syncany.gui.wizard;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
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
		GridLayout mainCompositeGridLayout = new GridLayout(1, false);
		mainCompositeGridLayout.marginTop = 15;
		mainCompositeGridLayout.marginLeft = 10;
		mainCompositeGridLayout.marginRight = 20;

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
		GridData pluginListGridData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
		pluginListGridData.verticalIndent = WidgetDecorator.VERTICAL_INDENT;
		pluginListGridData.horizontalSpan = 1;
		
		final org.eclipse.swt.widgets.List pluginList = new org.eclipse.swt.widgets.List(this, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL);
		pluginList.setLayoutData(pluginListGridData);
		
		for (TransferPlugin plugin : plugins) {
			pluginList.add(plugin.getName());
		}		

		pluginList.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (pluginList.getSelectionIndex() >= 0) {
					selectedPlugin = plugins.get(pluginList.getSelectionIndex());
				}
				else {
					selectedPlugin = null;					
				}
			}
		});

		pluginList.setSelection(0);
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
