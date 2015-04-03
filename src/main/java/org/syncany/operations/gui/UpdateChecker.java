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
package org.syncany.operations.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.syncany.Client;
import org.syncany.config.GuiEventBus;
import org.syncany.gui.util.I18n;
import org.syncany.operations.daemon.messages.PluginManagementRequest;
import org.syncany.operations.daemon.messages.PluginManagementResponse;
import org.syncany.operations.daemon.messages.UpdateManagementRequest;
import org.syncany.operations.daemon.messages.UpdateManagementResponse;
import org.syncany.operations.plugin.ExtendedPluginInfo;
import org.syncany.operations.plugin.PluginInfo;
import org.syncany.operations.plugin.PluginOperationAction;
import org.syncany.operations.plugin.PluginOperationOptions;
import org.syncany.operations.plugin.PluginOperationOptions.PluginListMode;
import org.syncany.operations.plugin.PluginOperationResult;
import org.syncany.operations.update.AppInfo;
import org.syncany.operations.update.UpdateOperationAction;
import org.syncany.operations.update.UpdateOperationOptions;
import org.syncany.operations.update.UpdateOperationResult;
import org.syncany.util.StringUtil;

import com.google.common.eventbus.Subscribe;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class UpdateChecker {
	private static final Logger logger = Logger.getLogger(UpdateChecker.class.getSimpleName());		
	
	private UpdateCheckListener listener;	
	private GuiEventBus eventBus;
	
	private PluginManagementResponse pluginResponse;
	private UpdateManagementResponse appResponse;
	
	public UpdateChecker(UpdateCheckListener listener) {
		this.listener = listener;		
		this.eventBus = GuiEventBus.getAndRegister(this);
		
		this.pluginResponse = null;
		this.appResponse = null;
	}	

	public PluginManagementResponse getPluginResponse() {
		return pluginResponse;
	}

	public UpdateManagementResponse getAppResponse() {
		return appResponse;
	}

	public void checkUpdates() {
		checkAppUpdates();
		checkPluginUpdates();     
	}
	
	private void checkAppUpdates() {
		logger.log(Level.INFO, "Update Check: Sending update management request ...");
		
		UpdateOperationOptions updateOperationOptions = new UpdateOperationOptions();
		updateOperationOptions.setAction(UpdateOperationAction.CHECK);
		
	    eventBus.post(new UpdateManagementRequest(updateOperationOptions));	    
	}

	private void checkPluginUpdates() {
		logger.log(Level.INFO, "Update Check: Sending plugin list management request ...");

		PluginOperationOptions pluginOperationOptions = new PluginOperationOptions();
		pluginOperationOptions.setAction(PluginOperationAction.LIST);
		pluginOperationOptions.setListMode(PluginListMode.ALL);
		
	    eventBus.post(new PluginManagementRequest(pluginOperationOptions));	
	}

	@Subscribe
	public void onUpdateResultReceived(UpdateManagementResponse updateResponse) {
		this.appResponse = updateResponse;		

		if (this.appResponse != null && this.pluginResponse != null) {
			fireUpdateResponse();
		}
	}

	@Subscribe
	public void onPluginResultReceived(PluginManagementResponse pluginResponse) {
		if (pluginResponse.getResult().getAction() == PluginOperationAction.LIST) {
			this.pluginResponse = pluginResponse;
			
			if (this.appResponse != null && this.pluginResponse != null) {
				fireUpdateResponse();
			}
		}
	}
	
	private void fireUpdateResponse() {
		String updateResponseText = getText();
		
		logger.log(Level.INFO, "Update Check: Response received: " + updateResponseText);		
		listener.updatesResponseReceived(appResponse, pluginResponse, updateResponseText);
	}

	private String getText() {
		String updateText = "";
		
		// Application updates
		UpdateOperationResult updateResult = appResponse.getResult();
		AppInfo appInfo = updateResult.getAppInfo();
		
		if (updateResult.isNewVersionAvailable()) {
			updateText = I18n.getText("org.syncany.gui.preferences.GeneralPanel.updates.app.newVersionAvailable", appInfo.getAppVersion());									
		}
		else {
			updateText = I18n.getText("org.syncany.gui.preferences.GeneralPanel.updates.app.upToDate", Client.getApplicationVersion());
		}
		
		// Plugin updates
		PluginOperationResult pluginResult = pluginResponse.getResult();
		List<String> outdatedPluginNames = new ArrayList<>();
		
		for (ExtendedPluginInfo extPluginInfo : pluginResult.getPluginList()) {	
			PluginInfo pluginInfo = (extPluginInfo.isInstalled()) ? extPluginInfo.getLocalPluginInfo() : extPluginInfo.getRemotePluginInfo();

			if (extPluginInfo.isOutdated()) {
				outdatedPluginNames.add(pluginInfo.getPluginName());
			}						
	    }	
		
		if (outdatedPluginNames.size() == 0) {
			updateText += " " + I18n.getText("org.syncany.gui.preferences.GeneralPanel.updates.plugins.upToDate");
		}
		else if (outdatedPluginNames.size() == 1) {
			String pluginNameText = outdatedPluginNames.get(0);
			updateText += " " + I18n.getText("org.syncany.gui.preferences.GeneralPanel.updates.plugins.oneOutdated", pluginNameText);
		}
		else {
			String pluginsNamesText = StringUtil.join(outdatedPluginNames, ", "); 
			updateText += " " + I18n.getText("org.syncany.gui.preferences.GeneralPanel.updates.plugins.manyOutdated", pluginsNamesText);						
		}
		
		return updateText;
	}
	
	public interface UpdateCheckListener {
		public void updatesResponseReceived(UpdateManagementResponse updateResponse, PluginManagementResponse pluginResponse, String updateText);
	}
}
