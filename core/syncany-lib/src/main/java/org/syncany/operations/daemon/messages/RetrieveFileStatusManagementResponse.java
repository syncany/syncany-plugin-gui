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
package org.syncany.operations.daemon.messages;

import java.io.File;

import org.simpleframework.xml.Element;
import org.syncany.operations.daemon.messages.api.ManagementResponse;

public class RetrieveFileStatusManagementResponse extends ManagementResponse {
	public enum FileStatus {
		NOP, SYNC, NEW, IGNORE, OK, ERROR
	}
	
	@Element(name = "fileName", required = true)
	private File file;
	
	@Element(name = "status", required = true)
	private FileStatus status;
	
	public RetrieveFileStatusManagementResponse() {
		// Nothing
	}
	
	public RetrieveFileStatusManagementResponse(int requestId, File file, FileStatus status) {
		super(200, requestId, null);
		
		this.file = file;
		this.status = status;
	}

	public File getFile() {
		return file;
	}

	public FileStatus getStatus() {
		return status;
	}	
}
