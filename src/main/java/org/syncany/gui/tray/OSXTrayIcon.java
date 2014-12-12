package org.syncany.gui.tray;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import com.google.common.collect.Sets;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;

/**
 * In contract to see {@link org.syncany.gui.tray.DefaultTrayIcon}, this tray icon uses growl notifications to notify
 * of changes. This is a more convienent way on OS X.
 *
 * <p>Only supported by Mac OS X 10.9+</p>
 *
 * @author Christian Roth <christian.roth@port17.de>
 */

public class OSXTrayIcon extends DefaultTrayIcon {
	private static final Logger logger = Logger.getLogger(OSXTrayIcon.class.getSimpleName());

	private final static String TERMINAL_NOTIFIER_PACKAGE = "/org/syncany/gui/helper/syncany-notifier.app.zip";
	private final static String TERMINAL_NOTIFIER_BINARY = "/syncany-notifier.app/Contents/MacOS/syncany-notification";

	private final Path terminalNotifierExtracted;
	private final String terminalNotifierExtractedBinary;

	public OSXTrayIcon(Shell shell, TrayIconTheme theme) {
		super(shell, theme);

		try {
			logger.log(Level.INFO, "Extracting required helper tools...");

			// create a target for the app
			terminalNotifierExtracted = Files.createTempDirectory("syncany-notifier");
			terminalNotifierExtracted.toFile().deleteOnExit();

			if (logger.isLoggable(Level.FINE)) {
				logger.log(Level.FINE, "Extracting syncany-notifier to {0}", new Object[]{terminalNotifierExtracted});
			}

			// extract the compressed notifier
			File temporaryZipFile = File.createTempFile("syncany-notifier", ".zip");
			FileUtils.copyInputStreamToFile(getClass().getResourceAsStream(TERMINAL_NOTIFIER_PACKAGE), temporaryZipFile);
			new ZipFile(temporaryZipFile).extractAll(terminalNotifierExtracted.toString());

			terminalNotifierExtractedBinary = terminalNotifierExtracted.toString() + "/" + TERMINAL_NOTIFIER_BINARY;
			Set<PosixFilePermission> perms = Sets.newHashSet(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
			Files.setPosixFilePermissions(Paths.get(terminalNotifierExtractedBinary), perms);
			logger.log(Level.INFO, "Using {0} to deliver notifications", new Object[]{terminalNotifierExtractedBinary});

			temporaryZipFile.delete();

			logger.log(Level.INFO, "Done Extracting helper tools");

			displayNotification("Setup complete", "Waiting for messages...");
		}
		catch(IOException | ZipException e) {
			throw new RuntimeException("Unable to extract required helpers", e);
		}
	}

	@Override
	protected void displayNotification(final String subject, final String message) {
		logger.log(Level.INFO, "NOTIFCATION");
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				List<String> command = new ArrayList<String>();
				command.add(terminalNotifierExtractedBinary);
				command.add("-title");
				command.add(subject);
				command.add("-message");
				command.add(message);

				try {
					Runtime.getRuntime().exec(command.toArray(new String[command.size()]));
				} catch (IOException e) {
					throw new RuntimeException("Unable to notify using " + terminalNotifierExtractedBinary, e);
				}
			}
		});
	}

}
