/*
 * Syncany, www.syncany.org
 * Copyright (C) 2011-2015 Philipp C. Heckel <philipp.heckel@gmail.com> 
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

import org.eclipse.swt.widgets.Display;
import org.syncany.config.to.ConfigTO;
import org.syncany.crypto.CipherUtil;
import org.syncany.gui.util.I18n;
import org.syncany.gui.wizard.ConnectTypeSelectPanel.ConnectPanelSelection;
import org.syncany.gui.wizard.FolderSelectPanel.SelectFolderValidationMethod;
import org.syncany.gui.wizard.WizardDialog.Action;
import org.syncany.operations.daemon.messages.ConnectManagementRequest;
import org.syncany.operations.daemon.messages.ConnectManagementResponse;
import org.syncany.operations.daemon.messages.GetPasswordUserInteractionExternalEvent;
import org.syncany.operations.daemon.messages.GetPasswordUserInteractionExternalEventResponse;
import org.syncany.operations.init.ApplicationLink;
import org.syncany.operations.init.ConnectOperationOptions;
import org.syncany.operations.init.ConnectOperationOptions.ConnectOptionsStrategy;
import org.syncany.plugins.transfer.TransferPlugin;

import com.google.common.eventbus.Subscribe;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class ConnectPanelController extends AbstractInitPanelController {
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
			folderSelectPanel.reset(SelectFolderValidationMethod.NO_APP_FOLDER);
			folderSelectPanel.setDescriptionText(I18n.getText("org.syncany.gui.wizard.FolderSelectPanel.connect.description"));

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
				handleFlowConnectTypeSelectPanelNextWithLink();				
			}
			else if (connectTypeSelection == ConnectPanelSelection.MANUAL) {
				handleFlowConnectTypeSelectPanelNextWithManual();				
			}				
		}
	}

	private void handleFlowConnectTypeSelectPanelNextWithLink() {
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
	
	private void handleFlowConnectTypeSelectPanelNextWithManual() {
		boolean pluginIsSet = connectTypeSelectPanel.getSelectedPlugin() != null;
		boolean pluginNewOrChanged = selectedPlugin == null || selectedPlugin != connectTypeSelectPanel.getSelectedPlugin();
		
		if (pluginIsSet && pluginNewOrChanged) {
			selectedPlugin = connectTypeSelectPanel.getSelectedPlugin();
			pluginSettingsPanel.init(selectedPlugin);
		}
		
		wizardDialog.validateAndSetCurrentPanel(pluginSettingsPanel, Action.PREVIOUS, Action.NEXT);	
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
			initProgressPanelLabels();
			
			boolean panelValid = wizardDialog.validateAndSetCurrentPanel(progressPanel);
			
			if (panelValid) {			
				sendConnectRequest();
			}
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
			if (connectTypeSelection == ConnectPanelSelection.MANUAL) {
				eventBus.post(new GetPasswordUserInteractionExternalEventResponse(enterPasswordPanel.getPassword()));
				wizardDialog.setCurrentPanel(progressPanel);				
			}
			else {
				initProgressPanelLabels();
				boolean panelValid = wizardDialog.validateAndSetCurrentPanel(progressPanel);
	
				if (panelValid) {
					sendConnectRequest();
				}
			}
		}
	}

	private void handleFlowProgressPanel(Action clickAction) {
		if (clickAction == Action.PREVIOUS) {
			if (connectTypeSelection == ConnectPanelSelection.LINK) {
				if (applicationLink.isEncrypted()) {
					wizardDialog.setCurrentPanel(enterPasswordPanel, Action.PREVIOUS, Action.NEXT);
				}
				else {
					wizardDialog.setCurrentPanel(connectTypeSelectPanel, Action.PREVIOUS, Action.NEXT);
				}
			}
			else {
				wizardDialog.setCurrentPanel(pluginSettingsPanel, Action.PREVIOUS, Action.NEXT);
			}			
		}
		else if (clickAction == Action.NEXT) {
			wizardDialog.validateAndSetCurrentPanel(startPanel);
		}
	}

	private void initProgressPanelLabels() {
		progressPanel.setTitleText(I18n.getText("org.syncany.gui.wizard.ProgressPanel.connect.title"));
		progressPanel.setDescriptionText(I18n.getText("org.syncany.gui.wizard.ProgressPanel.connect.description"));
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
				configTO.setTransferSettings(pluginSettingsPanel.getPluginSettings());
			}
						
			ConnectManagementRequest connectManagementRequest = new ConnectManagementRequest(connectOptions);
			
			progressPanel.resetPanel(3);
			progressPanel.appendLog(I18n.getText("org.syncany.gui.wizard.ProgressPanel.connect.connectingToRepo", folderSelectPanel.getFolder()));
	
			eventBus.post(connectManagementRequest);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Subscribe
	public void onGetPasswordEventReceived(final GetPasswordUserInteractionExternalEvent getPasswordUserEvent) {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				wizardDialog.setCurrentPanel(enterPasswordPanel, Action.NEXT);
			}
		});		
	}
	
	@Subscribe
	public void onConnectResponse(ConnectManagementResponse response) {
		logger.log(Level.INFO, "Received response from daemon: " + response);
		
		if (response.getCode() == 200) {
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
	
	private String formatErrorMessage(ConnectManagementResponse response) {
		String errorMessage = I18n.getText("org.syncany.gui.wizard.ProgressPanel.error") + "\n\n"
				+ I18n.getText("org.syncany.gui.wizard.ProgressPanel.connect.unableToConnect", response.getCode()) + "\n";
		
		switch (response.getCode()) {
		case ConnectManagementResponse.NOK_FAILED_TEST:
			errorMessage += formatTestResultMessage(response.getResult().getTestResult());				 
			break;
		
		case ConnectManagementResponse.NOK_FAILED_UNKNOWN:
			errorMessage += I18n.getText("org.syncany.gui.wizard.ProgressPanel.connect.failedWithUnknownError");				
			break;
			
		case ConnectManagementResponse.NOK_OPERATION_FAILED:
			errorMessage += I18n.getText("org.syncany.gui.wizard.ProgressPanel.connect.failedWithException");				
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
