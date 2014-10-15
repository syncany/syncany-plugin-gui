package org.syncany.gui;

import org.syncany.cli.GuiCommand;

public class Launcher {
	public static void main(String[] args) throws Exception {
		System.exit(new GuiCommand().execute(args));		
	}	
} 