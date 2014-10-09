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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.swt.widgets.Shell;
import org.syncany.config.LocalEventBus;
import org.syncany.gui.util.DesktopHelper;
import org.syncany.gui.util.I18n;
import org.syncany.operations.daemon.messages.ExitGuiInternalEvent;
import org.syncany.operations.daemon.messages.ListWatchesManagementResponse;
import org.syncany.operations.daemon.messages.UpEndSyncExternalEvent;
import org.syncany.operations.daemon.messages.UpStartSyncExternalEvent;
import org.syncany.operations.daemon.messages.UpUploadFileInTransactionSyncExternalEvent;
import org.syncany.util.FileUtil;

import com.google.common.eventbus.Subscribe;

/**
 * @author pheckel
 * @author Vincent Wiencek <vwiencek@gmail.com>
 */
public abstract class TrayIcon {
	private static int REFRESH_TIME = 500;
	private static String URL_DONATE = "https://www.syncany.org/donate.html";
	private static String URL_HOMEPAGE = "https://www.syncany.org";

	private LocalEventBus eventBus;
	private Thread systemTrayAnimationThread;
	
	private Shell shell;
	private AtomicBoolean syncing = new AtomicBoolean(false);
	
	private long uploadedFileSize;
	
	@SuppressWarnings("serial")
	protected Map<String, String> messages = new HashMap<String, String>(){{
		put("tray.menuitem.open", I18n.getString("tray.menuitem.open"));
		put("tray.menuitem.donate", I18n.getString("tray.menuitem.donate"));
		put("tray.menuitem.pause", I18n.getString("tray.menuitem.pause"));
		put("tray.menuitem.resume", I18n.getString("tray.menuitem.resume"));
		put("tray.menuitem.exit", I18n.getString("tray.menuitem.exit"));
		put("tray.menuitem.website", I18n.getString("tray.menuitem.website"));
	}};
	
	public TrayIcon(Shell shell) {
		this.shell = shell;
		
		this.eventBus = LocalEventBus.getInstance();		
		this.eventBus.register(this);
		
		startAnimationThread();
	}

	public Shell getShell() {
		return shell;
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
	public void onTrayStartSyncReceived(UpStartSyncExternalEvent message) {
		syncing.set(true);
	}

	@Subscribe
	public void handleTrayStopSyncReceived(UpEndSyncExternalEvent message) {
		syncing.set(false);
		setTrayImage(TrayIconImage.TRAY_IN_SYNC);
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
								setTrayImage(TrayIconImage.get(i));
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

	public abstract void setWatchedFolders(List<File> folders);

	public abstract void setStatusText(String statusText);
}