/*
 * Syncany, www.syncany.org
 * Copyright (C) 2011-2014 Philipp C. Heckel <philipp.heckel@gmail.com> 
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
package org.syncany.gui.wizard;

import org.syncany.plugins.transfer.StorageTestResult;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public abstract class AbstractInitPanelController extends ReloadDaemonPanelController {
	public AbstractInitPanelController(WizardDialog wizardDialog, ProgressPanel progressPanel) {
		super(wizardDialog, progressPanel);		
	}

	protected String toYesNo(boolean value) {
		return value ? "YES" : "NO";
	}
	
	protected String formatTestResultMessage(StorageTestResult testResult) {
		String errorMessage = "Testing the remote storage failed.\n\n"
				+ "- Was the connection successful: " + toYesNo(testResult.isTargetCanConnect()) + "\n"
				+ "- Files can be created: " + toYesNo(testResult.isTargetCanCreate()) + "\n"
				+ "- Files can be written to: " + toYesNo(testResult.isTargetCanWrite()) + "\n"
				+ "- The target folder/repo exists: " + toYesNo(testResult.isTargetExists()) + "\n";
		
		if (testResult.getErrorMessage() != null) {
			errorMessage += "\nDetailed test error:\n\n" + testResult.getErrorMessage();
		}
		
		return errorMessage;
	}
}
