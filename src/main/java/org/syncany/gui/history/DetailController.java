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

import java.util.Map;

import org.eclipse.swt.widgets.Display;
import org.syncany.config.GuiEventBus;
import org.syncany.database.FileVersion.FileType;
import org.syncany.database.PartialFileHistory.FileHistoryId;
import org.syncany.operations.daemon.messages.LsFolderRequest;
import org.syncany.operations.daemon.messages.LsFolderResponse;
import org.syncany.operations.ls.LsOperationOptions;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.eventbus.Subscribe;

/**
 * @author pheckel
 *
 */
public class DetailController {
	private DetailPanel detailPanel;
	
	private Map<Integer, LsFolderRequest> pendingLsFolderRequests;	
	private GuiEventBus eventBus; 

	public DetailController() {
		this.pendingLsFolderRequests = Maps.newConcurrentMap();

		this.eventBus = GuiEventBus.getInstance();
		this.eventBus.register(this);	
	}
	
	public void sendLsFolderRequest(String root, FileHistoryId fileHistoryId) {
		// Create list request
		LsOperationOptions lsOptions = new LsOperationOptions();
		
		lsOptions.setPathExpression(fileHistoryId.toString());
		lsOptions.setFileHistoryId(true);
		lsOptions.setRecursive(false);
		lsOptions.setDeleted(true);
		lsOptions.setFetchHistories(true);
		lsOptions.setFileTypes(Sets.newHashSet(FileType.FILE, FileType.SYMLINK));
		
		LsFolderRequest lsRequest = new LsFolderRequest();
		
		lsRequest.setRoot(root);
		lsRequest.setOptions(lsOptions);
		
		// Send request
		pendingLsFolderRequests.put(lsRequest.getId(), lsRequest);
		eventBus.post(lsRequest);
	}
	

	@Subscribe
	public void onLsFolderResponse(final LsFolderResponse lsResponse) {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				LsFolderRequest lsRequest = pendingLsFolderRequests.remove(lsResponse.getRequestId());
				
				if (lsRequest != null) {
					detailPanel.updateTable(lsRequest, lsResponse);
				}
			}
		});		
	}

}
