package org.syncany.gui.wizard;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.syncany.plugins.transfer.TransferPlugin;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class PluginSelectPanel extends Panel {
	private Label descriptionLabel;

	private PluginSelectComposite pluginSelectComposite;
	
	public PluginSelectPanel(WizardDialog wizardParentDialog, Composite parent, int style) {
		super(wizardParentDialog, parent, style);	
		
		this.createControls();		
	}
				
	private void createControls() {		
		// Main composite
		GridLayout mainCompositeGridLayout = new GridLayout(1, true);
		mainCompositeGridLayout.marginTop = 15;
		mainCompositeGridLayout.marginLeft = 10;
		mainCompositeGridLayout.marginRight = 20;
		mainCompositeGridLayout.marginBottom = 10;

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

		pluginSelectComposite = new PluginSelectComposite(this, SWT.NONE);		
	}

	public TransferPlugin getSelectedPlugin() {
		return pluginSelectComposite.getSelectedPlugin();
	}
	
	@Override
	public boolean validatePanel() {
		return pluginSelectComposite.getSelectedPlugin() != null;
	}	
}
