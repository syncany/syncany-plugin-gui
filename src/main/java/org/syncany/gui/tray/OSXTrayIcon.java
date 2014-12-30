package org.syncany.gui.tray;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.syncany.config.UserConfig;
import org.syncany.gui.util.DesktopUtil;
import com.google.common.collect.Sets;
import net.lingala.zip4j.core.ZipFile;

/**
 * In contract to see {@link org.syncany.gui.tray.DefaultTrayIcon}, this tray icon uses growl notifications to notify
 * of changes. This is a more convienent way on OS X.
 * <p>
 * <p>Only supported by Mac OS X 10.9+</p>
 *
 * @author Christian Roth <christian.roth@port17.de>
 */

public class OSXTrayIcon extends DefaultTrayIcon {
	private static final Logger logger = Logger.getLogger(OSXTrayIcon.class.getSimpleName());

	private static final File NOTIFICATION_HELPER_LOCATION = UserConfig.getUserPluginLibDir();
	private static final File TERMINAL_NOTIFIER_BINARY = new File(NOTIFICATION_HELPER_LOCATION, "syncany-notifier.app/Contents/MacOS/syncany-notifier");
	private static final String NOTIFICATION_HELPER_REMOTE = "https://www.dropbox.com/s/z6czxydzdxmdxkw/syncany-notifier.app.zip?dl=1";
	private static final String NOTIFICATION_HELPER_SHA256_HEX = "c0ed3770f6798c7fb86290ed50889e96783e3a5d2c83b62e54ed9161de9cdfda";

	private boolean USE_FALLBACK_NOTIFICATION = false;
	private Process utilityProcess;

	public OSXTrayIcon(Shell shell, TrayIconTheme theme) {
		super(shell, theme);

		logger.log(Level.INFO, "Extracting required helper tools...");

		try {
			if (!validateHelperUtility()) {
				logger.log(Level.INFO, "Notification helper util does not exist, needing to download");

				MessageBox dialog = new MessageBox(shell, SWT.ICON_WARNING | SWT.YES | SWT.NO | SWT.CANCEL);
				dialog.setText("Helper utility missing");
				dialog.setMessage("The OSX tray icon requires you to install a little helper utility. Do you want to download it now?\nSelect 'NO' if you want to compile the utility on your own.");
				int selected = dialog.open();

				switch (selected) {
					case SWT.YES:
						downloadAndExtractHelperUtility();
						displayNotification("Download complete", "The helper utility was successfully installed.");
						break;

					case SWT.NO:
						openWikiPage();
						USE_FALLBACK_NOTIFICATION = true;
						break;

					case SWT.CANCEL:
						MessageBox abort = new MessageBox(shell, SWT.ICON_CANCEL | SWT.OK);
						abort.setText("Helper utility missing");
						abort.setMessage("Since the helper utility is not installed, all further notifications are now being delivered using the generic behavior.");
						abort.open();
						USE_FALLBACK_NOTIFICATION = true;
						break;
				}
			}
		}
		catch (Exception e) {
			throw new RuntimeException("Unable to start", e);
		}

		if (!USE_FALLBACK_NOTIFICATION) {
			if (validateHelperUtility()) {
				logger.log(Level.INFO, "Using {0} to deliver notifications", new Object[]{NOTIFICATION_HELPER_LOCATION});
			}
			else {
				logger.log(Level.WARNING, "Unable to start helper utility, falling back to standard behavior");
				USE_FALLBACK_NOTIFICATION = true;
			}
		}

	}

	@Override
	protected void displayNotification(final String subject, final String message) {
		if (USE_FALLBACK_NOTIFICATION) {
			super.displayNotification(subject, message);
			return;
		}

		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				List<String> command = new ArrayList<String>();
				command.add(TERMINAL_NOTIFIER_BINARY.toString());
				command.add("-subject");
				command.add(subject);
				command.add("-message");
				command.add(message);

				try {
					Runtime.getRuntime().exec(command.toArray(new String[command.size()]));
				}
				catch (IOException e) {
					throw new RuntimeException("Unable to notify using " + NOTIFICATION_HELPER_LOCATION, e);
				}
			}
		});
	}

	private void downloadAndExtractHelperUtility() throws Exception {
		logger.log(Level.INFO, "Download helper utility from {0} to {1}", new Object[]{NOTIFICATION_HELPER_REMOTE, NOTIFICATION_HELPER_LOCATION});

		// download
		final URL ressourceURL = new URL(NOTIFICATION_HELPER_REMOTE);
		final File temporaryZipFile = File.createTempFile("syncany-notifier", ".zip");
		temporaryZipFile.deleteOnExit();

		FileUtils.copyURLToFile(ressourceURL, temporaryZipFile);
		logger.log(Level.INFO, "Download finished");

		// checksums
		final byte[] checksumDownloadedFile = DigestUtils.sha256(FileUtils.readFileToByteArray(temporaryZipFile));

		if (!Arrays.equals(checksumDownloadedFile, Hex.decodeHex(NOTIFICATION_HELPER_SHA256_HEX.toCharArray()))) {
			String actualHex = Hex.encodeHexString(checksumDownloadedFile);

			logger.log(Level.SEVERE, "Checksum did not match: expected {0}, actual {1}",
							new Object[]{NOTIFICATION_HELPER_SHA256_HEX, actualHex});

			throw new SecurityException("Checksum did not match: expected " + NOTIFICATION_HELPER_SHA256_HEX + ", actual " + actualHex);
		}

		// extract
		logger.log(Level.INFO, "Checksums match, unzipping...");

		if (!NOTIFICATION_HELPER_LOCATION.exists()) {
			NOTIFICATION_HELPER_LOCATION.mkdirs();
		}

		new ZipFile(temporaryZipFile).extractAll(NOTIFICATION_HELPER_LOCATION.toString());
		temporaryZipFile.delete();

		String terminalNotifierExtractedBinary = TERMINAL_NOTIFIER_BINARY.toString();
		Set<PosixFilePermission> perms = Sets.newHashSet(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
		Files.setPosixFilePermissions(Paths.get(terminalNotifierExtractedBinary), perms);

		logger.log(Level.INFO, "Notification utility extracted successfully");
	}

	private boolean validateHelperUtility() {
		return TERMINAL_NOTIFIER_BINARY.exists();
	}

	private void openWikiPage() {
		DesktopUtil.launch("https://github.com/syncany/syncany/wiki");
	}

}
