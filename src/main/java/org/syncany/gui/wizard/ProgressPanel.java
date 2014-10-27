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
package org.syncany.gui.wizard;

import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.syncany.gui.util.I18n;

/**
 * @author Vincent Wiencek <vwiencek@gmail.com>
 *
 */
public class ProgressPanel extends WizardPanel {
	private static Logger logger = Logger.getLogger(ProgressPanel.class.getSimpleName());
	
	private Label summaryIntroductionTitleLabel;
	private Label summaryIntroductionLabel;

	private ProgressBar progressBar;
	
	public ProgressPanel(WizardDialog wizardParentDialog, Composite parent, int style) {
		super(wizardParentDialog, parent, style);
		initComposite();
	}
	
	private void initComposite(){
		GridLayout gridLayout = new GridLayout(2, false);
		gridLayout.horizontalSpacing = 8;
		setLayout(gridLayout);
		
		summaryIntroductionTitleLabel = new Label(this, SWT.NONE);
		summaryIntroductionTitleLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		summaryIntroductionTitleLabel.setText(I18n.getString("dialog.summary.introduction.title"));
		
		summaryIntroductionLabel = new Label(this, SWT.NONE);
		summaryIntroductionLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 2, 1));
		summaryIntroductionLabel.setText(I18n.getString("dialog.summary.introduction"));
		
		GridData gridDataProgressBar = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
		gridDataProgressBar.verticalIndent = 50;

		progressBar = new ProgressBar(this, SWT.INDETERMINATE);
		progressBar.setLayoutData(gridDataProgressBar);
		progressBar.setState(SWT.PAUSED);
	
		WidgetDecorator.bold(summaryIntroductionTitleLabel);
	}
	
	@Override
	public boolean isValid() {
		return true;
	}

	public void startIndeterminateProgressBar() {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				progressBar.setState(SWT.NORMAL);
			}
		});
		
		new Timer().schedule(new TimerTask() {
			@Override
			public void run() {
				stopIndeterminateProgressBar();
			}
		}, 1000*5);
	}

	public void stopIndeterminateProgressBar() {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				if (!progressBar.isDisposed() && progressBar.getState() == SWT.NORMAL){
					progressBar.setState(SWT.PAUSED);
				}
			}
		});
	}

	@Override
	public PanelState getState() {
		return null;
	}
}
