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

import static org.syncany.gui.util.I18n._;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.syncany.config.GuiEventBus;
import org.syncany.gui.util.DesktopUtil;
import org.syncany.gui.wizard.WizardDialog;
import org.syncany.operations.ChangeSet;
import org.syncany.operations.daemon.Watch;
import org.syncany.operations.daemon.Watch.SyncStatus;
import org.syncany.operations.daemon.messages.CleanupEndSyncExternalEvent;
import org.syncany.operations.daemon.messages.CleanupStartCleaningSyncExternalEvent;
import org.syncany.operations.daemon.messages.DaemonReloadedExternalEvent;
import org.syncany.operations.daemon.messages.DownChangesDetectedSyncExternalEvent;
import org.syncany.operations.daemon.messages.DownDownloadFileSyncExternalEvent;
import org.syncany.operations.daemon.messages.DownEndSyncExternalEvent;
import org.syncany.operations.daemon.messages.DownStartSyncExternalEvent;
import org.syncany.operations.daemon.messages.ExitGuiInternalEvent;
import org.syncany.operations.daemon.messages.ListWatchesManagementRequest;
import org.syncany.operations.daemon.messages.ListWatchesManagementResponse;
import org.syncany.operations.daemon.messages.UpEndSyncExternalEvent;
import org.syncany.operations.daemon.messages.UpIndexChangesDetectedSyncExternalEvent;
import org.syncany.operations.daemon.messages.UpIndexStartSyncExternalEvent;
import org.syncany.operations.daemon.messages.UpStartSyncExternalEvent;
import org.syncany.operations.daemon.messages.UpUploadFileInTransactionSyncExternalEvent;
import org.syncany.operations.daemon.messages.UpUploadFileSyncExternalEvent;
import org.syncany.operations.daemon.messages.WatchEndSyncExternalEvent;
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
	private static final Logger logger = Logger.getLogger(TrayIcon.class.getSimpleName());

	private static int REFRESH_TIME = 500;
	private static String URL_REPORT_ISSUE = "https://www.syncany.org/r/issue";
	private static String URL_DONATE = "https://www.syncany.org/donate.html";
	private static String URL_HOMEPAGE = "https://www.syncany.org";

	protected Shell trayShell;
	private final TrayIconTheme theme;
	protected WizardDialog wizard;
	protected GuiEventBus eventBus;

	private Thread systemTrayAnimationThread;
	private AtomicBoolean syncing;
	private Map<String, Boolean> clientSyncStatus;
	private Map<String, Long> clientUploadFileSize;

	public TrayIcon(Shell shell, TrayIconTheme theme) {
		this.trayShell = shell;
		this.theme = theme;

		this.eventBus = GuiEventBus.getInstance();
		this.eventBus.register(this);

		this.syncing = new AtomicBoolean(false);
		this.clientSyncStatus = Maps.newConcurrentMap();
		this.clientUploadFileSize = Maps.newConcurrentMap();

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

	protected void showFolder(File folder) {
		DesktopUtil.launch(folder.getAbsolutePath());
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

	public TrayIconTheme getTheme() {
		return theme;
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
	public void onUpStartEventReceived(UpStartSyncExternalEvent syncEvent) {
		setStatusText(syncEvent.getRoot(), _("org.syncany.gui.tray.TrayIcon.up.start"));
	}

	@Subscribe
	public void onIndexStartEventReceived(UpIndexStartSyncExternalEvent syncEvent) {
		if (syncEvent.getFileCount() > 0) {
			setStatusText(syncEvent.getRoot(), _("org.syncany.gui.tray.TrayIcon.up.indexStartWithFileCount", syncEvent.getFileCount()));
		}
		else {
			setStatusText(syncEvent.getRoot(), _("org.syncany.gui.tray.TrayIcon.up.indexStartWithoutFileCount"));
		}
	}

	@Subscribe
	public void onUploadFileEventReceived(UpUploadFileSyncExternalEvent syncEvent) {
		if (syncEvent.getFilename() != null) {
			setStatusText(syncEvent.getRoot(), _("org.syncany.gui.tray.TrayIcon.up.uploadWithFilename", syncEvent.getFilename()));
		}
		else {
			setStatusText(syncEvent.getRoot(), _("org.syncany.gui.tray.TrayIcon.up.uploadWithoutFilename"));
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

		String statusText = _("org.syncany.gui.tray.TrayIcon.up.uploadFileInTransaction", syncEvent.getCurrentFileIndex(),
				syncEvent.getTotalFileCount(), uploadedTotalStr, uploadedPercent);
		setStatusText(syncEvent.getRoot(), statusText);

		currentClientUploadFileSize += syncEvent.getCurrentFileSize();
		clientUploadFileSize.put(syncEvent.getRoot(), currentClientUploadFileSize);
	}

	@Subscribe
	public void onUpEndEventReceived(UpEndSyncExternalEvent syncEvent) {
		setStatusText(syncEvent.getRoot(), _("org.syncany.gui.tray.TrayIcon.insync"));
	}

	@Subscribe
	public void onDownDownloadFileSyncEventReceived(DownDownloadFileSyncExternalEvent syncEvent) {
		String fileDescription = syncEvent.getFileDescription();
		int currentFileIndex = syncEvent.getCurrentFileIndex();
		int maxFileCount = syncEvent.getMaxFileCount();

		setStatusText(syncEvent.getRoot(), _("org.syncany.gui.tray.TrayIcon.down.downloadFile", fileDescription, currentFileIndex, maxFileCount));
	}

	@Subscribe
	public void onDownStartEventReceived(DownStartSyncExternalEvent syncEvent) {
		setStatusText(syncEvent.getRoot(), _("org.syncany.gui.tray.TrayIcon.down.start"));
	}

	@Subscribe
	public void onDownEndEventReceived(DownEndSyncExternalEvent downEndSyncEvent) {
		// Display notification
		ChangeSet changeSet = downEndSyncEvent.getChanges();

		if (changeSet.hasChanges()) {
			String rootName = new File(downEndSyncEvent.getRoot()).getName();
			int totalChangedFiles = changeSet.getNewFiles().size() + changeSet.getChangedFiles().size() + changeSet.getDeletedFiles().size();

			String subject = "";
			String message = "";

			if (totalChangedFiles == 1) {
				if (changeSet.getNewFiles().size() == 1) {
					subject = _("org.syncany.gui.tray.TrayIcon.notify.added.subject", changeSet.getNewFiles().first());
					message = _("org.syncany.gui.tray.TrayIcon.notify.added.message", changeSet.getNewFiles().first(), rootName);
				}

				if (changeSet.getChangedFiles().size() == 1) {
					subject = _("org.syncany.gui.tray.TrayIcon.notify.changed.subject", changeSet.getChangedFiles().first());
					message = _("org.syncany.gui.tray.TrayIcon.notify.changed.message", changeSet.getChangedFiles().first(), rootName);
				}

				if (changeSet.getDeletedFiles().size() == 1) {
					subject = _("org.syncany.gui.tray.TrayIcon.notify.deleted.subject", changeSet.getDeletedFiles().first());
					message = _("org.syncany.gui.tray.TrayIcon.notify.deleted.message", changeSet.getDeletedFiles().first(), rootName);
				}
			}
			else {
				List<String> messageParts = new ArrayList<>();

				if (changeSet.getNewFiles().size() > 0) {
					if (changeSet.getNewFiles().size() == 1) {
						messageParts.add(_("org.syncany.gui.tray.TrayIcon.notify.added.one"));
					}
					else {
						messageParts.add(_("org.syncany.gui.tray.TrayIcon.notify.added.many", changeSet.getNewFiles().size()));
					}
				}

				if (changeSet.getChangedFiles().size() > 0) {
					if (changeSet.getChangedFiles().size() == 1) {
						messageParts.add(_("org.syncany.gui.tray.TrayIcon.notify.changed.one"));
					}
					else {
						messageParts.add(_("org.syncany.gui.tray.TrayIcon.notify.changed.many", changeSet.getChangedFiles().size()));
					}
				}

				if (changeSet.getDeletedFiles().size() > 0) {
					if (changeSet.getDeletedFiles().size() == 1) {
						messageParts.add(_("org.syncany.gui.tray.TrayIcon.notify.deleted.one"));
					}
					else {
						messageParts.add(_("org.syncany.gui.tray.TrayIcon.notify.deleted.many", changeSet.getDeletedFiles().size()));
					}
				}

				subject = _("org.syncany.gui.tray.TrayIcon.notify.synced.subject", rootName);
				message = _("org.syncany.gui.tray.TrayIcon.notify.synced.message", StringUtil.join(messageParts, ", "), rootName);
			}

			displayNotification(subject, message);
		}
	}

	@Subscribe
	public void onCleanupStartCleaningEventReceived(CleanupStartCleaningSyncExternalEvent syncEvent) {
		setStatusText(syncEvent.getRoot(), _("org.syncany.gui.tray.TrayIcon.cleanup.startcleaning"));
	}

	@Subscribe
	public void onCleanupEndEventReceived(CleanupEndSyncExternalEvent syncEvent) {
		setStatusText(syncEvent.getRoot(), _("org.syncany.gui.tray.TrayIcon.insync"));
	}

	private void initAnimationThread() {
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
					setStatusText(null, _("org.syncany.gui.tray.TrayIcon.insync"));

					logger.log(Level.FINE, "Syncing image: Setting image to " + TrayIconImage.TRAY_IN_SYNC);
				}
			}
		});

		systemTrayAnimationThread.start();
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

	protected abstract void displayNotification(String subject, String message);

	protected abstract void dispose();
}