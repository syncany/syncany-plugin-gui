package org.syncany.gui.wizard;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.syncany.gui.Panel;
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.WidgetDecorator;
import org.syncany.plugins.transfer.TransferPlugin;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class PluginSelectPanel extends Panel {
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
		titleLabel.setText(I18n.getText("org.syncany.gui.wizard.PluginSelectPanel.title"));
		
		WidgetDecorator.title(titleLabel);

		Label descriptionLabel = new Label(this, SWT.WRAP);
		descriptionLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false, 1, 1));
		descriptionLabel.setText(I18n.getText("org.syncany.gui.wizard.PluginSelectPanel.description"));

		WidgetDecorator.normal(descriptionLabel);

		// Plugin composite
		pluginSelectComposite = new PluginSelectComposite(this, SWT.NONE);	
		pluginSelectComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
	}

	public TransferPlugin getSelectedPlugin() {
		return pluginSelectComposite.getSelectedPlugin();
	}
	
	@Override
	public boolean validatePanel() {
		return pluginSelectComposite.getSelectedPlugin() != null;
	}	
}
