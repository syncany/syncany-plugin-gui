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

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Text;
import org.syncany.config.GuiEventBus;
import org.syncany.gui.util.SWTResourceManager;

/**
 * @author Vincent Wiencek <vwiencek@gmail.com>
 *
 */
public class ProgressPanel extends Panel {
	private Label titleLabel;
	private Label descriptionLabel;

	private Button progressLogCheckButton;
	private Text progressLogText;
	private ProgressBar progressBar;

	private GuiEventBus eventBus;

	public ProgressPanel(WizardDialog wizardParentDialog, Composite parent, int style) {
		super(wizardParentDialog, parent, style);

		this.eventBus = GuiEventBus.getInstance();
		this.eventBus.register(this);

		initComposite();
	}

	private void initComposite() {
		// Main composite
		GridLayout mainCompositeGridLayout = new GridLayout(2, false);
		mainCompositeGridLayout.marginTop = 15;
		mainCompositeGridLayout.marginBottom = 15;
		mainCompositeGridLayout.marginLeft = 10;
		mainCompositeGridLayout.marginRight = 10;

		setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		setLayout(mainCompositeGridLayout);
		setBackground(SWTResourceManager.getColor(236, 236, 236));

		// Title and welcome text
		titleLabel = new Label(this, SWT.WRAP);
		titleLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 4, 1));
		titleLabel.setText("..");

		WidgetDecorator.title(titleLabel);

		descriptionLabel = new Label(this, SWT.WRAP);
		descriptionLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false, 3, 1));
		descriptionLabel.setText("..");

		WidgetDecorator.normal(descriptionLabel);		
		
		// Progress bar
		GridData progressBarGridData = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
		progressBarGridData.verticalIndent = 20;
		
		progressBar = new ProgressBar(this, SWT.SMOOTH);
		progressBar.setLayoutData(progressBarGridData);
		progressBar.setState(SWT.PAUSED);
		
		// Check box for details
		GridData logCheckGridData = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
		logCheckGridData.horizontalSpan = 2;
		
		progressLogCheckButton = new Button(this, SWT.CHECK);
		progressLogCheckButton.setText("Show Details");
		progressLogCheckButton.setLayoutData(logCheckGridData);
		
		progressLogCheckButton.addSelectionListener(new SelectionAdapter() {			
			@Override
			public void widgetSelected(SelectionEvent e) {
				progressLogText.setVisible(progressLogCheckButton.getSelection());
			}			
		});
		
		// Details
		GridData progressLogTextGridData = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
		progressLogTextGridData.verticalIndent = 20;

		progressLogText = new Text(this, SWT.MULTI | SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
		progressLogText.setLayoutData(new GridData(GridData.FILL_BOTH));
		progressLogText.setVisible(false);
	}
	
	public void setTitleText(final String titleStr) {
		Display.getDefault().asyncExec(new Runnable() {			
			@Override
			public void run() {
				titleLabel.setText(titleStr);
				layout();
			}
		});
	}
	
	public void setDescriptionText(final String descriptionStr) {
		Display.getDefault().asyncExec(new Runnable() {			
			@Override
			public void run() {
				descriptionLabel.setText(descriptionStr);
				layout();
			}
		});
	}

	public void appendLog(final String logLine) {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				progressLogText.append(logLine);
			}
		});
	}
	
	public void resetPanel(final int maximumProgress) {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				progressBar.setMinimum(0);
				progressBar.setMaximum(maximumProgress);
				progressBar.setSelection(0);
				
				progressLogCheckButton.setSelection(false);

				progressLogText.setVisible(false);
				progressLogText.setText("");
			}
		});		
	}
	
	public void setProgress(final int position) {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				progressBar.setSelection(position);
			}
		});
	}
	
	public void setShowDetails(final boolean showDetailsPanel) {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				progressLogCheckButton.setSelection(showDetailsPanel);
				progressLogText.setVisible(showDetailsPanel);
			}
		});
	}

	@Override
	public boolean isValid() {
		return true;
	}
}
