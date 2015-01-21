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
import java.util.Date;
import java.util.List;

import org.eclipse.swt.widgets.Display;
import org.syncany.config.GuiEventBus;
import org.syncany.database.DatabaseVersionHeader;
import org.syncany.operations.daemon.Watch;
import org.syncany.operations.daemon.messages.GetDatabaseVersionHeadersFolderRequest;
import org.syncany.operations.daemon.messages.GetDatabaseVersionHeadersFolderResponse;
import org.syncany.operations.daemon.messages.ListWatchesManagementRequest;
import org.syncany.operations.daemon.messages.ListWatchesManagementResponse;

import com.google.common.eventbus.Subscribe;

/**
 * @author pheckel
 *
 */
public class MainController implements MainPanelListener {
	private HistoryModel model;
	private MainPanel mainPanel;
	
	private ListWatchesManagementRequest pendingListWatchesRequest;
	private GuiEventBus eventBus;		

	public void sendListWatchesRequest() {
		pendingListWatchesRequest = new ListWatchesManagementRequest();
		eventBus.post(pendingListWatchesRequest);		
	}
	
	@Override
	public void onDateChanged(Date newDate) {
		boolean listUpdateRequired = !newDate.equals(model.getSelectedDate());
			
		if (listUpdateRequired) {
			model.setSelectedDate(newDate);							
		}			
	}

	@Override
	public void onRootChanged(String newRoot) {
		model.setSelectedRoot(newRoot);
		model.setSelectedDate(null);
		model.setSelectedFileHistoryId(null);
		model.getExpandedFilePaths().clear();
		
		GetDatabaseVersionHeadersFolderRequest getHeadersRequest = new GetDatabaseVersionHeadersFolderRequest();
		getHeadersRequest.setRoot(newRoot);
		
		eventBus.post(getHeadersRequest);
		
		fileTreeComposite.resetAndRefresh();
		logComposite.resetAndRefresh();
	}
	
	@Subscribe
	public void onListWatchesManagementResponse(final ListWatchesManagementResponse listWatchesResponse) {
		if (pendingListWatchesRequest != null && pendingListWatchesRequest.getId() == listWatchesResponse.getRequestId()) {
			// Nullify pending request
			pendingListWatchesRequest = null;

			// Update combo box
			mainPanel.updateRootsCombo(listWatchesResponse.getWatches());		
		}
	}
	
	@Subscribe
	public void onGetDatabaseVersionHeadersFolderResponse(final GetDatabaseVersionHeadersFolderResponse getHeadersResponse) {
		List<DatabaseVersionHeader> headers = getHeadersResponse.getDatabaseVersionHeaders();

		if (headers.size() > 0) {
			Date newSelectedDate = headers.get(headers.size()-1).getDate();
			model.setSelectedDate(newSelectedDate);	
		}
		else {			
			model.setSelectedDate(null);
		}			
		
		mainPanel.updateSlider(headers);		
	}

}
