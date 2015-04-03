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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.syncany.Client;
import org.syncany.config.GuiEventBus;
import org.syncany.config.UserConfig;
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
import org.syncany.plugins.gui.GuiPlugin;
import org.syncany.util.StringUtil;

import com.google.common.eventbus.Subscribe;

/**
 * The update checker can contact the Syncany server to check for available
 * application and plugin updates. It can either trigger a check immediately 
 * ({@link #check()}) or regularly in an update check ({@link #start()}). Instead
 * of contacting the server directly, this class communicates only with the daemon.
 * 
 * <p>Responses from the update check are delivered via the {@link UpdateCheckListener}
 * interface.
 * 
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class UpdateChecker {
	private static final Logger logger = Logger.getLogger(UpdateChecker.class.getSimpleName());		
	
	private static int UPDATE_CHECK_DELAY = 2*60*1000; // Wait a while after start
	private static int UPDATE_CHECK_TIMER_INTERVAL = 1*60*60*1000; // Check every 1 hour
	private static int UPDATE_CHECK_API_INTERVAL = 1*24*60*60*1000; // Check every 24 hours

	private UpdateCheckListener listener;	
	private GuiEventBus eventBus;
	
	private File userUpdateFile;
	
	private UpdateManagementRequest updateRequest;
	private UpdateManagementResponse updateResponse;

	private PluginManagementRequest pluginRequest;	
	private PluginManagementResponse pluginResponse;
	
	public UpdateChecker(UpdateCheckListener listener) {
		this.listener = listener;		
		this.eventBus = GuiEventBus.getAndRegister(this);
		
		this.userUpdateFile = new File(UserConfig.getUserPluginsUserdataDir(GuiPlugin.ID), "update");
		
		this.updateRequest = null;
		this.updateResponse = null;

		this.pluginRequest = null;
		this.pluginResponse = null;
	}	

	/**
	 * Start update timer to regularly check for updates. A background
	 * thread will check for updates every 24h. 
	 */
	public void start() {
		logger.log(Level.INFO, "Update check: Starting update timer ...");

		new Timer("UpdateTimer").schedule(new TimerTask() {
			@Override
			public void run() {
				checkUpdatesIfNecessary();
			}			
		}, UPDATE_CHECK_DELAY, UPDATE_CHECK_TIMER_INTERVAL);
	}
	
	/**
	 * Checks for updates immediately.
	 */
	public void check() {
		logger.log(Level.INFO, "Update check: Checking for updates NOW ...");

		checkAppUpdates();
		checkPluginUpdates();     
		
		touchUpdateFile();
	}
	
	private void touchUpdateFile() {
		try {
			if (!userUpdateFile.exists()) {
				userUpdateFile.createNewFile();
			}
			
			userUpdateFile.setLastModified(System.currentTimeMillis());	
		}
		catch (Exception e) {
			logger.log(Level.WARNING, "Update check: Cannot create update file.", e);
		}			
	}

	private void checkAppUpdates() {
		logger.log(Level.INFO, "Update Check: Sending update management request ...");
		
		UpdateOperationOptions updateOperationOptions = new UpdateOperationOptions();
		updateOperationOptions.setAction(UpdateOperationAction.CHECK);
		
		updateRequest = new UpdateManagementRequest(updateOperationOptions);
	    eventBus.post(updateRequest);	    
	}

	private void checkPluginUpdates() {
		logger.log(Level.INFO, "Update Check: Sending plugin list management request ...");

		PluginOperationOptions pluginOperationOptions = new PluginOperationOptions();
		pluginOperationOptions.setAction(PluginOperationAction.LIST);
		pluginOperationOptions.setListMode(PluginListMode.ALL);
		
		pluginRequest = new PluginManagementRequest(pluginOperationOptions);		
	    eventBus.post(pluginRequest);	
	}

	@Subscribe
	public void onUpdateResultReceived(UpdateManagementResponse updateResponse) {
		boolean isMatchingResponse = updateRequest != null && updateRequest.getId() == updateResponse.getRequestId();

		if (isMatchingResponse) {
			this.updateResponse = updateResponse;		
	
			if (this.updateResponse != null && this.pluginResponse != null) {
				fireUpdateResponse();
			}
		}
	}

	@Subscribe
	public void onPluginResultReceived(PluginManagementResponse pluginResponse) {
		boolean isMatchingResponse = pluginRequest != null && pluginRequest.getId() == pluginResponse.getRequestId();
		
		if (isMatchingResponse) {
			this.pluginResponse = pluginResponse;
			
			if (this.updateResponse != null && this.pluginResponse != null) {
				fireUpdateResponse();
			}
		}
	}
	
	private void fireUpdateResponse() {
		String updateResponseText = getUpdateText();		
		boolean updatesAvailable = isUpdateAvailable();

		logger.log(Level.INFO, "Update Check: Updates available (= " + updatesAvailable + "); text: " + updateResponseText);	
		listener.updateResponseReceived(updateResponse, pluginResponse, updateResponseText, updatesAvailable);
		
		updateResponse = null;
		pluginResponse = null;
	}
	
	private void checkUpdatesIfNecessary() {		
		if (!userUpdateFile.exists()) {
			logger.log(Level.INFO, "Update check: No update file (first run), so no update check necessary. Next file check in " + (UPDATE_CHECK_TIMER_INTERVAL/1000/60) + "min.");
			touchUpdateFile();
		}
		else if (System.currentTimeMillis() - userUpdateFile.lastModified() > UPDATE_CHECK_API_INTERVAL) {
			logger.log(Level.INFO, "Update check: Necessary, because last check is longer than " + (UPDATE_CHECK_API_INTERVAL/1000/60/60) + "h ago. Next file check in " + (UPDATE_CHECK_TIMER_INTERVAL/1000/60) + "min.");
			check();
		}
		else {
			logger.log(Level.INFO, "Update check: Not necessary, last check less than " + (UPDATE_CHECK_API_INTERVAL/1000/60/60) + "h ago. Next file check in " + (UPDATE_CHECK_TIMER_INTERVAL/1000/60) + "min.");
		}
	}

	private boolean isUpdateAvailable() {
		return isAppUpdateAvailable() || isPluginUpdateAvailable();
	}
	
	private boolean isAppUpdateAvailable() {
		return updateResponse != null && updateResponse.getResult() != null && updateResponse.getResult().isNewVersionAvailable();
	}

	private boolean isPluginUpdateAvailable() {
		if (pluginResponse == null || pluginResponse.getResult() == null) {
			return false;
		}
		else {
			for (ExtendedPluginInfo extPluginInfo : pluginResponse.getResult().getPluginList()) {	
				if (extPluginInfo.isOutdated()) {
					return true;
				}						
		    }	

			return false;
		}
	}

	private String getUpdateText() {
		return getAppUpdateText() + " " + getPluginUpdateText();		
	}

	private String getAppUpdateText() {
		UpdateOperationResult updateResult = updateResponse.getResult();
		AppInfo appInfo = updateResult.getAppInfo();
		
		if (updateResult.isNewVersionAvailable()) {
			return I18n.getText("org.syncany.operations.gui.UpdateChecker.updates.app.newVersionAvailable", appInfo.getAppVersion());									
		}
		else {
			return I18n.getText("org.syncany.operations.gui.UpdateChecker.updates.app.upToDate", Client.getApplicationVersion());
		}
	}

	private String getPluginUpdateText() {
		PluginOperationResult pluginResult = pluginResponse.getResult();
		List<String> outdatedPluginNames = new ArrayList<>();
		
		for (ExtendedPluginInfo extPluginInfo : pluginResult.getPluginList()) {	
			PluginInfo pluginInfo = (extPluginInfo.isInstalled()) ? extPluginInfo.getLocalPluginInfo() : extPluginInfo.getRemotePluginInfo();

			if (extPluginInfo.isOutdated()) {
				outdatedPluginNames.add(pluginInfo.getPluginName());
			}						
	    }	
		
		if (outdatedPluginNames.size() == 0) {
			return I18n.getText("org.syncany.operations.gui.UpdateChecker.updates.plugins.upToDate");
		}
		else if (outdatedPluginNames.size() == 1) {
			String pluginNameText = outdatedPluginNames.get(0);
			return I18n.getText("org.syncany.operations.gui.UpdateChecker.updates.plugins.oneOutdated", pluginNameText);
		}
		else {
			String pluginsNamesText = StringUtil.join(outdatedPluginNames, ", "); 
			return I18n.getText("org.syncany.operations.gui.UpdateChecker.updates.plugins.manyOutdated", pluginsNamesText);						
		}
	}

	public void dispose() {
		eventBus.unregister(this);
	}
	
	public interface UpdateCheckListener {
		public void updateResponseReceived(UpdateManagementResponse appResponse, PluginManagementResponse pluginResponse, String updateResponseText,
				boolean updatesAvailable);
	}
}
