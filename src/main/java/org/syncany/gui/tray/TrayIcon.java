/*
 * Syncany, www.syncany.org
 * Copyright (C) 2011-2013 Philipp C. Heckel <philipp.heckel@gmail.com> 
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
package org.syncany.gui.tray;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.swt.widgets.Shell;
import org.syncany.config.LocalEventBus;
import org.syncany.gui.util.DesktopHelper;
import org.syncany.gui.util.I18n;
import org.syncany.operations.ChangeSet;
import org.syncany.operations.daemon.messages.DownDownloadFileSyncExternalEvent;
import org.syncany.operations.daemon.messages.DownEndSyncExternalEvent;
import org.syncany.operations.daemon.messages.ExitGuiInternalEvent;
import org.syncany.operations.daemon.messages.ListWatchesManagementResponse;
import org.syncany.operations.daemon.messages.LsRemoteStartSyncExternalEvent;
import org.syncany.operations.daemon.messages.StatusStartSyncExternalEvent;
import org.syncany.operations.daemon.messages.UpEndSyncExternalEvent;
import org.syncany.operations.daemon.messages.UpIndexStartSyncExternalEvent;
import org.syncany.operations.daemon.messages.UpStartSyncExternalEvent;
import org.syncany.operations.daemon.messages.UpUploadFileInTransactionSyncExternalEvent;
import org.syncany.operations.daemon.messages.UpUploadFileSyncExternalEvent;
import org.syncany.operations.down.DownOperationResult;
import org.syncany.util.FileUtil;
import org.syncany.util.StringUtil;

import com.google.common.eventbus.Subscribe;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 * @author Vincent Wiencek <vwiencek@gmail.com>
 */
public abstract class TrayIcon {
	private static int REFRESH_TIME = 500;
	private static String URL_DONATE = "https://www.syncany.org/donate.html";
	private static String URL_HOMEPAGE = "https://www.syncany.org";

	private LocalEventBus eventBus;
	private Thread systemTrayAnimationThread;
	
	protected Shell shell;
	protected Map<String, String> messages;

	private AtomicBoolean syncing;	
	private long uploadedFileSize;	
	
	public TrayIcon(Shell shell) {
		this.shell = shell;
		this.messages = new HashMap<String, String>();
		
		this.eventBus = LocalEventBus.getInstance();		
		this.eventBus.register(this);
		
		this.syncing = new AtomicBoolean(false);
		
		initInternationalization();
		startAnimationThread();
	}

	protected void showFolder(File folder) {
		DesktopHelper.openFolder(folder);
	}

	protected void showDonate() {
		DesktopHelper.browse(URL_DONATE);
	}

	protected void showWebsite() {
		DesktopHelper.browse(URL_HOMEPAGE);
	}

	protected void exitApplication() {
		eventBus.post(new ExitGuiInternalEvent());
	}

	@Subscribe
	public void onListWatchesResponseReceived(ListWatchesManagementResponse listWatchesResponse){
		setWatchedFolders(listWatchesResponse.getWatches());
	}
	
	@Subscribe
	public void onUpStartEventReceived(UpStartSyncExternalEvent syncEvent) {
		syncing.set(true);
		setStatusText("Starting indexing and upload ...");					
	}
	
	@Subscribe
	public void onStatusStartEventReceived(StatusStartSyncExternalEvent syncEvent) {
		setStatusText("Checking for new or altered files ...");
	}
	
	@Subscribe
	public void onLsRemoteStartEventReceived(LsRemoteStartSyncExternalEvent syncEvent) {
		setStatusText("Checking remote changes ...");
	}
	
	@Subscribe
	public void onIndexStartEventReceived(UpIndexStartSyncExternalEvent syncEvent) {
		setStatusText("Indexing " + syncEvent.getFileCount() + " new or altered file(s)...");
	}
	
	@Subscribe
	public void onUploadFileEventReceived(UpUploadFileSyncExternalEvent syncEvent) {
		setStatusText("Uploading " + syncEvent.getFilename() + " ...");
	}
	
	@Subscribe
	public void onUploadFileInTransactionEventReceived(UpUploadFileInTransactionSyncExternalEvent syncEvent) {
		if (syncEvent.getCurrentFileIndex() <= 1) {
			uploadedFileSize = 0;
		}
		
		String currentFileSizeStr = FileUtil.formatFileSize(syncEvent.getCurrentFileSize());
		int uploadedPercent = (int) Math.round((double) uploadedFileSize / syncEvent.getTotalFileSize() * 100); 
		
		setStatusText("Uploading " + syncEvent.getCurrentFileIndex() + "/" + syncEvent.getTotalFileCount() + " (" + currentFileSizeStr + ", total " + uploadedPercent + "%) ...");
		uploadedFileSize += syncEvent.getCurrentFileSize();
	}	
	
	@Subscribe
	public void onUpEndEventReceived(UpEndSyncExternalEvent syncEvent) {
		syncing.set(false);
		
		setTrayImage(TrayIconImage.TRAY_IN_SYNC);
		setStatusText("All files in sync");					
	}
	
	@Subscribe
	public void onDownDownloadFileSyncEventReceived(DownDownloadFileSyncExternalEvent syncEvent) {
		String fileDescription = syncEvent.getFileDescription();
		int currentFileIndex = syncEvent.getCurrentFileIndex();
		int maxFileCount = syncEvent.getMaxFileCount();
		
		setStatusText("Downloading " + fileDescription + " "+ currentFileIndex + "/" + maxFileCount + " ...");			
	}
	
	@Subscribe
	public void onPostDownOperation(DownEndSyncExternalEvent downEndSyncEvent) {
		DownOperationResult downOperationResult = downEndSyncEvent.getResult();
		ChangeSet changeSet = downOperationResult.getChangeSet();
		
		if (changeSet.hasChanges()) {
			List<String> changeMessageParts = new ArrayList<>();
			
			if (changeSet.getNewFiles().size() > 0) {
				changeMessageParts.add(changeSet.getNewFiles().size() + " file(s) added");
			}
			
			if (changeSet.getChangedFiles().size() > 0) {
				changeMessageParts.add(changeSet.getChangedFiles().size() + " file(s) changed");
			}
			
			if (changeSet.getDeletedFiles().size() > 0) {
				changeMessageParts.add(changeSet.getDeletedFiles().size() + " file(s) deleted");
			}
			
			String changedMessage = StringUtil.join(changeMessageParts, ", ");
		
			displayNotification(changedMessage, changedMessage);
		}			
	}	

	private void initInternationalization() {
		messages.put("tray.menuitem.open", I18n.getString("tray.menuitem.open"));
		messages.put("tray.menuitem.donate", I18n.getString("tray.menuitem.donate"));
		messages.put("tray.menuitem.pause", I18n.getString("tray.menuitem.pause"));
		messages.put("tray.menuitem.resume", I18n.getString("tray.menuitem.resume"));
		messages.put("tray.menuitem.exit", I18n.getString("tray.menuitem.exit"));
		messages.put("tray.menuitem.website", I18n.getString("tray.menuitem.website"));
	}
	
	private void startAnimationThread() {
		systemTrayAnimationThread = new Thread(new Runnable() {
				@Override
				public void run() {
					while (true) {
						while (!syncing.get()) {
							try {
								Thread.sleep(200);
							}
							catch (InterruptedException e) {
								// Don't care
							}
						}

						int i = 0;

						while (syncing.get()) {
							try {
								setTrayImage(TrayIconImage.getSyncImage(i));
								i++;
								
								if (i == 6) {
									i = 0;
								}
								
								Thread.sleep(REFRESH_TIME);
							}
							catch (InterruptedException e) {
								// Don't care
							}
						}
						
						setTrayImage(TrayIconImage.TRAY_IN_SYNC);
					}
				}
			});
		
		systemTrayAnimationThread.start();
	}
	
	// Abstract methods
	
	protected abstract void setTrayImage(TrayIconImage image);

	protected abstract void setWatchedFolders(List<File> folders);

	protected abstract void setStatusText(String statusText);
	
	protected abstract void displayNotification(String subject, String message);
}