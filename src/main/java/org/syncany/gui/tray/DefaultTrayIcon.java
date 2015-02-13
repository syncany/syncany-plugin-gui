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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolTip;
import org.eclipse.swt.widgets.Tray;
import org.eclipse.swt.widgets.TrayItem;
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.SWTResourceManager;
import org.syncany.util.EnvironmentUtil;

import com.google.common.collect.Maps;

/**
 * The default tray icon uses the default SWT {@link TrayItem}
 * class and the {@link Menu} to display the tray icon.
 *
 * <p>These classes are supported by all operating systems and
 * desktop environment,  except Ubuntu/Unity.
 *
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 * @author Vincent Wiencek <vwiencek@gmail.com>
 */
public class DefaultTrayIcon extends TrayIcon {
	private static final String STATUS_TEXT_GLOBAL_IDENTIFIER = "GLOBAL";
	private static final String STATUS_TEXT_FOLDER_FORMAT = (EnvironmentUtil.isWindows()) ? "(%s) %s" : "%s\n%s";

	protected TrayItem trayItem;
	protected String trayImageResourceRoot;

	private Menu menu;
	private MenuItem addFolderMenuItem;
	private MenuItem recentFileChangesItem;
	private MenuItem browseHistoryMenuItem;

	private List<File> watches;
	private Map<String, MenuItem> watchedFolderMenuItems;

	private Map<String, String> statusTexts;
	private Map<String, MenuItem> statusTextItems;

	private Map<TrayIconImage, Image> images;

	public DefaultTrayIcon(final Shell shell, final TrayIconTheme theme) {
		super(shell, theme);

		this.trayItem = null;
		this.menu = null;

		this.addFolderMenuItem = null;

		this.watches = Collections.synchronizedList(new ArrayList<File>());
		this.watchedFolderMenuItems = Maps.newConcurrentMap();

		this.recentFileChangesItem = null;

		this.statusTexts = Maps.newConcurrentMap();
		this.statusTextItems = Maps.newConcurrentMap();

		this.images = null;

		setTrayImageResourcesRoot();
		fillImageCache();
		buildTray();
	}

	protected void setTrayImageResourcesRoot() {
		trayImageResourceRoot = "/" + DefaultTrayIcon.class.getPackage().getName().replace(".", "/") + "/" + getTheme().toString().toLowerCase() + "/";
	}

	private void fillImageCache() {
		images = new HashMap<TrayIconImage, Image>();

		for (TrayIconImage trayIconImage : TrayIconImage.values()) {
			String trayImageFileName = trayImageResourceRoot + trayIconImage.getFileName();
			Image trayImage = SWTResourceManager.getImage(trayImageFileName);

			images.put(trayIconImage, trayImage);
		}
	}

	private void buildTray() {
		Tray tray = Display.getDefault().getSystemTray();

		if (tray != null) {
			trayItem = new TrayItem(tray, SWT.NONE);
			setTrayImage(TrayIconImage.TRAY_NO_OVERLAY);

			buildMenuItems(null);
			addMenuListeners();
		}
	}

	private void addMenuListeners() {
		Listener showMenuListener = new Listener() {
			public void handleEvent(Event event) {
				menu.setVisible(true);
			}
		};

		trayItem.addListener(SWT.MenuDetect, showMenuListener);

		if (!EnvironmentUtil.isUnixLikeOperatingSystem()) {
			// Tray icon popup menu positioning in Linux is off,
			// Disable it for now.

			trayItem.addListener(SWT.Selection, showMenuListener);
		}
	}

	private void buildMenuItems(final List<File> newWatches) {
		watches.clear();

		if (newWatches != null) {
			watches.addAll(newWatches);
		}

		if (menu == null) {
			menu = new Menu(trayShell, SWT.POP_UP);
		}

		clearMenuItems();

		buildStatusTextMenuItems();
		buildAddFolderMenuItem();
		buildOrUpdateRecentChangesMenuItems();
		buildWatchMenuItems();
		buildStaticMenuItems();
	}

	private void buildStatusTextMenuItems() {
		// Create per-folder status text item
		for (String root : statusTexts.keySet()) {
			String statusText = statusTexts.get(root);
			updateFolderStatusTextItem(root, statusText);
		}

		// Add or hide global status text
		resetGlobalStatusTextItem();
	}

	private void buildAddFolderMenuItem() {
		new MenuItem(menu, SWT.SEPARATOR);

		MenuItem addFolderMenuItem = new MenuItem(menu, SWT.PUSH);
		addFolderMenuItem.setText(I18n.getText("org.syncany.gui.tray.TrayIcon.menu.new"));
		addFolderMenuItem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				showNew();
			}
		});
		
		browseHistoryMenuItem = new MenuItem(menu, SWT.PUSH);
		browseHistoryMenuItem.setText(I18n.getText("org.syncany.gui.tray.TrayIcon.menu.browse"));
		browseHistoryMenuItem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				showBrowseHistory();
			}
		});
	}

	private synchronized void buildOrUpdateRecentChangesMenuItems() {
		if (recentFileChangesItem != null && !recentFileChangesItem.isDisposed()) {
			updateRecentFileChangesMenuItems();
		}
		else {
			buildRecentFileChangesMenuItems();
		}
	}

	private void updateRecentFileChangesMenuItems() {
		if (recentFileChanges.size() > 0) {
			Menu recentFileChangesSubMenu = recentFileChangesItem.getMenu();

			// Clear old items from submenu
			for (MenuItem recentFileChangesSubMenuItem : recentFileChangesSubMenu.getItems()) {
				recentFileChangesSubMenuItem.dispose();
			}

			// Add items to old submenu
			updateRecentFileChangesSubMenu(recentFileChangesSubMenu);
		}
		else {
			recentFileChangesItem.dispose();
		}
	}

	private void buildRecentFileChangesMenuItems() {
		if (recentFileChanges.size() > 0) {
			// Create new 'Recent changes >' item, and submenu
			recentFileChangesItem = new MenuItem(menu, SWT.CASCADE, findAddFolderMenuItemIndex());
			recentFileChangesItem.setText(I18n.getText("org.syncany.gui.tray.TrayIcon.menu.recentChanges"));

			Menu recentChangesSubMenu = new Menu(menu);
			recentFileChangesItem.setMenu(recentChangesSubMenu);

			// Add items to submenu
			updateRecentFileChangesSubMenu(recentChangesSubMenu);
		}
	}

	private void updateRecentFileChangesSubMenu(Menu recentFileChangesSubMenu) {
		for (final File recentFile : recentFileChanges.getRecentFiles()) {
			MenuItem recentFileItem = new MenuItem(recentFileChangesSubMenu, SWT.PUSH);
			recentFileItem.setText(recentFile.getName().replaceAll("&", "&&"));

			recentFileItem.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					showRecentFile(recentFile);
				}
			});
		}
	}

	private int findAddFolderMenuItemIndex() {
		for (int i = 0; i < menu.getItemCount(); i++) {
			MenuItem menuItem = menu.getItem(i);

			if (menuItem.equals(addFolderMenuItem)) {
				return i+1;
			}
		}

		return 4; // Guessing.
	}

	private void buildWatchMenuItems() {
		new MenuItem(menu, SWT.SEPARATOR);

		if (watches.size() > 0) {
			for (final File folder : watches) {
				if (!watchedFolderMenuItems.containsKey(folder.getAbsolutePath())) {
					if (folder.exists()) {
						// Menu item for folder  (with submenu)
						MenuItem folderMenuItem = new MenuItem(menu, SWT.CASCADE);
						folderMenuItem.setText(folder.getName());

						Menu folderSubMenu = new Menu(menu);
						folderMenuItem.setMenu(folderSubMenu);

						// Menu item for 'Remove'
						MenuItem folderOpenMenuItem = new MenuItem(folderSubMenu, SWT.PUSH);
						folderOpenMenuItem.setText(I18n.getText("org.syncany.gui.tray.TrayIcon.menu.open"));
						folderOpenMenuItem.addSelectionListener(new SelectionAdapter() {
							@Override
							public void widgetSelected(SelectionEvent e) {
								showFolder(folder);
							}
						});

						// Menu item for 'Copy link'
						MenuItem folderCopyLinkMenuItem = new MenuItem(folderSubMenu, SWT.PUSH);
						folderCopyLinkMenuItem.setText(I18n.getText("org.syncany.gui.tray.TrayIcon.menu.copyLink"));
						folderCopyLinkMenuItem.addSelectionListener(new SelectionAdapter() {
							@Override
							public void widgetSelected(SelectionEvent e) {
								copyLink(folder);
							}
						});

						// Menu item for 'Remove'
						MenuItem folderRemoveMenuItem = new MenuItem(folderSubMenu, SWT.PUSH);
						folderRemoveMenuItem.setText(I18n.getText("org.syncany.gui.tray.TrayIcon.menu.remove"));
						folderRemoveMenuItem.addSelectionListener(new SelectionAdapter() {
							@Override
							public void widgetSelected(SelectionEvent e) {
								removeFolder(folder);
							}
						});

						watchedFolderMenuItems.put(folder.getAbsolutePath(), folderMenuItem);
					}
				}
			}

			for (String filePath : watchedFolderMenuItems.keySet()){
				boolean removeFilePath = true;

				for (File file : watches) {
					if (file.getAbsolutePath().equals(filePath)) {
						removeFilePath = false;
					}
				}

				if (removeFilePath) {
					watchedFolderMenuItems.get(filePath).dispose();
					watchedFolderMenuItems.keySet().remove(filePath);
				}
			}

			new MenuItem(menu, SWT.SEPARATOR);
		}
	}

	private void buildStaticMenuItems() {
		MenuItem preferencesItem = new MenuItem(menu, SWT.PUSH);
		preferencesItem.setText(I18n.getText("org.syncany.gui.tray.TrayIcon.menu.preferences"));
		preferencesItem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				showPreferences();
			}
		});

		new MenuItem(menu, SWT.SEPARATOR);

		MenuItem reportIssueItem = new MenuItem(menu, SWT.PUSH);
		reportIssueItem.setText(I18n.getText("org.syncany.gui.tray.TrayIcon.menu.issue"));
		reportIssueItem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				showReportIssue();
			}
		});

		MenuItem donateItem = new MenuItem(menu, SWT.PUSH);
		donateItem.setText(I18n.getText("org.syncany.gui.tray.TrayIcon.menu.donate"));
		donateItem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				showDonate();
			}
		});

		MenuItem websiteItem = new MenuItem(menu, SWT.PUSH);
		websiteItem.setText(I18n.getText("org.syncany.gui.tray.TrayIcon.menu.website"));
		websiteItem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				showWebsite();
			}
		});

		new MenuItem(menu, SWT.SEPARATOR);

		MenuItem exitMenu = new MenuItem(menu, SWT.PUSH);
		exitMenu.setText(I18n.getText("org.syncany.gui.tray.TrayIcon.menu.exit"));
		exitMenu.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				exitApplication();
			}
		});
	}

	private void clearMenuItems() {
		if (menu != null) {
			// Dispose of SWT menu items
			while (menu.getItems().length > 0) {
				MenuItem item = menu.getItem(0);
				item.dispose();
			}

			// Clear menu item cache
			watchedFolderMenuItems.clear();
			statusTextItems.clear();
		}
	}

	@Override
	public void setWatchedFolders(final List<File> folders) {
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				buildMenuItems(folders);
			}
		});
	}

	@Override
	public void setStatusText(final String root, final String statusText) {
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				logger.log(Level.INFO, "setStatusText(" + root + ", " + statusText + ")");

				if (root != null) {
					updateFolderStatusTextItem(root, statusText);
				}
				else {
					clearStatusTextItems();
				}

				resetGlobalStatusTextItem();
			}
		});
	}

	private void clearStatusTextItems() {
		statusTexts.clear();

		synchronized (statusTextItems) {
			Iterator<String> rootIterator = statusTextItems.keySet().iterator();

			while (rootIterator.hasNext()) {
				String root = rootIterator.next();
				MenuItem statusTextItem = statusTextItems.remove(root);

				statusTextItem.dispose();
			}
		}
	}

	private void updateFolderStatusTextItem(String root, String statusText) {
		String inSyncStatusText = I18n.getText("org.syncany.gui.tray.TrayIcon.insync");

		MenuItem statusTextItem = statusTextItems.get(root);
		boolean watchIsInSync = statusText.equals(inSyncStatusText);

		if (watchIsInSync) {
			statusTexts.remove(root);
			statusTextItems.remove(root);

			if (statusTextItem != null) {
				statusTextItem.dispose();
			}
		}
		else {
			statusTexts.put(root, statusText);

			String statusTextPrefix = new File(root).getName();
			String fullStatusText = String.format(STATUS_TEXT_FOLDER_FORMAT, statusTextPrefix, statusText);

			if (statusTextItem != null) {
				statusTextItem.setText(fullStatusText);
			}
			else {
				statusTextItem = new MenuItem(menu, SWT.PUSH, 0);
				statusTextItem.setText(fullStatusText);
				statusTextItem.setEnabled(false);

				statusTextItems.put(root, statusTextItem);
			}
		}
	}

	private void resetGlobalStatusTextItem() {
		MenuItem globalStatusTextItem = statusTextItems.get(STATUS_TEXT_GLOBAL_IDENTIFIER);
		boolean otherStatusTextItemsVisible = statusTexts.size() > 0;

		if (otherStatusTextItemsVisible) {
			if (globalStatusTextItem != null && !globalStatusTextItem.isDisposed()) {
				globalStatusTextItem.dispose();
			}
		}
		else {
			if (globalStatusTextItem == null || globalStatusTextItem.isDisposed()) {
				MenuItem statusTextItem = new MenuItem(menu, SWT.PUSH, 0);
				statusTextItem.setText(I18n.getText("org.syncany.gui.tray.TrayIcon.insync"));
				statusTextItem.setEnabled(false);

				statusTextItems.put(STATUS_TEXT_GLOBAL_IDENTIFIER, statusTextItem);
			}
		}
	}

	@Override
	protected void setTrayImage(final TrayIconImage trayIconImage) {
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				trayItem.setImage(images.get(trayIconImage));
			}
		});
	}

	@Override
	protected void displayNotification(final String subject, final String message) {
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				ToolTip toolTip = new ToolTip(trayShell, SWT.BALLOON | SWT.ICON_INFORMATION);

				toolTip.setText(subject);
				toolTip.setMessage(message);

				trayItem.setImage(images.get(TrayIconImage.TRAY_NO_OVERLAY));
				trayItem.setToolTip(toolTip);

				toolTip.setVisible(true);
				toolTip.setAutoHide(true);
			}
		});
	}

	@Override
	protected void setRecentChanges(List<File> newRecentChangesFiles) {
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				buildOrUpdateRecentChangesMenuItems();
			}
		});
	}

	@Override
	protected void dispose() {
		trayItem.dispose();
	}
}
