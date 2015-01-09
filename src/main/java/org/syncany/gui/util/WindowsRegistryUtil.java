/*
 * Syncany, www.syncany.org
 * Copyright (C) 2011-2015 Philipp C. Heckel <philipp.heckel@gmail.com> 
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
package org.syncany.gui.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WindowsRegistryUtil {	

	/**
	 * Reads a value from the Windows registry (only string type, REG_SZ).
	 * 
	 * <p>This method executes the following command (example):
	 * <pre>
	 * $>reg query HKCU\Software\Microsoft\Windows\CurrentVersion\Run /v Syncany /t REG_SZ
	 * 
	 * HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Run
	 *    Syncany    REG_SZ    <value>
	 *    
	 * End of search: 1 match(es) found.
	 * </pre>
	 * 
	 * @throws IOException  
	 */
    public static final String readString(String path, String key) throws IOException {
    	// Build command
		List<String> command = new ArrayList<>();
	    command.add("reg");
	    command.add("query");
	    command.add(path);
	    command.add("/t");
	    command.add("REG_SZ");

	    // Run it
	    try {
		    Process regProcess = Runtime.getRuntime().exec(command.toArray(new String[0]));
			BufferedReader regOutputReader = new BufferedReader(new InputStreamReader(regProcess.getInputStream()));
			
			// And parse the output
			Pattern outputMatchPattern = Pattern.compile("\\s+" + key + "\\s+REG_SZ\\s+(.+)");
		    
			String result = null;
			String line = null;
		    
		    while ((line = regOutputReader.readLine()) != null) {
		        Matcher outputLineMatcher = outputMatchPattern.matcher(line);
	        
		        if (outputLineMatcher.find()) {
		        	result = outputLineMatcher.group(1);
		        }
		    }		
		    
		    throwAwayStream(regProcess.getErrorStream());
	    	regProcess.waitFor();
		    
		    return result;
	    }
	    catch (InterruptedException e) {
	    	throw new IOException(e);
	    }
    }
    
	/**
	 * Deletes a registry key from the Windows registry.
	 * 
	 * <p>This method executes the following command (example):
	 * <pre>
	 * $> reg delete /f HKCU\Software\Microsoft\Windows\CurrentVersion\Run /v Syncany
	 * The operation completed successfully.
	 * </pre>
	 * 
	 * @throws IOException 
	 */
	public static final void deleteKey(String path, String key) throws IOException {
		Objects.requireNonNull(path);
		
		// Build command
		List<String> command = new ArrayList<>();
	    command.add("reg");
	    command.add("delete");
	    command.add(path);
	    
	    if (key != null) {
	    	command.add("/v");
	    	command.add(key);
	    }
	    else {
	    	command.add("/ve");
	    }

	    command.add("/f");

	    // And run it
	    try {
	    	Process regProcess = Runtime.getRuntime().exec(command.toArray(new String[0]));
	    	
	    	throwAwayStream(regProcess.getInputStream());
	    	throwAwayStream(regProcess.getErrorStream());
	    				
	    	regProcess.waitFor();
	    }
	    catch (InterruptedException e) {
	    	throw new IOException(e);
	    }	    		    
	}	
	
	/**
	 * Writes a registry key to the Windows registry (only string type, REG_SZ).
	 * 
	 * <p>This method executes the following command (example):
	 * <pre>
	 * $> reg add /f HKCU\Software\Microsoft\Windows\CurrentVersion\Run /v Syncany /t REG_SZ /d "<path>"
	 * </pre>
	 * 
	 * @throws InterruptedException 
	 * @throws IOException 
	 */
	public static final void writeString(String path, String key, String value) throws IOException {
		Objects.requireNonNull(path, value);
		
		// Build command
		List<String> command = new ArrayList<>();
	    command.add("reg");
	    command.add("add");
	    command.add(path);
	    
	    if (key != null) {
	    	command.add("/v");
	    	command.add(key);
	    }
	    
	    command.add("/t");
	    command.add("REG_SZ");
	    command.add("/d");
	    command.add(value);
	    command.add("/f");

	    // And run it
	    try {
	    	Process regProcess = Runtime.getRuntime().exec(command.toArray(new String[0]));
	    	
	    	throwAwayStream(regProcess.getInputStream());
	    	throwAwayStream(regProcess.getErrorStream());
	    				
	    	regProcess.waitFor();
	    }
	    catch (InterruptedException e) {
	    	throw new IOException(e);
	    }	    		    
	}	


    private static void throwAwayStream(InputStream inputStream) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
		
		while ((reader.readLine()) != null) {
			// Do nothing.
		}
	}    
}