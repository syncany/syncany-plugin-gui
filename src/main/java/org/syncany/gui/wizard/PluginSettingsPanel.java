package org.syncany.gui.wizard;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.syncany.gui.util.DesktopHelper;
import org.syncany.gui.util.SWTResourceManager;
import org.syncany.plugins.transfer.OAuthGenerator;
import org.syncany.plugins.transfer.StorageException;
import org.syncany.plugins.transfer.TransferPlugin;
import org.syncany.plugins.transfer.TransferPluginOption;
import org.syncany.plugins.transfer.TransferPluginOption.ValidationResult;
import org.syncany.plugins.transfer.TransferPluginOptions;
import org.syncany.plugins.transfer.TransferPluginUtil;
import org.syncany.plugins.transfer.TransferSettings;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class PluginSettingsPanel extends Panel {
	private static final Logger logger = Logger.getLogger(PluginSettingsPanel.class.getSimpleName());	

	private Label warningImageLabel;
	private Label warningMessageLabel;

	private TransferPlugin plugin;
	private TransferSettings pluginSettings;
	
	private OAuthGenerator oAuthGenerator;
	private Text oAuthText;
	
	private Map<TransferPluginOption, Text> pluginOptionControlMap;	
	private Set<TransferPluginOption> invalidPluginOptions;
	
	public PluginSettingsPanel(WizardDialog wizardParentDialog, Composite parent, int style) {
		super(wizardParentDialog, parent, style);			
	}
				
	public void init(TransferPlugin plugin) {		
		setPlugin(plugin);
		
		clearControls();
		createControls();
	}

	private void setPlugin(TransferPlugin plugin) {
		try {
			this.plugin = plugin;
			this.pluginSettings = plugin.createEmptySettings();
			
			this.oAuthGenerator = null;
			
			this.pluginOptionControlMap = new HashMap<>();
			this.invalidPluginOptions = new HashSet<>();
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
		List<TransferPluginOption> pluginOptions = TransferPluginOptions.getOrderedOptions(pluginSettings.getClass());
				
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
		titleLabel.setText(plugin.getName() + " settings");
		
		WidgetDecorator.title(titleLabel);
		
		// Create OAuth controls
		createOAuthControls();
		
		// Create fields
		for (TransferPluginOption pluginOption : pluginOptions) {
			if (pluginOption.isVisible()) {
				createPluginOptionControl(pluginOption);
			}
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
	
	private void createOAuthControls() {
		Class<? extends OAuthGenerator> oAuthGeneratorClass = TransferPluginUtil.getOAuthGeneratorClass(pluginSettings.getClass());

		if (oAuthGeneratorClass != null) {
			try {
				Constructor<? extends OAuthGenerator> optionCallbackClassConstructor = oAuthGeneratorClass.getDeclaredConstructor(pluginSettings.getClass());
				oAuthGenerator = optionCallbackClassConstructor.newInstance(pluginSettings);			
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}

			try {
				final URI oAuthURL = oAuthGenerator.generateAuthUrl();
				
				// OAuth help text
				Label descriptionLabel = new Label(this, SWT.WRAP);
				descriptionLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false, 3, 1));
				descriptionLabel.setText("This plugin needs to authenticate your account so that Syncany can access it. Please click on the 'Authorize' button to do that.");
				
				WidgetDecorator.normal(descriptionLabel);
				
				// Label "Token:"
				GridData oAuthTokenLabelGridData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
				oAuthTokenLabelGridData.verticalIndent = 2;
				oAuthTokenLabelGridData.horizontalSpan = 3;

				Label pluginOptionLabel = new Label(this, SWT.WRAP);
				pluginOptionLabel.setLayoutData(oAuthTokenLabelGridData);
				pluginOptionLabel.setText("Token");
				
				// Textfield "Token"		
				GridData optionValueTextGridData = new GridData(SWT.FILL, SWT.CENTER, true, false);
				optionValueTextGridData.verticalIndent = 0;
				optionValueTextGridData.horizontalSpan = 2;
				optionValueTextGridData.minimumWidth = 200;
				optionValueTextGridData.grabExcessHorizontalSpace = true;

				oAuthText = new Text(this, SWT.BORDER);
				oAuthText.setLayoutData(optionValueTextGridData);
				oAuthText.setBackground(WidgetDecorator.WHITE);
				
				WidgetDecorator.normal(oAuthText);

				// Add 'Authorize ..' button for 'File' fields
				Button oAuthAuthorizeButton = new Button(this, SWT.NONE);
				oAuthAuthorizeButton.setText("Authorize ...");
					
				oAuthAuthorizeButton.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						DesktopHelper.launch(oAuthURL.toString());
					}
				});							
			}
			catch (Exception e) {
				// TODO [high] This should not be a runtime exception. what if the network isnt available?!
				throw new RuntimeException(e);
			}
		}
	}

	private void createPluginOptionControl(final TransferPluginOption pluginOption) {
		Field pluginField = pluginOption.getField();
				
		// Label "Option X:"
		GridData pluginOptionLabelGridData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
		pluginOptionLabelGridData.verticalIndent = 2;
		pluginOptionLabelGridData.horizontalSpan = 3;

		String pluginOptionLabelText = pluginOption.getDescription();
		
		if (pluginOption.isSensitive()) {
			pluginOptionLabelText += (pluginOption.isRequired()) ? " (not displayed)" : " (not displayed, optional)";
		}
		else {
			pluginOptionLabelText += (pluginOption.isRequired()) ? "" : " (optional)";
		}

		Label pluginOptionLabel = new Label(this, SWT.WRAP);
		pluginOptionLabel.setLayoutData(pluginOptionLabelGridData);
		pluginOptionLabel.setText(pluginOptionLabelText);
		
		// Textfield "Option X"		
		GridData optionValueTextGridData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		optionValueTextGridData.verticalIndent = 0;
		optionValueTextGridData.horizontalSpan = (pluginField.getType() == File.class) ? 2 : 3;
		optionValueTextGridData.minimumWidth = 200;
		optionValueTextGridData.grabExcessHorizontalSpace = true;

		int optionValueTextStyle = (pluginOption.isSensitive()) ? SWT.BORDER | SWT.PASSWORD : SWT.BORDER;
		
		final Text pluginOptionValueText = new Text(this, optionValueTextStyle);
		pluginOptionValueText.setLayoutData(optionValueTextGridData);
		pluginOptionValueText.setBackground(WidgetDecorator.WHITE);
		
		setPluginOptionDefaultValue(pluginOptionValueText, pluginField);
		setPluginOptionModifyListener(pluginOption, pluginOptionValueText);
		setPluginOptionVerifyListener(pluginOption, pluginOptionValueText);
	
		WidgetDecorator.normal(pluginOptionValueText);

		// Add 'Select ..' button for 'File' fields
		if (pluginField.getType() == File.class) {
			Button pluginOptionFileSelectButton = new Button(this, SWT.NONE);
			pluginOptionFileSelectButton.setText("Select ...");
			
			setPluginOptionFileSelectListener(pluginOption, pluginOptionValueText, pluginOptionFileSelectButton);			
		}
		
		// Set cache
		pluginOptionControlMap.put(pluginOption, pluginOptionValueText);
	}

	private void setPluginOptionFileSelectListener(final TransferPluginOption pluginOption, final Text pluginOptionValueText,
			final Button pluginOptionFileSelectButton) {
		
		pluginOptionFileSelectButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				onSelectFileClick(pluginOptionValueText);
			}
		});
	}

	private void setPluginOptionDefaultValue(Text pluginOptionValueText, Field pluginField) {
		try {
			String defaultValue = pluginSettings.getField(pluginField.getName());
			
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
				modifyPluginOptionText(pluginOption, pluginOptionValueText);				
			}			
		});
	}
	
	private void modifyPluginOptionText(TransferPluginOption pluginOption, Text pluginOptionValueText) {
		try {
			// Set field (at least try to; fails if type mismatches)
			logger.log(Level.INFO, "Setting field '" + pluginOption.getName() + "' with value '" + pluginOptionValueText.getText() + "'");						
			pluginSettings.setField(pluginOption.getField().getName(), pluginOptionValueText.getText());
			
			// Validate value (fails if content mismatches)
	        ValidationResult validationResult = pluginOption.isValid(pluginOptionValueText.getText());
			
	        switch (validationResult) {
	        case INVALID_NOT_SET:
	        	if (pluginOption.isRequired()) {
	        		invalidPluginOptions.add(pluginOption);
	        		WidgetDecorator.markAsInvalid(pluginOptionValueText);
	        	}
	        	else {
	        		invalidPluginOptions.remove(pluginOption);
	        		WidgetDecorator.markAsValid(pluginOptionValueText);
	        	}
	        	
	        	break;
	        	
	        case INVALID_TYPE:
	        	logger.log(Level.SEVERE, " Invalid type in field '" + pluginOption.getName() + "'. This should be caught by verify listener!");
	    		
	        	invalidPluginOptions.add(pluginOption);
	    		WidgetDecorator.markAsInvalid(pluginOptionValueText);

	    		break;
	    		
	        case VALID:
	    		invalidPluginOptions.remove(pluginOption);
	    		WidgetDecorator.markAsValid(pluginOptionValueText);

	    		break;
	        }
		}
		catch (StorageException e) {
			logger.log(Level.WARNING, "Cannot set field '" + pluginOption.getName() + "' with value '" + pluginOptionValueText.getText() + "'", e);						

        	invalidPluginOptions.add(pluginOption);
        	WidgetDecorator.markAsInvalid(pluginOptionValueText);
		}	
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
	

	private void onSelectFileClick(Text pluginOptionValueText) {
		DirectoryDialog directoryDialog = new DirectoryDialog(getShell());
		directoryDialog.setFilterPath(pluginOptionValueText.getText());
		
		String selectedFolder = directoryDialog.open();

		if (selectedFolder != null && selectedFolder.length() > 0) {
			pluginOptionValueText.setText(selectedFolder);
		}
	}

	@Override
	public boolean validatePanel() {
		hideWarning();
		
		// Validation order is important, because the validate*() methods
		// also mark fields 'red'. Also: OAuth needs to be before 
		// cross-field dependencies!
		
		boolean individualFieldsValid = validateIndividualFields();
		boolean oAuthFieldsValid = validateOAuthToken();
		
		return individualFieldsValid && oAuthFieldsValid && validateFieldDependencies();
	}
	
	private boolean validateIndividualFields() {
		for (Map.Entry<TransferPluginOption, Text> optionControlEntry : pluginOptionControlMap.entrySet()) {
			TransferPluginOption pluginOption = optionControlEntry.getKey();
			Text pluginOptionText = optionControlEntry.getValue();
			
			modifyPluginOptionText(pluginOption, pluginOptionText);
		}
		
		boolean validFields = invalidPluginOptions.size() == 0;
		
		if (validFields) {
			return true;
		}
		else {
			showWarning("Please fill check that you've filled all fields correctly.");
			return false;
		}
	}
	
	private boolean validateFieldDependencies() {
		try {
			pluginSettings.validateRequiredFields();		

			logger.log(Level.WARNING, "Validation succeeded on panel.");			
			return true;
		}
		catch (StorageException e) {
			showWarning(e.getMessage());
			
			logger.log(Level.WARNING, "Validate error on panel.", e);			
			return false;
		}		
	}
	
	private boolean validateOAuthToken() {
		if (oAuthGenerator != null) {
			if (oAuthText.getText().isEmpty()) {
				showWarning("Please fill the token field. It's there for a reason.");
				WidgetDecorator.markAsInvalid(oAuthText);
				
				logger.log(Level.INFO, "OAuth token is empty.");
				return false;
			}
			else {
				try {				
					oAuthGenerator.checkToken(oAuthText.getText()); // Sets pluginSettings.accessToken, or similar!				
					WidgetDecorator.markAsValid(oAuthText);
					
					logger.log(Level.INFO, "OAuth token check succeeded.");
					return true;
				}
				catch (Exception e) {
					showWarning("Invalid auth token. Please retry authenticating.");
					WidgetDecorator.markAsInvalid(oAuthText);
					
					logger.log(Level.INFO, "OAuth token check failed. ", e);
					return false;
				}
			}
		}
		else {
			return true;
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
		return pluginSettings;
	}
}
