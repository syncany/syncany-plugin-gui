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

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.swt.widgets.Shell;
import org.syncany.config.LocalEventBus;
import org.syncany.gui.events.ExitGuiInternalEvent;
import org.syncany.gui.util.BrowserHelper;
import org.syncany.gui.util.I18n;
import org.syncany.operations.daemon.messages.ListWatchesManagementResponse;
import org.syncany.operations.daemon.messages.StatusFolderResponse;
import org.syncany.operations.daemon.messages.UpEndSyncExternalEvent;
import org.syncany.operations.daemon.messages.UpStartSyncExternalEvent;
import org.syncany.operations.daemon.messages.api.ExternalEvent;
import org.syncany.operations.daemon.messages.api.Response;
import org.syncany.operations.status.StatusOperationResult;

import com.google.common.eventbus.Subscribe;

/**
 * @author pheckel
 * @author Vincent Wiencek <vwiencek@gmail.com>
 */
public abstract class TrayIcon {
	private static int REFRESH_TIME = 1000;

	private LocalEventBus eventBus;
	private Thread systemTrayAnimationThread;
	
	private Shell shell;
	private AtomicBoolean syncing = new AtomicBoolean(false);
	private AtomicBoolean paused = new AtomicBoolean(false);
	
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

	private void startAnimationThread() {
		systemTrayAnimationThread = new Thread(new Runnable() {
				@Override
				public void run() {
					while (true) {
						while (paused.get() || !syncing.get()) {
							try {
								Thread.sleep(500);
							}
							catch (InterruptedException e) {
							}
						}

						int i = 0;

						while (syncing.get()) {
							try {
								setTrayImage(TrayIcons.get(i));
								i++;
								if (i == 6)
									i = 0;
								Thread.sleep(REFRESH_TIME);
							}
							catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
						setTrayImage(TrayIcons.TRAY_IN_SYNC);
					}
				}
			});
		
		systemTrayAnimationThread.start();
	}

	public Shell getShell() {
		return shell;
	}

	protected void showFolder(File folder) {
		try {
			if (folder.exists() && folder.isDirectory()) {
				Desktop.getDesktop().open(folder);
			}
		}
		catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	protected void showDonate() {
		BrowserHelper.browse("https://www.syncany.org/donate.html");
	}

	protected void showWebsite() {
		BrowserHelper.browse("https://www.syncany.org");
	}

	protected void quit() {
		eventBus.post(new ExitGuiInternalEvent());
	}

	public void makeSystemTrayStartSync() {
		syncing.set(true);
		paused.set(false);
	}

	public void makeSystemTrayStopSync() {
		syncing.set(false);
		paused.set(false);
		setTrayImage(TrayIcons.TRAY_IN_SYNC);
	}

	public void pauseSyncing() {
		paused.set(true);
		setTrayImage(TrayIcons.TRAY_PAUSE_SYNC);
	}

	public void resumeSyncing() {
		paused.set(false);
	}

	protected void showSettings() {
		// show settings
	}

	protected void showWizard() {
		// start wizard
	}

	@Subscribe
	public void handleResponse(Response message){
		if (message instanceof ListWatchesManagementResponse){
			updateWatchedFolders(((ListWatchesManagementResponse)message).getWatches());
		}
		else if (message instanceof StatusFolderResponse){
			updateWatchedFoldersStatus(((StatusFolderResponse)message).getResult());
		}
	}
	
	@Subscribe
	public void handleExternalEvent(ExternalEvent message) {
		if (message instanceof UpStartSyncExternalEvent) {
			makeSystemTrayStartSync();
		}
		else if (message instanceof UpEndSyncExternalEvent) {
			makeSystemTrayStopSync();
		}
	}

	// Abstract methods
	protected abstract void setTrayImage(TrayIcons image);

	public abstract void updateWatchedFolders(List<File> folders);

	public abstract void updateStatusText(String statusText);

	public abstract void updateWatchedFoldersStatus(StatusOperationResult result);
}