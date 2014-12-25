/*
 * Syncany, www.syncany.org
 * Copyright (C) 2011-2014 Philipp C. Heckel <philipp.heckel@gmail.com> 
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.syncany.gui.wizard;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.syncany.config.to.ConfigTO;
import org.syncany.config.to.DefaultRepoTOFactory;
import org.syncany.config.to.RepoTOFactory;
import org.syncany.crypto.CipherSpec;
import org.syncany.crypto.CipherSpecs;
import org.syncany.crypto.CipherUtil;
import org.syncany.gui.util.I18n;
import org.syncany.gui.wizard.FolderSelectPanel.SelectFolderValidationMethod;
import org.syncany.gui.wizard.WizardDialog.Action;
import org.syncany.operations.daemon.messages.InitManagementRequest;
import org.syncany.operations.daemon.messages.InitManagementResponse;
import org.syncany.operations.init.InitOperationOptions;
import org.syncany.plugins.transfer.StorageTestResult;
import org.syncany.plugins.transfer.TransferPlugin;

import com.google.common.eventbus.Subscribe;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class InitPanelController extends ReloadDaemonPanelController {
	private static final Logger logger = Logger.getLogger(InitPanelController.class.getSimpleName());

	private StartPanel startPanel;
	private FolderSelectPanel folderSelectPanel;
	private PluginSelectPanel pluginSelectPanel;
	private PluginSettingsPanel pluginSettingsPanel;
	private ChoosePasswordPanel choosePasswordPanel;
	private ProgressPanel progressPanel;

	private TransferPlugin selectedPlugin;

	public InitPanelController(WizardDialog wizardDialog, StartPanel startPanel, FolderSelectPanel folderSelectPanel,
			PluginSelectPanel pluginSelectPanel, PluginSettingsPanel pluginSettingsPanel, ChoosePasswordPanel choosePasswordPanel,
			ProgressPanel progressPanel) {

		super(wizardDialog, progressPanel);

		this.startPanel = startPanel;
		this.folderSelectPanel = folderSelectPanel;
		this.pluginSelectPanel = pluginSelectPanel;
		this.pluginSettingsPanel = pluginSettingsPanel;
		this.choosePasswordPanel = choosePasswordPanel;
		this.progressPanel = progressPanel;

		this.selectedPlugin = null;
	}

	@Override
	public void handleFlow(Action clickAction) {
		if (wizardDialog.getCurrentPanel() == startPanel) {
			if (clickAction == Action.NEXT) {
				folderSelectPanel.reset(SelectFolderValidationMethod.NO_APP_FOLDER);
				folderSelectPanel.setDescriptionText(I18n.getString("dialog.selectLocalFolder.watchIntroduction"));

				wizardDialog.validateAndSetCurrentPanel(folderSelectPanel, Action.PREVIOUS, Action.NEXT);
			}
		}
		else if (wizardDialog.getCurrentPanel() == folderSelectPanel) {
			if (clickAction == Action.PREVIOUS) {
				wizardDialog.setCurrentPanel(startPanel, Action.NEXT);
			}
			else if (clickAction == Action.NEXT) {
				wizardDialog.validateAndSetCurrentPanel(pluginSelectPanel, Action.PREVIOUS, Action.NEXT);
			}
		}
		else if (wizardDialog.getCurrentPanel() == pluginSelectPanel) {
			if (clickAction == Action.PREVIOUS) {
				wizardDialog.setCurrentPanel(folderSelectPanel, Action.PREVIOUS, Action.NEXT);
			}
			else if (clickAction == Action.NEXT) {
				boolean pluginIsSet = pluginSelectPanel.getSelectedPlugin() != null;
				boolean pluginNewOrChanged = selectedPlugin == null || selectedPlugin != pluginSelectPanel.getSelectedPlugin();

				if (pluginIsSet && pluginNewOrChanged) {
					selectedPlugin = pluginSelectPanel.getSelectedPlugin();
					pluginSettingsPanel.init(selectedPlugin);
				}

				wizardDialog.validateAndSetCurrentPanel(pluginSettingsPanel, Action.PREVIOUS, Action.NEXT);
			}
		}
		else if (wizardDialog.getCurrentPanel() == pluginSettingsPanel) {
			if (clickAction == Action.PREVIOUS) {
				wizardDialog.setCurrentPanel(pluginSelectPanel, Action.PREVIOUS, Action.NEXT);
			}
			else if (clickAction == Action.NEXT) {
				wizardDialog.validateAndSetCurrentPanel(choosePasswordPanel, Action.PREVIOUS, Action.NEXT);
			}
		}
		else if (wizardDialog.getCurrentPanel() == choosePasswordPanel) {
			if (clickAction == Action.PREVIOUS) {
				wizardDialog.setCurrentPanel(pluginSettingsPanel, Action.PREVIOUS, Action.NEXT);
			}
			else if (clickAction == Action.NEXT) {
				progressPanel.setTitleText("Initializing remote repository");
				progressPanel.setDescriptionText("Syncany is creating a repository for you. This might take a while.");

				boolean panelValid = wizardDialog.validateAndSetCurrentPanel(progressPanel);

				if (panelValid) {
					sendInitRequest();
				}
			}
		}
		else if (wizardDialog.getCurrentPanel() == progressPanel) {
			if (clickAction == Action.PREVIOUS) {
				wizardDialog.setCurrentPanel(choosePasswordPanel, Action.PREVIOUS, Action.NEXT);
			}
			else if (clickAction == Action.NEXT) {
				wizardDialog.validateAndSetCurrentPanel(startPanel);
			}
		}
	}

	private void sendInitRequest() {
		try {
			// Cipher specs: --no-encryption, --advanced
			List<CipherSpec> cipherSpecs = CipherSpecs.getDefaultCipherSpecs();

			// Compression: --no-compression
			RepoTOFactory repoTOFactory = new DefaultRepoTOFactory(true, cipherSpecs);

			ConfigTO configTO = new ConfigTO();
			configTO.setDisplayName(System.getProperty("user.name"));
			configTO.setMachineName(CipherUtil.createRandomAlphabeticString(20));
			configTO.setTransferSettings(pluginSettingsPanel.getPluginSettings());

			InitOperationOptions initOptions = new InitOperationOptions();

			initOptions.setLocalDir(folderSelectPanel.getFolder());
			initOptions.setCreateTarget(true);
			initOptions.setEncryptionEnabled(true);
			initOptions.setPassword(choosePasswordPanel.getPassword());
			initOptions.setConfigTO(configTO);
			initOptions.setCipherSpecs(cipherSpecs);
			initOptions.setRepoTO(repoTOFactory.createRepoTO());
			initOptions.setDaemon(true);

			InitManagementRequest initManagementRequest = new InitManagementRequest(initOptions);

			progressPanel.resetPanel(3);
			progressPanel.appendLog("Initializing repo for folder " + folderSelectPanel.getFolder() + " ... ");

			eventBus.post(initManagementRequest);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Subscribe
	public void onInitResponse(InitManagementResponse response) {
		logger.log(Level.INFO, "Received response from daemon: " + response);

		if (response.getCode() == 200) {
			sendReloadDaemonAndMenusCommand();			
		}
		else {
			String errorMessage = "ERROR.\n\nUnable to initialize folder (code: " + response.getCode() + ")\n";
			
			switch (response.getCode()) {
			case InitManagementResponse.NOK_FAILED_TEST:
				StorageTestResult testResult = response.getResult().getTestResult();
				
				errorMessage += "Testing the remote storage failed.\n\n"
						+ "- Was the connection successful: " + toYesNo(testResult.isTargetCanConnect()) + "\n"
						+ "- Files can be created: " + toYesNo(testResult.isTargetCanCreate()) + "\n"
						+ "- Files can be written to: " + toYesNo(testResult.isTargetCanWrite()) + "\n"
						+ "- The target folder/repo exists: " + toYesNo(testResult.isTargetExists()) + "\n";
				
				if (testResult.getErrorMessage() != null) {
					errorMessage += "\nDetailed error message:\n\n" + testResult.getErrorMessage();
				}
				 
				break;
			
			//case InitManagementResponse.NOK_FAILED_UNKNOWN:
				//break;
			case InitManagementResponse.NOK_OPERATION_FAILED:
				errorMessage += "The operation failed entirely. The following exception was thrown:\n\n"
						+ response.getMessage(); 
				
				break;

			default: 
				break;
			}	
			
			progressPanel.finish();
			progressPanel.setShowDetails(true);
			progressPanel.appendLog(errorMessage);

			wizardDialog.setAllowedActions(Action.PREVIOUS);
		}
	}

	private String toYesNo(boolean value) {
		return value ? "YES" : "NO";
	}
}
