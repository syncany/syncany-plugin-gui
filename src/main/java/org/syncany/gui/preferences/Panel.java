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
package org.syncany.gui.preferences;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.syncany.gui.util.SWTResourceManager;
import org.syncany.gui.wizard.WizardDialog;

/**
 * @author Vincent Wiencek <vwiencek@gmail.com>
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public abstract class Panel extends Composite {
	private PreferencesDialog parentDialog;

	protected Panel(Composite parent, int style) {
		super(parent, style);
		
		String backImageResource = "/" + WizardDialog.class.getPackage().getName().replace(".", "/") + "/wizard-back.png";
		Image backImage = SWTResourceManager.getImage(backImageResource);

		setBackgroundImage(backImage);
		setBackgroundMode(SWT.INHERIT_FORCE);
	}

	public Panel(PreferencesDialog parentDialog, Composite parent, int style) {
		this(parent, style);
		this.parentDialog = parentDialog;
	}

	public PreferencesDialog getParentDialog() {
		return parentDialog;
	}	
}
