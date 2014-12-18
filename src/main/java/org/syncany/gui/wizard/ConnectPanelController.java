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

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.syncany.chunk.Chunker;
import org.syncany.chunk.CipherTransformer;
import org.syncany.chunk.FixedChunker;
import org.syncany.chunk.GzipTransformer;
import org.syncany.chunk.MultiChunker;
import org.syncany.chunk.ZipMultiChunker;
import org.syncany.config.to.ConfigTO;
import org.syncany.config.to.RepoTO;
import org.syncany.config.to.RepoTO.ChunkerTO;
import org.syncany.config.to.RepoTO.MultiChunkerTO;
import org.syncany.config.to.RepoTO.TransformerTO;
import org.syncany.crypto.CipherSpec;
import org.syncany.crypto.CipherSpecs;
import org.syncany.crypto.CipherUtil;
import org.syncany.gui.util.I18n;
import org.syncany.gui.wizard.ConnectTypeSelectPanel.ConnectPanelSelection;
import org.syncany.gui.wizard.FolderSelectPanel.SelectFolderValidationMethod;
import org.syncany.gui.wizard.WizardDialog.Action;
import org.syncany.operations.daemon.ControlServer.ControlCommand;
import org.syncany.operations.daemon.messages.ControlManagementRequest;
import org.syncany.operations.daemon.messages.InitManagementRequest;
import org.syncany.operations.daemon.messages.InitManagementResponse;
import org.syncany.operations.init.InitOperationOptions;
import org.syncany.plugins.transfer.TransferPlugin;
import org.syncany.util.StringUtil;
import org.syncany.util.StringUtil.StringJoinListener;

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
				wizardDialog.validateAndSetCurrentPanel(connectTypeSelectPanel, Action.PREVIOUS, Action.NEXT);
			}
		}
		else if (wizardDialog.getCurrentPanel() == connectTypeSelectPanel) {
			if (clickAction == Action.PREVIOUS) {
				wizardDialog.setCurrentPanel(folderSelectPanel, Action.PREVIOUS, Action.NEXT);
			}
			else if (clickAction == Action.NEXT) {
				connectTypeSelection = connectTypeSelectPanel.getSelection();

				if (connectTypeSelection == ConnectPanelSelection.LINK) {
					wizardDialog.validateAndSetCurrentPanel(enterPasswordPanel, Action.PREVIOUS, Action.NEXT);
				}
				else {
					wizardDialog.validateAndSetCurrentPanel(pluginSettingsPanel, Action.PREVIOUS, Action.NEXT);	
				}				
			}
		}
		else if (wizardDialog.getCurrentPanel() == pluginSettingsPanel) {
			if (clickAction == Action.PREVIOUS) {
				wizardDialog.setCurrentPanel(connectTypeSelectPanel, Action.PREVIOUS, Action.NEXT);
			}
			else if (clickAction == Action.NEXT) {
				wizardDialog.validateAndSetCurrentPanel(enterPasswordPanel, Action.PREVIOUS, Action.NEXT);
			}
		}
		else if (wizardDialog.getCurrentPanel() == enterPasswordPanel) {
			if (clickAction == Action.PREVIOUS) {
				if (connectTypeSelection == ConnectPanelSelection.LINK) {
					wizardDialog.setCurrentPanel(connectTypeSelectPanel, Action.PREVIOUS, Action.NEXT);
				}
				else {
					wizardDialog.validateAndSetCurrentPanel(pluginSettingsPanel, Action.PREVIOUS, Action.NEXT);	
				}					
			}
			else if (clickAction == Action.NEXT) {
				progressPanel.setTitleText("Initializing remote repository");
				progressPanel.setDescriptionText("Syncany is creating a repository for you. This might take a while.");

				boolean panelValid = wizardDialog.validateAndSetCurrentPanel(progressPanel);

				if (panelValid) {
					//sendInitRequest();
					System.out.println("Do something.");
				}								
			}
		}
		else if (wizardDialog.getCurrentPanel() == progressPanel) {
			if (clickAction == Action.PREVIOUS) {
				wizardDialog.setCurrentPanel(enterPasswordPanel, Action.PREVIOUS, Action.NEXT);
			}
			else if (clickAction == Action.NEXT) {
				wizardDialog.validateAndSetCurrentPanel(startPanel);
			}
		}
	}

	@Subscribe
	public void onInitResponse(InitManagementResponse response) {
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
