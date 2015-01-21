package org.syncany.gui.history;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TreeItem;
import org.syncany.config.GuiEventBus;
import org.syncany.config.Logging;
import org.syncany.database.DatabaseVersionHeader;
import org.syncany.database.FileVersion;
import org.syncany.database.FileVersion.FileType;
import org.syncany.database.PartialFileHistory.FileHistoryId;
import org.syncany.gui.Dialog;
import org.syncany.gui.Panel;
import org.syncany.gui.util.DesktopUtil;
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.WidgetDecorator;
import org.syncany.operations.daemon.Watch;
import org.syncany.operations.daemon.messages.GetDatabaseVersionHeadersFolderResponse;
import org.syncany.operations.daemon.messages.ListWatchesManagementRequest;
import org.syncany.operations.daemon.messages.ListWatchesManagementResponse;
import org.syncany.operations.daemon.messages.LogFolderRequest;
import org.syncany.operations.daemon.messages.LogFolderResponse;
import org.syncany.operations.daemon.messages.LsFolderRequest;
import org.syncany.operations.daemon.messages.LsFolderResponse;
import org.syncany.operations.daemon.messages.RestoreFolderRequest;
import org.syncany.operations.log.LightweightDatabaseVersion;
import org.syncany.operations.log.LogOperationOptions;
import org.syncany.operations.ls.LsOperationOptions;
import org.syncany.operations.restore.RestoreOperationOptions;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.eventbus.Subscribe;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class HistoryDialog extends Dialog {
	// Model
	private HistoryModel model;	
		
	// View
	private Shell windowShell;	
	private Composite stackComposite;
	private StackLayout stackLayout;
			
	private MainPanel mainPanel;
	private MainController mainController;
	
	private DetailPanel detailPanel;
	private DetailController detailController;
	
	private Panel currentPanel;
		
	// Controller

	private GuiEventBus eventBus;	
	
	public HistoryDialog() {		
		this.model = new HistoryModel();
		
		this.windowShell = null;	
		
		this.eventBus = GuiEventBus.getInstance();
		this.eventBus.register(this);					
	}

	public void open() {
		// Create controls
		createContents();
		buildPanels();
		
		setCurrentPanel(mainPanel);

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
		GridLayout shellGridLayout = new GridLayout(1, false);
		shellGridLayout.marginTop = 0;
		shellGridLayout.marginLeft = 0;
		shellGridLayout.marginHeight = 0;
		shellGridLayout.marginWidth = 0;
		shellGridLayout.horizontalSpacing = 0;
		shellGridLayout.verticalSpacing = 0;

		windowShell = new Shell(Display.getDefault(), SWT.SHELL_TRIM | SWT.DOUBLE_BUFFERED);
		windowShell.setToolTipText("");
		windowShell.setBackground(WidgetDecorator.COLOR_WIDGET);
		windowShell.setSize(900, 560);
		windowShell.setText(I18n.getText("org.syncany.gui.history.HistoryDialog.title"));
		windowShell.setLayout(shellGridLayout);		
		windowShell.addListener(SWT.Close, new Listener() {
			public void handleEvent(Event event) {
				safeDispose();
			}
		});
		
		createStackComposite();
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
	}

	private void buildPanels() {
		mainPanel = new MainPanel(stackComposite, SWT.NONE, model, this, this, this);
		detailPanel = new DetailPanel(stackComposite, SWT.NONE, model, this);
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
				eventBus.unregister(HistoryDialog.this);
				
				if (!windowShell.isDisposed()) {
					windowShell.dispose();
				}									
			}
		});
	}
	
	public void showDetails(String root, FileHistoryId fileHistoryId) {
		detailController.sendLsFolderRequest(root, fileHistoryId);
		showDetailsPanel();
	}

	public void showTree() {
		setCurrentPanel(mainPanel);
	}

	private void refreshRoot() {
		sendLsRequest("");
	}

	public void resetAndRefresh() {
		fileTree.removeAll();
		refreshRoot();
	}
	
	private List<String> getPaths(String pathExpression) {
		List<String> paths = new ArrayList<>();
		int previousIndexOf = -1;
		
		while (-1 != (previousIndexOf = pathExpression.indexOf('/', previousIndexOf + 1))) {
			paths.add(pathExpression.substring(0, previousIndexOf));
		}
		
		return paths;
	}	

	

	
	
	private void launchOrDisplayError(File file) {
		if (file.exists()) {
			DesktopUtil.launch(file.getAbsolutePath());	
		}
		else {
			MessageBox messageBox = new MessageBox(getShell(), SWT.ICON_WARNING | SWT.OK);	        
	        messageBox.setText(I18n.getText("org.syncany.gui.history.LogTabComposite.warningNotExist.title"));
	        messageBox.setMessage(I18n.getText("org.syncany.gui.history.LogTabComposite.warningNotExist.description", file.getAbsolutePath()));
	        
	        messageBox.open();
		}
	}

	public void showMainPanel() {
		setCurrentPanel(mainPanel);
	}
	
	public void showDetailsPanel() {
		setCurrentPanel(currentPanel);
	}


}
