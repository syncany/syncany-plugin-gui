package org.syncany.gui;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.syncany.config.Config;
import org.syncany.config.LocalEventBus;
import org.syncany.config.UserConfig;
import org.syncany.gui.events.ExitGuiInternalEvent;
import org.syncany.gui.messaging.websocket.WebSocket;
import org.syncany.gui.tray.TrayIcon;
import org.syncany.gui.tray.TrayIconFactory;
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.SWTResourceManager;
import org.syncany.operations.daemon.DaemonOperation;
import org.syncany.operations.daemon.messages.ListWatchesManagementRequest;
import org.syncany.util.EnvironmentUtil;
import org.syncany.util.PidFileUtil;

import com.google.common.eventbus.Subscribe;

public class Launcher {
	private static final Logger logger = Logger.getLogger(Launcher.class.getSimpleName());

	private LocalEventBus eventBus;
	
	private Shell shell;
	private TrayIcon trayIcon;
	private boolean daemonStarted;
	private WebSocket webSocketClient;	

	static {
		UserConfig.init();
	}
	
	public static void main(String[] args) {
		new Launcher();
	}
	
	public Launcher() {
		initEventBus();		
		initShutdownHook();		
		initDisplayWindow();
		initInternationalization();
		initTray();
		
		startDaemon();

		initWebSocket();
		sendListWatchesRequest();
				
		startEventDispatchLoop();
	}

	private void initEventBus() {
		eventBus = LocalEventBus.getInstance();
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
		I18n.registerBundleFilter("plugin_messages*");		
	}
	
	private void initTray() {
		trayIcon = TrayIconFactory.createTrayIcon(shell);
	}
	
	public void startDaemon() {
		try {
			File daemonPidFile = new File(UserConfig.getUserConfigDir(), DaemonOperation.PID_FILE);
			boolean daemonRunning = PidFileUtil.isProcessRunning(daemonPidFile);

			if (!daemonRunning) {
				File launchScript = getLaunchScript();

				Process daemonProcees = new ProcessBuilder(launchScript.getAbsolutePath(), "daemon", "start").start();
				daemonProcees.waitFor();
				
				daemonStarted = true;
			}
		}
		catch (IOException | InterruptedException e) {
			logger.log(Level.WARNING, "Unable to start daemon", e);
		}		
	}

	private void initWebSocket() {
		webSocketClient = new WebSocket(); 
		webSocketClient.init();
	}
	
	private void sendListWatchesRequest() {
		eventBus.post(new ListWatchesManagementRequest());
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
			shell.dispose();
		}
	}	

	public void stopDaemon() throws IOException, InterruptedException {
		if (daemonStarted) {
			File daemonPidFile = new File(UserConfig.getUserConfigDir(), DaemonOperation.PID_FILE);
			boolean daemonRunning = PidFileUtil.isProcessRunning(daemonPidFile);
	
			if (daemonRunning) {
				File launchScript = getLaunchScript();
	
				Process daemonProcees = new ProcessBuilder(launchScript.getAbsolutePath(), "daemon", "stop").start();
				daemonProcees.waitFor();			
			}
		}
	}
	
	private File getLaunchScript() {		
		File libJarFile = getJarFromClass(Config.class);
		File appHomeDir = libJarFile.getParentFile().getParentFile();
		File appBinDir = new File(appHomeDir, "bin");
		
		String appLaunchScriptName = (EnvironmentUtil.isWindows()) ? "sy.bat" : "sy";			
		File appLaunchScript = new File(appBinDir, appLaunchScriptName);
		
		return appLaunchScript;		
	}
	
	private File getJarFromClass(Class<?> searchClass) {
		try {
			URL pluginClassLocation = searchClass.getResource('/' + searchClass.getName().replace('.', '/') + ".class");
			String pluginClassLocationStr = pluginClassLocation.toString();
			logger.log(Level.INFO, "Plugin class is at " + pluginClassLocation);
	
			int indexStartAfterSchema = "jar:file:".length();
			int indexEndAtExclamationPoint = pluginClassLocationStr.indexOf("!");
			File searchClassJarFile = new File(pluginClassLocationStr.substring(indexStartAfterSchema, indexEndAtExclamationPoint));
			
			return searchClassJarFile;
		}
		catch (Exception e) {
			throw new RuntimeException("Cannot find JAR file for class " + searchClass, e);
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