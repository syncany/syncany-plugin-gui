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

import java.util.ArrayList;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.syncany.gui.util.DialogUtil;
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.SWTResourceManager;
import org.syncany.gui.wizard.SelectFolderPanel.SelectFolderValidationMethod;
import org.syncany.gui.wizard.StartPanel.StartPanelSelection;

import com.google.common.collect.Lists;

/**
 * @author Vincent Wiencek <vwiencek@gmail.com>
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class WizardDialog extends Dialog {	
	private Shell shell;
	private Composite stackComposite;
	private StackLayout stackLayout;

	private WizardPanel currentPanel;	
	private StartPanel startPanel;
	private StartPanelSelection startPanelSelection;
	private SelectFolderPanel selectFolderPanel;
	private ProgressPanel progressPanel;
	private SummaryPanel summaryPanel;
		
	private Button cancelButton;
	private Button nextButton;
	private Button previousButton;

	public enum ClickAction { PREVIOUS, NEXT };

	public static void main(String[] a) {
		String intlPackage = I18n.class.getPackage().getName().replace(".", "/");  
		
		I18n.registerBundleName(intlPackage + "/i18n/messages");
		I18n.registerBundleFilter("plugin_messages*");		

		Shell shell = new Shell(); 
		Display display = Display.getDefault();

		WizardDialog wizardDialog = new WizardDialog(shell, SWT.APPLICATION_MODAL);
		wizardDialog.open();
		
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}		
	}
	
	public WizardDialog(Shell parent, int style) {
		super(parent, style);
		
		this.setText(I18n.getString("dialog.wizard.title"));
	}

	public Object open() {
		createContents();
		buildPanels();
		showPanel(startPanel, ClickAction.NEXT);

		DialogUtil.centerOnScreen(shell);
		
		shell.open();
		shell.layout();
		
		Display display = getParent().getDisplay();
		
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
		
		return null;
	}

	/**
	 * Create contents of the dialog.
	 */
	private void createContents() {
		GridLayout gridLayoutShell = new GridLayout(2, false);
		gridLayoutShell.marginTop = 0;
		gridLayoutShell.marginLeft = -2;
		gridLayoutShell.marginHeight = 0;
		gridLayoutShell.marginWidth = 0;
		gridLayoutShell.horizontalSpacing = 0;
		gridLayoutShell.verticalSpacing = 0;

		shell = new Shell(getParent(), SWT.DIALOG_TRIM);
		shell.setToolTipText("");
		shell.setBackground(WidgetDecorator.COLOR_WIDGET);
		shell.setSize(700, 500);
		shell.setText(getText());
		shell.setLayout(gridLayoutShell);

		String leftImageResource = "/" + WizardDialog.class.getPackage().getName().replace(".", "/") + "/wizard-left.png";
		Image leftImage = SWTResourceManager.getImage(leftImageResource);
		
		Label leftImageLabel = new Label(shell, SWT.NONE);
		leftImageLabel.setLayoutData(new GridData(SWT.LEFT, SWT.FILL, false, true, 1, 2));
		leftImageLabel.setImage(leftImage);

		stackLayout = new StackLayout();

		stackComposite = new Composite(shell, SWT.NONE);
		stackComposite.setLayout(stackLayout);
		stackComposite.setLayoutData(new GridData(SWT.RIGHT, SWT.FILL, true, true, 1, 1));

		RowLayout rowLayoutButtonComposite = new RowLayout(SWT.HORIZONTAL);
		rowLayoutButtonComposite.marginBottom = 15;
		rowLayoutButtonComposite.marginRight = 20;

		Composite buttonComposite = new Composite(shell, SWT.NONE);
		buttonComposite.setLayout(rowLayoutButtonComposite);
		buttonComposite.setLayoutData(new GridData(SWT.RIGHT, SWT.FILL, false, false, 1, 1));

		previousButton = new Button(buttonComposite, SWT.NONE);
		previousButton.setLayoutData(new RowData(WidgetDecorator.DEFAULT_BUTTON_WIDTH, WidgetDecorator.DEFAULT_BUTTON_HEIGHT));
		previousButton.setText(I18n.getString("dialog.default.previous"));
		previousButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				handleFlow(ClickAction.PREVIOUS);
			}
		});

		nextButton = new Button(buttonComposite, SWT.NONE);
		nextButton.setLayoutData(new RowData(WidgetDecorator.DEFAULT_BUTTON_WIDTH, WidgetDecorator.DEFAULT_BUTTON_HEIGHT));
		nextButton.setText(I18n.getString("dialog.default.next"));
		nextButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				handleFlow(ClickAction.NEXT);
			}
		});

		cancelButton = new Button(buttonComposite, SWT.NONE);
		cancelButton.setLayoutData(new RowData(WidgetDecorator.DEFAULT_BUTTON_WIDTH, WidgetDecorator.DEFAULT_BUTTON_HEIGHT));
		cancelButton.setText(I18n.getString("dialog.default.cancel"));
		cancelButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				safeDispose();
			}
		});

		shell.setDefaultButton(nextButton);
		
		WidgetDecorator.normal(nextButton, previousButton, cancelButton);
	}
	
	private void handleFlow(ClickAction clickAction) {
		if (currentPanel == startPanel) {
			startPanelSelection = startPanel.getState().getSelection();
		}

		switch (startPanelSelection) {
		case INIT: 
			handleInitFlow(clickAction);
			break;
		case CONNECT_MANUAL: 
			handleConnectFlowManual(clickAction);
			break;
		case CONNECT_URL: 
			handleConnectFlowUrl(clickAction);
			break;
		case ADD_EXISTING: 
			handleAddExistingFlow(clickAction);
			break;				
		}
	}

	private void handleAddExistingFlow(ClickAction clickAction) {
		if (currentPanel == startPanel) {
			if (clickAction == ClickAction.NEXT) {
				selectFolderPanel.setValidationMethod(SelectFolderValidationMethod.APP_FOLDER);				
				showPanel(selectFolderPanel, ClickAction.PREVIOUS, ClickAction.NEXT);
			}
		}
		else if (currentPanel == selectFolderPanel) {
			if (clickAction == ClickAction.PREVIOUS) {
				showPanel(startPanel, ClickAction.NEXT);
			}
			else if (clickAction == ClickAction.NEXT) {
				showPanel(progressPanel);
			}
		}
		else if (currentPanel == progressPanel) {
			if (clickAction == ClickAction.PREVIOUS) {
				showPanel(selectFolderPanel, ClickAction.PREVIOUS, ClickAction.NEXT);
			}
			else if (clickAction == ClickAction.NEXT) {
				showPanel(startPanel);
			}
		}
	}

	private void handleConnectFlowUrl(ClickAction clickAction) {
		// TODO Auto-generated method stub
		
	}

	private void handleConnectFlowManual(ClickAction clickAction) {
		// TODO Auto-generated method stub
		
	}

	private void handleInitFlow(ClickAction clickAction) {
		// TODO Auto-generated method stub
		
	}

	private void showPanel(WizardPanel panel, ClickAction... allowedActions) {
		boolean currentPanelValid = currentPanel == null || currentPanel.isValid();
		
		if (currentPanelValid) {		
			// Set new current panel
			currentPanel = panel;
			
			// Do layout
			stackLayout.topControl = currentPanel;			
			stackComposite.layout();
			
			// Toggle buttons
			ArrayList<ClickAction> allowedActionsList = Lists.newArrayList(allowedActions);
			
			nextButton.setEnabled(allowedActionsList.contains(ClickAction.NEXT));
			previousButton.setEnabled(allowedActionsList.contains(ClickAction.PREVIOUS));
		}
	}	

	public void safeDispose() {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				shell.dispose();
			}
		});
	}

	private void buildPanels() {
		startPanel = new StartPanel(this, stackComposite, SWT.NONE);
		selectFolderPanel = new SelectFolderPanel(this, stackComposite, SWT.NONE);
		progressPanel = new ProgressPanel(this, stackComposite, SWT.NONE);
		summaryPanel = new SummaryPanel(this, stackComposite, SWT.NONE);
	}
}
