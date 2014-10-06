package org.syncany.gui.tray;

import static io.undertow.Handlers.resource;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;

import java.util.logging.Logger;

/**
 * Static resources web server
 * Used to access python script and resources to python script
 * 
 * @author Vincent Wiencek <vwiencek@gmail.com>
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class StaticResourcesWebServer {
	private static final Logger logger = Logger.getLogger(StaticResourcesWebServer.class.getSimpleName());

	public static int WEBSERVER_PORT = 51601;

	private Undertow webServer;

	public StaticResourcesWebServer() {
		String resourcesRoot = TrayIcon.class.getPackage().getName().replace(".", "/");
		HttpHandler resourceHttpHandler = resource(new ClassPathResourceManager(TrayIcon.class.getClassLoader(), resourcesRoot));
		
		webServer = Undertow
			.builder()
			.addHttpListener(WEBSERVER_PORT, "localhost")
			.setHandler(resourceHttpHandler)
			.build();	
	}

	public void start() {
		webServer.start();
	}

	public void stop() {
		webServer.stop();
	}
}
