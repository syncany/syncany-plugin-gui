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
package org.syncany.config;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.syncany.config.to.GuiConfigTO;
import org.syncany.gui.tray.TrayIconTheme;
import org.syncany.gui.tray.TrayIconType;

public class GuiConfigHelper {
	private static final Logger logger = Logger.getLogger(GuiConfigHelper.class.getSimpleName());	

	private static final String GUI_CONFIG_FILE = "gui.xml";
	private static final String GUI_CONFIG_EXAMPLE_FILE = "gui-example.xml";
	
	private GuiConfigHelper() {
		// Nothing
	}
	
	public static GuiConfigTO loadOrCreateGuiConfig() {	
		try {
			File configFile = new File(UserConfig.getUserConfigDir(), GUI_CONFIG_FILE);
			File configFileExample = new File(UserConfig.getUserConfigDir(), GUI_CONFIG_EXAMPLE_FILE);
			
			if (configFile.exists()) {
				return GuiConfigTO.load(configFile);
			}
			else {
				// Write example config to daemon-example.xml, and default config to daemon.xml
				GuiConfigTO exampleGuiConfig = new GuiConfigTO();
				exampleGuiConfig.setTray(TrayIconType.DEFAULT);
				exampleGuiConfig.setTheme(TrayIconTheme.DEFAULT);				
				exampleGuiConfig.setNotifications(true);
				
				GuiConfigTO.save(exampleGuiConfig, configFileExample);
				
				// Use default settings
				return new GuiConfigTO();
			}			
		}
		catch (Exception e) {
			logger.log(Level.WARNING, "Cannot (re-)load config. Using default config.", e);
			return new GuiConfigTO();
		}
	}		
	
	public static void saveGuiConfig(GuiConfigTO guiConfigTO) throws ConfigException {
		File configFile = new File(UserConfig.getUserConfigDir(), GUI_CONFIG_FILE);
		GuiConfigTO.save(guiConfigTO, configFile);		
	}
}
