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
import org.syncany.config.GuiEventBus;
import org.syncany.gui.util.DesktopHelper;
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.SWTResourceManager;
import org.syncany.gui.wizard.StartPanel.StartPanelSelection;

import com.google.common.collect.Lists;

/**
 * @author Vincent Wiencek <vwiencek@gmail.com>
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class WizardDialog extends Dialog {
	public enum Action {
		PREVIOUS, NEXT, FINISH
	};

	private Shell shell;
	private Composite stackComposite;
	private StackLayout stackLayout;

	private StartPanel startPanel;
	private SelectFolderPanel selectFolderPanel;
	private ProgressPanel progressPanel;
	
	private Panel currentPanel;
	private PanelController panelController;

	private Button cancelButton;
	private Button nextButton;
	private Button previousButton;
	
	private GuiEventBus eventBus;

	public static void main(String[] a) {
		String intlPackage = I18n.class.getPackage().getName().replace(".", "/");

		I18n.registerBundleName(intlPackage + "/i18n/messages");
		I18n.registerBundleFilter("plugin_messages*");

		Shell shell = new Shell();

		WizardDialog wizardDialog = new WizardDialog(shell, SWT.APPLICATION_MODAL);
		wizardDialog.open();

		shell.dispose();
	}

	public WizardDialog(Shell parent, int style) {
		super(parent, style);

		this.eventBus = GuiEventBus.getInstance();
		this.eventBus.register(this);
		
		this.setText(I18n.getString("dialog.wizard.title"));
	}

	public void open() {
		// Create controls
		createContents();
		buildPanels();
		
		setCurrentPanel(startPanel, Action.NEXT);

		// Open shell
		DesktopHelper.centerOnScreen(shell);

		shell.open();
		shell.layout();

		// Dispatch loop
		Display display = getParent().getDisplay();

		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
	}

	/**
	 * Create contents of the dialog.
	 */
	private void createContents() {
		GridLayout shellGridLayout = new GridLayout(2, false);
		shellGridLayout.marginTop = 0;
		shellGridLayout.marginLeft = -2;
		shellGridLayout.marginHeight = 0;
		shellGridLayout.marginWidth = 0;
		shellGridLayout.horizontalSpacing = 0;
		shellGridLayout.verticalSpacing = 0;
		shellGridLayout.numColumns = 2;

		shell = new Shell(getParent(), SWT.DIALOG_TRIM);
		shell.setToolTipText("");
		shell.setBackground(WidgetDecorator.COLOR_WIDGET);
		shell.setSize(640, 480);
		shell.setText(getText());
		shell.setLayout(shellGridLayout);		

		// Row 1, Column 1: Image
		String leftImageResource = "/" + WizardDialog.class.getPackage().getName().replace(".", "/") + "/wizard-left.png";
		Image leftImage = SWTResourceManager.getImage(leftImageResource);

		Label leftImageLabel = new Label(shell, SWT.NONE);
		leftImageLabel.setLayoutData(new GridData(SWT.LEFT, SWT.FILL, false, true, 1, 2));
		leftImageLabel.setImage(leftImage);

		// Row 1, Column 2: Panel
		stackLayout = new StackLayout();
		stackLayout.marginHeight = 0;
		stackLayout.marginWidth = 0;

		stackComposite = new Composite(shell, SWT.NONE);
		stackComposite.setLayout(stackLayout);
		stackComposite.setLayoutData(new GridData(SWT.RIGHT, SWT.FILL, true, true, 1, 1));

		// Row 2, Column 1+2: Line
		GridData horizontalLineGridData = new GridData(GridData.FILL_HORIZONTAL);
		horizontalLineGridData.horizontalSpan = 2;

		Label horizontalLine = new Label(shell, SWT.SEPARATOR | SWT.HORIZONTAL);
		horizontalLine.setLayoutData(horizontalLineGridData);

		// Row 3: Column 1+2: Button Composite
		RowLayout buttonCompositeRowLayout = new RowLayout(SWT.HORIZONTAL);
		buttonCompositeRowLayout.marginTop = 15;
		buttonCompositeRowLayout.marginBottom = 15;
		buttonCompositeRowLayout.marginRight = 20;

		GridData buttonCompositeGridData = new GridData(SWT.RIGHT, SWT.FILL, false, false);
		buttonCompositeGridData.horizontalSpan = 2;
		buttonCompositeGridData.verticalSpan = 1;

		Composite buttonComposite = new Composite(shell, SWT.NONE);
		buttonComposite.setLayout(buttonCompositeRowLayout);
		buttonComposite.setLayoutData(buttonCompositeGridData);
		buttonComposite.setBackground(WidgetDecorator.COLOR_WIDGET);

		// Buttons
		previousButton = new Button(buttonComposite, SWT.NONE);
		previousButton.setLayoutData(new RowData(WidgetDecorator.DEFAULT_BUTTON_WIDTH, WidgetDecorator.DEFAULT_BUTTON_HEIGHT));
		previousButton.setText(I18n.getString("dialog.default.previous"));
		previousButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				handleFlow(Action.PREVIOUS);
			}
		});

		nextButton = new Button(buttonComposite, SWT.NONE);
		nextButton.setLayoutData(new RowData(WidgetDecorator.DEFAULT_BUTTON_WIDTH, WidgetDecorator.DEFAULT_BUTTON_HEIGHT));
		nextButton.setText(I18n.getString("dialog.default.next"));
		nextButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				handleFlow(Action.NEXT);
			}
		});
		
		Label spacingLabel = new Label(buttonComposite, SWT.NONE);
		spacingLabel.setText(" ");

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
	
	private void buildPanels() {
		startPanel = new StartPanel(this, stackComposite, SWT.NONE);
		selectFolderPanel = new SelectFolderPanel(this, stackComposite, SWT.NONE);
		progressPanel = new ProgressPanel(this, stackComposite, SWT.NONE);
	}

	private void handleFlow(Action clickAction) {
		if (stackLayout.topControl == startPanel) {
			if (panelController != null) {
				panelController.destroy();
			}
			
			panelController = createPanelStrategy(startPanel.getSelection());
		}
		
		if (panelController != null) {
			panelController.handleFlow(clickAction);
		}
	}
	
	private PanelController createPanelStrategy(StartPanelSelection startPanelSelection) {
		switch (startPanelSelection) {
		case ADD_EXISTING:
			return new AddExistingPanelController(this, startPanel, selectFolderPanel, progressPanel);

		case INIT:
		case CONNECT:
		default:
			return null;
		}
	}

	public Panel getCurrentPanel() {
		return currentPanel;
	}

	public void setCurrentPanel(Panel newPanel, Action... allowedActions) {
		// Set current panel
		currentPanel = newPanel;
		
		stackLayout.topControl = currentPanel;
		stackComposite.layout();	

		// Toggle buttons
		setAllowedActions(allowedActions);
	}
	
	public void setAllowedActions(final Action... allowedActions) {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				ArrayList<Action> allowedActionsList = Lists.newArrayList(allowedActions);

				nextButton.setEnabled(allowedActionsList.contains(Action.NEXT));
				previousButton.setEnabled(allowedActionsList.contains(Action.PREVIOUS));
				
				if (allowedActionsList.contains(Action.FINISH)) {
					shell.setDefaultButton(cancelButton);
					cancelButton.setText(I18n.getString("dialog.default.finish"));
				}
				else {
					cancelButton.setText(I18n.getString("dialog.default.cancel"));			
				}
			}
		});
	}
	
	protected boolean validateAndSetCurrentPanel(Panel panel, Action... allowedActions) {
		boolean currentPanelValid = currentPanel == null || currentPanel.validatePanel();

		if (currentPanelValid) {
			setCurrentPanel(panel, allowedActions);
			return true;
		}
		else {
			return false;
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
}