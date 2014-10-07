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
import io.undertow.websockets.spi.WebSocketHttpExchange;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.widgets.Shell;
import org.syncany.config.LocalEventBus;
import org.syncany.operations.daemon.messages.GuiInternalEvent;
import org.syncany.operations.daemon.messages.api.Message;
import org.syncany.operations.daemon.messages.api.MessageFactory;
import org.syncany.operations.daemon.messages.api.Request;
import org.syncany.operations.status.StatusOperationResult;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 * @author Vincent Wiencek <vwiencek@gmail.com>
 */
public class PythonTrayIcon extends TrayIcon {
	private static final Logger logger = Logger.getLogger(PythonTrayIcon.class.getSimpleName());
	private static int WEBSERVER_PORT = 51601;
	
	private Undertow webServer;
	private Process pythonProcess;
	private LocalEventBus eventBus;

	public PythonTrayIcon(Shell shell) {
		super(shell);
		
		this.eventBus = LocalEventBus.getInstance();
		this.eventBus.register(this);
		
		startWebServer();
		startTray();
	}

	private void startWebServer() {	
		String resourcesRoot = TrayIcon.class.getPackage().getName().replace(".", "/");

		HttpHandler pathHttpHandler = path()
				.addPrefixPath("/api/ws", websocket(new InternalWebSocketHandler()))
				.addPrefixPath("/api/rs", resource(new ClassPathResourceManager(TrayIcon.class.getClassLoader(), resourcesRoot)));
		
		webServer = Undertow
			.builder()
			.addHttpListener(WEBSERVER_PORT, "localhost")
			.setHandler(pathHttpHandler)
			.build();	
		
		webServer.start();
	}

	private void startTray() {
		try {
			String webSocketUri = "ws://127.0.0.1:" + WEBSERVER_PORT + "/api/ws";
			String webServerUrl = "http://127.0.0.1:" + WEBSERVER_PORT + "/api/rs";
			String scriptUrl = webServerUrl + "/tray.py";

			Object[] args = new Object[] {
				webServerUrl,
				webSocketUri, 
				messages.toString(),
				scriptUrl
			};
			
			String startScript = String.format(
				"import urllib2 ; " + 
				"baseUrl   = '%s' ; " + 
				"wsUrl     = '%s' ; " +
				"i18n      = '%s' ; " +
				"exec urllib2.urlopen('%s').read()", args);

			String[] command = new String[] { "/usr/bin/python", "-c", startScript };
			ProcessBuilder processBuilder = new ProcessBuilder(command);

			pythonProcess = processBuilder.start();

			BufferedReader is = new BufferedReader(new InputStreamReader(pythonProcess.getInputStream()));
			BufferedReader es = new BufferedReader(new InputStreamReader(pythonProcess.getErrorStream()));

			launchLoggerThread(is, "Python Input Stream : ");
			launchLoggerThread(es, "Python Error Stream : ");
		}
		catch (Exception e) {
			throw new RuntimeException("Cannot start Python process for Unity Tray Icon.", e);
		}
	}

	@Override
	protected void quit() {
		webServer.stop();		
		super.quit();
	}

	protected void handleCommand(Map<String, Object> map) {
		String command = (String) map.get("action");

		switch (command) {
		case "tray_menu_clicked_new":
			showWizard();
			break;
		case "tray_menu_clicked_preferences":
			showSettings();
			break;
		case "tray_menu_clicked_folder":
			showFolder(new File((String) map.get("folder")));
			break;
		case "tray_menu_clicked_donate":
			showDonate();
			break;
		case "tray_menu_clicked_website":
			showWebsite();
			break;
		case "tray_menu_clicked_quit":
			quit();
			break;
		}
	}

	public void sendToAll(String message) {		

	}

	private void launchLoggerThread(final BufferedReader stdinReader, final String prefix) {
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					String line;

					while ((line = stdinReader.readLine()) != null) {
						logger.info(prefix + line);
					}
				}
				catch (Exception e) {
					logger.warning("Exception " + e);
				}
			}
		});
		t.start();
	}

	@Override
	public void updateWatchedFolders(final List<File> folders) {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("action", "update_tray_menu");
		parameters.put("folders", folders);
		sendToAll(parameters.toString());
	}

	@Override
	public void updateStatusText(String statusText) {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("action", "update_tray_status_text");
		parameters.put("text", statusText);
		sendToAll(parameters.toString());
	}

	@Override
	protected void setTrayImage(TrayIconImage image) {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("action", "update_tray_icon");
		parameters.put("imageFileName", image.getFileName());
		sendToAll(parameters.toString());
	}

	@Override
	public void updateWatchedFoldersStatus(StatusOperationResult result) {
		// TODO Auto-generated method stub
		
	}
	
	private class InternalWebSocketHandler implements WebSocketConnectionCallback {
		private WebSocketChannel pythonClientChannel;
		
		@Override
		public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
			// Validate origin header (security!)
			String originHeader = exchange.getRequestHeader("Origin");
			
			if (originHeader != null && !originHeader.startsWith("http://127.0.0.1:" + WEBSERVER_PORT)) {
				logger.log(Level.INFO, channel.toString() + " disconnected due to invalid origin header: " + originHeader);
				exchange.close();
			}
			
			logger.log(Level.INFO, "Valid origin header, setting up connection.");
				
			channel.getReceiveSetter().set(new AbstractReceiveListener() {
				@Override
				protected void onFullTextMessage(WebSocketChannel clientChannel, BufferedTextMessage message) {
					handleWebSocketRequest(clientChannel, message.getData());
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
		
		private void handleWebSocketRequest(WebSocketChannel clientSocket, String messageStr) {
			logger.log(Level.INFO, "Web socket message received: " + messageStr);

			try {
				Message message = MessageFactory.toMessage(messageStr);
				
				if (message instanceof GuiInternalEvent) {						
					eventBus.post(message);
				}
			}
			catch (Exception e) {
				logger.log(Level.WARNING, "Invalid request received; cannot serialize to Request.", e);
				//eventBus.post(new BadRequestResponse(-1, "Invalid request."));
			}
		}
	}
}
