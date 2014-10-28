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
import org.syncany.gui.wizard.SelectFolderPanel.SelectFolderValidationMethod;
import org.syncany.gui.wizard.WizardDialog.ClickAction;
import org.syncany.operations.daemon.ControlServer.ControlCommand;
import org.syncany.operations.daemon.messages.AddWatchManagementRequest;
import org.syncany.operations.daemon.messages.AddWatchManagementResponse;
import org.syncany.operations.daemon.messages.ControlManagementRequest;
import org.syncany.operations.daemon.messages.ControlManagementResponse;
import org.syncany.operations.daemon.messages.ListWatchesManagementRequest;
import org.syncany.operations.daemon.messages.ListWatchesManagementResponse;

import com.google.common.eventbus.Subscribe;

/**
 * @author pheckel
 *
 */
public class AddExistingPanelController extends PanelController {
	private StartPanel startPanel;
	private SelectFolderPanel selectFolderPanel;
	private ProgressPanel progressPanel;
	
	private ListWatchesManagementRequest listWatchesRequest;
	
	public AddExistingPanelController(WizardDialog wizardDialog, StartPanel startPanel, SelectFolderPanel selectFolderPanel, ProgressPanel progressPanel) {
		super(wizardDialog);
		
		this.startPanel = startPanel;
		this.selectFolderPanel = selectFolderPanel;
		this.progressPanel = progressPanel;
	}

	@Override
	public void handleFlow(ClickAction clickAction) {
		if (wizardDialog.getCurrentPanel() == startPanel) {
			if (clickAction == ClickAction.NEXT) {
				selectFolderPanel.setValidationMethod(SelectFolderValidationMethod.APP_FOLDER);
				selectFolderPanel.setDescriptionText(I18n.getString("dialog.selectLocalFolder.watchIntroduction"));

				wizardDialog.validateAndSetCurrentPanel(selectFolderPanel, ClickAction.PREVIOUS, ClickAction.NEXT);
			}
		}
		else if (wizardDialog.getCurrentPanel() == selectFolderPanel) {
			if (clickAction == ClickAction.PREVIOUS) {
				wizardDialog.setCurrentPanel(startPanel, ClickAction.NEXT);
			}
			else if (clickAction == ClickAction.NEXT) {
				progressPanel.setTitleText(I18n.getString("dialog.progressPanel.add.title"));
				progressPanel.setDescriptionText(I18n.getString("dialog.progressPanel.add.text"));

				boolean panelValid = wizardDialog.validateAndSetCurrentPanel(progressPanel);

				if (panelValid) {
					sendAddFolderRequest();
				}
			}
		}
		else if (wizardDialog.getCurrentPanel() == progressPanel) {
			if (clickAction == ClickAction.PREVIOUS) {
				wizardDialog.setCurrentPanel(selectFolderPanel, ClickAction.PREVIOUS, ClickAction.NEXT);
			}
			else if (clickAction == ClickAction.NEXT) {
				wizardDialog.validateAndSetCurrentPanel(startPanel);
			}
		}
	}

	private void sendAddFolderRequest() {
		File newWatchFolder = selectFolderPanel.getFolder();
		AddWatchManagementRequest addWatchManagementRequest = new AddWatchManagementRequest(newWatchFolder);
		
		progressPanel.resetProgressBar(3);
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
			progressPanel.appendLog("ERROR.\nUnable to add folder; reason: " + response.getCode() + ", " + response.getMessage());
			
			wizardDialog.setAllowedActions(ClickAction.PREVIOUS);			
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
			progressPanel.appendLog("ERROR.\nUnable to reload daemon; reason: " + response.getCode() + ", " + response.getMessage());
			
			wizardDialog.setAllowedActions(ClickAction.PREVIOUS);			
		}
	}

	@Subscribe
	public void onListWatchesManagementResponse(ListWatchesManagementResponse response) {
		boolean isMatchingResponse = listWatchesRequest != null && listWatchesRequest.getId() == response.getRequestId();
		
		if (isMatchingResponse) {
			if (response.getCode() == 200) {
				progressPanel.setProgress(3);
				progressPanel.appendLog("DONE.\nAdding folder successful.");
				
				wizardDialog.setAllowedActions(ClickAction.FINISH);			
			}
			else {
				progressPanel.setProgress(3);
				progressPanel.setShowDetails(true);
				progressPanel.appendLog("ERROR.\nUnable to list folders; reason: " + response.getCode() + ", " + response.getMessage());
	
				wizardDialog.setAllowedActions(ClickAction.PREVIOUS);			
			}
		}
	}
}
