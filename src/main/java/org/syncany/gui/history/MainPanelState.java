/*
 * Syncany, www.syncany.org
 * Copyright (C) 2011-2015 Philipp C. Heckel <philipp.heckel@gmail.com> 
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
package org.syncany.gui.history;

import java.util.Date;
import java.util.Set;

import org.syncany.database.FileVersion;

import com.google.common.collect.Sets;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class MainPanelState {
	private String selectedRoot;
	private Date selectedDate;
	private FileVersion selectedFileVersion;
	private Set<String> expandedFilePaths;
	
	public MainPanelState() {
		this.selectedRoot = null;
		this.selectedDate = null;		
		this.selectedFileVersion = null;		
		this.expandedFilePaths = Sets.newConcurrentHashSet();
	}
	
	public String getSelectedRoot() {
		return selectedRoot;
	}
	
	public void setSelectedRoot(String selectedRoot) {
		this.selectedRoot = selectedRoot;
	}
	
	public Date getSelectedDate() {
		return selectedDate;
	}
	
	public void setSelectedDate(Date selectedDate) {
		this.selectedDate = selectedDate;
	}
	
	public FileVersion getSelectedFileVersion() {
		return selectedFileVersion;
	}
	
	public void setSelectedFileVersion(FileVersion selectedFileVersion) {
		this.selectedFileVersion = selectedFileVersion;
	}
	
	public Set<String> getExpandedFilePaths() {
		return expandedFilePaths;
	}
	
	public void setExpandedFilePaths(Set<String> expandedFilePaths) {
		this.expandedFilePaths = expandedFilePaths;
	}
}
