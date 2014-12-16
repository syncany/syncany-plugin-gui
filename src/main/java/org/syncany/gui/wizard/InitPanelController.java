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

import org.syncany.gui.util.I18n;
import org.syncany.gui.wizard.FolderSelectPanel.SelectFolderValidationMethod;
import org.syncany.gui.wizard.WizardDialog.Action;
import org.syncany.operations.daemon.ControlServer.ControlCommand;
import org.syncany.operations.daemon.messages.AddWatchManagementRequest;
import org.syncany.operations.daemon.messages.AddWatchManagementResponse;
import org.syncany.operations.daemon.messages.ControlManagementRequest;
import org.syncany.operations.daemon.messages.ControlManagementResponse;
import org.syncany.operations.daemon.messages.ListWatchesManagementRequest;
import org.syncany.operations.daemon.messages.ListWatchesManagementResponse;

import com.google.common.eventbus.Subscribe;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class InitPanelController extends PanelController {
	private StartPanel startPanel;
	private FolderSelectPanel folderSelectPanel;
	private PluginSelectPanel pluginSelectPanel;
	private PluginSettingsPanel pluginSettingsPanel;
	private ChoosePasswordPanel choosePasswordPanel;
	private ProgressPanel progressPanel;
	
	private ListWatchesManagementRequest listWatchesRequest;

	public InitPanelController(WizardDialog wizardDialog, StartPanel startPanel, FolderSelectPanel folderSelectPanel,
			PluginSelectPanel pluginSelectPanel, PluginSettingsPanel pluginSettingsPanel, ChoosePasswordPanel choosePasswordPanel,
			ProgressPanel progressPanel) {

		super(wizardDialog);
		
		this.startPanel = startPanel;
		this.folderSelectPanel = folderSelectPanel;
		this.pluginSelectPanel = pluginSelectPanel;
		this.pluginSettingsPanel = pluginSettingsPanel;
		this.choosePasswordPanel = choosePasswordPanel;
		this.progressPanel = progressPanel;
	}

	@Override
	public void handleFlow(Action clickAction) {
		if (wizardDialog.getCurrentPanel() == startPanel) {
			if (clickAction == Action.NEXT) {
				folderSelectPanel.setValidationMethod(SelectFolderValidationMethod.NO_APP_FOLDER);
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
				pluginSettingsPanel.init(pluginSelectPanel.getSelectedPlugin());
				
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
				wizardDialog.validateAndSetCurrentPanel(progressPanel, Action.PREVIOUS, Action.NEXT);
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

	private void sendAddFolderRequest() {
		File newWatchFolder = folderSelectPanel.getFolder();
		AddWatchManagementRequest addWatchManagementRequest = new AddWatchManagementRequest(newWatchFolder);
		
		progressPanel.resetPanel(3);
		progressPanel.appendLog("Adding folder "+ newWatchFolder + " ... ");

		eventBus.post(addWatchManagementRequest);		
	}

	@Subscribe
	public void onAddWatchManagementResponse(AddWatchManagementResponse response) {
		if (response.getCode() == AddWatchManagementResponse.OKAY) {
			progressPanel.setProgress(1);
			progressPanel.appendLog("DONE.\nReloading daemon ... ");
			
			eventBus.post(new ControlManagementRequest(ControlCommand.RELOAD));
		}
		else {
			progressPanel.setProgress(3);
			progressPanel.setShowDetails(true);
			progressPanel.appendLog("ERROR.\n\nUnable to add folder (code: " + response.getCode() + ")\n" + response.getMessage());
			
			wizardDialog.setAllowedActions(Action.PREVIOUS);			
		}
	}
	
	@Subscribe
	public void onControlManagementResponseReceived(ControlManagementResponse response) {
		if (response.getCode() == 200) {
			progressPanel.setProgress(2);
			progressPanel.appendLog("DONE.\nRefreshing menus ... ");

			listWatchesRequest = new ListWatchesManagementRequest();			
			eventBus.post(listWatchesRequest);
		}
		else {
			progressPanel.setProgress(3);
			progressPanel.setShowDetails(true);
			progressPanel.appendLog("ERROR.\n\nUnable to reload daemon (code: " + response.getCode() + ")\n" + response.getMessage());
			
			wizardDialog.setAllowedActions(Action.PREVIOUS);			
		}
	}

	@Subscribe
	public void onListWatchesManagementResponse(ListWatchesManagementResponse response) {
		boolean isMatchingResponse = listWatchesRequest != null && listWatchesRequest.getId() == response.getRequestId();
		
		if (isMatchingResponse) {
			if (response.getCode() == 200) {
				progressPanel.setProgress(3);
				progressPanel.appendLog("DONE.\nAdding folder successful.");
				
				wizardDialog.setAllowedActions(Action.FINISH);			
			}
			else {
				progressPanel.setProgress(3);
				progressPanel.setShowDetails(true);
				progressPanel.appendLog("ERROR.\n\nUnable to list folders (code: " + response.getCode() + ")\n" + response.getMessage());
	
				wizardDialog.setAllowedActions(Action.PREVIOUS);			
			}
		}
	}
}
