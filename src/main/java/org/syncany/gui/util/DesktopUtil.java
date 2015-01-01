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

import io.undertow.util.FileUtils;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.Shell;
import org.syncany.gui.preferences.GeneralPanel;
import org.syncany.util.EnvironmentUtil;

import com.google.common.base.StandardSystemProperty;

/**
 * Helper class to open web sites and local folders, and to center
 * a window on the screen. 
 * 
 * @author Vincent Wiencek <vwiencek@gmail.com>
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class DesktopUtil {
	private static final Logger logger = Logger.getLogger(DesktopUtil.class.getSimpleName());
	private static final String STARTUP_SCRIPT_LINUX_RESOURCE = "/" + GeneralPanel.class.getPackage().getName().replace('.', '/') + "/syncany.desktop";
	private static final String STARTUP_SCRIPT_LINUX_TARGET_FILENAME = "syncany.desktop";

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
		if (EnvironmentUtil.isUnixLikeOperatingSystem()) {
			File autostartDir = new File(StandardSystemProperty.USER_HOME.value(), ".config/autostart");
			File startupScriptFile = new File(autostartDir, STARTUP_SCRIPT_LINUX_TARGET_FILENAME);
			
			if (launchAtStartupEnabled) {
				writeLinuxStartupFile(autostartDir, startupScriptFile);
			}
			else {
				deleteStartupScriptFile(startupScriptFile);
			}			
		}
		else {
			logger.log(Level.INFO, "Autostart: Launch at startup feature is NOT SUPPORTED (yet) on this operating system. Ignoring option.");
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
			InputStream startupScriptInputStream = GeneralPanel.class.getResourceAsStream(STARTUP_SCRIPT_LINUX_RESOURCE);
			FileUtils.copyFile(startupScriptInputStream, startupScriptFile);
		}
		catch (IOException e) {
			logger.log(Level.WARNING, "Autostart: Cannot write Linux startup script to " + startupScriptFile + ". Ignoring.", e);
		}								
	}
	
	private static void deleteStartupScriptFile(File startupScriptFile) {
		if (startupScriptFile.exists()) {
			logger.log(Level.INFO, "Autostart (disabled): Deleting startup script file from " + startupScriptFile + " ...");
			startupScriptFile.delete();
		}
		else {
			logger.log(Level.INFO, "Autostart (disabled): Linux startup script does not exist at " + startupScriptFile + ". Nothing to do.");
		}
	}
}
