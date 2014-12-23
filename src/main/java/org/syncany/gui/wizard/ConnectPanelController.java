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

import java.util.logging.Level;
import java.util.logging.Logger;

import org.syncany.config.to.ConfigTO;
import org.syncany.crypto.CipherUtil;
import org.syncany.gui.util.I18n;
import org.syncany.gui.wizard.ConnectTypeSelectPanel.ConnectPanelSelection;
import org.syncany.gui.wizard.FolderSelectPanel.SelectFolderValidationMethod;
import org.syncany.gui.wizard.WizardDialog.Action;
import org.syncany.operations.daemon.ControlServer.ControlCommand;
import org.syncany.operations.daemon.messages.ConnectManagementRequest;
import org.syncany.operations.daemon.messages.ConnectManagementResponse;
import org.syncany.operations.daemon.messages.ControlManagementRequest;
import org.syncany.operations.init.ApplicationLink;
import org.syncany.operations.init.ConnectOperationOptions;
import org.syncany.operations.init.ConnectOperationOptions.ConnectOptionsStrategy;
import org.syncany.plugins.transfer.TransferPlugin;

import com.google.common.eventbus.Subscribe;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class ConnectPanelController extends ReloadDaemonPanelController {
	private static final Logger logger = Logger.getLogger(ConnectPanelController.class.getSimpleName());	
	
	private StartPanel startPanel;
	private FolderSelectPanel folderSelectPanel;
	private ConnectTypeSelectPanel connectTypeSelectPanel;
	private PluginSettingsPanel pluginSettingsPanel;
	private EnterPasswordPanel enterPasswordPanel;
	private ProgressPanel progressPanel;
	
	private ConnectPanelSelection connectTypeSelection;
	private ApplicationLink applicationLink;
	private TransferPlugin selectedPlugin;

	public ConnectPanelController(WizardDialog wizardDialog, StartPanel startPanel, FolderSelectPanel folderSelectPanel,
			ConnectTypeSelectPanel connectTypeSelectPanel, PluginSettingsPanel pluginSettingsPanel, EnterPasswordPanel enterPasswordPanel,
			ProgressPanel progressPanel) {

		super(wizardDialog, progressPanel);
		
		this.startPanel = startPanel;
		this.folderSelectPanel = folderSelectPanel;
		this.connectTypeSelectPanel = connectTypeSelectPanel;
		this.pluginSettingsPanel = pluginSettingsPanel;
		this.enterPasswordPanel = enterPasswordPanel;
		this.progressPanel = progressPanel;
		
		this.connectTypeSelection = null;
		this.selectedPlugin = null;
	}

	@Override
	public void handleFlow(Action clickAction) {
		if (wizardDialog.getCurrentPanel() == startPanel) {
			handleFlowStartPanel(clickAction);			
		}
		else if (wizardDialog.getCurrentPanel() == folderSelectPanel) {
			handelFlowFolderSelectPanel(clickAction);			
		}
		else if (wizardDialog.getCurrentPanel() == connectTypeSelectPanel) {
			handleFlowConnectTypeSelectPanel(clickAction);
		}
		else if (wizardDialog.getCurrentPanel() == pluginSettingsPanel) {
			handleFlowPluginSettingsPanel(clickAction);
		}
		else if (wizardDialog.getCurrentPanel() == enterPasswordPanel) {
			handleFlowEnterPasswordPanel(clickAction);
		}
		else if (wizardDialog.getCurrentPanel() == progressPanel) {
			handleFlowProgressPanel(clickAction);
		}
	}
	
	private void handleFlowStartPanel(Action clickAction) {
		if (clickAction == Action.NEXT) {
			folderSelectPanel.setValidationMethod(SelectFolderValidationMethod.NO_APP_FOLDER);
			folderSelectPanel.setDescriptionText(I18n.getString("dialog.selectLocalFolder.watchIntroduction"));

			wizardDialog.validateAndSetCurrentPanel(folderSelectPanel, Action.PREVIOUS, Action.NEXT);
		}
	}
	
	private void handleFlowConnectTypeSelectPanel(Action clickAction) {
		if (clickAction == Action.PREVIOUS) {
			wizardDialog.setCurrentPanel(folderSelectPanel, Action.PREVIOUS, Action.NEXT);
		}
		else if (clickAction == Action.NEXT) {
			connectTypeSelection = connectTypeSelectPanel.getSelection();

			if (connectTypeSelection == ConnectPanelSelection.LINK) {
				boolean panelValid = connectTypeSelectPanel.validatePanel();
				
				if (panelValid) {
					applicationLink = connectTypeSelectPanel.getApplicationLink();
					
					if (applicationLink.isEncrypted()) {						
						wizardDialog.setCurrentPanel(enterPasswordPanel, Action.PREVIOUS, Action.NEXT);
					}
					else {
						initProgressPanelLabels();
						wizardDialog.setCurrentPanel(progressPanel, Action.PREVIOUS, Action.NEXT);
						
						sendConnectRequest();
					}
				}
			}
			else {
				boolean pluginIsSet = connectTypeSelectPanel.getSelectedPlugin() != null;
				boolean pluginNewOrChanged = selectedPlugin == null || selectedPlugin != connectTypeSelectPanel.getSelectedPlugin();
				
				if (pluginIsSet && pluginNewOrChanged) {
					selectedPlugin = connectTypeSelectPanel.getSelectedPlugin();
					pluginSettingsPanel.init(selectedPlugin);
				}
				
				wizardDialog.validateAndSetCurrentPanel(pluginSettingsPanel, Action.PREVIOUS, Action.NEXT);	
			}				
		}
	}

	private void handelFlowFolderSelectPanel(Action clickAction) {
		if (clickAction == Action.PREVIOUS) {
			wizardDialog.setCurrentPanel(startPanel, Action.NEXT);
		}
		else if (clickAction == Action.NEXT) {				
			wizardDialog.validateAndSetCurrentPanel(connectTypeSelectPanel, Action.PREVIOUS, Action.NEXT);
		}
	}

	private void handleFlowPluginSettingsPanel(Action clickAction) {
		if (clickAction == Action.PREVIOUS) {
			wizardDialog.setCurrentPanel(connectTypeSelectPanel, Action.PREVIOUS, Action.NEXT);
		}
		else if (clickAction == Action.NEXT) {
			wizardDialog.validateAndSetCurrentPanel(enterPasswordPanel, Action.PREVIOUS, Action.NEXT);
		}
	}

	private void handleFlowEnterPasswordPanel(Action clickAction) {
		if (clickAction == Action.PREVIOUS) {
			if (connectTypeSelection == ConnectPanelSelection.LINK) {
				wizardDialog.setCurrentPanel(connectTypeSelectPanel, Action.PREVIOUS, Action.NEXT);
			}
			else {
				wizardDialog.setCurrentPanel(pluginSettingsPanel, Action.PREVIOUS, Action.NEXT);	
			}					
		}
		else if (clickAction == Action.NEXT) {
			initProgressPanelLabels();
			boolean panelValid = wizardDialog.validateAndSetCurrentPanel(progressPanel);

			if (panelValid) {
				sendConnectRequest();
			}								
		}
	}

	private void handleFlowProgressPanel(Action clickAction) {
		if (clickAction == Action.NEXT) {
			wizardDialog.validateAndSetCurrentPanel(startPanel);
		}
	}

	private void initProgressPanelLabels() {
		progressPanel.setTitleText("Connecting to remote repository");
		progressPanel.setDescriptionText("Syncany is connecting to the remote repository for you. This might take a while.");
	}

	private void sendConnectRequest() {
		try {			
			ConfigTO configTO = new ConfigTO();
			configTO.setDisplayName(System.getProperty("user.name"));
			configTO.setMachineName(CipherUtil.createRandomAlphabeticString(20));

			ConnectOperationOptions connectOptions = new ConnectOperationOptions();
			
			connectOptions.setDaemon(true);
			connectOptions.setLocalDir(folderSelectPanel.getFolder());					
			connectOptions.setConfigTO(configTO);

			if (connectTypeSelectPanel.getSelection() == ConnectPanelSelection.LINK) {
				connectOptions.setStrategy(ConnectOptionsStrategy.CONNECTION_LINK);
				connectOptions.setConnectLink(connectTypeSelectPanel.getApplicationLinkText());
				
				if (applicationLink.isEncrypted()) {
					connectOptions.setPassword(enterPasswordPanel.getPassword());
				}
			}
			else {
				connectOptions.setStrategy(ConnectOptionsStrategy.CONNECTION_TO);	
				connectOptions.setPassword(enterPasswordPanel.getPassword());
				configTO.setTransferSettings(pluginSettingsPanel.getPluginSettings());
			}
						
			ConnectManagementRequest connectManagementRequest = new ConnectManagementRequest(connectOptions);
			
			progressPanel.resetPanel(3);
			progressPanel.appendLog("Connecting to repo for folder "+ folderSelectPanel.getFolder() + " ... ");
	
			eventBus.post(connectManagementRequest);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Subscribe
	public void onConnectResponse(ConnectManagementResponse response) {
		logger.log(Level.INFO, "Received response from daemon: " + response);
		
		if (response.getCode() == 200) {
			progressPanel.increase();
			progressPanel.appendLog("DONE.\nReloading daemon ... ");
			
			eventBus.post(new ControlManagementRequest(ControlCommand.RELOAD));
		}
		else {
			progressPanel.finish();
			progressPanel.setShowDetails(true);
			progressPanel.appendLog("ERROR.\n\nUnable to initialize folder (code: " + response.getCode() + ")\n" + response.getMessage());
			
			wizardDialog.setAllowedActions(Action.PREVIOUS);			
		}
	}
}
