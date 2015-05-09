package org.syncany.gui.wizard;

import java.io.File;
import java.lang.reflect.Field;
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
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.syncany.gui.Panel;
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.SWTResourceManager;
import org.syncany.gui.util.WidgetDecorator;
import org.syncany.gui.wizard.PluginSettingsPanelOAuthHelper.Consumer;
import org.syncany.plugins.transfer.FileType;
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

	private TransferPlugin plugin;
	private TransferSettings pluginSettings;
	private static PluginSettingsPanelOAuthHelper pluginSettingsPanelOAuthHelper;

	private Map<TransferPluginOption, Control> pluginOptionControlMap;
	private Set<TransferPluginOption> invalidPluginOptions;

	public PluginSettingsPanel(WizardDialog wizardParentDialog, Composite parent, int style) {
		super(wizardParentDialog, parent, style);
	}

	@Override
	public void dispose() {
		logger.log(Level.INFO, "PluginSettingsPanel is about to get disposed, resetting OAuthhelper");
		resetOAuthHelper();
	}

	public void init(TransferPlugin plugin) {
		setPlugin(plugin);

		resetOAuthHelper();
		clearControls();

		createControls();
	}

	private void setPlugin(TransferPlugin plugin) {
		try {
			this.plugin = plugin;
			this.pluginSettings = plugin.createEmptySettings();

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

	private void resetOAuthHelper() {
		if (pluginSettingsPanelOAuthHelper != null) {
			pluginSettingsPanelOAuthHelper.reset(false);
			pluginSettingsPanelOAuthHelper = null;
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
		titleLabel.setText(I18n.getText("org.syncany.gui.wizard.PluginSettingsPanel.title", plugin.getName()));

		WidgetDecorator.title(titleLabel);

		// Create OAuth controls (if any)
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
		resetOAuthHelper();

		PluginSettingsPanelOAuthHelper.Builder builder;

		try {
			builder = PluginSettingsPanelOAuthHelper.forSettings(pluginSettings);
		}
		catch (UnsupportedOperationException e) {
			// ok plugin does not support oauth
			return;
		}

		// OAuth help text
		Label descriptionLabel = new Label(this, SWT.WRAP);
		descriptionLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false, 3, 1));
		descriptionLabel.setText(I18n.getText("org.syncany.gui.wizard.PluginSettingsPanel.oauth.description"));

		WidgetDecorator.normal(descriptionLabel);

		// Label "Token:"
		GridData oAuthTokenLabelGridData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
		oAuthTokenLabelGridData.verticalIndent = 2;
		oAuthTokenLabelGridData.horizontalSpan = 3;

		Label oAuthTokenLabel = new Label(this, SWT.WRAP);
		oAuthTokenLabel.setLayoutData(oAuthTokenLabelGridData);
		oAuthTokenLabel.setText(I18n.getText("org.syncany.gui.wizard.PluginSettingsPanel.oauth.token"));

		// Textfield "Token"
		GridData oAuthTokenTextGridData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		oAuthTokenTextGridData.verticalIndent = 0;
		oAuthTokenTextGridData.horizontalSpan = 2;
		oAuthTokenTextGridData.minimumWidth = 200;
		oAuthTokenTextGridData.grabExcessHorizontalSpace = true;

		// Do not manage contents of the following GUI items, done by OAuthInformationManager
		Text oAuthTokenText = new Text(this, SWT.BORDER);
		oAuthTokenText.setLayoutData(oAuthTokenTextGridData);
		oAuthTokenText.setBackground(WidgetDecorator.WHITE);

		// Add 'Authorize ..' button for 'File' fields
		Button oAuthAuthorizeButton = new Button(this, SWT.NONE);
		oAuthAuthorizeButton.setText(I18n.getText("org.syncany.gui.wizard.PluginSettingsPanel.oauth.button.connecting")); // needs text for size

		try {
			pluginSettingsPanelOAuthHelper = builder
							.withWarningHandler(new WarningHandler())
							.withButton(oAuthAuthorizeButton)
							.withText(oAuthTokenText)
							.build();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

		pluginSettingsPanelOAuthHelper.start();
	}

	private void createPluginOptionControl(final TransferPluginOption pluginOption) {
		Field pluginField = pluginOption.getField();

		// Label "Option X:"
		GridData pluginOptionLabelGridData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
		pluginOptionLabelGridData.verticalIndent = 2;
		pluginOptionLabelGridData.horizontalSpan = 3;

		String pluginOptionLabelText = pluginOption.getDescription();

		if (pluginOption.isSensitive()) {
			pluginOptionLabelText += " " + ((pluginOption.isRequired())
							? I18n.getText("org.syncany.gui.wizard.PluginSettingsPanel.pluginOptionLabelExt.notDisplayed")
							: I18n.getText("org.syncany.gui.wizard.PluginSettingsPanel.pluginOptionLabelExt.notDisplayedOptional"));
		}
		else {
			pluginOptionLabelText += (pluginOption.isRequired()) ? "" : " " + I18n.getText("org.syncany.gui.wizard.PluginSettingsPanel.pluginOptionLabelExt.optional");
		}

		Label pluginOptionLabel = new Label(this, SWT.WRAP);
		pluginOptionLabel.setLayoutData(pluginOptionLabelGridData);
		pluginOptionLabel.setText(pluginOptionLabelText);

		Control pluginOptionControl;
		
		if (pluginField.getType() == File.class) {
			pluginOptionControl = createPluginOptionFileControl(pluginOption, pluginField);						
		}
		else if (pluginField.getType() instanceof Class && ((Class<?>) pluginField.getType()).isEnum()) {
			pluginOptionControl = createPluginOptionEnumControl(pluginOption, pluginField);			
		}
		else {
			pluginOptionControl = createPluginOptionTextControl(pluginOption, pluginField);					
		}
		
		// Set cache
		pluginOptionControlMap.put(pluginOption, pluginOptionControl);
	}

	private Control createPluginOptionFileControl(TransferPluginOption pluginOption, Field pluginField) {
		// Create controls
		Text pluginOptionValueText = createPluginOptionTextField(pluginOption, pluginField, 2);
		createPluginOptionFileSelectButton(pluginOption, pluginOptionValueText);
		
		return pluginOptionValueText;
	}
	
	private Control createPluginOptionEnumControl(TransferPluginOption pluginOption, Field pluginField) {
		Combo pluginOptionCombo = new Combo(this, SWT.DROP_DOWN | SWT.BORDER | SWT.READ_ONLY);
		pluginOptionCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		for (Object enumValue : pluginField.getType().getEnumConstants()) {
			pluginOptionCombo.add(enumValue.toString());				
		}					
		
		// Listeners
		setPluginOptionEnumModifyListener(pluginOption, pluginOptionCombo);

		// Set value
		pluginOptionCombo.select(0);
		modifyPluginOptionEnum(pluginOption, pluginOptionCombo);		
		
		return pluginOptionCombo;
	}

	private void setPluginOptionEnumModifyListener(final TransferPluginOption pluginOption, final Combo pluginOptionCombo) {
		pluginOptionCombo.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				modifyPluginOptionEnum(pluginOption, pluginOptionCombo);
			}
		});
	}

	private Control createPluginOptionTextControl(TransferPluginOption pluginOption, Field pluginField) {
		return createPluginOptionTextField(pluginOption, pluginField, 2);
	}

	private Text createPluginOptionTextField(TransferPluginOption pluginOption, Field pluginField, int horizontalSpan) {
		// Textfield "Option X"
		GridData optionValueTextGridData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		optionValueTextGridData.verticalIndent = 0;
		optionValueTextGridData.horizontalSpan = horizontalSpan;
		optionValueTextGridData.minimumWidth = 200;
		optionValueTextGridData.grabExcessHorizontalSpace = true;

		int optionValueTextStyle = (pluginOption.isSensitive()) ? SWT.BORDER | SWT.PASSWORD : SWT.BORDER;

		Text pluginOptionValueText = new Text(this, optionValueTextStyle);
		pluginOptionValueText.setLayoutData(optionValueTextGridData);
		pluginOptionValueText.setBackground(WidgetDecorator.WHITE);

		setPluginOptionTextFieldDefaultValue(pluginOptionValueText, pluginField);
		setPluginOptionTextFieldModifyListener(pluginOption, pluginOptionValueText);
		setPluginOptionTextFieldVerifyListener(pluginOption, pluginOptionValueText);

		WidgetDecorator.normal(pluginOptionValueText);
		
		return pluginOptionValueText;
	}

	private Button createPluginOptionFileSelectButton(TransferPluginOption pluginOption, Text pluginOptionValueText) {
		Button pluginOptionFileSelectButton = new Button(this, SWT.NONE);
		pluginOptionFileSelectButton.setText(I18n.getText("org.syncany.gui.wizard.PluginSettingsPanel.selectFile"));

		setPluginOptionFileSelectListener(pluginOption, pluginOptionValueText, pluginOptionFileSelectButton);
		
		return pluginOptionFileSelectButton;
	}

	private void setPluginOptionFileSelectListener(final TransferPluginOption pluginOption, final Text pluginOptionValueText,
	                                               final Button pluginOptionFileSelectButton) {

		pluginOptionFileSelectButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				onSelectFileClick(pluginOption, pluginOptionValueText);
			}
		});
	}

	private void setPluginOptionTextFieldDefaultValue(Text pluginOptionValueText, Field pluginField) {
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

	private void setPluginOptionTextFieldModifyListener(final TransferPluginOption pluginOption, final Text pluginOptionValueText) {
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
					logger.log(Level.WARNING, " Invalid type in field '" + pluginOption.getName() + "'. This should be caught by verify listener!");

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
	
	private void modifyPluginOptionEnum(TransferPluginOption pluginOption, Combo pluginOptionCombo) {
		try {
			logger.log(Level.INFO, "Setting field '" + pluginOption.getName() + "' with value '" + pluginOptionCombo.getText() + "'");
			pluginSettings.setField(pluginOption.getField().getName(), pluginOptionCombo.getText());
		}
		catch (StorageException e) {
			throw new RuntimeException("Cannot set field '" + pluginOption.getName() + "' with value '" + pluginOptionCombo.getText()
					+ "'. This is an ENUM, so this should not happen.", e);
		}
	}

	private void setPluginOptionTextFieldVerifyListener(final TransferPluginOption pluginOption, final Text pluginOptionValueText) {
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

	private void onSelectFileClick(TransferPluginOption pluginOption, Text pluginOptionValueText) {
		if (pluginOption.getFileType() == FileType.FILE) {
			String filterPath = new File(pluginOptionValueText.getText()).getParent();

			FileDialog fileDialog = new FileDialog(getShell(), SWT.OPEN);
			fileDialog.setFilterExtensions(new String[]{"*.*"});
			fileDialog.setFilterPath(filterPath);

			String selectedFile = fileDialog.open();

			if (selectedFile != null && selectedFile.length() > 0) {
				pluginOptionValueText.setText(selectedFile);
			}
		}
		else {
			DirectoryDialog directoryDialog = new DirectoryDialog(getShell());
			directoryDialog.setFilterPath(pluginOptionValueText.getText());

			String selectedFolder = directoryDialog.open();

			if (selectedFolder != null && selectedFolder.length() > 0) {
				pluginOptionValueText.setText(selectedFolder);
			}
		}
	}

	@Override
	public boolean validatePanel() {
		hideWarning();

		logger.log(Level.INFO, "Validating settings panel ...");

		// Validation order is important, because the validate*() methods
		// also mark fields 'red'. Also: OAuth needs to be before
		// cross-field dependencies!

		boolean individualFieldsValid = validateIndividualFields();
		boolean oAuthFieldsValid = validateOAuthToken();

		return individualFieldsValid && oAuthFieldsValid && validateFieldDependencies();
	}

	private boolean validateIndividualFields() {
		logger.log(Level.INFO, " - Validating individual fields ...");

		for (Map.Entry<TransferPluginOption, Control> optionControlEntry : pluginOptionControlMap.entrySet()) {
			TransferPluginOption pluginOption = optionControlEntry.getKey();					
			Control pluginOptionControl = optionControlEntry.getValue();

			if (pluginOptionControl instanceof Text) {
				modifyPluginOptionText(pluginOption, (Text) pluginOptionControl);	
			}			
		}

		boolean validFields = invalidPluginOptions.size() == 0;

		if (validFields) {
			return true;
		}
		else {
			showWarning(I18n.getText("org.syncany.gui.wizard.PluginSettingsPanel.errorFieldValidation"));
			return false;
		}
	}

	private boolean validateFieldDependencies() {
		logger.log(Level.INFO, " - Validating field dependencies ...");

		try {
			pluginSettings.validateRequiredFields();

			logger.log(Level.INFO, "Validation succeeded on panel.");
			return true;
		}
		catch (StorageException e) {
			showWarning(e.getMessage());

			logger.log(Level.WARNING, "Validate error on panel.", e);
			return false;
		}
	}

	private boolean validateOAuthToken() {
		return pluginSettingsPanelOAuthHelper == null || pluginSettingsPanelOAuthHelper.isSuccess();
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

	private class WarningHandler implements Consumer<String> {
		@Override
		public void accept(final String warning) {
			Display.getDefault().asyncExec(new Runnable() {
				@Override
				public void run() {
					PluginSettingsPanel.this.showWarning(warning);
				}
			});
		}
	}
}
