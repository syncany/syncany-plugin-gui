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
package org.syncany.operations.cleanup;

import java.util.SortedMap;
import java.util.TreeMap;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementMap;
import org.syncany.operations.OperationOptions;
import org.syncany.operations.status.StatusOperationOptions;

public class CleanupOperationOptions implements OperationOptions {
	@Element(name = "status", required = false)
	private StatusOperationOptions statusOptions = new StatusOperationOptions();

	@Element(required = false)
	private boolean force = false;

	@Element(required = false)
	private boolean removeOldVersions = true;

	@Element(required = false)
	private boolean removeUnreferencedTemporaryFiles = true;

	@Element(required = false)
	private int keepVersionsCount = 5;

	@Element(required = false)
	private long minSecondsBeforeFullyDeletingFiles = 3600L * 24L * 30L;

	@Element(required = false)
	private int maxDatabaseFiles = 15;

	@Element(required = false)
	private long minSecondsBetweenCleanups = 10800;

	@ElementMap(entry = "fromTime", key = "truncateDateFormat", required = false, attribute = true, inline = true)
	private SortedMap<Long, String> purgeFileVersionSettings;

	// TODO [medium] Implement multichunk repackaging

	// private boolean repackageMultiChunks = true; 
	// private double repackageUnusedThreshold = 0.7;

	public StatusOperationOptions getStatusOptions() {
		return statusOptions;
	}

	public void setStatusOptions(StatusOperationOptions statusOptions) {
		this.statusOptions = statusOptions;
	}

	public boolean isRemoveOldVersions() {
		return removeOldVersions;
	}

	public boolean isRemoveUnreferencedTemporaryFiles() {
		return removeUnreferencedTemporaryFiles;
	}

	public int getKeepVersionsCount() {
		return keepVersionsCount;
	}

	public void setRemoveOldVersions(boolean removeOldVersions) {
		this.removeOldVersions = removeOldVersions;
	}

	public void setRemoveUnreferencedTemporaryFiles(boolean removeUnreferencedTemporaryFiles) {
		this.removeUnreferencedTemporaryFiles = removeUnreferencedTemporaryFiles;
	}

	public void setKeepVersionsCount(int keepVersionsCount) {
		this.keepVersionsCount = keepVersionsCount;
	}

	public void setMaxDatabaseFiles(int maxDatabaseFiles) {
		this.maxDatabaseFiles = maxDatabaseFiles;
	}

	public int getMaxDatabaseFiles() {
		return maxDatabaseFiles;
	}

	public void setMinSecondsBetweenCleanups(long minSecondsBetweenCleanups) {
		this.minSecondsBetweenCleanups = minSecondsBetweenCleanups;
	}

	public long getMinSecondsBetweenCleanups() {
		return minSecondsBetweenCleanups;
	}

	public void setMinSecondsBeforeFullyDeletingFiles(long minSecondsBeforeFullyDeletingFiles) {
		this.minSecondsBeforeFullyDeletingFiles = minSecondsBeforeFullyDeletingFiles;
	}

	public long getMinSecondsBeforeFullyDeletingFiles() {
		return minSecondsBeforeFullyDeletingFiles;
	}
	
	public SortedMap<Long, String> getPurgeFileVersionSettings() {
		if (purgeFileVersionSettings == null) {
			purgeFileVersionSettings = new TreeMap<Long, String>();
			purgeFileVersionSettings.put(30L * 24L * 3600L, "BC");
			purgeFileVersionSettings.put(3L * 24L * 3600L, "YYYYMMDD");
			purgeFileVersionSettings.put(3600L, "YYYYMMDDHH");
			purgeFileVersionSettings.put(600L, "YYYYMMDDHHMI");
		}
		return purgeFileVersionSettings;
	}

	public void setPurgeFileVersionSettings(SortedMap<Long, String> newSettings) {
		purgeFileVersionSettings = newSettings;
	}

	public boolean isForce() {
		return force;
	}

	public void setForce(boolean force) {
		this.force = force;
	}
}