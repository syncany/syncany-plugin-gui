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

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author pheckel
 *
 */
public class ShellServerSocket {
	private Thread socketThread;
	private ServerSocket serverSocket;
	private AtomicBoolean running;
	
	public ShellServerSocket() {
		this.socketThread = initSocketThread();
		this.serverSocket = null;
		this.running = new AtomicBoolean(false);
	}

	public void start() {
		socketThread.start();
	}
	
	private Thread initSocketThread() {
		return new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					serverSocket = new ServerSocket(12345);
					running.set(true);
					
					while (running.get()) {
						Socket clientSocket = serverSocket.accept();
						ShellWorker shellWorker = new ShellWorker(clientSocket, running);
						
						new Thread(shellWorker).start();
					}
				}
				catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
		});
	}
}
