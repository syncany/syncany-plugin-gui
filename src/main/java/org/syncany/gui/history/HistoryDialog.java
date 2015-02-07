package org.syncany.gui.history;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.syncany.config.GuiEventBus;
import org.syncany.config.Logging;
import org.syncany.database.PartialFileHistory.FileHistoryId;
import org.syncany.gui.Dialog;
import org.syncany.gui.Panel;
import org.syncany.gui.util.DesktopUtil;
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.WidgetDecorator;
import org.syncany.operations.daemon.messages.LsFolderRequest;

/**
 * The history dialog allows the user to browse old database versions and file versions
 * in a tree view, a log view, and restore certain files via a detail view.
 * 
 * <p>The dialog itself knows only the {@link MainPanel} and the {@link DetailPanel} and
 * can switch between them. The main logic is implemented in the {@link MainPanel}. The dialog
 * and the panels implement a not-quite-textbook version of MVC, with the {@link HistoryModel}
 * being the model, and the panels being the views and the controllers.
 *  
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class HistoryDialog extends Dialog {		
	private static final Logger logger = Logger.getLogger(HistoryDialog.class.getSimpleName());		

	private Shell windowShell;	
	private Composite stackComposite;
	private StackLayout stackLayout;
			
	private Panel currentPanel;
	private MainPanel mainPanel;
	private DetailPanel detailPanel;
			
	private HistoryModel model;	
	private GuiEventBus eventBus;	
	
	public HistoryDialog() {				
		this.windowShell = null;	
		this.stackComposite = null;
		this.stackLayout = null;

		this.currentPanel = null;
		this.mainPanel = null;
		this.detailPanel = null;
		
		this.model = new HistoryModel();
		this.eventBus = GuiEventBus.getInstance();
		this.eventBus.register(this);					
	}

	/**
	 * Main method to run/test the history dialog without connecting
	 * to the daemon. This only partially works, because no actual data
	 * is retrieved from the daemon.
	 */
	public static void main(String[] args) {
		Logging.init();
		
		String intlPackage = I18n.class.getPackage().getName().replace(".", "/");
		I18n.registerBundleName(intlPackage + "/i18n/messages");

		HistoryDialog dialog = new HistoryDialog();
		dialog.open();		
	}
	
	/**
	 * Open dialog, switch to main panel and enter the
	 * dispatch loop. This method blocks until the dialog
	 * is closed.
	 */
	public void open() {
		// Create controls
		createContents();
		createStackComposite();
		createPanels();
		
		showMainPanel();

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
		
		windowShell.dispose();
	}
	
	/**
	 * Return window shell, i.e. the dialog window.
	 */
	public Shell getWindowShell() {
		return windowShell;
	}
		
	/**
	 * Switch to the detail panel, and send {@link LsFolderRequest} (which will
	 * update the detail panel, once the response is returned). 
	 */
	public void showDetailsPanel(String root, FileHistoryId fileHistoryId) {
		logger.log(Level.INFO, "History dialog: Sending LsRequest for history ID " + fileHistoryId + " (root " + root + "); and switching to detail view ...");
		
		detailPanel.resetPanel(root, fileHistoryId);
		setCurrentPanel(detailPanel);
	}

	/**
	 * Switch to main panel
	 */
	public void showMainPanel() {
		logger.log(Level.INFO, "History dialog: Switching to main view ...");
		setCurrentPanel(mainPanel);
	}	

	private void createContents() {
		logger.log(Level.INFO, "History dialog: Creating dialog contents ...");

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
				dispose();
			}
		});
	}		

	private void createStackComposite() {
		logger.log(Level.INFO, "History dialog: Creating stack composite (for main/detail panel) ...");
		
		stackLayout = new StackLayout();
		stackLayout.marginHeight = 0;
		stackLayout.marginWidth = 0;
		
		GridData stackCompositeGridData = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
		stackCompositeGridData.minimumWidth = 500;

		stackComposite = new Composite(windowShell, SWT.DOUBLE_BUFFERED);
		stackComposite.setLayout(stackLayout);
		stackComposite.setLayoutData(stackCompositeGridData);
	}

	private void createPanels() {
		logger.log(Level.INFO, "History dialog: Creating main and detail panel ...");

		mainPanel = new MainPanel(stackComposite, SWT.NONE, model, this);
		detailPanel = new DetailPanel(stackComposite, SWT.NONE, model, this);
	}
	
	private void setCurrentPanel(final Panel newPanel) {
		logger.log(Level.INFO, "History dialog: Setting current panel to " + newPanel.getClass().getSimpleName() + " ...");
	
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
	
	private void dispose() {
		logger.log(Level.INFO, "History dialog: Disposing dialog ...");

		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {	
				eventBus.unregister(HistoryDialog.this);
				
				if (!mainPanel.isDisposed()) {
					mainPanel.dispose();
				}		
				
				if (!detailPanel.isDisposed()) {
					detailPanel.dispose();
				}		
				
				if (!windowShell.isDisposed()) {
					windowShell.dispose();
				}									
			}
		});
	}
}
