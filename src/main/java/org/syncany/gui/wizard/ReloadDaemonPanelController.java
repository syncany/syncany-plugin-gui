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

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.syncany.gui.wizard.WizardDialog.Action;
import org.syncany.operations.daemon.messages.ConfirmUserInteractionExternalEvent;
import org.syncany.operations.daemon.messages.ConfirmUserInteractionExternalManagementRequest;
import org.syncany.operations.daemon.messages.ControlManagementResponse;
import org.syncany.operations.daemon.messages.ListWatchesManagementRequest;
import org.syncany.operations.daemon.messages.ListWatchesManagementResponse;

import com.google.common.eventbus.Subscribe;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public abstract class ReloadDaemonPanelController extends PanelController {
	private ProgressPanel progressPanel;	
	private ListWatchesManagementRequest listWatchesRequest;
	
	public ReloadDaemonPanelController(WizardDialog wizardDialog, ProgressPanel progressPanel) {
		super(wizardDialog);		
		this.progressPanel = progressPanel;
	}

	@Subscribe
	public void onControlManagementResponseReceived(ControlManagementResponse response) {
		if (response.getCode() == 200) {
			progressPanel.increase();
			progressPanel.appendLog("DONE.\nRefreshing menus ... ");

			listWatchesRequest = new ListWatchesManagementRequest();			
			eventBus.post(listWatchesRequest);
		}
		else {
			progressPanel.finish();
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
				progressPanel.increase();
				progressPanel.appendLog("DONE.\nAdding folder successful.");
				
				wizardDialog.setAllowedActions(Action.FINISH);			
			}
			else {
				progressPanel.finish();
				progressPanel.setShowDetails(true);
				progressPanel.appendLog("ERROR.\n\nUnable to list folders (code: " + response.getCode() + ")\n" + response.getMessage());
	
				wizardDialog.setAllowedActions(Action.PREVIOUS);			
			}
		}
	}

	@Subscribe
	public void onUserConfirmEventReceived(final ConfirmUserInteractionExternalEvent confirmUserEvent) {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				MessageBox messageBox = new MessageBox(wizardDialog.getWindowShell(), SWT.ICON_QUESTION | SWT.YES | SWT.NO);
				messageBox.setText(confirmUserEvent.getHeader());
				messageBox.setMessage(confirmUserEvent.getMessage() + "\n\n" + confirmUserEvent.getQuestion());

				int response = messageBox.open();
				boolean userConfirms = response == SWT.YES;

				eventBus.post(new ConfirmUserInteractionExternalManagementRequest(userConfirms));
			}
		});		
	}
}
