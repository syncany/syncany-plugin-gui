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

import static org.syncany.gui.util.I18n._;
import org.syncany.plugins.transfer.StorageTestResult;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public abstract class AbstractInitPanelController extends ReloadDaemonPanelController {
	public AbstractInitPanelController(WizardDialog wizardDialog, ProgressPanel progressPanel) {
		super(wizardDialog, progressPanel);		
	}

	protected String toYesNo(boolean value) {
		if (value) {
			return _("org.syncany.gui.wizard.AbstractInitPanelController.yes");
		}
		else {
			return _("org.syncany.gui.wizard.AbstractInitPanelController.no");
		}
	}
	
	protected String formatTestResultMessage(StorageTestResult testResult) {
		String errorMessage = _("org.syncany.gui.wizard.AbstractInitPanelController.testResult", 
			toYesNo(testResult.isTargetCanConnect()),
			toYesNo(testResult.isTargetCanCreate()),
			toYesNo(testResult.isTargetCanWrite()),
			toYesNo(testResult.isTargetExists())
		) + "\n";
				
		if (testResult.getErrorMessage() != null) {
			errorMessage += "\n" + _("org.syncany.gui.wizard.AbstractInitPanelController.testResultErrorMessage", testResult.getErrorMessage());
		}
		
		return errorMessage;
	}
}
