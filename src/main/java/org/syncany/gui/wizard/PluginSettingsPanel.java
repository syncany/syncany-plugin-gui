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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.syncany.gui.Panel;
import org.syncany.gui.util.DesktopUtil;
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.SWTResourceManager;
import org.syncany.gui.util.WidgetDecorator;
import org.syncany.plugins.transfer.FileType;
import org.syncany.plugins.transfer.StorageException;
import org.syncany.plugins.transfer.TransferPlugin;
import org.syncany.plugins.transfer.TransferPluginOption;
import org.syncany.plugins.transfer.TransferPluginOption.ValidationResult;
import org.syncany.plugins.transfer.TransferPluginOptions;
import org.syncany.plugins.transfer.TransferSettings;
import org.syncany.plugins.transfer.oauth.OAuth;
import org.syncany.plugins.transfer.oauth.OAuthGenerator;
import org.syncany.plugins.transfer.oauth.OAuthGenerator.WithExtractor;
import org.syncany.plugins.transfer.oauth.OAuthGenerator.WithInterceptor;
import org.syncany.plugins.transfer.oauth.OAuthTokenFinish;
import org.syncany.plugins.transfer.oauth.OAuthTokenWebListener;
import org.syncany.plugins.transfer.oauth.OAuthTokenWebListener.Builder;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class PluginSettingsPanel extends Panel {
	private static final Logger logger = Logger.getLogger(PluginSettingsPanel.class.getSimpleName());
	private static final int OAUTH_TOKEN_WAIT_TIMEOUT = 60;

	private Label warningImageLabel;
	private Label warningMessageLabel;

	private TransferPlugin plugin;
	private TransferSettings pluginSettings;

	private OAuth oAuthSettings;
	private OAuthGenerator oAuthGenerator;
	private Button oAuthAuthorizeButton;
	private Text oAuthTokenText;
	private URI oAuthUrl;
	private Future<OAuthTokenFinish> oAuthTokenFinish;
	private boolean oAuthTokenReceived;
	private boolean oAuthTokenValid;

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

			this.oAuthSettings = null;
			this.oAuthGenerator = null;
			this.oAuthTokenReceived = false;
			this.oAuthTokenValid = false;

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
		oAuthSettings = pluginSettings.getClass().getAnnotation(OAuth.class);

		if (oAuthSettings != null) {
			try {
				Constructor<? extends OAuthGenerator> optionCallbackClassConstructor = oAuthSettings.value().getDeclaredConstructor(pluginSettings.getClass());
				oAuthGenerator = optionCallbackClassConstructor.newInstance(pluginSettings);
			}
			catch (Exception e) {
				throw new RuntimeException(e);
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

			oAuthTokenText = new Text(this, SWT.BORDER);
			oAuthTokenText.setLayoutData(oAuthTokenTextGridData);
			oAuthTokenText.setBackground(WidgetDecorator.WHITE);
			oAuthTokenText.setEditable(false);

			WidgetDecorator.normal(oAuthTokenText);

			// Add 'Authorize ..' button for 'File' fields
			oAuthAuthorizeButton = new Button(this, SWT.NONE);
			oAuthAuthorizeButton.setText(I18n.getText("org.syncany.gui.wizard.PluginSettingsPanel.oauth.button.connecting"));
			oAuthAuthorizeButton.setEnabled(false);

			oAuthAuthorizeButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					waitForTokenResponse();
					DesktopUtil.launch(oAuthUrl.toString());
				}
			});

			// Asynchronously get OAuth URL
			asyncRetrieveOAuthUrlAndEnableAuthButton();
		}
	}

	private void asyncRetrieveOAuthUrlAndEnableAuthButton() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					// Start the token listener
					Builder tokenListerBuilder = OAuthTokenWebListener.forMode(oAuthSettings.mode());

					if (oAuthSettings.callbackPort() != OAuth.RANDOM_PORT) {
						tokenListerBuilder.setPort(oAuthSettings.callbackPort());
					}

					if (!oAuthSettings.callbackId().equals(OAuth.PLUGIN_ID)) {
						tokenListerBuilder.setId(oAuthSettings.callbackId());
					}

					// Non standard plugin?
					if (oAuthGenerator instanceof WithInterceptor) {
						tokenListerBuilder.setTokenInterceptor(((WithInterceptor) oAuthGenerator).getInterceptor());
					}

					if (oAuthGenerator instanceof WithExtractor) {
						tokenListerBuilder.setTokenExtractor(((WithExtractor) oAuthGenerator).getExtractor());
					}

					OAuthTokenWebListener tokenListener = tokenListerBuilder.build();

					oAuthUrl = oAuthGenerator.generateAuthUrl(tokenListener.start());
					logger.log(Level.INFO, "OAuth URL generated: " + oAuthUrl);
					
					oAuthTokenFinish = tokenListener.getToken();

					Display.getDefault().asyncExec(new Runnable() {
						@Override
						public void run() {
							oAuthAuthorizeButton.setText(I18n.getText("org.syncany.gui.wizard.PluginSettingsPanel.oauth.button.authorize"));							
							oAuthAuthorizeButton.setEnabled(true);													
						}
					});
				}
				catch (final Exception e) {
					Display.getDefault().asyncExec(new Runnable() {
						@Override
						public void run() {
							showWarning(I18n.getText("org.syncany.gui.wizard.PluginSettingsPanel.oauth.errorCannotRetrieveOAuthURL", e.getMessage()));
							logger.log(Level.WARNING, "Cannot retrieve OAuth URL.", e);

							oAuthAuthorizeButton.setText(I18n.getText("org.syncany.gui.wizard.PluginSettingsPanel.oauth.button.error"));
						}
					});
				}
			}

		}, "GetOAuthUrl").start();
	}

	private void waitForTokenResponse() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					final OAuthTokenFinish tokenResponse = oAuthTokenFinish.get(OAUTH_TOKEN_WAIT_TIMEOUT, TimeUnit.SECONDS);

					if (tokenResponse != null) {
						oAuthGenerator.checkToken(tokenResponse.getToken(), tokenResponse.getCsrfState());
						logger.log(Level.INFO, "Token " + tokenResponse.getToken() + " received and field set");

						Display.getDefault().asyncExec(new Runnable() {
							@Override
							public void run() {
								oAuthTokenText.setText(tokenResponse.getToken());
								WidgetDecorator.markAsValid(oAuthTokenText);
							}
						});

						oAuthTokenValid = true;
					}
					else {
						Display.getDefault().asyncExec(new Runnable() {
							@Override
							public void run() {
								showWarning(I18n.getText("org.syncany.gui.wizard.PluginSettingsPanel.errorInvalidOAuthToken"));
								WidgetDecorator.markAsInvalid(oAuthTokenText);
							}
						});
					}
				}
				catch (TimeoutException e) {
					logger.log(Level.WARNING, "Unable to receive token in the given timeout", e);

					Display.getDefault().asyncExec(new Runnable() {
						@Override
						public void run() {
							showWarning(I18n.getText("org.syncany.gui.wizard.PluginSettingsPanel.errorTimeoutOAuthToken"));
							WidgetDecorator.markAsInvalid(oAuthTokenText);
						}
					});
					
					asyncRetrieveOAuthUrlAndEnableAuthButton();
				}
				catch (InterruptedException | ExecutionException | StorageException e) {
					logger.log(Level.SEVERE, "Unable to receive token", e);

					Display.getDefault().asyncExec(new Runnable() {
						@Override
						public void run() {
							showWarning(I18n.getText("org.syncany.gui.wizard.PluginSettingsPanel.errorExceptionOAuthToken"));
							WidgetDecorator.markAsInvalid(oAuthTokenText);
						}
					});
				}
				finally {
					oAuthTokenReceived = true;

					Display.getDefault().asyncExec(new Runnable() {
						@Override
						public void run() {
							oAuthAuthorizeButton.setEnabled(false);
						}
					});
				}
			}
		}, "WaitOAuthToken").start();
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
			pluginOptionFileSelectButton.setText(I18n.getText("org.syncany.gui.wizard.PluginSettingsPanel.selectFile"));

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
				onSelectFileClick(pluginOption, pluginOptionValueText);
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
		return oAuthGenerator == null || oAuthTokenReceived && oAuthTokenValid;
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
