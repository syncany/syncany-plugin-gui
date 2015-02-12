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
import java.util.logging.Level;
import java.util.logging.Logger;

import org.syncany.config.GuiEventBus;
import org.syncany.database.PartialFileHistory.FileHistoryId;
import org.syncany.gui.history.events.ModelSelectedDateUpdatedEvent;
import org.syncany.gui.history.events.ModelSelectedFilePathUpdatedEvent;
import org.syncany.gui.history.events.ModelSelectedRootUpdatedEvent;

import com.google.common.base.Objects;

/**
 * This class represents the model of the {@link HistoryDialog}.
 * Its main purpose is to keep track of the selected date and root.
 * 
 * <p>When the model is changed, the changes are published to the
 * event bus, from which the views will be updated.
 *  
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class HistoryModel {
	private static final Logger logger = Logger.getLogger(HistoryModel.class.getSimpleName());		
	
	private String selectedRoot;
	private Date selectedDate;
	private FileHistoryId selectedFileHistoryId;
	private String selectedFilePath;
	
	private GuiEventBus eventBus;
	
	public HistoryModel() {
		this.eventBus = GuiEventBus.getInstance();
		this.reset();
	}
	
	public void reset() {
		this.selectedRoot = null;
		this.selectedDate = null;		
		this.selectedFileHistoryId = null;	
		this.selectedFilePath = null;
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
}
