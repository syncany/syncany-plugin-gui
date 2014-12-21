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

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
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
import org.syncany.gui.wizard.FolderSelectPanel.SelectFolderValidationMethod;
import org.syncany.gui.wizard.WizardDialog.Action;
import org.syncany.operations.daemon.ControlServer.ControlCommand;
import org.syncany.operations.daemon.messages.ConfirmUserInteractionExternalEvent;
import org.syncany.operations.daemon.messages.ConfirmUserInteractionExternalManagementRequest;
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
			/* <duplicate code> */
			
			// Cipher specs: --no-encryption, --advanced
			List<CipherSpec> cipherSpecs = CipherSpecs.getDefaultCipherSpecs();
	
			// Compression: --no-compression
			List<TransformerTO> transformersTO = getTransformersTO(true, cipherSpecs);
			
			/* </duplicate code> */
					
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
			initOptions.setRepoTO(createRepoTO(getDefaultChunkerTO(), getDefaultMultiChunkerTO(), transformersTO));
			initOptions.setDaemon(true);
			
			InitManagementRequest initManagementRequest = new InitManagementRequest(initOptions);
			
			progressPanel.resetPanel(3);
			progressPanel.appendLog("Initializing repo for folder "+ folderSelectPanel.getFolder() + " ... ");
	
			eventBus.post(initManagementRequest);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}	

	/* < duplicate code, see InitCommand > */ 
	// TODO [high] Duplicate code!!
	
	protected List<TransformerTO> getTransformersTO(boolean gzipEnabled, List<CipherSpec> cipherSpecs) {
		List<TransformerTO> transformersTO = new ArrayList<TransformerTO>();

		if (gzipEnabled) {
			transformersTO.add(getGzipTransformerTO());
		}

		if (cipherSpecs.size() > 0) {
			TransformerTO cipherTransformerTO = getCipherTransformerTO(cipherSpecs);
			transformersTO.add(cipherTransformerTO);
		}

		return transformersTO;
	}

	protected RepoTO createRepoTO(ChunkerTO chunkerTO, MultiChunkerTO multiChunkerTO, List<TransformerTO> transformersTO) throws Exception {
		// Make transfer object
		RepoTO repoTO = new RepoTO();

		// Create random repo identifier
		byte[] newRepoId = new byte[32];
		new SecureRandom().nextBytes(newRepoId);

		repoTO.setRepoId(newRepoId);

		// Add to repo transfer object
		repoTO.setChunkerTO(chunkerTO);
		repoTO.setMultiChunker(multiChunkerTO);
		repoTO.setTransformers(transformersTO);

		return repoTO;
	}

	protected ChunkerTO getDefaultChunkerTO() {
		ChunkerTO chunkerTO = new ChunkerTO();

		chunkerTO.setType(FixedChunker.TYPE);
		chunkerTO.setSettings(new HashMap<String, String>());
		chunkerTO.getSettings().put(Chunker.PROPERTY_SIZE, "16");

		return chunkerTO;
	}

	protected MultiChunkerTO getDefaultMultiChunkerTO() {
		MultiChunkerTO multichunkerTO = new MultiChunkerTO();

		multichunkerTO.setType(ZipMultiChunker.TYPE);
		multichunkerTO.setSettings(new HashMap<String, String>());
		multichunkerTO.getSettings().put(MultiChunker.PROPERTY_SIZE, "4096");

		return multichunkerTO;
	}

	protected TransformerTO getGzipTransformerTO() {
		TransformerTO gzipTransformerTO = new TransformerTO();
		gzipTransformerTO.setType(GzipTransformer.TYPE);

		return gzipTransformerTO;
	}

	protected TransformerTO getCipherTransformerTO(List<CipherSpec> cipherSpec) {
		String cipherSuitesIdStr = StringUtil.join(cipherSpec, ",", new StringJoinListener<CipherSpec>() {
			@Override
			public String getString(CipherSpec cipherSpec) {
				return "" + cipherSpec.getId();
			}
		});

		Map<String, String> cipherTransformerSettings = new HashMap<String, String>();
		cipherTransformerSettings.put(CipherTransformer.PROPERTY_CIPHER_SPECS, cipherSuitesIdStr);
		// Note: Property 'password' is added dynamically by CommandLineClient

		TransformerTO cipherTransformerTO = new TransformerTO();
		cipherTransformerTO.setType(CipherTransformer.TYPE);
		cipherTransformerTO.setSettings(cipherTransformerSettings);

		return cipherTransformerTO;
	}
	
	/* </ end of duplicate code> */ 
	
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
	
	@Subscribe
	public void onUserConfirmEventReceived(final ConfirmUserInteractionExternalEvent confirmUserEvent) {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				MessageBox messageBox = new MessageBox(wizardDialog.getWindowShell(), SWT.ICON_QUESTION | SWT.YES | SWT.NO);
				messageBox.setText(confirmUserEvent.getHeader());
				messageBox.setMessage(confirmUserEvent.getMessage() + "\n\n" + confirmUserEvent.getQuestion() + "?");

				int response = messageBox.open();
				boolean userConfirms = response == SWT.YES;

				eventBus.post(new ConfirmUserInteractionExternalManagementRequest(userConfirms));
			}
		});		
	}
}
