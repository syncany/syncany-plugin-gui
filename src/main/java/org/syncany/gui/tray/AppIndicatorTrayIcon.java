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
package org.syncany.gui.tray;

import static io.undertow.Handlers.path;
import static io.undertow.Handlers.resource;
import static io.undertow.Handlers.websocket;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.StreamSourceFrameChannel;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.spi.WebSocketHttpExchange;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.widgets.Shell;
import org.syncany.operations.daemon.messages.ClickRecentChangesGuiInternalEvent;
import org.syncany.operations.daemon.messages.ClickTrayMenuFolderGuiInternalEvent;
import org.syncany.operations.daemon.messages.ClickTrayMenuGuiInternalEvent;
import org.syncany.operations.daemon.messages.DisplayNotificationGuiInternalEvent;
import org.syncany.operations.daemon.messages.ListWatchesManagementRequest;
import org.syncany.operations.daemon.messages.UpdateRecentChangesGuiInternalEvent;
import org.syncany.operations.daemon.messages.UpdateStatusTextGuiInternalEvent;
import org.syncany.operations.daemon.messages.UpdateTrayIconGuiInternalEvent;
import org.syncany.operations.daemon.messages.UpdateWatchesGuiInternalEvent;
import org.syncany.operations.daemon.messages.api.Message;
import org.syncany.operations.daemon.messages.api.XmlMessageFactory;
import org.syncany.util.StringUtil;

/**
 * The app indicator tray icon uses a Python script to create
 * a so called "app indicator" (introduced by Ubuntu Unity).
 *
 * <p>The class starts a Python script that creates an app indicator
 * and connects to the embedded web and websocket server. The embedded
 * server serves static content (tray icon images) and provides a
 * websocket server to communicate between the script and this class.
 *
 * @see https://unity.ubuntu.com/projects/appindicators/
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 * @author Vincent Wiencek <vwiencek@gmail.com>
 */
public class AppIndicatorTrayIcon extends TrayIcon {
	private static final Logger logger = Logger.getLogger(AppIndicatorTrayIcon.class.getSimpleName());

	private static String WEBSERVER_HOST = "127.0.0.1";
	private static int WEBSERVER_PORT = 51601;
	private static String WEBSERVER_PATH_HTTP_RESOURCES = "/api/res";
	private static String WEBSERVER_PATH_WEBSOCKET_XML = "/api/ws/xml";
	private static String WEBSERVER_ENDPOINT_HTTP = "http://" + WEBSERVER_HOST + ":" + WEBSERVER_PORT + WEBSERVER_PATH_HTTP_RESOURCES;
	private static String WEBSERVER_ENDPOINT_HTTP_THEME_FORMAT = WEBSERVER_ENDPOINT_HTTP + "/%s";
	private static String WEBSERVER_ENDPOINT_WEBSOCKET = "ws://" + WEBSERVER_HOST + ":" + WEBSERVER_PORT + WEBSERVER_PATH_WEBSOCKET_XML;
	private static String WEBSERVER_URL_SCRIPT = WEBSERVER_ENDPOINT_HTTP + "/tray.py";
	private static String PYTHON_LAUNCH_SCRIPT = "import urllib2; base_url = '%s'; ws_url = '%s'; exec urllib2.urlopen('%s').read()";

	private Undertow webServer;
	
	private Process pythonProcess;
	private AtomicBoolean pythonProcessRestart;
	private WebSocketChannel pythonClientChannel;

	public AppIndicatorTrayIcon(Shell shell, TrayIconTheme theme) {
		super(shell, theme);

		startWebServer();
		startTray();
	}

	private void startWebServer() {		
		String resourcesRoot = TrayIcon.class.getPackage().getName().replace(".", "/");

		HttpHandler pathHttpHandler = path()
				.addPrefixPath(WEBSERVER_PATH_WEBSOCKET_XML, websocket(new InternalWebSocketHandler()))
				.addPrefixPath(WEBSERVER_PATH_HTTP_RESOURCES, resource(new ClassPathResourceManager(TrayIcon.class.getClassLoader(), resourcesRoot)));

		webServer = Undertow
				.builder()
				.addHttpListener(WEBSERVER_PORT, WEBSERVER_HOST)
				.setHandler(pathHttpHandler)
				.build();

		webServer.start();
	}

	private void startTray() {
		new Thread(new Runnable() {			
			@Override
			public void run() {
				startPythonProcessWithRestartLoop();				
			}
		}, "PyAppInd").start();		
	}

	private void startPythonProcessWithRestartLoop() {
		pythonProcessRestart = new AtomicBoolean(true);
		int restartCount = 0;
		
		while (pythonProcessRestart.get()) {
			try {
				startPythonProcess();
				startPythonLoggerThreads();

				if (restartCount > 0) {
					waitAndSendListWatchesRequest();
				}
				
				waitForPythonProcess(); // Wait indefinitely (or until it crashes!)				
				waitBeforeRestart();
				
				restartCount++;				
			}
			catch (IOException e) {
				logger.log(Level.SEVERE, "ERROR starting AppIndicator Python process.", e);
			}
			catch (InterruptedException e) {
				logger.log(Level.SEVERE, "Python process or sleep interrupted. NOT RESTARTING. Assuming we want this process to die.", e);
				pythonProcessRestart.set(false);
			}
		}
	}

	private void startPythonProcess() throws IOException {
		String webServerEndpointHttpWithTheme = String.format(WEBSERVER_ENDPOINT_HTTP_THEME_FORMAT, getTheme().toString().toLowerCase());		

		String startScript = String.format(PYTHON_LAUNCH_SCRIPT, new Object[] {
				webServerEndpointHttpWithTheme, WEBSERVER_ENDPOINT_WEBSOCKET, WEBSERVER_URL_SCRIPT });

		String[] command = new String[] { "/usr/bin/python", "-c", startScript };
		ProcessBuilder processBuilder = new ProcessBuilder(command);

		logger.log(Level.INFO, "Starting external app indicator command: " + StringUtil.join(command, " "));						
		pythonProcess = processBuilder.start();
	}
	
	private void startPythonLoggerThreads() {
		BufferedReader scriptStdOutReader = new BufferedReader(new InputStreamReader(pythonProcess.getInputStream()));
		BufferedReader scriptStdErrReader = new BufferedReader(new InputStreamReader(pythonProcess.getErrorStream()));

		launchLoggerThread(scriptStdOutReader, "Python Input Stream: ", "PySTDIN");
		launchLoggerThread(scriptStdErrReader, "Python Error Stream: ", "PySTDERR");
	}
	
	private void waitAndSendListWatchesRequest() throws InterruptedException {
		logger.log(Level.INFO, "Python process started after crash (!): Waiting a bit, then sending list watches request ...");						

		Thread.sleep(5000);
		eventBus.post(new ListWatchesManagementRequest());
	}

	private void waitForPythonProcess() throws InterruptedException {
		logger.log(Level.INFO, "Python process started. Waiting indefinitely (or until it crashes :-/) ...");						
		pythonProcess.waitFor();
	}
	
	private void waitBeforeRestart() throws InterruptedException {
		if (pythonProcessRestart.get()) {
			logger.log(Level.INFO, "Python process crashed. Waiting a bit before restart.");						
			Thread.sleep(3000);				
		}
		else {
			logger.log(Level.INFO, "Python process crashed or terminated. NO RESTART requested.");
		}
	}

	@Override
	protected void exitApplication() {
		pythonProcessRestart.set(false);
		pythonProcess.destroy();
		
		webServer.stop();
		
		super.exitApplication();
	}

	private void launchLoggerThread(final BufferedReader stdinReader, final String prefix, String threadName) {
		logger.log(Level.INFO, "Starting Python logger thread for '" + threadName + "' ...");

		Thread loggerThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					String line;

					while ((line = stdinReader.readLine()) != null) {
						logger.log(Level.INFO, prefix + line);
					}
				}
				catch (Exception e) {
					logger.log(Level.SEVERE, "Unable to read from Python STDIN/STDERR.", e);
				}
			}
		}, threadName);
		
		loggerThread.start();
	}

	@Override
	public void setWatchedFolders(List<File> folders) {
		sendWebSocketMessage(new UpdateWatchesGuiInternalEvent(new ArrayList<>(folders)));
	}

	@Override
	public void setStatusText(String root, String statusText) {
		sendWebSocketMessage(new UpdateStatusTextGuiInternalEvent(root, statusText));
	}

	@Override
	protected void setTrayImage(TrayIconImage image) {
		sendWebSocketMessage(new UpdateTrayIconGuiInternalEvent(image.getFileName()));
	}

	@Override
	protected void setRecentChanges(List<File> recentFiles) {
		sendWebSocketMessage(new UpdateRecentChangesGuiInternalEvent(new ArrayList<>(recentFiles)));
	}

	@Override
	protected void displayNotification(String subject, String message) {
		sendWebSocketMessage(new DisplayNotificationGuiInternalEvent(subject, message));
	}

	public void sendWebSocketMessage(Message message) {
		if (pythonClientChannel != null) {
			try {
				String messageStr = XmlMessageFactory.toXml(message);
				logger.log(Level.INFO, "Sending message: " + messageStr);

				WebSockets.sendText(messageStr, pythonClientChannel, null);
			}
			catch (Exception e) {
				logger.log(Level.WARNING, "Cannot send message. Failed to create/send message.", e);
			}
		}
	}

	private void handleWebSocketMessage(WebSocketChannel clientSocket, String messageStr) {
		logger.log(Level.INFO, "Web socket message received: " + messageStr);

		try {
			Message message = XmlMessageFactory.toMessage(messageStr);

			if (message instanceof ClickTrayMenuFolderGuiInternalEvent) {
				ClickTrayMenuFolderGuiInternalEvent folderClickEvent = (ClickTrayMenuFolderGuiInternalEvent) message;
				File folder = new File(folderClickEvent.getFolder());
				
				switch (folderClickEvent.getAction()) {
				case OPEN:
					showFolder(folder);
					break;
					
				case COPY_LINK:
					copyLink(folder);
					break;
					
				case REMOVE:
					removeFolder(folder);
					break;
				}				
			}
			else if (message instanceof ClickRecentChangesGuiInternalEvent) {
				ClickRecentChangesGuiInternalEvent folderClickEvent = (ClickRecentChangesGuiInternalEvent) message;
				File file = new File(folderClickEvent.getFile());
				
				showRecentFile(file);
			}
			else if (message instanceof ClickTrayMenuGuiInternalEvent) {
				ClickTrayMenuGuiInternalEvent clickEvent = (ClickTrayMenuGuiInternalEvent) message;

				switch (clickEvent.getAction()) {
				case NEW:
					showNew();
					break;
				
				case BROWSE_HISTORY:
					showBrowseHistory();
					break;
					
				case PREFERENCES:
					showPreferences();
					break;

				case REPORT_ISSUE:
					showReportIssue();
					break;

				case DONATE:
					showDonate();
					break;

				case WEBSITE:
					showWebsite();
					break;

				case EXIT:
					exitApplication();
					break;
				}
			}
			else {
				logger.log(Level.WARNING, "UNKNOWN MESSAGE. IGNORING.");
			}
		}
		catch (Exception e) {
			logger.log(Level.WARNING, "Invalid request received; cannot serialize to Request.", e);
		}
	}

	private class InternalWebSocketHandler implements WebSocketConnectionCallback {
		@Override
		public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
			// Validate origin header (security!)
			String originHeader = exchange.getRequestHeader("Origin");

			if (originHeader != null && !originHeader.startsWith("http://" + WEBSERVER_HOST + ":" + WEBSERVER_PORT)) {
				logger.log(Level.INFO, channel.toString() + " disconnected due to invalid origin header: " + originHeader);
				exchange.close();
			}

			logger.log(Level.INFO, "Valid origin header, setting up connection.");

			channel.getReceiveSetter().set(new AbstractReceiveListener() {
				@Override
				protected void onFullTextMessage(WebSocketChannel clientChannel, BufferedTextMessage message) {
					handleWebSocketMessage(clientChannel, message.getData());
				}

				@Override
				protected void onError(WebSocketChannel webSocketChannel, Throwable error) {
					logger.log(Level.INFO, "Server error : " + error.toString());
				}

				@Override
				protected void onClose(WebSocketChannel clientChannel, StreamSourceFrameChannel streamSourceChannel) throws IOException {
					logger.log(Level.INFO, clientChannel.toString() + " disconnected");
				}
			});

			pythonClientChannel = channel;
			channel.resumeReceives();
		}
	}

	@Override
	protected void dispose() {
		// Do nothing.
	}
}
