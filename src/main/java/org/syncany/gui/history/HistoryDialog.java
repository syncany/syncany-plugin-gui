package org.syncany.gui.history;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.syncany.config.Logging;
import org.syncany.gui.Dialog;
import org.syncany.gui.Panel;
import org.syncany.gui.util.DesktopUtil;
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.WidgetDecorator;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class HistoryDialog extends Dialog {
	private Shell trayShell;
	private Shell windowShell;	
	private Composite stackComposite;
	private StackLayout stackLayout;
	
	private TreePanel treePanel;
	
	private Panel currentPanel;

	public static void main(String[] a) {
		Logging.init();
		
		String intlPackage = I18n.class.getPackage().getName().replace(".", "/");
		I18n.registerBundleName(intlPackage + "/i18n/messages");

		Shell shell = new Shell();

		HistoryDialog dialog = new HistoryDialog(shell);
		dialog.open();

		shell.dispose();
	}

	public HistoryDialog(Shell trayShell) {
		this.trayShell = trayShell;
		this.windowShell = null;		
	}

	public void open() {
		// Create controls
		createContents();
		buildPanels();
		
		setCurrentPanel(treePanel);

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

		windowShell = new Shell(trayShell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.DOUBLE_BUFFERED);
		windowShell.setToolTipText("");
		windowShell.setBackground(WidgetDecorator.COLOR_WIDGET);
		windowShell.setSize(640, 480);
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
		treePanel = new TreePanel(this, stackComposite, SWT.NONE);
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
				if (!windowShell.isDisposed()) {
					windowShell.dispose();
				}				
			}
		});
	}		
}
