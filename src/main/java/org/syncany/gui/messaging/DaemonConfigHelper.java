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
package org.syncany.gui.messaging;

import java.util.ArrayList;
import java.util.List;

import org.syncany.config.to.DaemonConfigTO;
import org.syncany.config.to.UserTO;

/**
 * @author Vincent Wiencek <vwiencek@gmail.com>
 *
 */
public class DaemonConfigHelper {
	public static UserTO getFirstDaemonUser(DaemonConfigTO daemonConfig) {
		List<UserTO> users = readWebSocketServerUsers(daemonConfig);
		
		if (users.size() > 0) {
			return users.get(0);
		}
		else {
			return null;
		}
	}

	private static List<UserTO> readWebSocketServerUsers(DaemonConfigTO daemonConfigTO) {
		List<UserTO> users = daemonConfigTO.getUsers();

		if (users == null) {
			users = new ArrayList<UserTO>();
		}

		// Add CLI credentials
		if (daemonConfigTO.getPortTO() != null) {
			users.add(daemonConfigTO.getPortTO().getUser());
		}

		return users;
	}
}
