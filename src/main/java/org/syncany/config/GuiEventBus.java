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

/**
 * Event bus for the GUI plugin, mainly used to pass messages
 * between tray icon and main operation.
 * 
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class GuiEventBus extends InternalEventBus {
	public static GuiEventBus getInstance() {
		return InternalEventBus.getInstance(GuiEventBus.class);
	}
	
	public static GuiEventBus getAndRegister(Object registerObject) {
		GuiEventBus eventBus = getInstance();
		
		eventBus.register(registerObject);
		return eventBus;
	}
}
