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
package org.syncany.gui.util;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.Shell;
import org.syncany.gui.preferences.GeneralPanel;
import org.syncany.util.EnvironmentUtil;
import org.syncany.util.FileUtil;
import com.google.common.base.StandardSystemProperty;
import io.undertow.util.FileUtils;

/**
 * Helper class to open web sites and local folders, and to center
 * a window on the screen.
 *
 * @author Vincent Wiencek <vwiencek@gmail.com>
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class DesktopUtil {
	private static final Logger logger = Logger.getLogger(DesktopUtil.class.getSimpleName());

	private static final String STARTUP_LINUX_SCRIPT_RESOURCE = "/" + GeneralPanel.class.getPackage().getName().replace('.', '/') + "/syncany.desktop";
	private static final String STARTUP_LINUX_SCRIPT_TARGET_FILENAME = "syncany.desktop";

	private static final String STARTUP_WINDOWS_APP_HOME_ENV_VARIABLE = "APP_HOME";
	private static final String STARTUP_WINDOWS_APP_LAUNCHER_PATH_FORMAT = "%s\\bin\\launcher.vbs";
	private static final String STARTUP_WINDOWS_REG_ROOT = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
	private static final String STARTUP_WINDOWS_REG_KEY = "Syncany";

	private static final String STARTUP_OSX_APPLICATION_NAME = "Syncany";
	private static final Path STARTUP_OSX_OSASCRIPT = Paths.get("/usr/bin/osascript");
	private static final String STARTUP_OSX_OSASCRIPT_ADD =  "'tell application \"System Events\" to make login item at end with properties {name: \"%s\", path:\"%s\", hidden:false}'";
	private static final String STARTUP_OSX_OSASCRIPT_REMOVE = "'tell application \"System Events\" to delete login item \"%s\"''";

	/**
	 * Launches a program or a URL using SWT's {@link Program}
	 * class. This method returns immediately and hands over the
	 * opening task to the UI thread.
	 */
	public static void launch(final String uri) {
			Display.getDefault().asyncExec(new Runnable() {
				@Override
				public void run() {
					try {
						if (!Program.launch(uri)) {
							throw new Exception("Unable to open URI: " + uri);
						}
					}
					catch (Exception e) {
						logger.log(Level.WARNING, "Cannot open folder " + uri, e);
					}
				}
			});
	}

	/**
	 * This method centers the dialog on the screen using
	 * <code>Display.getCurrent().getPrimaryManitor()</code>
	 */
	public static void centerOnScreen(Shell shell) {
		Monitor primary = Display.getCurrent().getPrimaryMonitor();
		Rectangle bounds = primary.getBounds();
		Rectangle rect = shell.getBounds();

		int x = bounds.x + (bounds.width - rect.width) / 2;
		int y = bounds.y + (bounds.height - rect.height) / 2;

		shell.setLocation(x, y);
	}

	/**
	 * Copies the given text to the user clipboard.
	 */
	public static void copyToClipboard(String copyText) {
		StringSelection applicationLinkStringSelection = new StringSelection(copyText);

	    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
	    clipboard.setContents(applicationLinkStringSelection, applicationLinkStringSelection);
	}

	/**
	 * Set or unset the automatic system startup for Syncany.
	 */
	// TODO [low] This method should be more generic. It is very Syncany-specific.
	public static void writeAutostart(boolean launchAtStartupEnabled) {
		switch (EnvironmentUtil.getOperatingSystem()) {
			case WINDOWS:
				writeAutostartWindows(launchAtStartupEnabled);
				break;
			case OSX:
				writeAutostartOSX(launchAtStartupEnabled);
				break;
			case UNIX_LIKE:
				writeAutostartLinux(launchAtStartupEnabled);
				break;
			default:
				logger.log(Level.INFO, "Autostart: Launch at startup feature is NOT SUPPORTED (yet) on this operating system. Ignoring option.");
				break;
		}
	}

	private static void writeAutostartLinux(boolean launchAtStartupEnabled) {
		File autostartDir = new File(StandardSystemProperty.USER_HOME.value(), ".config/autostart");
		File startupScriptFile = new File(autostartDir, STARTUP_LINUX_SCRIPT_TARGET_FILENAME);

		if (launchAtStartupEnabled) {
			writeLinuxStartupFile(autostartDir, startupScriptFile);
		}
		else {
			deleteLinuxStartupScriptFile(startupScriptFile);
		}
	}

	private static void writeLinuxStartupFile(File autostartDir, File startupScriptFile) {
		// This method always re-writes the startup/autostart script. This
		// makes sure that any altered settings (X-GNOME-Autostart, etc.) are
		// wiped out.

		logger.log(Level.INFO, "Autostart (enabled): Writing Linux startup script to " + startupScriptFile + " ...");

		if (!autostartDir.isDirectory()) {
			autostartDir.mkdirs();
		}

		try {
			InputStream startupScriptInputStream = GeneralPanel.class.getResourceAsStream(STARTUP_LINUX_SCRIPT_RESOURCE);
			FileUtils.copyFile(startupScriptInputStream, startupScriptFile);
		}
		catch (IOException e) {
			logger.log(Level.WARNING, "Autostart: Cannot write Linux startup script to " + startupScriptFile + ". Ignoring.", e);
		}
	}

	private static void deleteLinuxStartupScriptFile(File startupScriptFile) {
		if (startupScriptFile.exists()) {
			logger.log(Level.INFO, "Autostart (disabled): Deleting startup script file from " + startupScriptFile + " ...");
			startupScriptFile.delete();
		}
		else {
			logger.log(Level.INFO, "Autostart (disabled): Linux startup script does not exist at " + startupScriptFile + ". Nothing to do.");
		}
	}

	private static void writeAutostartWindows(boolean launchAtStartupEnabled) {
		try {
			if (launchAtStartupEnabled) {
				addAutostartRegistryKeyWindows();
			}
			else {
				deleteAutostartRegistryKeyWindows();
			}
		}
		catch (IOException e) {
			logger.log(Level.WARNING, "Autostart: Cannot write Windows registry key for startup.", e);
		}
	}

	private static void addAutostartRegistryKeyWindows() throws IOException {
		String appHome = System.getenv(STARTUP_WINDOWS_APP_HOME_ENV_VARIABLE);

		if (appHome != null) {
			String appLauncherFilePath = String.format(STARTUP_WINDOWS_APP_LAUNCHER_PATH_FORMAT, appHome);
			String canonicalAppLauncherFilePath = FileUtil.getCanonicalFile(new File(appLauncherFilePath)).getAbsolutePath();

			logger.log(Level.INFO, "Autostart (enabled): Windows writing registry key " + STARTUP_WINDOWS_REG_ROOT + " -> " + STARTUP_WINDOWS_REG_KEY
					+ " to value '" + canonicalAppLauncherFilePath + "' ...");

			WindowsRegistryUtil.writeString(STARTUP_WINDOWS_REG_ROOT, STARTUP_WINDOWS_REG_KEY, canonicalAppLauncherFilePath);
		}
		else {
			logger.log(Level.WARNING, "Autostart (enabled): CANNOT write Windows registry key. No " + STARTUP_WINDOWS_APP_HOME_ENV_VARIABLE + " env variable present. Ignoring.");
		}
	}

	private static void deleteAutostartRegistryKeyWindows() throws IOException {
		logger.log(Level.INFO, "Autostart (disabled): Windows deleting registry key " + STARTUP_WINDOWS_REG_ROOT + " -> " + STARTUP_WINDOWS_REG_KEY + " ...");
		WindowsRegistryUtil.deleteKey(STARTUP_WINDOWS_REG_ROOT, STARTUP_WINDOWS_REG_KEY);
	}

	private static void writeAutostartOSX(boolean launchAtStartupEnabled) {
		try {
			if (launchAtStartupEnabled) {
				addLoginItemsEntryOSX();
			}
			else {
				removeLoginItemsEntryOSX();
			}
		}
		catch (IOException e) {
			logger.log(Level.WARNING, "Autostart: Cannot alter OSX login items.", e);
		}
	}

	private static void addLoginItemsEntryOSX() throws IOException {
		logger.log(Level.INFO, "Autostart (enabled): Adding item to OSX login items");
		final String javaLibraryPath = System.getProperty("java.library.path");

		if (javaLibraryPath == null || javaLibraryPath.equals("")) {
			throw new IOException("Unable to add autostart for unbundled app");
		}

		// -Djava.library.path=/Users/chr/Desktop/Syncany.app/Contents/MacOS
		final Path bundlePath = Paths.get(javaLibraryPath).getParent().getParent();
		Runtime.getRuntime().exec(new String[]{STARTUP_OSX_OSASCRIPT.toString(), "-e", String.format(STARTUP_OSX_OSASCRIPT_ADD, STARTUP_OSX_APPLICATION_NAME, bundlePath)});
	}

	private static void removeLoginItemsEntryOSX() throws IOException {
		logger.log(Level.INFO, "Autostart (disabled): Removing item from OSX login items");

		Runtime.getRuntime().exec(new String[]{STARTUP_OSX_OSASCRIPT.toString(), "-e", String.format(STARTUP_OSX_OSASCRIPT_REMOVE, STARTUP_OSX_APPLICATION_NAME)});
	}
}
