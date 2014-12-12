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
package org.syncany.config.to;

import java.io.File;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.core.Persister;
import org.syncany.config.ConfigException;
import org.syncany.gui.tray.TrayIconTheme;
import org.syncany.gui.tray.TrayIconType;

@Root(name = "gui", strict = false)
public class GuiConfigTO {
	@Element(name = "tray", required = false)
	private TrayIconType tray;

	@Element(name = "theme", required = false)
	private TrayIconTheme theme;

	public GuiConfigTO() {
		// Nothing
	}

	public static GuiConfigTO load(File file) throws ConfigException {
		try {
			return new Persister().read(GuiConfigTO.class, file);
		}
		catch (Exception e) {
			throw new ConfigException("Config file does not exist or is invalid: " + file, e);
		}
	}

	public static void save(GuiConfigTO configTO, File file) throws ConfigException {
		try {
			new Persister().write(configTO, file);
		}
		catch (Exception e) {
			throw new ConfigException("Config could not be written: " + file, e);
		}
	}

	public TrayIconType getTray() {
		return tray;
	}

	public void setTray(TrayIconType tray) {
		this.tray = tray;
	}

	public TrayIconTheme getTheme() {
		return theme;
	}

	public void setTheme(TrayIconTheme theme) {
		this.theme = theme;
	}
}
