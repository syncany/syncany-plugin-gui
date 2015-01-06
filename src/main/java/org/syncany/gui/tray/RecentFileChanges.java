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
package org.syncany.gui.tray;

import java.io.File;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import org.syncany.util.LimitedSortedSet;
import org.syncany.operations.ChangeSet;
import org.syncany.operations.log.LightweightDatabaseVersion;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class RecentFileChanges {
	public static final int RECENT_CHANGES_COUNT = 15;
	
	private TrayIcon trayIcon;
	
	private Timer recentFileChangesUpdateTimer;
	private AtomicBoolean recentFileChangesUpdated;
	private LimitedSortedSet<RecentFileEntry> recentFileChanges;

	public RecentFileChanges(TrayIcon trayIcon) {	
		this.trayIcon = trayIcon;
		
		this.recentFileChangesUpdateTimer = new Timer();
		this.recentFileChangesUpdated = new AtomicBoolean(false);
		this.recentFileChanges = new LimitedSortedSet<>(RECENT_CHANGES_COUNT);
		
		initTimer();		
	}

	private void initTimer() {
		recentFileChangesUpdateTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				if (recentFileChangesUpdated.get()) {
					trayIcon.setRecentChanges(getRecentFiles());
					recentFileChangesUpdated.set(false);
				}					
			}
		}, 6000, 3000);
	}

	public void updateRecentFiles(String root, List<LightweightDatabaseVersion> databaseVersions) {
		for (LightweightDatabaseVersion databaseVersion : databaseVersions) {
			updateRecentFiles(root, databaseVersion.getDate(), databaseVersion.getChangeSet());
		}
	}
	
	public void updateRecentFiles(String root, Date date, ChangeSet changeSet) {
		// Update recent file entries (list only)
		for (String newFile : changeSet.getNewFiles()) {
			replaceEntry(new RecentFileEntry(new File(root, newFile), date));
		}

		for (String changedFile : changeSet.getChangedFiles()) {
			replaceEntry(new RecentFileEntry(new File(root, changedFile), date));
		}
		
		// Trigger update thread
		if (changeSet.getNewFiles().size() > 0 || changeSet.getChangedFiles().size() > 0) {
			recentFileChangesUpdated.set(true);
		}
	}
	
	private synchronized void replaceEntry(final RecentFileEntry recentFileEntry) {
		// Remove old entry
		Iterables.removeIf(recentFileChanges, new Predicate<RecentFileEntry>() {
			@Override
			public boolean apply(RecentFileEntry aRecentFileEntry) {
				return aRecentFileEntry.file.equals(recentFileEntry.file);
			}			
		});
		
		// Add new entry
		recentFileChanges.add(recentFileEntry);
	}

	public synchronized List<File> getRecentFiles() {		
		List<File> recentChanges = Lists.newArrayList();		
		Iterator<RecentFileEntry> recentChangeEntryIterator = recentFileChanges.iterator();
		
		while (recentChangeEntryIterator.hasNext()) {
			recentChanges.add(recentChangeEntryIterator.next().file);
		}			

		return recentChanges;
	}
	
	public synchronized void clear() {
		recentFileChanges.clear();
	}
	
	private static class RecentFileEntry implements Comparable<RecentFileEntry> {
		private File file;
		private Date date;
		
		public RecentFileEntry(File file, Date date) {
			this.file = file;
			this.date = date;
		}

		@Override
		public int compareTo(RecentFileEntry other) {
			int dateCompare = other.date.compareTo(date);
			
			if (dateCompare == 0) {
				return other.file.compareTo(file);
			}
			else {
				return dateCompare;
			}
		}		
	}
}
