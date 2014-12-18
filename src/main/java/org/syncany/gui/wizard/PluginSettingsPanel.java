package org.syncany.gui.wizard;

import java.lang.reflect.Field;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
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
import org.syncany.plugins.transfer.TransferPluginOption.ValidationResult;
import org.syncany.plugins.transfer.TransferPluginOptions;
import org.syncany.plugins.transfer.TransferSettings;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class PluginSettingsPanel extends Panel {
	private static final Logger logger = Logger.getLogger(PluginSettingsPanel.class.getSimpleName());	

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
		GridLayout mainCompositeGridLayout = new GridLayout(2, false);
		mainCompositeGridLayout.marginTop = 15;
		mainCompositeGridLayout.marginLeft = 10;
		mainCompositeGridLayout.marginRight = 20;

		setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));		
		setLayout(mainCompositeGridLayout);
		
		// Title and description
		Label titleLabel = new Label(this, SWT.WRAP);
		titleLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 2, 1));
		titleLabel.setText(selectedPlugin.getName() + " settings");
		
		WidgetDecorator.title(titleLabel);
		
		for (TransferPluginOption pluginOption : pluginOptions) {
			createPluginOptionControl(pluginOption);			
		}

		// Warning message and label
		String warningImageResource = "/" + WizardDialog.class.getPackage().getName().replace(".", "/") + "/warning-icon.png";
		Image warningImage = SWTResourceManager.getImage(warningImageResource);

		warningImageLabel = new Label(this, SWT.NONE);
		warningImageLabel.setImage(warningImage);
		warningImageLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));
		warningImageLabel.setVisible(false);

		warningMessageLabel = new Label(this, SWT.WRAP);
		warningMessageLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		warningMessageLabel.setVisible(false);
		
		WidgetDecorator.bold(warningMessageLabel);

		pack();
	}
	
	private void createPluginOptionControl(final TransferPluginOption pluginOption) {
		Field pluginField = pluginOption.getField();
				
		// Label "Option X:"
		GridData selectFolderLabel = new GridData(SWT.LEFT, SWT.CENTER, false, false);
		selectFolderLabel.verticalIndent = 5;
		selectFolderLabel.horizontalSpan = 3;

		String optionLabelText = pluginOption.getDescription() + (pluginOption.isSensitive() ? " (not displayed)" : "");

		Label optionLabel = new Label(this, SWT.WRAP);
		optionLabel.setLayoutData(selectFolderLabel);
		optionLabel.setText(optionLabelText);
		
		// Textfield "Option X"
		GridData optionValueTextGridData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		optionValueTextGridData.verticalIndent = 0;
		optionValueTextGridData.horizontalSpan = 2;
		optionValueTextGridData.minimumWidth = 200;

		int optionValueTextStyle = (pluginOption.isSensitive()) ? SWT.BORDER | SWT.PASSWORD : SWT.BORDER;
		
		final Text pluginOptionValueText = new Text(this, optionValueTextStyle);
		pluginOptionValueText.setLayoutData(optionValueTextGridData);
		pluginOptionValueText.setBackground(WidgetDecorator.WHITE);
		
		setPluginOptionDefaultValue(pluginOptionValueText, pluginField);
		setPluginOptionModifyListener(pluginOption, pluginOptionValueText);
		setPluginOptionVerifyListener(pluginOption, pluginOptionValueText);
		
		WidgetDecorator.normal(pluginOptionValueText);
	}

	private void setPluginOptionDefaultValue(Text pluginOptionValueText, Field pluginField) {
		try {
			String defaultValue = selectedPluginSettings.getField(pluginField.getName());
			
			if (defaultValue != null && !defaultValue.isEmpty()) {
				pluginOptionValueText.setText(defaultValue); 
			}
		}
		catch (StorageException e) {
			throw new RuntimeException("Error creating controls.", e);
		} 
	}
	
	private void setPluginOptionModifyListener(final TransferPluginOption pluginOption, final Text pluginOptionValueText) {
		pluginOptionValueText.addModifyListener(new ModifyListener() {			
			@Override
			public void modifyText(ModifyEvent e) {
				try {
					logger.log(Level.INFO, "Setting field '" + pluginOption.getName() + "' with value '" + pluginOptionValueText.getText() + "'", e);						
					selectedPluginSettings.setField(pluginOption.getField().getName(), pluginOptionValueText.getText());
					WidgetDecorator.markAsValid(pluginOptionValueText);
				}
				catch (StorageException e1) {
					logger.log(Level.WARNING, "Cannot set field '" + pluginOption.getName() + "' with value '" + pluginOptionValueText.getText() + "'", e);						
					WidgetDecorator.markAsInvalid(pluginOptionValueText);
				}
				
	            ValidationResult validationResult = pluginOption.isValid(pluginOptionValueText.getText());
				
	            switch (validationResult) {
	            case INVALID_NOT_SET:
	            	if (pluginOption.isRequired()) {
	            		WidgetDecorator.markAsInvalid(pluginOptionValueText);
	            	}
	            	else {
	            		WidgetDecorator.markAsValid(pluginOptionValueText);
	            	}
	            	break;
	            	
	            case INVALID_TYPE:
	            	logger.log(Level.SEVERE, " Invalid type in field '" + pluginOption.getName() + "'. This should be caught by verify listener!");
            		WidgetDecorator.markAsInvalid(pluginOptionValueText);
            		break;
            		
	            case VALID:
            		WidgetDecorator.markAsValid(pluginOptionValueText);
            		break;
	            }
			}
		});	
	}
	
	private void setPluginOptionVerifyListener(final TransferPluginOption pluginOption, final Text pluginOptionValueText) {
		pluginOptionValueText.addVerifyListener(new VerifyListener() {
	        @Override
	        public void verifyText(VerifyEvent e) {
	            Text text = (Text) e.getSource();

	            // Get old text and create new text by using the VerifyEvent.text
	            final String oldValue = text.getText();
	            String newValue = oldValue.substring(0, e.start) + e.text + oldValue.substring(e.end);
	            
	            // Validate correct type
	            ValidationResult validationResult = pluginOption.isValid(newValue);
	            e.doit = newValue.isEmpty() || validationResult != ValidationResult.INVALID_TYPE;		            
	        }
	    });
	}

	@Override
	public boolean validatePanel() {
		try {
			hideWarning();			
			selectedPluginSettings.validateRequiredFields();

			logger.log(Level.WARNING, "Validation succeeded on panel.");			
			return true;
		}
		catch (StorageException e) {
			showWarning(e.getMessage());
			
			logger.log(Level.WARNING, "Validate error on panel.", e);			
			return false;
		}		
	}
	
	private void showWarning(String warningStr) {
		warningImageLabel.setVisible(true);
		warningMessageLabel.setVisible(true);			
		warningMessageLabel.setText(warningStr);
	}
	
	private void hideWarning() {
		warningImageLabel.setVisible(false);
		warningMessageLabel.setVisible(false);
	}
	
	public TransferSettings getPluginSettings() {
		return selectedPluginSettings;
	}
}
