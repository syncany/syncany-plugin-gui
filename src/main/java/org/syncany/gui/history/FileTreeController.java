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
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.TreeItem;
import org.syncany.config.GuiEventBus;
import org.syncany.database.FileVersion;
import org.syncany.database.FileVersion.FileType;
import org.syncany.operations.daemon.messages.LsFolderRequest;
import org.syncany.operations.daemon.messages.LsFolderResponse;
import org.syncany.operations.ls.LsOperationOptions;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.eventbus.Subscribe;

/**
 * @author pheckel
 *
 */
public class FileTreeController implements FileTreeCompositeListener {
	private static final Logger logger = Logger.getLogger(FileTreeController.class.getSimpleName());		

	private HistoryModel model;
	
	private Map<Integer, LsFolderRequest> pendingLsFolderRequests;	
	
	private GuiEventBus eventBus;	
	
	private void sendLsRequest(String pathExpression) {
		// Date
		Date browseDate = (model.getSelectedDate() != null) ? model.getSelectedDate() : new Date();
		
		// Create list request
		LsOperationOptions lsOptions = new LsOperationOptions();
		
		lsOptions.setPathExpression(pathExpression);
		lsOptions.setDate(browseDate);
		lsOptions.setRecursive(false);
		lsOptions.setFetchHistories(false);
		lsOptions.setFileTypes(Sets.newHashSet(FileType.FILE, FileType.FOLDER, FileType.SYMLINK));
		
		LsFolderRequest lsRequest = new LsFolderRequest();
		
		lsRequest.setRoot(model.getSelectedRoot());
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
				fileTree.setEnabled(true);
				
				LsFolderRequest lsRequest = pendingLsFolderRequests.remove(lsResponse.getRequestId());
				
				if (lsRequest != null) {
					updateTree(lsRequest, lsResponse);
				}
			}
		});		
	}

	
	public void refreshTree(String file) {
		fileTreeComposite.refreshTree(file);
	}


	

	public void refreshTree(String pathExpression) {
		if ("".equals(pathExpression)) {
			throw new IllegalArgumentException();
		}
				
		logger.log(Level.INFO, "Refreshing tree at " + pathExpression + " ...");	
		
		// Remember this as expanded path
		state.getExpandedFilePaths().add(pathExpression);
		
		// Find all sub-paths, a/b/c/ -> [a, a/b, a/b/c]
		List<String> allPaths = getPaths(pathExpression + "/");
		List<String> notLoadedPaths = new ArrayList<>();
		
		for (String path : allPaths) {
			TreeItem treeItem = findItemByPath(path);
			
			if (!notLoadedPaths.isEmpty()) {
				notLoadedPaths.add(path);
				logger.log(Level.INFO, "- Item '" + path + "' has not been loaded (2).");					
			}
			else if (treeItem != null) {			
				if (hasRetrievingChildItem(treeItem)) {
					notLoadedPaths.add(path);
					logger.log(Level.INFO, "- Item '" + path + "' has not been loaded (1).");					
				}
			}
		}
		
		// If items unloaded: set 'select after load' item, and send load requests
		if (!notLoadedPaths.isEmpty()) {
			for (String path : notLoadedPaths) {
				sendLsRequest(path + "/");
			}			
		}
		else {
			selectItemByPath(pathExpression);
		}
	}
	
	@Override
	public void onDoubleClickItem(FileVersion fileVersion) {
		historyDialog.showDetails(root, fileHistoryId);
	}

	@Override
	public void onSelectItem(FileVersion fileVersion) {
		model.setSelectedFileHistoryId(fileVersion.getFileHistoryId());
	}

	@Override
	public void onExpandItem(FileVersion fileVersion) {
		refreshTree(fileVersion.getPath());
	}

	@Override
	public void onCollapseItem(FileVersion fileVersion) {
		// Remove all children items from saved expanded paths
		Iterables.removeIf(model.getExpandedFilePaths(), new Predicate<String>() {
			@Override
			public boolean apply(String expandedPath) {				
				return expandedPath.startsWith(fileVersion.getPath());
			}			
		});		
	}
	
	

}
