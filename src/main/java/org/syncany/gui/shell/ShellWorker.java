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
package org.syncany.gui.shell;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.syncany.config.GuiEventBus;
import org.syncany.operations.daemon.Watch;
import org.syncany.operations.daemon.messages.ListWatchesManagementResponse;
import org.syncany.operations.daemon.messages.RetrieveFileStatusManagementRequest;
import org.syncany.operations.daemon.messages.RetrieveFileStatusManagementResponse;

import com.google.common.eventbus.Subscribe;

/**
 * @author pheckel
 *
 */
public class ShellWorker implements Runnable {
	private static final Logger logger = Logger.getLogger(ShellWorker.class.getSimpleName());	
	
	private static final Pattern MESSAGE_RETRIEVE_FILE_STATUS_PATTERN = Pattern.compile("RETRIEVE_(?:FILE|FOLDER)_STATUS:(.+)");
	private static final int MESSAGE_RETRIEVE_FILE_STATUS_PATTERN_GROUP_FILENAME = 1;
	
	private static final String MESSAGE_STATUS_FORMAT = "STATUS:%s:%s";
	private static final String MESSAGE_REGISTER_PATH_FORMAT = "REGISTER_PATH:%s"; 
	
	private Socket clientSocket;
	private BufferedReader clientIn;
	private PrintWriter clientOut;
	
	private AtomicBoolean running;
	private GuiEventBus eventBus;	
	
	public ShellWorker(Socket clientSocket, AtomicBoolean running) {
		this.clientSocket = clientSocket;
		this.running = running;
		
		this.eventBus = GuiEventBus.getInstance();
		this.eventBus.register(this);
	}

	public void send(String message) {
		logger.log(Level.INFO, "Sending message: " + message);
		
		clientOut.println(message);
		clientOut.flush();
	}
	
	@Subscribe
	public void onStatusEvent(RetrieveFileStatusManagementResponse statusResponse) {
		send(String.format(MESSAGE_STATUS_FORMAT, statusResponse.getStatus(), statusResponse.getFile()));
	}
	
	@Subscribe
	public void onWatchesEvent(ListWatchesManagementResponse listWatchesResponse) {
		for (Watch watch : listWatchesResponse.getWatches()) {
			send(String.format(MESSAGE_REGISTER_PATH_FORMAT, watch.getFolder().getAbsolutePath()));	
		}		
	}
	
	@Override 
	public void run() {
		try {
			clientIn = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			clientOut = new PrintWriter(clientSocket.getOutputStream());
			
			while (running.get()) {
				try {				
					String requestLine;
					
					while (null != (requestLine = clientIn.readLine())) {
						processRequest(requestLine);					
					}
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	private void processRequest(String requestLine) {
		logger.log(Level.INFO, "Request received: " + requestLine);
		
		Matcher matcher = MESSAGE_RETRIEVE_FILE_STATUS_PATTERN.matcher(requestLine);
		
		if (matcher.matches()) {
			File file = new File(matcher.group(MESSAGE_RETRIEVE_FILE_STATUS_PATTERN_GROUP_FILENAME));			
			eventBus.post(new RetrieveFileStatusManagementRequest(file));			 					
		}
		else {
			send("ERR"); 
		}
	}
}
