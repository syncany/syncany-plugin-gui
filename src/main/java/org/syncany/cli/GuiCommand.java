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
package org.syncany.cli;

import static java.util.Arrays.asList;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import org.syncany.gui.tray.TrayIconType;
import org.syncany.operations.OperationResult;
import org.syncany.operations.gui.GuiOperation;
import org.syncany.operations.gui.GuiOperationOptions;

/**
 * This command is used to start the graphical user interface (including
 * the tray icon) from the command line. 
 * 
 * @author Vincent Wiencek <vwiencek@gmail.com>
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class GuiCommand extends Command {
	public static void main(String[] args) throws Exception {
		System.exit(new GuiCommand().execute(args));		
	}	
	
	@Override
	public int execute(String[] operationArgs) throws Exception {
		GuiOperationOptions operationOptions = parseOptions(operationArgs);

		GuiOperation operation = new GuiOperation(operationOptions);
		operation.execute();
		
		return 0;
	}

	@Override
	public CommandScope getRequiredCommandScope() {
		return CommandScope.ANY;
	}

	@Override
	public boolean canExecuteInDaemonScope() {
		return false;
	}

	@Override
	public GuiOperationOptions parseOptions(String[] operationArgs) throws Exception {
		GuiOperationOptions operationOptions = new GuiOperationOptions();

		OptionParser parser = new OptionParser();	
		parser.allowsUnrecognizedOptions();
		
		OptionSpec<String> optionTray = parser.acceptsAll(asList("t", "tray")).withRequiredArg();
		
		OptionSet options = parser.parse(operationArgs);	
		
		// --tray
		if (options.has(optionTray)) {
			TrayIconType trayType = TrayIconType.valueOf(options.valueOf(optionTray).toUpperCase());
			operationOptions.setTrayType(trayType);
		}
		
		return operationOptions;
	}

	@Override
	public void printResults(OperationResult result) {
		// Nothing
	}
}
