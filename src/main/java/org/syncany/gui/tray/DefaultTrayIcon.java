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
import java.util.TreeMap;

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
	protected TrayItem trayItem;
	protected String trayImageResourceRoot;
	protected Map<TrayIconImage, Image> images;

	private Menu menu;
	private List<File> watches;
	private Map<String, MenuItem> statusTextItems = new TreeMap<String, MenuItem>();
	private Map<String, String> statusTexts = new TreeMap<String, String>();
	private Map<String, MenuItem> watchedFolderMenuItems = new HashMap<String, MenuItem>();

	public DefaultTrayIcon(final Shell shell, final TrayIconTheme theme) {
		super(shell, theme);

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

	private void buildMenuItems() {
		buildMenuItems(watches);
	}

	private void buildMenuItems(final List<File> watches) {
		this.watches = watches;

		if (menu == null) {
			menu = new Menu(trayShell, SWT.POP_UP);
		}

		clearMenuItems();

		buildStatusTextMenuItems();
		buildNewWatchMenuItem();
		buildWatchMenuItems();
		buildStaticMenuItems();
	}

	private void buildStatusTextMenuItems() {
		String inSyncStatusText = I18n.getText("org.syncany.gui.tray.TrayIcon.insync");

		// Create per-folder status text item
		for (String root : statusTexts.keySet()) {
			boolean watchIsInSync = statusTexts.get(root).equals(inSyncStatusText);

			if (!watchIsInSync) {
				String statusTextPrefix = new File(root).getName();

				MenuItem statusTextItem = new MenuItem(menu, SWT.PUSH);
				statusTextItem.setText(statusTextPrefix + "\n" + statusTexts.get(root));
				statusTextItem.setEnabled(false);

				statusTextItems.put(root, statusTextItem);
			}
		}

		// Or, if they are all in sync, create a global one
		if (statusTextItems.isEmpty()) {
			MenuItem statusTextItem = new MenuItem(menu, SWT.PUSH);
			statusTextItem.setText(inSyncStatusText);
			statusTextItem.setEnabled(false);

			statusTextItems.put("GLOBAL", statusTextItem);
		}
	}

	private void buildNewWatchMenuItem() {
		new MenuItem(menu, SWT.SEPARATOR);

		MenuItem newItem = new MenuItem(menu, SWT.PUSH);
		newItem.setText(I18n.getText("org.syncany.gui.tray.TrayIcon.menu.new"));
		newItem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				showNew();
			}
		});
	}

	private void buildWatchMenuItems() {
		new MenuItem(menu, SWT.SEPARATOR);

		if (watches != null && watches.size() > 0) {
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
				if (root != null) {
					statusTexts.put(root, statusText);
				}
				else {
					statusTexts.clear();
				}

				buildMenuItems();
			}
		});
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
	protected void dispose() {
		trayItem.dispose();
	}
}
