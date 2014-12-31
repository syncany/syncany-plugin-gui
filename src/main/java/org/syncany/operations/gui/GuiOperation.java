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
package org.syncany.operations.gui;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.syncany.config.GuiConfigHelper;
import org.syncany.config.GuiEventBus;
import org.syncany.config.LocalEventBus;
import org.syncany.config.Logging;
import org.syncany.config.UserConfig;
import org.syncany.config.to.GuiConfigTO;
import org.syncany.gui.tray.TrayIcon;
import org.syncany.gui.tray.TrayIconFactory;
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.SWTResourceManager;
import org.syncany.operations.Operation;
import org.syncany.operations.OperationResult;
import org.syncany.operations.daemon.ControlServer.ControlCommand;
import org.syncany.operations.daemon.DaemonOperation;
import org.syncany.operations.daemon.messages.ExitGuiInternalEvent;
import org.syncany.util.PidFileUtil;

import com.google.common.eventbus.Subscribe;

/**
 * @author Vincent Wiencek <vwiencek@gmail.com>
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class GuiOperation extends Operation {	
	private static final Logger logger = Logger.getLogger(GuiOperation.class.getSimpleName());	
	
	private GuiConfigTO guiConfig;
	
	private GuiEventBus eventBus;	
	private GuiOperationOptions options;
	
	private Shell shell;
	private TrayIcon trayIcon;
	private boolean daemonStarted;
	private Thread daemonThread;
	private GuiWebSocketClient webSocketClient;	
	
	static {
		Logging.init();
		UserConfig.init();
	}
	
	public GuiOperation() {
		this(new GuiOperationOptions());
	}
	
	public GuiOperation(GuiOperationOptions options) {
		super(null);
		this.options = options;
	}

	@Override
	public OperationResult execute() throws Exception {
		logger.log(Level.INFO, "Starting GUI operation ...");
		
		loadOrCreateGuiConfig();
		
		initEventBus();		
		initShutdownHook();		
		initDisplayWindow();
		initInternationalization();
		initTray();
		
		startDaemon();		
		startWebSocketClient();
				
		startEventDispatchLoop();
		
		return null;
	}	

	private void loadOrCreateGuiConfig() {
		guiConfig = GuiConfigHelper.loadOrCreateGuiConfig();
	}

	private void initEventBus() {
		eventBus = GuiEventBus.getInstance();
		eventBus.register(this);
	}
	
	private void initShutdownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				logger.info("Releasing SWT Resources");
				SWTResourceManager.dispose();
			}
		});
	}
	
	private void initDisplayWindow() {
		Display.setAppName("Syncany");
		Display.setAppVersion("1.0");
		
		shell = new Shell();
		shell.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				System.exit(0);
			}
		});
	}

	private void initInternationalization() {
		String intlPackage = I18n.class.getPackage().getName().replace(".", "/");  		
		I18n.registerBundleName(intlPackage + "/i18n/messages");
	}
	
	private void initTray() {
		if (options.getTrayType() != null) {
			trayIcon = TrayIconFactory.createTrayIcon(shell, options.getTrayType());
		}
		else {
			if (guiConfig.getTray() != null) {
				trayIcon = TrayIconFactory.createTrayIcon(shell, guiConfig.getTray());
			}
			else {
				trayIcon = TrayIconFactory.createTrayIcon(shell);
			}
		}
		
		trayIcon.hashCode(); // Dummy call to avoid 'don't use' warning
	}
	
	public void startDaemon() {	
		File daemonPidFile = new File(UserConfig.getUserConfigDir(), DaemonOperation.PID_FILE);
		boolean daemonRunning = PidFileUtil.isProcessRunning(daemonPidFile);

		if (!daemonRunning) {
			daemonThread = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						logger.log(Level.INFO, "Starting daemon in separate thread ...");
						
						new DaemonOperation().execute();
						
						logger.log(Level.INFO, "SHUTDOWN of daemon complete.");
					}
					catch (Exception e) {
						logger.log(Level.SEVERE, "Cannot start daemon or daemon execution failed.", e);
					}
				}					
			});
			
			daemonThread.start();
			daemonStarted = true;
		}		
	}

	private void startWebSocketClient() {
		webSocketClient = new GuiWebSocketClient(); 
		webSocketClient.start();
	}

	public void startEventDispatchLoop() {
		Display display = Display.getDefault();

		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
	}

	public void disposeShell() {
		if (shell != null && !shell.isDisposed()) {
			Display.getDefault().syncExec(new Runnable() {				
				@Override
				public void run() {
					shell.dispose();
				}
			});			
		}
	}	

	public void stopDaemon() throws IOException, InterruptedException {
		if (daemonStarted) {
			LocalEventBus.getInstance().post(ControlCommand.SHUTDOWN);
		}
	}
	
	@Subscribe
	public void onExitGuiEventReceived(ExitGuiInternalEvent quitEvent) {
		try {
			stopDaemon();
		}
		catch (IOException e) {
			logger.warning("Unable to stop daemon: " + e);
		}
		catch (InterruptedException e) {
			logger.warning("Unable to stop daemon: " + e);
		}
		
		disposeShell();
		System.exit(0);
	}
}
