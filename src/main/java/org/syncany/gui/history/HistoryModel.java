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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.syncany.config.GuiEventBus;
import org.syncany.database.DatabaseVersionHeader;
import org.syncany.database.FileVersion;
import org.syncany.database.PartialFileHistory.FileHistoryId;
import org.syncany.gui.history.events.ModelSelectedDateUpdatedEvent;
import org.syncany.gui.history.events.ModelSelectedFilePathUpdatedEvent;
import org.syncany.gui.history.events.ModelSelectedRootUpdatedEvent;
import org.syncany.operations.log.LightweightDatabaseVersion;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * @author pheckel
 *
 */
public class HistoryModel {
	private static final Logger logger = Logger.getLogger(HistoryModel.class.getSimpleName());		

	private List<String> roots;
	private List<DatabaseVersionHeader> databaseVersionHeaders;
	private Map<String, List<FileVersion>> fileTree;
	private List<LightweightDatabaseVersion> databaseVersions;
	
	private String selectedRoot;
	private Date selectedDate;
	private FileHistoryId selectedFileHistoryId;
	private String selectedFilePath;
	private Set<String> expandedFilePaths;
	
	private GuiEventBus eventBus;
	
	public HistoryModel() {
		this.eventBus = GuiEventBus.getInstance();
		reset();
	}
	
	public void reset() {
		this.roots = Collections.synchronizedList(new ArrayList<String>());
		this.databaseVersionHeaders = Collections.synchronizedList(new ArrayList<DatabaseVersionHeader>());
		this.databaseVersions = Collections.synchronizedList(new ArrayList<LightweightDatabaseVersion>());
		this.fileTree = Maps.newConcurrentMap();
				
		this.selectedRoot = null;
		this.selectedDate = null;		
		this.selectedFileHistoryId = null;	
		this.selectedFilePath = null;
		this.expandedFilePaths = Sets.newConcurrentHashSet();	
	}
	
	public List<String> getRoots() {
		return roots;
	}

	public void setRoots(List<String> roots) {
		this.roots = roots;		
	}
	
	public List<DatabaseVersionHeader> getDatabaseVersionHeaders() {
		return databaseVersionHeaders;
	}

	public void setDatabaseVersionHeaders(List<DatabaseVersionHeader> databaseVersionHeaders) {
		this.databaseVersionHeaders = databaseVersionHeaders;
	}

	public Map<String, List<FileVersion>> getFileTree() {
		return fileTree;
	}

	public void setFileTree(Map<String, List<FileVersion>> fileTree) {
		this.fileTree = fileTree;
	}

	public List<LightweightDatabaseVersion> getDatabaseVersions() {
		return databaseVersions;
	}

	public void setDatabaseVersions(List<LightweightDatabaseVersion> databaseVersions) {
		this.databaseVersions = databaseVersions;
	}

	public String getSelectedRoot() {
		return selectedRoot;
	}
	
	public void setSelectedRoot(String selectedRoot) {
		if (!Objects.equal(selectedRoot, this.selectedRoot)) {			
			this.selectedRoot = selectedRoot;
			
			logger.log(Level.INFO, "Model: Selecting new root " + selectedRoot + "; Sending model update event ...");
			eventBus.post(new ModelSelectedRootUpdatedEvent(selectedRoot));
		}
	}
	
	public Date getSelectedDate() {
		return selectedDate;
	}
	
	public void setSelectedDate(Date selectedDate) {
		if (!Objects.equal(selectedDate, this.selectedDate)) {			
			this.selectedDate = selectedDate;
			
			logger.log(Level.INFO, "Model: Selecting new date " + selectedDate + "; Sending model update event ...");
			eventBus.post(new ModelSelectedDateUpdatedEvent(selectedDate));
		}
	}
	
	public FileHistoryId getSelectedFileHistoryId() {
		return selectedFileHistoryId;
	}

	public void setSelectedFileHistoryId(FileHistoryId selectedFileHistoryId) {
		logger.log(Level.INFO, "Model: Selected history ID " + selectedFileHistoryId + "; selected path set to null.");

		this.selectedFileHistoryId = selectedFileHistoryId;
		this.selectedFilePath = null;
	}
	
	public String getSelectedFilePath() {
		return selectedFilePath;
	}

	public void setSelectedFilePath(String selectedFilePath) {
		if (!Objects.equal(selectedFilePath, this.selectedFilePath)) {			
			this.selectedFilePath = selectedFilePath;
			this.selectedFileHistoryId = null;
			
			logger.log(Level.INFO, "Model: Selected path " + selectedFilePath + "; selected history ID set to null.");
			eventBus.post(new ModelSelectedFilePathUpdatedEvent(selectedFilePath));
		}		
	}

	public Set<String> getExpandedFilePaths() {
		return expandedFilePaths;
	}
	
	public void setExpandedFilePaths(Set<String> expandedFilePaths) {
		this.expandedFilePaths = expandedFilePaths;
	}		
}
