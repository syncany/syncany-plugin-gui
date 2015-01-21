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

import java.io.File;

import org.eclipse.swt.widgets.Display;
import org.syncany.config.GuiEventBus;
import org.syncany.gui.util.DesktopUtil;
import org.syncany.operations.daemon.messages.LogFolderRequest;
import org.syncany.operations.daemon.messages.LogFolderResponse;
import org.syncany.operations.log.LightweightDatabaseVersion;
import org.syncany.operations.log.LogOperationOptions;

import com.google.common.eventbus.Subscribe;

/**
 * @author pheckel
 *
 */
public class LogController implements LogCompositeListener {
	public static final int LOG_REQUEST_DATABASE_COUNT = 15;
	public static final int LOG_REQUEST_FILE_COUNT = 10;

	private LogFolderRequest pendingLogFolderRequest;
	private GuiEventBus eventBus;	
	
	public void resetAndRefresh() {
		resetAndRefresh(0);
	}
	
	public void resetAndRefresh(int startIndex) {
		LogOperationOptions logOptions = new LogOperationOptions();
		logOptions.setMaxDatabaseVersionCount(LOG_REQUEST_DATABASE_COUNT);
		logOptions.setStartDatabaseVersionIndex(startIndex);
		logOptions.setMaxFileHistoryCount(LOG_REQUEST_FILE_COUNT);
		
		pendingLogFolderRequest = new LogFolderRequest();
		pendingLogFolderRequest.setRoot(state.getSelectedRoot());
		pendingLogFolderRequest.setOptions(logOptions);
		
		eventBus.post(pendingLogFolderRequest);
	}
	

	@Subscribe
	public void onLogFolderResponse(final LogFolderResponse logResponse) {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				if (pendingLogFolderRequest != null && pendingLogFolderRequest.getId() == logResponse.getRequestId()) {
					updateTabs(pendingLogFolderRequest, logResponse);
					pendingLogFolderRequest = null;
				}				
			}
		});		
	}



	@Override
	public void onSelectDatabaseVersion(LightweightDatabaseVersion databaseVersion) {
		mainPanel.setSelectedDate(databaseVersion.getDate());
	}
	
	@Override
	public void onDoubleClickDatabaseVesion(LightweightDatabaseVersion databaseVersion) {
		mainPanel.showTree();
	}

	@Override
	public void onFileJumpToDetail(LightweightDatabaseVersion databaseVersion, String relativeFilePath) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onFileJumpToTree(LightweightDatabaseVersion databaseVersion, String relativeFilePath) {
		mainPanelState.setSelectedFilePath(relativeFilePath);
		
		mainPanel.setSelectedDate(databaseVersion.getDate());
		mainPanel.refreshTree(relativeFilePath);
		
		mainPanel.showTree();				

	}

	@Override
	public void onFileOpen(LightweightDatabaseVersion databaseVersion, String relativeFilePath) {
		final File file = new File(root, relativeFilePath);
		launchOrDisplayError(file);
	}

	@Override
	public void onFileOpenContainingFolder(LightweightDatabaseVersion databaseVersion, String relativeFilePath) {
		final File file = new File(root, relativeFilePath);
		launchOrDisplayError(file.getParentFile());
	}

	@Override
	public void onFileCopytoClipboard(LightweightDatabaseVersion databaseVersion, String relativeFilePath) {
		final File file = new File(root, relativeFilePath);
		DesktopUtil.copyToClipboard(file.getAbsolutePath());
	}


}
