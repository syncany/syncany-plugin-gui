/*
 * Syncany, www.syncany.org
 * Copyright (C) 2011-2013 Philipp C. Heckel <philipp.heckel@gmail.com> 
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
package org.syncany.gui;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.widgets.Display;
import org.syncany.config.Config;
import org.syncany.config.Logging;
import org.syncany.config.UserConfig;
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.SWTResourceManager;
import org.syncany.operations.daemon.DaemonOperation;
import org.syncany.util.EnvironmentUtil;
import org.syncany.util.PidFileUtil;

/**
 * @author Vincent Wiencek <vwiencek@gmail.com>
 *
 */
public class Launcher {
	private static final Logger logger = Logger.getLogger(Launcher.class.getSimpleName());
	
	public static MainGUI window;

	static {
		Logging.init();
	}

	public static void main(String[] args) {
		try {
			startDeamon();
		}
		catch (IOException | InterruptedException e) {
			logger.warning("Unable to start daemon");
		}

		startApplication();
	}

	private static void startApplication() {
		UserConfig.init();
		startGUI();
	}

	public static void stopApplication() {
		try {
			shutDownDaemon();
		}
		catch (IOException e) {
			logger.warning("Unable to stop daemon: " + e);
		}
		catch (InterruptedException e) {
			logger.warning("Unable to stop daemon: " + e);
		}
		stopGUI();
		System.exit(0);
	}

	private static void stopGUI() {
		window.dispose();
	}

	public static void startDeamon() throws IOException, InterruptedException {
		File daemonPidFile = new File(UserConfig.getUserConfigDir(), DaemonOperation.PID_FILE);
		boolean daemonRunning = PidFileUtil.isProcessRunning(daemonPidFile);

		if (!daemonRunning){
			File launchScript = getLaunchScript();

			Process daemonProcees = new ProcessBuilder(launchScript.getAbsolutePath(), "daemon", "start").start();
			daemonProcees.waitFor();
		}
	}

	public static void shutDownDaemon() throws IOException, InterruptedException {
		File daemonPidFile = new File(UserConfig.getUserConfigDir(), DaemonOperation.PID_FILE);
		boolean daemonRunning = PidFileUtil.isProcessRunning(daemonPidFile);

		if (daemonRunning){
			File launchScript = getLaunchScript();

			Process daemonProcees = new ProcessBuilder(launchScript.getAbsolutePath(), "daemon", "stop").start();
			daemonProcees.waitFor();			
		}
	}
	
	private static void startGUI() {
		Display.setAppName("Syncany");
		Display.setAppVersion("1.0");
		
		// Register messages bundles
		I18n.registerBundleName("i18n/messages");
		I18n.registerBundleFilter("plugin_messages*");

		// Shutdown hook to release swt resources
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				logger.info("Releasing SWT Resources");
				SWTResourceManager.dispose();
			}
		});

		logger.info("Starting Graphical User Interface");

		window = new MainGUI();
		window.open();
	}
	
	private static File getLaunchScript() {		
		File appLibDir = getJarFromClass(Config.class);
		File appHomeDir = appLibDir.getParentFile();
		File appBinDir = new File(appHomeDir, "bin");
		
		String appLaunchScriptName = (EnvironmentUtil.isWindows()) ? "sy.bat" : "sy";			
		File appLaunchScript = new File(appBinDir, appLaunchScriptName);
		
		return appLaunchScript;		
	}
	
	private static File getJarFromClass(Class<?> searchClass) {
		try {
			URL pluginClassLocation = searchClass.getResource('/' + searchClass.getName().replace('.', '/') + ".class");
			String pluginClassLocationStr = pluginClassLocation.toString();
			logger.log(Level.INFO, "Plugin class is at " + pluginClassLocation);
	
			int indexStartAfterSchema = "jar:file:".length();
			int indexEndAtExclamationPoint = pluginClassLocationStr.indexOf("!");
			File searchClassJarFile = new File(pluginClassLocationStr.substring(indexStartAfterSchema, indexEndAtExclamationPoint));
			
			return searchClassJarFile;
		}
		catch (Exception e) {
			throw new RuntimeException("Cannot find application home; Cannot run GUI from outside a JAR.", e);
		}
	}
}
