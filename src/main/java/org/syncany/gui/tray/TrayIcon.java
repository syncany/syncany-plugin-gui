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
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.syncany.config.GuiConfigHelper;
import org.syncany.config.GuiEventBus;
import org.syncany.config.to.GuiConfigTO;
import org.syncany.gui.preferences.PreferencesDialog;
import org.syncany.gui.util.DesktopUtil;
import org.syncany.gui.util.I18n;
import org.syncany.gui.wizard.WizardDialog;
import org.syncany.operations.ChangeSet;
import org.syncany.operations.daemon.ControlServer.ControlCommand;
import org.syncany.operations.daemon.Watch;
import org.syncany.operations.daemon.Watch.SyncStatus;
import org.syncany.operations.daemon.messages.CleanupEndSyncExternalEvent;
import org.syncany.operations.daemon.messages.CleanupStartCleaningSyncExternalEvent;
import org.syncany.operations.daemon.messages.ControlManagementRequest;
import org.syncany.operations.daemon.messages.DaemonReloadedExternalEvent;
import org.syncany.operations.daemon.messages.DownChangesDetectedSyncExternalEvent;
import org.syncany.operations.daemon.messages.DownDownloadFileSyncExternalEvent;
import org.syncany.operations.daemon.messages.DownEndSyncExternalEvent;
import org.syncany.operations.daemon.messages.ExitGuiInternalEvent;
import org.syncany.operations.daemon.messages.GenlinkFolderRequest;
import org.syncany.operations.daemon.messages.GenlinkFolderResponse;
import org.syncany.operations.daemon.messages.GuiConfigChangedGuiInternalEvent;
import org.syncany.operations.daemon.messages.ListWatchesManagementRequest;
import org.syncany.operations.daemon.messages.ListWatchesManagementResponse;
import org.syncany.operations.daemon.messages.LogFolderRequest;
import org.syncany.operations.daemon.messages.LogFolderResponse;
import org.syncany.operations.daemon.messages.RemoveWatchManagementRequest;
import org.syncany.operations.daemon.messages.RemoveWatchManagementResponse;
import org.syncany.operations.daemon.messages.UpEndSyncExternalEvent;
import org.syncany.operations.daemon.messages.UpIndexChangesDetectedSyncExternalEvent;
import org.syncany.operations.daemon.messages.UpIndexStartSyncExternalEvent;
import org.syncany.operations.daemon.messages.UpUploadFileInTransactionSyncExternalEvent;
import org.syncany.operations.daemon.messages.UpUploadFileSyncExternalEvent;
import org.syncany.operations.daemon.messages.WatchEndSyncExternalEvent;
import org.syncany.operations.init.GenlinkOperationOptions;
import org.syncany.operations.log.LogOperationOptions;
import org.syncany.util.FileUtil;
import org.syncany.util.StringUtil;

import com.google.common.base.Predicate;
import com.google.common.collect.Maps;
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
	protected static final Logger logger = Logger.getLogger(TrayIcon.class.getSimpleName());

	private static int REFRESH_TIME = 800;
	private static String URL_REPORT_ISSUE = "https://www.syncany.org/r/issue";
	private static String URL_DONATE = "https://www.syncany.org/r/donate";
	private static String URL_HOMEPAGE = "https://www.syncany.org";

	protected Shell trayShell;
	protected WizardDialog wizard;
	protected PreferencesDialog preferences;
	
	protected GuiConfigTO guiConfig;
	protected GuiEventBus eventBus;

	private Thread animationThread;
	private AtomicBoolean syncing;
	private Map<String, Boolean> clientSyncStatus;
	private Map<String, Long> clientUploadFileSize;
	
	protected RecentFileChanges recentFileChanges;
	
	public TrayIcon(Shell shell) {
		this.trayShell = shell;

		this.guiConfig = GuiConfigHelper.loadOrCreateGuiConfig();
		
		this.eventBus = GuiEventBus.getInstance();
		this.eventBus.register(this);

		this.syncing = new AtomicBoolean(false);
		this.clientSyncStatus = Maps.newConcurrentMap();
		this.clientUploadFileSize = Maps.newConcurrentMap();
		
		this.recentFileChanges = new RecentFileChanges(this);
		
		initAnimationThread();
		initTrayImage();
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
	
	protected void showPreferences() {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				if (preferences == null) {
					preferences = new PreferencesDialog(trayShell);
					preferences.open();

					preferences = null;
				}
			}
		});
	}

	protected void showFolder(File folder) {
		DesktopUtil.launch(folder.getAbsolutePath());
	}
	
	protected void showRecentFile(File file) {
		DesktopUtil.launch(file.getAbsolutePath());
	}
	
	protected void showReportIssue() {
		DesktopUtil.launch(URL_REPORT_ISSUE);
	}

	protected void showDonate() {
		DesktopUtil.launch(URL_DONATE);
	}

	protected void showWebsite() {
		DesktopUtil.launch(URL_HOMEPAGE);
	}

	protected void exitApplication() {
		dispose();
		eventBus.post(new ExitGuiInternalEvent());
	}

	protected void removeFolder(File folder) {
		eventBus.post(new RemoveWatchManagementRequest(folder));
	}
	
	protected void copyLink(File folder) {
		GenlinkOperationOptions genlinkOptions = new GenlinkOperationOptions();
		genlinkOptions.setShortUrl(true);
		
		GenlinkFolderRequest genlinkRequest = new GenlinkFolderRequest();
		genlinkRequest.setRoot(folder.getAbsolutePath());
		genlinkRequest.setOptions(genlinkOptions);
		
		eventBus.post(genlinkRequest);
	}
	
	@Subscribe
	public void onGenlinkResponseReceived(GenlinkFolderResponse genlinkResponse) {		
		DesktopUtil.copyToClipboard(genlinkResponse.getResult().getShareLink());
		
		if (guiConfig.isNotifications()) {
			String subject = I18n.getText("org.syncany.gui.tray.TrayIcon.notify.copied.subject");
			String message = I18n.getText("org.syncany.gui.tray.TrayIcon.notify.copied.message");
			
			displayNotification(subject, message);
		}
	}

	@Subscribe
	public void onRemoveWatchResponseReceived(RemoveWatchManagementResponse removeWatchResponse) {		
		if (removeWatchResponse.getCode() == RemoveWatchManagementResponse.OKAY) {
			logger.log(Level.INFO, "Watch removed successfully from daemon config. Now reloading daemon.");
			eventBus.post(new ControlManagementRequest(ControlCommand.RELOAD));
		}
		else {
			logger.log(Level.WARNING, "Watch NOT removed from daemon config. Doing nothing.");
		}
	}
	
	@Subscribe
	public void onDaemonReloadedEventReceived(DaemonReloadedExternalEvent daemonReloadedEvent) {
		eventBus.post(new ListWatchesManagementRequest());
	}

	@Subscribe
	public void onListWatchesResponseReceived(ListWatchesManagementResponse listWatchesResponse) {
		logger.log(Level.FINE, "List watches response recevied: " + listWatchesResponse.getWatches().size() + " watch(es)");

		cleanSyncStatus();
		List<File> watchedFolders = new ArrayList<File>();

		for (Watch watch : listWatchesResponse.getWatches()) {
			watchedFolders.add(watch.getFolder());

			boolean watchFolderIsSyncing = watch.getStatus() == SyncStatus.SYNCING;
			updateSyncStatus(watch.getFolder().getAbsolutePath(), watchFolderIsSyncing);
		}

		// Update folders in menu
		setWatchedFolders(watchedFolders);

		// Update tray icon
		if (!syncing.get()) {
			setTrayImage(TrayIconImage.TRAY_IN_SYNC);
			logger.log(Level.FINE, "Syncing image: Setting to image " + TrayIconImage.TRAY_IN_SYNC);
		}	
		
		// Get recent changes
		recentFileChanges.clear();
		sendLogRequests(listWatchesResponse.getWatches());		
	}

	private void sendLogRequests(final ArrayList<Watch> watchedFolders) {
		if (watchedFolders.size() > 0) {
			Timer logRequestTimer = new Timer();
			
			logRequestTimer.schedule(new TimerTask() {
				@Override
				public void run() {
					for (Watch watch : watchedFolders) {
						LogOperationOptions logOptions = new LogOperationOptions();
						logOptions.setMaxDatabaseVersionCount(RecentFileChanges.RECENT_CHANGES_COUNT);
						logOptions.setMaxFileHistoryCount(RecentFileChanges.RECENT_CHANGES_COUNT);
										
						LogFolderRequest logRequest = new LogFolderRequest();
						logRequest.setRoot(watch.getFolder().getAbsolutePath());
						logRequest.setOptions(logOptions);
														
						eventBus.post(logRequest);
					}
				}				
			}, 2000);					
		}
	}

	@Subscribe
	public void onDownChangesDetectedEvent(DownChangesDetectedSyncExternalEvent downChangesDetectedEvent) {
		updateSyncStatus(downChangesDetectedEvent.getRoot(), true);
	}

	@Subscribe
	public void onUpIndexChangesDetectedEvent(UpIndexChangesDetectedSyncExternalEvent upIndexChangesDetectedEvent) {
		updateSyncStatus(upIndexChangesDetectedEvent.getRoot(), true);
	}

	@Subscribe
	public void onWatchEndEventReceived(WatchEndSyncExternalEvent watchEndEvent) {
		updateSyncStatus(watchEndEvent.getRoot(), false);
	}

	@Subscribe
	public void onIndexStartEventReceived(UpIndexStartSyncExternalEvent syncEvent) {
		if (syncEvent.getFileCount() > 0) {
			setStatusText(syncEvent.getRoot(), I18n.getText("org.syncany.gui.tray.TrayIcon.up.indexStartWithFileCount", syncEvent.getFileCount()));
		}
		else {
			setStatusText(syncEvent.getRoot(), I18n.getText("org.syncany.gui.tray.TrayIcon.up.indexStartWithoutFileCount"));
		}
	}

	@Subscribe
	public void onUploadFileEventReceived(UpUploadFileSyncExternalEvent syncEvent) {
		if (syncEvent.getFilename() != null) {
			setStatusText(syncEvent.getRoot(), I18n.getText("org.syncany.gui.tray.TrayIcon.up.uploadWithFilename", syncEvent.getFilename()));
		}
		else {
			setStatusText(syncEvent.getRoot(), I18n.getText("org.syncany.gui.tray.TrayIcon.up.uploadWithoutFilename"));
		}
	}

	@Subscribe
	public void onUploadFileInTransactionEventReceived(UpUploadFileInTransactionSyncExternalEvent syncEvent) {
		Long currentClientUploadFileSize = clientUploadFileSize.get(syncEvent.getRoot());

		if (currentClientUploadFileSize == null || syncEvent.getCurrentFileIndex() <= 1) {
			currentClientUploadFileSize = 0L;
		}

		String uploadedTotalStr = FileUtil.formatFileSize(currentClientUploadFileSize);
		int uploadedPercent = (int) Math.round((double) currentClientUploadFileSize / syncEvent.getTotalFileSize() * 100);

		String statusText = I18n.getText("org.syncany.gui.tray.TrayIcon.up.uploadFileInTransaction", syncEvent.getCurrentFileIndex(),
				syncEvent.getTotalFileCount(), uploadedTotalStr, uploadedPercent);
		setStatusText(syncEvent.getRoot(), statusText);

		currentClientUploadFileSize += syncEvent.getCurrentFileSize();
		clientUploadFileSize.put(syncEvent.getRoot(), currentClientUploadFileSize);
	}

	@Subscribe
	public void onUpEndEventReceived(UpEndSyncExternalEvent syncEvent) {
		recentFileChanges.updateRecentFiles(syncEvent.getRoot(), new Date(), syncEvent.getResult());
		setStatusText(syncEvent.getRoot(), I18n.getText("org.syncany.gui.tray.TrayIcon.insync"));
	}

	@Subscribe
	public void onDownDownloadFileSyncEventReceived(DownDownloadFileSyncExternalEvent syncEvent) {
		String fileDescription = syncEvent.getFileDescription();
		int currentFileIndex = syncEvent.getCurrentFileIndex();
		int maxFileCount = syncEvent.getMaxFileCount();

		setStatusText(syncEvent.getRoot(), I18n.getText("org.syncany.gui.tray.TrayIcon.down.downloadFile", fileDescription, currentFileIndex, maxFileCount));
	}

	@Subscribe
	public void onDownEndEventReceived(DownEndSyncExternalEvent downEndSyncEvent) {
		String root = downEndSyncEvent.getRoot();
		ChangeSet changeSet = downEndSyncEvent.getChanges();

		// Update recent changes entries
		recentFileChanges.updateRecentFiles(root, new Date(), changeSet); 
		
		// Display notification (if enabled)		
		if (guiConfig.isNotifications() && changeSet.hasChanges()) {
			String rootName = new File(root).getName();
			int totalChangedFiles = changeSet.getNewFiles().size() + changeSet.getChangedFiles().size() + changeSet.getDeletedFiles().size();

			String subject = "";
			String message = "";

			if (totalChangedFiles == 1) {
				if (changeSet.getNewFiles().size() == 1) {
					subject = I18n.getText("org.syncany.gui.tray.TrayIcon.notify.added.subject", changeSet.getNewFiles().first());
					message = I18n.getText("org.syncany.gui.tray.TrayIcon.notify.added.message", changeSet.getNewFiles().first(), rootName);
				}

				if (changeSet.getChangedFiles().size() == 1) {
					subject = I18n.getText("org.syncany.gui.tray.TrayIcon.notify.changed.subject", changeSet.getChangedFiles().first());
					message = I18n.getText("org.syncany.gui.tray.TrayIcon.notify.changed.message", changeSet.getChangedFiles().first(), rootName);
				}

				if (changeSet.getDeletedFiles().size() == 1) {
					subject = I18n.getText("org.syncany.gui.tray.TrayIcon.notify.deleted.subject", changeSet.getDeletedFiles().first());
					message = I18n.getText("org.syncany.gui.tray.TrayIcon.notify.deleted.message", changeSet.getDeletedFiles().first(), rootName);
				}
			}
			else {
				List<String> messageParts = new ArrayList<>();

				if (changeSet.getNewFiles().size() > 0) {
					if (changeSet.getNewFiles().size() == 1) {
						messageParts.add(I18n.getText("org.syncany.gui.tray.TrayIcon.notify.added.one"));
					}
					else {
						messageParts.add(I18n.getText("org.syncany.gui.tray.TrayIcon.notify.added.many", changeSet.getNewFiles().size()));
					}
				}

				if (changeSet.getChangedFiles().size() > 0) {
					if (changeSet.getChangedFiles().size() == 1) {
						messageParts.add(I18n.getText("org.syncany.gui.tray.TrayIcon.notify.changed.one"));
					}
					else {
						messageParts.add(I18n.getText("org.syncany.gui.tray.TrayIcon.notify.changed.many", changeSet.getChangedFiles().size()));
					}
				}

				if (changeSet.getDeletedFiles().size() > 0) {
					if (changeSet.getDeletedFiles().size() == 1) {
						messageParts.add(I18n.getText("org.syncany.gui.tray.TrayIcon.notify.deleted.one"));
					}
					else {
						messageParts.add(I18n.getText("org.syncany.gui.tray.TrayIcon.notify.deleted.many", changeSet.getDeletedFiles().size()));
					}
				}

				subject = I18n.getText("org.syncany.gui.tray.TrayIcon.notify.synced.subject", rootName);
				message = I18n.getText("org.syncany.gui.tray.TrayIcon.notify.synced.message", StringUtil.join(messageParts, ", "), rootName);
			}

			displayNotification(subject, message);
		}
	}

	@Subscribe
	public void onCleanupStartCleaningEventReceived(CleanupStartCleaningSyncExternalEvent syncEvent) {
		setStatusText(syncEvent.getRoot(), I18n.getText("org.syncany.gui.tray.TrayIcon.cleanup.startcleaning"));
	}

	@Subscribe
	public void onCleanupEndEventReceived(CleanupEndSyncExternalEvent syncEvent) {
		setStatusText(syncEvent.getRoot(), I18n.getText("org.syncany.gui.tray.TrayIcon.insync"));
	}
	
	@Subscribe
	public void onGuiConfigChanged(GuiConfigChangedGuiInternalEvent guiConfigChangedEvent) {
		guiConfig = guiConfigChangedEvent.getNewGuiConfig();
	}
	
	@Subscribe
	public void onLogResponse(LogFolderResponse logResponse) {
		recentFileChanges.updateRecentFiles(logResponse.getRoot(), logResponse.getResult().getDatabaseVersions());
	}

	private void initAnimationThread() {
		animationThread = new Thread(new Runnable() {
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

					int trayImageIndex = 0;

					while (syncing.get()) {
						try {
							TrayIconImage syncImage = TrayIconImage.getSyncImage(trayImageIndex);
							setTrayImage(syncImage);

							logger.log(Level.FINE, "Syncing image: Setting image to " + syncImage);

							trayImageIndex = (trayImageIndex + 1) % TrayIconImage.MAX_SYNC_IMAGES;
							Thread.sleep(REFRESH_TIME);
						}
						catch (InterruptedException e) {
							// Don't care
						}
					}

					setTrayImage(TrayIconImage.TRAY_IN_SYNC);
					setStatusText(null, I18n.getText("org.syncany.gui.tray.TrayIcon.insync"));

					logger.log(Level.FINE, "Syncing image: Setting image to " + TrayIconImage.TRAY_IN_SYNC);
				}
			}
		});

		animationThread.start();
	}

	private void initTrayImage() {
		setTrayImage(TrayIconImage.TRAY_NO_OVERLAY);
		logger.log(Level.FINE, "Syncing image: Setting image to " + TrayIconImage.TRAY_NO_OVERLAY);
	}

	private void cleanSyncStatus() {
		logger.log(Level.FINE, "Resetting sync status for clients.");
		clientSyncStatus.clear();
	}

	private void updateSyncStatus(String root, boolean syncStatus) {
		clientSyncStatus.put(root, syncStatus);
		logger.log(Level.FINE, "Sync status for " + root + ": " + syncStatus);

		// Update 'syncing' variable: Set true if any of the folders is syncing
		Map<String, Boolean> syncingFolders = Maps.filterValues(clientSyncStatus, new Predicate<Boolean>() {
			@Override
			public boolean apply(Boolean syncStatus) {
				return syncStatus;
			}
		});

		syncing.set(syncingFolders.size() > 0);
	}
		
	
	// Abstract methods

	protected abstract void setTrayImage(TrayIconImage image);

	protected abstract void setWatchedFolders(List<File> folders);

	protected abstract void setStatusText(String root, String statusText);
	
	protected abstract void setRecentChanges(List<File> recentFiles);

	protected abstract void displayNotification(String subject, String message);

	protected abstract void dispose();
}