#!/usr/bin/python
#
# Syncany Linux Native Functions
# Copyright (C) 2011 Philipp C. Heckel <philipp.heckel@gmail.com> 
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.
#
# author : Philipp C. Heckel <philipp.heckel@gmail.com> 
# author : Vincent Wiencek <vwiencek@gmail.com>

# Dependencies:
# - python-appindicator
# - python-websocket-client

import os
import sys
import time
import gtk
import pynotify
import threading
import websocket
import appindicator
import urllib
import tempfile
import re

from lxml import etree

def fetch_image(relative_url):
	global base_url, images_map
	
	# Caching images
	if not relative_url in images_map:
		temp_image_file = tempfile.NamedTemporaryFile(prefix='syncanytray', suffix='.png', delete=False)
		filename, headers = urllib.urlretrieve(base_url + relative_url, temp_image_file.name)
		
		do_print("Caching image '" + relative_url + "' to '" + filename + "'")
		images_map[relative_url] = filename
		
		temp_image_file.close()
	
	return images_map[relative_url]

def do_notify(request):
	do_print("Creating notification ...")

	subject = request.find("subject").text
	message = request.find("message").text
	image = fetch_image("/tray.png")
	
	try:
		gtk.gdk.threads_enter()
		pynotify.init("Syncany")
		notification = pynotify.Notification(subject, message, image)
		notification.show()
		gtk.gdk.threads_leave()
	except:
		do_print(sys.exc_info())
		do_print("Displaying notification via pynotify failed; trying notify-send ...")

		os.system("notify-send -t 2000 -i '{0}' '{1}' '{2}'".format(image, request["summary"], request["body"]))
		
	return None		
	
def do_update_icon(request):
	global indicator
	indicator.set_icon(fetch_image("/" + request.find("filename").text))		
	return None
	
def do_update_text(request):
	global status_texts, watches_last_request
	
	# Set status text for this watch
	root_element = request.find("root")
			
	if root_element is None:
		status_texts.clear()
	
	else:
		root = request.find("root").text
		text = request.find("text").text

		status_texts[root] = text

	# Rebuild menu
	do_update_menu(watches_last_request)	
	
	return None	
	
def do_update_recent_changes(request):
	global recent_changes, watches_last_request

	# File entries
	files = request.xpath("//file")

	# Delete all entries, and add new ones
	del recent_changes[:]
	
	for afile in files:
		recent_changes.append(afile.text)
					
	# Rebuild menu
	do_update_menu(watches_last_request)	
	
	return None			
			
	
def do_update_menu(request):
	global menu, status_texts, status_text_default, recent_changes, watches_last_request

	gtk.gdk.threads_enter()
	
	# For next status text update
	watches_last_request = request

	# Remove all children
	for child in menu.get_children():
		menu.remove(child)		
	
	# Status
	status_text_items_count = 0
	
	for root, text in status_texts.iteritems():
		watch_is_in_sync = text == status_text_default
		
		if not watch_is_in_sync:
			status_text_items_count += 1
			
			menu_item_status = gtk.MenuItem(os.path.basename(root) + "\n" + text)
			menu_item_status.set_can_default(0);	
			menu_item_status.set_sensitive(0);

			menu.append(menu_item_status)
	
	if status_text_items_count == 0:
		menu_item_status = gtk.MenuItem(status_text_default)
		menu_item_status.set_can_default(0);	
		menu_item_status.set_sensitive(0);

		menu.append(menu_item_status)
	
	# ---
	menu.append(gtk.SeparatorMenuItem())	

	# New ...
	menu_item_new_folder = gtk.MenuItem("Add folder ...")
	menu_item_new_folder.connect("activate", menu_item_clicked,  "<clickTrayMenuGuiInternalEvent><action>NEW</action></clickTrayMenuGuiInternalEvent>")
	
	menu.append(menu_item_new_folder)
	
	# Browse ...
	menu_item_new_folder = gtk.MenuItem("Browse history ...")
	menu_item_new_folder.connect("activate", menu_item_clicked,  "<clickTrayMenuGuiInternalEvent><action>BROWSE_HISTORY</action></clickTrayMenuGuiInternalEvent>")
	
	menu.append(menu_item_new_folder)		
	
	if recent_changes:
		# ---
		menu.append(gtk.SeparatorMenuItem())
	
		# Create submenu
		sub_menu_recent_changes = gtk.Menu()

		# Create submenu entries
		for afile in recent_changes:
			menu_item_recent_changes_file = gtk.MenuItem(os.path.basename(afile))
			menu_item_recent_changes_file.connect("activate", menu_item_recent_changes_file_clicked, afile)
			menu_item_recent_changes_file.set_tooltip_text(afile)
		
			sub_menu_recent_changes.append(menu_item_recent_changes_file)		
			
		# Create folder menu item
		menu_item_recent_changes = gtk.MenuItem("Recent changes")
		menu_item_recent_changes.set_submenu(sub_menu_recent_changes)
	
		menu.append(menu_item_recent_changes)			
	
	# ---
	menu.append(gtk.SeparatorMenuItem())	

	# Folders
	if request is not None:
		folders = request.xpath("//folder")
	
		for folder in folders:
			# Create submenu
			sub_menu_folder = gtk.Menu()
			
			menu_item_folder_open = gtk.MenuItem("Open folder")
			menu_item_folder_open.connect("activate", menu_item_folder_open_clicked, folder.text)
					
			menu_item_folder_copy_link = gtk.MenuItem("Copy link")
			menu_item_folder_copy_link.connect("activate", menu_item_folder_copy_link_clicked, folder.text)
		
			menu_item_folder_remove = gtk.MenuItem("Remove folder")
			menu_item_folder_remove.connect("activate", menu_item_folder_remove_clicked, folder.text)
		
			sub_menu_folder.append(menu_item_folder_open)		
			sub_menu_folder.append(menu_item_folder_copy_link)			
			sub_menu_folder.append(menu_item_folder_remove)
			
			# Create folder menu item
			menu_item_folder = gtk.MenuItem(os.path.basename(folder.text))
			menu_item_folder.set_submenu(sub_menu_folder)
		
			menu.append(menu_item_folder)			
		
		if len(folders) > 0:
			# ---
			menu.append(gtk.SeparatorMenuItem())	
	
	# Preferences
	menu_item_issue = gtk.MenuItem("Preferences ...")
	menu_item_issue.connect("activate", menu_item_clicked, "<clickTrayMenuGuiInternalEvent><action>PREFERENCES</action></clickTrayMenuGuiInternalEvent>")
	
	menu.append(menu_item_issue)	
	
	# ---
	menu.append(gtk.SeparatorMenuItem())
	
	# Report a bug
	menu_item_issue = gtk.MenuItem("Report a bug")
	menu_item_issue.connect("activate", menu_item_clicked, "<clickTrayMenuGuiInternalEvent><action>REPORT_ISSUE</action></clickTrayMenuGuiInternalEvent>")
	
	menu.append(menu_item_issue)	

	# Donate ...
	menu_item_donate = gtk.MenuItem("Buy us a coffee")
	menu_item_donate.connect("activate", menu_item_clicked,  "<clickTrayMenuGuiInternalEvent><action>DONATE</action></clickTrayMenuGuiInternalEvent>")
	
	menu.append(menu_item_donate)	
	
	# Website
	menu_item_website = gtk.MenuItem("Visit website")
	menu_item_website.connect("activate", menu_item_clicked, "<clickTrayMenuGuiInternalEvent><action>WEBSITE</action></clickTrayMenuGuiInternalEvent>")
	
	menu.append(menu_item_website)	
	
	# ---
	menu.append(gtk.SeparatorMenuItem())	

	# Quit
	menu_item_quit = gtk.MenuItem("Exit")
	menu_item_quit.connect("activate", menu_item_exit_clicked)
		
	menu.append(menu_item_quit)	
	
	# Set as menu for indicator
	indicator.set_menu(menu)

	# Show
	menu.show_all()
	gtk.gdk.threads_leave()
	
	return None

def init_menu():
	do_update_menu(None)

def init_tray_icon():
	global indicator

	# Default image
	image = fetch_image("/tray.png")									

	# Go!
	do_print("Initializing indicator ...")
	
	indicator = appindicator.Indicator("syncany", image, appindicator.CATEGORY_APPLICATION_STATUS)
	indicator.set_status(appindicator.STATUS_ACTIVE)
	indicator.set_attention_icon("indicator-messages-new")	
	
def menu_item_clicked(widget, message):
	do_print("Menu item '" + message + "' clicked.")
	ws.send(message)

def menu_item_exit_clicked(widget):
	message = "<clickTrayMenuGuiInternalEvent><action>EXIT</action></clickTrayMenuGuiInternalEvent>"

	do_print("Menu item EXIT clicked (Sending '" + message + "')")
	ws.send(message)
	
	delete_images()
	time.sleep(2)
	sys.exit(0)

def menu_item_recent_changes_file_clicked(widget, afile):
	do_print("Opening file/folder '" + afile + "' ...")
	ws.send("<clickRecentChangesGuiInternalEvent><file>" + afile + "</file></clickRecentChangesGuiInternalEvent>")

def menu_item_folder_open_clicked(widget, folder):
	do_print("Opening folder '" + folder + "' ...")
	ws.send("<clickTrayMenuFolderGuiInternalEvent><action>OPEN</action><folder>" + folder + "</folder></clickTrayMenuFolderGuiInternalEvent>")

def menu_item_folder_copy_link_clicked(widget, folder):
	do_print("Copying link for folder '" + folder + "' ...")
	ws.send("<clickTrayMenuFolderGuiInternalEvent><action>COPY_LINK</action><folder>" + folder + "</folder></clickTrayMenuFolderGuiInternalEvent>")

def menu_item_folder_remove_clicked(widget, folder):
	do_print("Removing folder '" + folder + "' ...")
	ws.send("<clickTrayMenuFolderGuiInternalEvent><action>REMOVE</action><folder>" + folder + "</folder></clickTrayMenuFolderGuiInternalEvent>")

def do_kill():
	# Note: this method cannot contain any do_print() calls since it is called
	#       by do_print if the STDOUT socket breaks!
	
	pid = os.getpid()
	os.system("kill -9 {0}".format(pid))
	
def do_print(msg):
	try:
		sys.stdout.write("{0}\n".format(msg))
		sys.stdout.flush()
	except:
		# An IOError happens when the calling process is killed unexpectedly		
		do_kill()			

def on_ws_message(ws, message):	
	try:
		do_print("Received request: " + message)				

		messageTypeMatch = re.search('^<([^>]+)', message)
		messageType = messageTypeMatch.group(1)

		request = etree.XML(message)
		response = None		
		
		last_request = time.time()
		
		if messageType == "displayNotificationGuiInternalEvent":
			response = do_notify(request)
		
		elif messageType == "updateWatchesGuiInternalEvent":
			response = do_update_menu(request)
		
		elif messageType == "updateTrayIconGuiInternalEvent":
			response = do_update_icon(request)
		
		elif messageType == "updateStatusTextGuiInternalEvent":
			response = do_update_text(request)

		elif messageType == "updateRecentChangesGuiInternalEvent":
			response = do_update_recent_changes(request)
			
		else:
			do_print("UNKNOWN MESSAGE. IGNORING.")			
		
	except:	
		do_print(sys.exc_info())
		do_print("Unexpected error: {0}".format(sys.exc_info()[0]))

	if response is not None:
		ws.send(response)

def on_ws_error(ws, error):
	do_print("WS error")
	do_print(error)

def on_ws_close(ws):
	do_print("WS closed")
	do_kill()

def on_ws_open(ws):
	do_print("WS open")

def ws_start_client():
	global ws, ws_url

	do_print("Connecting to Web Socket " + ws_url)
	
	ws = websocket.WebSocketApp(ws_url,
		on_message = on_ws_message,
		on_error = on_ws_error,
		on_close = on_ws_close,
	)

	ws.on_open = on_ws_open

	ws.run_forever()
	
def delete_images():
	global images_map
	
	for relative_url, temp_file in images_map.iteritems():
		try:
			os.remove(temp_file)
		except:
			pass

def main():
	init_tray_icon()
	init_menu()	
	
	ws_server_thread = threading.Thread(target=ws_start_client)
	ws_server_thread.setDaemon(True)
	ws_server_thread.start()

	gtk.gdk.threads_init()
	gtk.gdk.threads_enter()
	gtk.main()	

if __name__ == "__main__":
	# Global variables
	images_map = dict()
	status_text_default = "All files in sync"
	status_texts = dict()
	recent_changes = []
	watches_last_request = None
	
	indicator = None
	ws = None
	ws_server_thread = None
	terminated = 0
		
	# Default values
	menu = gtk.Menu()

	# Go!
	main()
