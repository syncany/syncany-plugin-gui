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
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.syncany.config.GuiEventBus;
import org.syncany.config.Logging;
import org.syncany.gui.Dialog;
import org.syncany.gui.Panel;
import org.syncany.gui.util.DesktopUtil;
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.SWTResourceManager;
import org.syncany.gui.util.WidgetDecorator;

/**
 * @author Vincent Wiencek <vwiencek@gmail.com>
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class PreferencesDialog extends Dialog {
	private static final String NAV_ICON_RESOURCE_FORMAT = "/" + PreferencesDialog.class.getPackage().getName().replace('.', '/') + "/nav-%s.png";

	private enum NavSelection {
		GENERAL, PLUGINS, NETWORK
	}
	
	private Shell trayShell;
	private Shell windowShell;
	private Composite stackComposite;
	private StackLayout stackLayout;
	
	private Table navTable;
	private GeneralPanel generalPanel;
	private PluginsPanel pluginsPanel;
	private NetworkPanel networkPanel;
	
	private Panel currentPanel;

	private GuiEventBus eventBus;

	public static void main(String[] a) {
		Logging.init();
		
		String intlPackage = I18n.class.getPackage().getName().replace(".", "/");
		I18n.registerBundleName(intlPackage + "/i18n/messages");

		Shell shell = new Shell();

		PreferencesDialog dialog = new PreferencesDialog(shell);
		dialog.open();

		shell.dispose();
	}

	public PreferencesDialog(Shell trayShell) {
		this.trayShell = trayShell;
		this.eventBus = GuiEventBus.getInstance();
		this.eventBus.register(this);
	}

	public void open() {
		// Create controls
		createContents();
		buildPanels();
		
		setCurrentPanel(generalPanel);

		// Open shell
		DesktopUtil.centerOnScreen(windowShell);

		windowShell.open();
		windowShell.layout();

		// Dispatch loop
		Display display = Display.getDefault();
	
		while (!windowShell.isDisposed()) {
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
		shellGridLayout.marginLeft = 0;
		shellGridLayout.marginHeight = 0;
		shellGridLayout.marginWidth = 0;
		shellGridLayout.horizontalSpacing = 0;
		shellGridLayout.verticalSpacing = 0;
		shellGridLayout.numColumns = 2;

		windowShell = new Shell(trayShell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.DOUBLE_BUFFERED);
		windowShell.setToolTipText("");
		windowShell.setBackground(WidgetDecorator.COLOR_WIDGET);
		windowShell.setSize(640, 480);
		windowShell.setText(I18n.getText("org.syncany.gui.preferences.PreferencesDialog.title"));
		windowShell.setLayout(shellGridLayout);		
		windowShell.addListener(SWT.Close, new Listener() {
			public void handleEvent(Event event) {
				safeDispose();
			}
		});
		
		// Navigation table (row 1, column 1) and stack composite (row 1, column 2)
		createNavTable();
		createStackComposite();
		
		// Button composite (row 2, column 1+2)
		createButtonComposite();		
	}
	
	private void createNavTable() {
		GridData navTableGridData = new GridData(SWT.LEFT, SWT.FILL, true, true);
		navTableGridData.verticalIndent = 0;
		navTableGridData.horizontalIndent = 0;
		navTableGridData.minimumWidth = 120;
		
	    navTable = new Table(windowShell, SWT.SINGLE | SWT.FULL_SELECTION);
		navTable.setHeaderVisible(false);
		navTable.setBackground(WidgetDecorator.WHITE);
		navTable.setLayoutData(navTableGridData);				
		
		navTable.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (navTable.getSelectionIndex() >= 0) {
					TableItem tableItem = navTable.getItem(navTable.getSelectionIndex());
					NavSelection panelSelection = (NavSelection) tableItem.getData();
					
					switch (panelSelection) {
					case GENERAL:
						setCurrentPanel(generalPanel);
						break;
						
					case PLUGINS:
						setCurrentPanel(pluginsPanel);
						break;
						
					case NETWORK:
						setCurrentPanel(networkPanel);
						break;
					}
				}
			}
		});	
		
		navTable.addListener(SWT.MeasureItem, new Listener() {
			public void handleEvent(Event event) {				
				event.height = 30; // Row height workaround	
			}
		});				
		
	    TableColumn navTableColumnEmpty = new TableColumn(navTable, SWT.CENTER);
	    navTableColumnEmpty.setWidth(5);

	    TableColumn navTableColumnImage = new TableColumn(navTable, SWT.CENTER);
	    navTableColumnImage.setWidth(30);

	    TableColumn navTableColumnText = new TableColumn(navTable,  SWT.LEFT);
	    navTableColumnText.setWidth(85);

	    // Entry 'General'
    	String navGeneralImageResource = String.format(NAV_ICON_RESOURCE_FORMAT, "general");
	    Image navGeneralImage = SWTResourceManager.getImage(navGeneralImageResource);

	    TableItem navGeneralTableItem = new TableItem(navTable, SWT.NONE);		    
	    navGeneralTableItem.setImage(1, navGeneralImage);
	    navGeneralTableItem.setText(2, I18n.getText("org.syncany.gui.preferences.PreferencesDialog.nav.general"));		    
	    navGeneralTableItem.setData(NavSelection.GENERAL);		
	    
	    // Entry 'Plugins'
    	String navPluginsImageResource = String.format(NAV_ICON_RESOURCE_FORMAT, "plugins");
	    Image navPluginsImage = SWTResourceManager.getImage(navPluginsImageResource);

	    TableItem navPluginsTableItem = new TableItem(navTable, SWT.NONE);		    
	    navPluginsTableItem.setImage(1, navPluginsImage);
	    navPluginsTableItem.setText(2, I18n.getText("org.syncany.gui.preferences.PreferencesDialog.nav.plugins"));		    
	    navPluginsTableItem.setData(NavSelection.PLUGINS);	
	    
	    // Entry 'Network'
    	String navNetworkImageResource = String.format(NAV_ICON_RESOURCE_FORMAT, "network");
	    Image navNetworkImage = SWTResourceManager.getImage(navNetworkImageResource);

	    TableItem navNetworkTableItem = new TableItem(navTable, SWT.NONE);		    
	    navNetworkTableItem.setImage(1, navNetworkImage);
	    navNetworkTableItem.setText(2, I18n.getText("org.syncany.gui.preferences.PreferencesDialog.nav.network"));		    
	    navNetworkTableItem.setData(NavSelection.NETWORK);	

	    // Select 'General'
	    navTable.select(0);	    
	}
	
	private void createStackComposite() {
		stackLayout = new StackLayout();
		stackLayout.marginHeight = 0;
		stackLayout.marginWidth = 0;
		
		GridData stackCompositeGridData = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
		stackCompositeGridData.minimumWidth = 500;

		stackComposite = new Composite(windowShell, SWT.DOUBLE_BUFFERED);
		stackComposite.setLayout(stackLayout);
		stackComposite.setLayoutData(stackCompositeGridData);

		// Row 2, Column 1+2: Line
		GridData horizontalLineGridData = new GridData(GridData.FILL_HORIZONTAL);
		horizontalLineGridData.horizontalSpan = 2;

		Label horizontalLine = new Label(windowShell, SWT.SEPARATOR | SWT.HORIZONTAL);
		horizontalLine.setLayoutData(horizontalLineGridData);
	}

	private void createButtonComposite() {
		RowLayout buttonCompositeRowLayout = new RowLayout(SWT.HORIZONTAL);
		buttonCompositeRowLayout.marginTop = 15;
		buttonCompositeRowLayout.marginBottom = 15;
		buttonCompositeRowLayout.marginRight = 20;

		GridData buttonCompositeGridData = new GridData(SWT.RIGHT, SWT.FILL, false, false);
		buttonCompositeGridData.horizontalSpan = 2;
		buttonCompositeGridData.verticalSpan = 1;

		Composite buttonComposite = new Composite(windowShell, SWT.NONE);
		buttonComposite.setLayout(buttonCompositeRowLayout);
		buttonComposite.setLayoutData(buttonCompositeGridData);
		buttonComposite.setBackground(WidgetDecorator.COLOR_WIDGET);

		// Buttons		
		Button closeButton = new Button(buttonComposite, SWT.NONE);
		closeButton.setLayoutData(new RowData(WidgetDecorator.DEFAULT_BUTTON_WIDTH, WidgetDecorator.DEFAULT_BUTTON_HEIGHT));
		closeButton.setText(I18n.getText("org.syncany.gui.preferences.PreferencesDialog.button.close"));
		closeButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				safeDispose();
			}
		});

		windowShell.setDefaultButton(closeButton);

		WidgetDecorator.normal(closeButton);
	}


	private void buildPanels() {
		generalPanel = new GeneralPanel(this, stackComposite, SWT.NONE);
		pluginsPanel = new PluginsPanel(this, stackComposite, SWT.NONE);
		networkPanel = new NetworkPanel(this, stackComposite, SWT.NONE);
	}

	public Panel getCurrentPanel() {
		return currentPanel;
	}
	
	public Shell getTrayShell() {
		return trayShell;
	}

	public Shell getWindowShell() {
		return windowShell;
	}

	public void setCurrentPanel(final Panel newPanel) {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				currentPanel = newPanel;
				
				stackLayout.topControl = currentPanel;
				stackComposite.layout();	
				
				currentPanel.setFocus();
			}
		});
	}
	
	public void safeDispose() {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {	
				if (!pluginsPanel.isDisposed()) {
					pluginsPanel.dispose();
				}
				
				if (!windowShell.isDisposed()) {
					windowShell.dispose();
				}
			}
		});
	}
}
