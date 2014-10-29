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

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.Shell;

/**
 * Helper class to open web sites and local folders, and to center
 * a window on the screen. 
 * 
 * @author Vincent Wiencek <vwiencek@gmail.com>
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class DesktopHelper {
	private static final Logger logger = Logger.getLogger(DesktopHelper.class.getSimpleName());
	
	/**
	 * Opens the file manager at the location of the 
	 * given folder. The method first tries to open the folder
	 * using {@link Program} and then falls back to {@link Desktop}. 
	 */
	public static void openFolder(File folder) {
		try {
			if (folder.exists() && folder.isDirectory()) {
				boolean folderOpened = Program.launch(folder.getAbsolutePath());
				
				if (!folderOpened) {
					Desktop.getDesktop().open(folder);
				}
			}
		}
		catch (IOException e) {
			logger.log(Level.WARNING, "Cannot open folder " + folder, e);
		}
	}

	public static void browse(String url) {
		try {
			Desktop.getDesktop().browse(new URI(url));
		}
		catch (Exception e) {
			logger.log(Level.WARNING, "Cannot browse to URL " + url, e);
		}	
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
}
