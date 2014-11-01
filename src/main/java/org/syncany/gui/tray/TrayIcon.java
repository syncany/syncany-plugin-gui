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
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.syncany.config.GuiEventBus;
import org.syncany.gui.util.DesktopHelper;
import org.syncany.gui.util.I18n;
import org.syncany.gui.wizard.WizardDialog;
import org.syncany.operations.ChangeSet;
import org.syncany.operations.daemon.messages.DaemonReloadedExternalEvent;
import org.syncany.operations.daemon.messages.DownDownloadFileSyncExternalEvent;
import org.syncany.operations.daemon.messages.DownEndSyncExternalEvent;
import org.syncany.operations.daemon.messages.DownStartSyncExternalEvent;
import org.syncany.operations.daemon.messages.ExitGuiInternalEvent;
import org.syncany.operations.daemon.messages.ListWatchesManagementRequest;
import org.syncany.operations.daemon.messages.ListWatchesManagementResponse;
import org.syncany.operations.daemon.messages.StatusEndSyncExternalEvent;
import org.syncany.operations.daemon.messages.UpEndSyncExternalEvent;
import org.syncany.operations.daemon.messages.UpIndexStartSyncExternalEvent;
import org.syncany.operations.daemon.messages.UpStartSyncExternalEvent;
import org.syncany.operations.daemon.messages.UpUploadFileInTransactionSyncExternalEvent;
import org.syncany.operations.daemon.messages.UpUploadFileSyncExternalEvent;
import org.syncany.util.FileUtil;
import org.syncany.util.StringUtil;

import com.google.common.eventbus.Subscribe;

/**
 * Represents the tray icon, showing the status of the application,
 * a menu to control the application and the ability to display
 * notifications. The tray icon is the central entry point for
 * the application.
 * 
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 * @author Vincent Wiencek <vwiencek@gmail.com>
 */
public abstract class TrayIcon {
	private static int REFRESH_TIME = 500;
	private static String URL_REPORT_ISSUE = "https://www.syncany.org/r/issue";
	private static String URL_DONATE = "https://www.syncany.org/donate.html";
	private static String URL_HOMEPAGE = "https://www.syncany.org";
	
	protected Shell trayShell;
	protected WizardDialog wizard;
	protected GuiEventBus eventBus;
	protected Map<String, String> messages;

	private Thread systemTrayAnimationThread;
	private AtomicInteger syncingCount;
	private long uploadedFileSize;

	public TrayIcon(Shell shell) {
		this.trayShell = shell;
		this.messages = new HashMap<String, String>();

		this.eventBus = GuiEventBus.getInstance();
		this.eventBus.register(this);

		this.syncingCount = new AtomicInteger(0);

		initInternationalization();
		startAnimationThread();
	}

	protected void showNew() {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				if (wizard == null) {
					wizard = new WizardDialog(trayShell);
					wizard.open();
					
					wizard = null;
				}
			}
		});
	}

	protected void showFolder(File folder) {
		DesktopHelper.launch(folder.getAbsolutePath());
	}

	protected void showReportIssue() {
		DesktopHelper.launch(URL_REPORT_ISSUE);
	}

	protected void showDonate() {
		DesktopHelper.launch(URL_DONATE);
	}

	protected void showWebsite() {
		DesktopHelper.launch(URL_HOMEPAGE);
	}

	protected void exitApplication() {
		dispose();
		eventBus.post(new ExitGuiInternalEvent());
	}

	@Subscribe
	public void onStatusEndSyncEvent(StatusEndSyncExternalEvent statusEndSyncEvent) {
		//if (statusEndSyncEvent)
	}
	
	@Subscribe
	public void onDaemonReloadedEventReceived(DaemonReloadedExternalEvent daemonReloadedEvent) {
		eventBus.post(new ListWatchesManagementRequest());
	}

	@Subscribe
	public void onListWatchesResponseReceived(ListWatchesManagementResponse listWatchesResponse) {
		setWatchedFolders(listWatchesResponse.getWatches());
	}

	@Subscribe
	public void onUpStartEventReceived(UpStartSyncExternalEvent syncEvent) {
		syncingCount.incrementAndGet();		
		setStatusText("Starting indexing and upload ...");
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

		String uploadedTotalStr = FileUtil.formatFileSize(uploadedFileSize);
		int uploadedPercent = (int) Math.round((double) uploadedFileSize / syncEvent.getTotalFileSize() * 100);

		setStatusText("Uploading " + syncEvent.getCurrentFileIndex() + "/" + syncEvent.getTotalFileCount() + " (" + uploadedTotalStr + " / "
				+ uploadedPercent + "%) ...");
		
		uploadedFileSize += syncEvent.getCurrentFileSize();
	}

	@Subscribe
	public void onUpEndEventReceived(UpEndSyncExternalEvent syncEvent) {
		syncingCount.decrementAndGet();
	}

	@Subscribe
	public void onDownDownloadFileSyncEventReceived(DownDownloadFileSyncExternalEvent syncEvent) {
		String fileDescription = syncEvent.getFileDescription();
		int currentFileIndex = syncEvent.getCurrentFileIndex();
		int maxFileCount = syncEvent.getMaxFileCount();

		setStatusText("Downloading " + fileDescription + " " + currentFileIndex + "/" + maxFileCount + " ...");
	}

	@Subscribe
	public void onDownStartEventReceived(DownStartSyncExternalEvent syncEvent) {
		syncingCount.incrementAndGet();
		setStatusText("Checking for remote changes ...");
	}

	@Subscribe
	public void onDownEndEventReceived(DownEndSyncExternalEvent downEndSyncEvent) {
		syncingCount.decrementAndGet();

		// Display notification
		ChangeSet changeSet = downEndSyncEvent.getChanges();

		if (changeSet.hasChanges()) {
			String rootName = new File(downEndSyncEvent.getRoot()).getName();
			int totalChangedFiles = changeSet.getNewFiles().size() + changeSet.getChangedFiles().size() + changeSet.getDeletedFiles().size();

			String subject = "";
			String message = "";

			if (totalChangedFiles == 1) {
				if (changeSet.getNewFiles().size() == 1) {
					subject = changeSet.getNewFiles().first() + " added";
					message = "File '" + changeSet.getNewFiles().first() + "' was added to your Syncany folder '" + rootName + "'";
				}

				if (changeSet.getChangedFiles().size() == 1) {
					subject = changeSet.getChangedFiles().first() + " changed";
					message = "File '" + changeSet.getChangedFiles().first() + "' was altered or moved in your Syncany folder '" + rootName + "'";
				}

				if (changeSet.getDeletedFiles().size() == 1) {
					subject = changeSet.getDeletedFiles().first() + " deleted";
					message = "File '" + changeSet.getDeletedFiles().first() + "' was removed from your Syncany folder '" + rootName + "'";
				}
			}
			else {
				List<String> messageParts = new ArrayList<>();

				if (changeSet.getNewFiles().size() > 0) {
					if (changeSet.getNewFiles().size() == 1) {
						messageParts.add(changeSet.getNewFiles().size() + " file added");
					}
					else {
						messageParts.add(changeSet.getNewFiles().size() + " files added");
					}
				}

				if (changeSet.getChangedFiles().size() > 0) {
					if (changeSet.getChangedFiles().size() == 1) {
						messageParts.add(changeSet.getChangedFiles().size() + " file changed");
					}
					else {
						messageParts.add(changeSet.getChangedFiles().size() + " files changed");
					}
				}

				if (changeSet.getDeletedFiles().size() > 0) {
					if (changeSet.getDeletedFiles().size() == 1) {
						messageParts.add(changeSet.getDeletedFiles().size() + " file deleted");
					}
					else {
						messageParts.add(changeSet.getDeletedFiles().size() + " files deleted");
					}
				}

				subject = "Syncany folder '" + rootName + "' synced";
				message = StringUtil.join(messageParts, ", ") + " in your Syncany folder '" + rootName + "'";
			}

			displayNotification(subject, message);
		}
	}

	private void initInternationalization() {
		messages.put("tray.menuitem.new", I18n.getString("tray.menuitem.new"));
		messages.put("tray.menuitem.status.insync", I18n.getString("tray.menuitem.status.insync"));
		messages.put("tray.menuitem.issue", I18n.getString("tray.menuitem.issue"));
		messages.put("tray.menuitem.donate", I18n.getString("tray.menuitem.donate"));
		messages.put("tray.menuitem.exit", I18n.getString("tray.menuitem.exit"));
		messages.put("tray.menuitem.website", I18n.getString("tray.menuitem.website"));
	}

	private void startAnimationThread() {
		systemTrayAnimationThread = new Thread(new Runnable() {
			@Override
			public void run() {
				while (true) {
					while (syncingCount.get() <= 0) {
						try {
							Thread.sleep(200);
						}
						catch (InterruptedException e) {
							// Don't care
						}
					}

					int trayImageIndex = 0;

					while (syncingCount.get() > 0) {
						try {
							setTrayImage(TrayIconImage.getSyncImage(trayImageIndex));
							trayImageIndex = (trayImageIndex + 1) % TrayIconImage.MAX_SYNC_IMAGES;

							Thread.sleep(REFRESH_TIME);
						}
						catch (InterruptedException e) {
							// Don't care
						}
					}

					setTrayImage(TrayIconImage.TRAY_IN_SYNC);
					setStatusText("All files in sync");					
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

	protected abstract void dispose();
}