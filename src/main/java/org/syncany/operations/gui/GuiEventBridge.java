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

import java.util.logging.Logger;

import org.syncany.config.GuiEventBus;
import org.syncany.config.LocalEventBus;
import org.syncany.operations.daemon.messages.api.ExternalEvent;
import org.syncany.operations.daemon.messages.api.ExternalEventResponse;
import org.syncany.operations.daemon.messages.api.Request;
import org.syncany.operations.daemon.messages.api.Response;

import com.google.common.eventbus.Subscribe;

/**
 * This class bridges events from the {@link GuiEventBus} to the {@link LocalEventBus}
 * and back. It replaces the web socket connection if the daemon is running in a separate JVM.
 * 
 * The event flow is as follows:
 * 
 * <pre>
 *   Request/EventResponse: 
 *     GUI class -> GuiEventBus -> GuiBusListener -> LocalEventBus -> Daemon/Core class
 * 
 *   Response/Event:
 *     Daemon/Core class -> LocalEventBus -> LocalBusListener -> GuiEventBus -> GUI class
 * </pre>
 * 
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
*/
@SuppressWarnings("unused")
public class GuiEventBridge {	
	private static final Logger logger = Logger.getLogger(GuiEventBridge.class.getSimpleName());

	private GuiEventBus guiEventBus;
	private LocalEventBus localEventBus;
	
	private GuiBusListener guiListener;
	private LocalBusListener localListener;

	public GuiEventBridge() {	
		this.localEventBus = LocalEventBus.getInstance();
		this.guiEventBus = GuiEventBus.getInstance();		
	}

	public void start() {
		this.localListener = new LocalBusListener();
		this.guiListener = new GuiBusListener();		
	}
	
	private class GuiBusListener {		
		public GuiBusListener() {
			guiEventBus.register(this);
		}

		@Subscribe
		public void onRequest(Request request) {
			localEventBus.post(request);
		}
		
		@Subscribe
		public void onEventResponse(ExternalEventResponse eventResponse) {
			localEventBus.post(eventResponse);
		}
	}
	
	private class LocalBusListener {
		public LocalBusListener() {
			localEventBus.register(this);
		}
		
		@Subscribe 
		public void onResponse(Response response) {
			guiEventBus.post(response);
		}
		
		@Subscribe 
		public void onEvent(ExternalEvent event) {
			guiEventBus.post(event);
		}
	}
}
