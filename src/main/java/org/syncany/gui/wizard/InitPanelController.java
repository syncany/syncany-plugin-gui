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

import java.io.File;
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
import org.syncany.operations.daemon.messages.ListWatchesManagementResponse;
import org.syncany.operations.init.GenlinkOperationOptions;
import org.syncany.operations.init.InitOperationOptions;
import org.syncany.operations.init.InitOperationResult;
import org.syncany.plugins.transfer.TransferPlugin;

import com.google.common.eventbus.Subscribe;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class InitPanelController extends AbstractInitPanelController {
	private static final Logger logger = Logger.getLogger(InitPanelController.class.getSimpleName());

	private StartPanel startPanel;
	private FolderSelectPanel folderSelectPanel;
	private PluginSelectPanel pluginSelectPanel;
	private PluginSettingsPanel pluginSettingsPanel;
	private ChoosePasswordPanel choosePasswordPanel;
	private ProgressPanel progressPanel;
	private InitSuccessPanel initSuccessPanel;
	
	private File localDir;
	private TransferPlugin selectedPlugin;
	private InitOperationResult initResult;

	public InitPanelController(WizardDialog wizardDialog, StartPanel startPanel, FolderSelectPanel folderSelectPanel,
			PluginSelectPanel pluginSelectPanel, PluginSettingsPanel pluginSettingsPanel, ChoosePasswordPanel choosePasswordPanel,
			ProgressPanel progressPanel, InitSuccessPanel initSuccessPanel) {

		super(wizardDialog, progressPanel);

		this.startPanel = startPanel;
		this.folderSelectPanel = folderSelectPanel;
		this.pluginSelectPanel = pluginSelectPanel;
		this.pluginSettingsPanel = pluginSettingsPanel;
		this.choosePasswordPanel = choosePasswordPanel;
		this.progressPanel = progressPanel;
		this.initSuccessPanel = initSuccessPanel;

		this.localDir = null;
		this.selectedPlugin = null;
		this.initResult = null;
	}

	@Override
	public void handleFlow(Action clickAction) {
		if (wizardDialog.getCurrentPanel() == startPanel) {
			if (clickAction == Action.NEXT) {
				folderSelectPanel.reset(SelectFolderValidationMethod.NO_APP_FOLDER);
				folderSelectPanel.setDescriptionText(I18n.getText("org.syncany.gui.wizard.FolderSelectPanel.init.description"));

				wizardDialog.validateAndSetCurrentPanel(folderSelectPanel, Action.PREVIOUS, Action.NEXT);
			}
		}
		else if (wizardDialog.getCurrentPanel() == folderSelectPanel) {
			if (clickAction == Action.PREVIOUS) {
				wizardDialog.setCurrentPanel(startPanel, Action.NEXT);
			}
			else if (clickAction == Action.NEXT) {
				localDir = folderSelectPanel.getFolder();
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
				progressPanel.setTitleText(I18n.getText("org.syncany.gui.wizard.ProgressPanel.init.title"));
				progressPanel.setDescriptionText(I18n.getText("org.syncany.gui.wizard.ProgressPanel.init.description"));

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

			GenlinkOperationOptions genlinkOptions = new GenlinkOperationOptions();
			genlinkOptions.setShortUrl(true);
			
			InitOperationOptions initOptions = new InitOperationOptions();

			initOptions.setGenlinkOptions(genlinkOptions);
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
			progressPanel.appendLog(I18n.getText("org.syncany.gui.wizard.ProgressPanel.init.initializingRepo", folderSelectPanel.getFolder()));

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
			if (response.getResult() != null) {
				initResult = response.getResult();
			}
			
			sendReloadDaemonAndMenusCommand();			
		}
		else {
			String errorMessage = formatErrorMessage(response); 			
			
			progressPanel.finish();
			progressPanel.setShowDetails(true);
			progressPanel.appendLog(errorMessage);

			wizardDialog.setAllowedActions(Action.PREVIOUS);
		}
	}
	
	@Subscribe
	public void onListWatchesManagementResponse(ListWatchesManagementResponse response) {
		if (initResult != null && initResult.getGenLinkResult() != null) {
			String applicationLink = initResult.getGenLinkResult().getShareLink();
			
			initSuccessPanel.setApplicationLinkText(applicationLink);
			initSuccessPanel.setLocalDir(localDir);
	
			wizardDialog.setCurrentPanel(initSuccessPanel, Action.FINISH);
		}
	}

	private String formatErrorMessage(InitManagementResponse response) {		
		String errorMessage = I18n.getText("org.syncany.gui.wizard.ProgressPanel.error") + "\n\n"
				+ I18n.getText("org.syncany.gui.wizard.ProgressPanel.init.unableToInit", response.getCode()) + "\n";
		
		switch (response.getCode()) {
		case InitManagementResponse.NOK_FAILED_TEST:
			errorMessage += formatTestResultMessage(response.getResult().getTestResult());				 
			break;
		
		case InitManagementResponse.NOK_FAILED_UNKNOWN:
			errorMessage += I18n.getText("org.syncany.gui.wizard.ProgressPanel.init.failedWithUnknownError");				
			break;
			
		case InitManagementResponse.NOK_OPERATION_FAILED:
			errorMessage += I18n.getText("org.syncany.gui.wizard.ProgressPanel.init.failedWithException");
			break;

		default: 
			break;
		}	
		
		if (response.getMessage() != null) {
			errorMessage += "\n\n" + response.getMessage();
		}
		
		return errorMessage;
	}
}
