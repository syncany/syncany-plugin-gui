package org.syncany.gui.wizard;

import java.io.File;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.syncany.config.Config;
import org.syncany.gui.util.I18n;

/**
 * @author Vincent Wiencek <vwiencek@gmail.com>
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class SelectFolderPanel extends WizardPanel {
	public enum SelectFolderValidationMethod {
		NO_APP_FOLDER, APP_FOLDER
	};

	private Text localDir;
	private Label introductionLabel;
	private Label messageLabel;

	private SelectFolderValidationMethod validationMethod;

	public SelectFolderPanel(WizardDialog wizardParentDialog, Composite parent, int style) {
		super(wizardParentDialog, parent, style);
		
		GridLayout gridLayoutComposite = new GridLayout(3, false);

		setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		setLayout(gridLayoutComposite);

		Label introductionTitleLabel = new Label(this, SWT.WRAP);
		introductionTitleLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 4, 1));
		introductionTitleLabel.setText(I18n.getString("dialog.selectLocalFolder.introduction.title"));

		introductionLabel = new Label(this, SWT.WRAP);
		introductionLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
		introductionLabel.setText(I18n.getString("dialog.selectLocalFolder.introduction"));

		GridData gridDataHostLabel = new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1);
		gridDataHostLabel.verticalIndent = WidgetDecorator.VERTICAL_INDENT;

		Label hostLabel = new Label(this, SWT.WRAP);
		hostLabel.setLayoutData(gridDataHostLabel);
		hostLabel.setText(I18n.getString("dialog.selectLocalFolder.selectLocalFolder", true));

		GridData gridDataHostText = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
		gridDataHostText.verticalIndent = WidgetDecorator.VERTICAL_INDENT;
		gridDataHostText.minimumWidth = 200;

		localDir = new Text(this, SWT.BORDER);
		localDir.setLayoutData(gridDataHostText);

		Button selectFolderButton = new Button(this, SWT.FLAT);
		selectFolderButton.setText("Select Folder ...");
		selectFolderButton.setLayoutData(new GridData(SWT.LEFT, SWT.BOTTOM, false, false, 1, 1));
		selectFolderButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				onSelectFolderClick();
			}
		});

		messageLabel = new Label(this, SWT.WRAP);
		messageLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 3, 1));

		WidgetDecorator.bold(introductionTitleLabel);
		WidgetDecorator.normal(introductionLabel, localDir, hostLabel, messageLabel);
	}
	
	public void setValidationMethod(SelectFolderValidationMethod validationMethod) {
		this.validationMethod = validationMethod;
	}
	
	@Override
	public boolean isValid() {
		switch (validationMethod) {
		case APP_FOLDER:
			return isValidAppFolder();
			
		case NO_APP_FOLDER:
			return isValidNoAppFolder();
			
		default:
			throw new RuntimeException("Invalid validation method: " + validationMethod);				
		}
	}

	private boolean isValidNoAppFolder() {
		File selectedDir = new File(localDir.getText());
		File appDir = new File(selectedDir, Config.DIR_APPLICATION);

		if (appDir.exists()) {
			WidgetDecorator.markAs(false, localDir);
			return false;
		}
		else {
			if (!selectedDir.isDirectory()) {
				boolean allowCreate = askCreateFolder(getShell(), selectedDir);
				WidgetDecorator.markAs(allowCreate, localDir);
				
				if (allowCreate) {
					if (selectedDir.mkdirs()) {
						WidgetDecorator.markAs(true, localDir);
						return true;
					}
					else {
						WidgetDecorator.markAs(false, localDir);
						return false;
					}
				}
				else {
					WidgetDecorator.markAs(false, localDir);
					return false;
				}
			}
			else {
				WidgetDecorator.markAs(true, localDir);
				return true;
			}
		}		
	}

	private boolean isValidAppFolder() {
		File selectedDir = new File(localDir.getText());
		File appDir = new File(selectedDir, Config.DIR_APPLICATION);

		boolean isValid = appDir.exists();
			
		WidgetDecorator.markAs(isValid, localDir);
		return isValid;
	}

	private boolean askCreateFolder(Shell shell, File selectedDir) {
		MessageBox dialog = new MessageBox(shell, SWT.ICON_QUESTION | SWT.OK | SWT.CANCEL);
		
		dialog.setText("Create Folder");
		dialog.setMessage(String.format("Would you like to create the folder [%s]?", selectedDir.getAbsolutePath()));

		int returnCode = dialog.open();

		if (returnCode == SWT.OK) {
			return true;
		}
		
		return false;
	}
	
	private void onSelectFolderClick() {
		DirectoryDialog directoryDialog = new DirectoryDialog(getShell());
		String selectedFolder = directoryDialog.open();

		if (selectedFolder != null && selectedFolder.length() > 0) {
			localDir.setText(selectedFolder);
		}
	}

	@Override
	public SelectFolderPanelState getState() {
		return new SelectFolderPanelState(new File(localDir.getText()));
	}

	public class SelectFolderPanelState implements PanelState {
		private File folder;

		public SelectFolderPanelState(File folder) {
			this.folder = folder;
		}

		public File getFolder() {
			return folder;
		}
	}
}
