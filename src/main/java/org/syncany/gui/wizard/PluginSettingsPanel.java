package org.syncany.gui.wizard;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.syncany.gui.util.SWTResourceManager;
import org.syncany.plugins.transfer.StorageException;
import org.syncany.plugins.transfer.TransferPlugin;
import org.syncany.plugins.transfer.TransferPluginOption;
import org.syncany.plugins.transfer.TransferPluginOptions;
import org.syncany.plugins.transfer.TransferSettings;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class PluginSettingsPanel extends Panel {
	private Label warningImageLabel;
	private Label warningMessageLabel;

	private TransferPlugin selectedPlugin;
	private TransferSettings selectedPluginSettings;
	
	public PluginSettingsPanel(WizardDialog wizardParentDialog, Composite parent, int style) {
		super(wizardParentDialog, parent, style);	
	}
				
	public void init(TransferPlugin selectedPlugin) {		
		setSelectedPlugin(selectedPlugin);
		
		clearControls();
		createControls();
	}

	private void setSelectedPlugin(TransferPlugin selectedPlugin) {
		try {
			this.selectedPluginSettings = selectedPlugin.createEmptySettings();
			this.selectedPlugin = selectedPlugin;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void clearControls() {
		for (Control childComponent : getChildren()) {
			childComponent.dispose();
		}
	}

	private void createControls() {
		List<TransferPluginOption> pluginOptions = TransferPluginOptions.getOrderedOptions(selectedPluginSettings.getClass());
				
		// Main composite
		GridLayout mainCompositeGridLayout = new GridLayout(3, false);
		mainCompositeGridLayout.marginTop = 15;
		mainCompositeGridLayout.marginLeft = 10;
		mainCompositeGridLayout.marginRight = 20;

		setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));		
		setLayout(mainCompositeGridLayout);
		
		// Title and description
		Label titleLabel = new Label(this, SWT.WRAP);
		titleLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 3, 1));
		titleLabel.setText(selectedPlugin.getName() + " settings");
		
		WidgetDecorator.title(titleLabel);
		
		for (final TransferPluginOption pluginOption : pluginOptions) {
			// Label "Folder:"
			GridData selectFolderLabel = new GridData(SWT.LEFT, SWT.CENTER, false, false);
			selectFolderLabel.verticalIndent = 5;
			selectFolderLabel.horizontalSpan = 3;

			Label optionLabel = new Label(this, SWT.WRAP);
			optionLabel.setLayoutData(selectFolderLabel);
			optionLabel.setText(pluginOption.getDescription());
			
			// Textfield "Folder"
			GridData optionValueTextGridData = new GridData(SWT.FILL, SWT.CENTER, true, false);
			optionValueTextGridData.verticalIndent = 0;
			optionValueTextGridData.horizontalSpan = 2;
			optionValueTextGridData.minimumWidth = 200;

			int optionValueTextStyle = (pluginOption.isSensitive()) ? SWT.BORDER | SWT.PASSWORD : SWT.BORDER;
			
			final Text optionValueText = new Text(this, optionValueTextStyle);
			optionValueText.setLayoutData(optionValueTextGridData);
			optionValueText.setBackground(WidgetDecorator.WHITE);
			optionValueText.addModifyListener(new ModifyListener() {			
				@Override
				public void modifyText(ModifyEvent e) {
					// Do something.
					try {
						selectedPluginSettings.setField(pluginOption.getName(), e.toString());
					}
					catch (StorageException e1) {
						WidgetDecorator.markAsInvalid(optionValueText);
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				}
			});
			
			WidgetDecorator.normal(optionValueText);
		}

		// Warning message and label
		String warningImageResource = "/" + WizardDialog.class.getPackage().getName().replace(".", "/") + "/warning-icon.png";
		Image warningImage = SWTResourceManager.getImage(warningImageResource);

		warningImageLabel = new Label(this, SWT.NONE);
		warningImageLabel.setImage(warningImage);
		warningImageLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));
		warningImageLabel.setVisible(false);

		warningMessageLabel = new Label(this, SWT.WRAP);
		warningMessageLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 2, 1));
		warningMessageLabel.setVisible(false);
		
		WidgetDecorator.bold(warningMessageLabel);

		pack();
	}
	
	@Override
	public boolean validatePanel() {
		try {
			selectedPluginSettings.validateRequiredFields();
			return true;
		}
		catch (StorageException e) {
			e.printStackTrace();
			return false;
		}		
	}
}
