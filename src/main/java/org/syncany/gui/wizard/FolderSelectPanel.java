package org.syncany.gui.wizard;

import static org.syncany.gui.util.I18n._;

import java.io.File;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.syncany.config.Config;
import org.syncany.gui.util.SWTResourceManager;

/**
 * @author Vincent Wiencek <vwiencek@gmail.com>
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class FolderSelectPanel extends Panel {
	public enum SelectFolderValidationMethod {
		NO_APP_FOLDER, APP_FOLDER
	};

	private Text localDir;
	private Label descriptionLabel;
	
	private Label warningImageLabel;
	private Label warningMessageLabel;

	private SelectFolderValidationMethod validationMethod;
	private boolean firstValidationDone;

	public FolderSelectPanel(WizardDialog wizardParentDialog, Composite parent, int style) {
		super(wizardParentDialog, parent, style);
		
		this.createControls();
		this.firstValidationDone = false;
	}
				
	private void createControls() {
		// Main composite
		GridLayout mainCompositeGridLayout = new GridLayout(3, false);
		mainCompositeGridLayout.marginTop = 15;
		mainCompositeGridLayout.marginLeft = 10;
		mainCompositeGridLayout.marginRight = 20;

		setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));		
		setLayout(mainCompositeGridLayout);
	
		// Title and description
		Label titleLabel = new Label(this, SWT.WRAP);
		titleLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 3, 1));
		titleLabel.setText(_("org.syncany.gui.wizard.FolderSelectPanel.title"));
		
		WidgetDecorator.title(titleLabel);

		descriptionLabel = new Label(this, SWT.WRAP);
		descriptionLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false, 3, 1));

		WidgetDecorator.normal(descriptionLabel);

		// Label "Folder:"
		GridData selectFolderLabelGridDate = new GridData(SWT.LEFT, SWT.CENTER, false, false);
		selectFolderLabelGridDate.verticalIndent = WidgetDecorator.VERTICAL_INDENT;
		selectFolderLabelGridDate.horizontalSpan = 3;

		Label selectFolderLabel = new Label(this, SWT.WRAP);
		selectFolderLabel.setLayoutData(selectFolderLabelGridDate);
		selectFolderLabel.setText(_("org.syncany.gui.wizard.FolderSelectPanel.selectFolderLabel"));
		
		// Textfield "Folder"
		GridData folderTextGridData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		folderTextGridData.verticalIndent = 0;
		folderTextGridData.horizontalSpan = 2;
		folderTextGridData.minimumWidth = 200;

		localDir = new Text(this, SWT.BORDER);
		localDir.setLayoutData(folderTextGridData);
		localDir.setBackground(WidgetDecorator.WHITE);

		localDir.addModifyListener(new ModifyListener() {			
			@Override
			public void modifyText(ModifyEvent e) {
				if (firstValidationDone) {
					validatePanel(false);
				}
			}
		});
		
		WidgetDecorator.normal(localDir);

		// Button "Select ..."
		Button selectFolderButton = new Button(this, SWT.FLAT);
		selectFolderButton.setText(_("org.syncany.gui.wizard.FolderSelectPanel.selectLocalFolderButton"));
		selectFolderButton.setLayoutData(new GridData(SWT.LEFT, SWT.BOTTOM, false, false, 1, 1));
		selectFolderButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				onSelectFolderClick();
			}
		});
		
		WidgetDecorator.normal(selectFolderLabel);

		// Warning message and label
		String warningImageResource = "/" + WizardDialog.class.getPackage().getName().replace(".", "/") + "/warning-icon.png";
		Image warningImage = SWTResourceManager.getImage(warningImageResource);

		warningImageLabel = new Label(this, SWT.NONE);
		warningImageLabel.setImage(warningImage);
		warningImageLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));
		warningImageLabel.setVisible(false);

		warningMessageLabel = new Label(this, SWT.WRAP);
		warningMessageLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 2, 1));
		warningMessageLabel.setVisible(false);
		
		WidgetDecorator.bold(warningMessageLabel);
	}
	
	public void reset(SelectFolderValidationMethod validationMethod) {
		this.validationMethod = validationMethod;
		
		firstValidationDone = false;		
		localDir.setText("");		
		
		hideWarning();
	}
	
	public void setDescriptionText(final String descriptionTextStr) {
		Display.getDefault().asyncExec(new Runnable() {			
			@Override
			public void run() {
				if (!descriptionLabel.isDisposed()) {
					descriptionLabel.setText(descriptionTextStr);
				}
				
				layout();
			}
		});
	}
	
	@Override
	public boolean validatePanel() {
		return validatePanel(true);		
	}

	private boolean validatePanel(boolean allowAskFolderCreate) {
		firstValidationDone = true;
		
		switch (validationMethod) {
		case APP_FOLDER:
			return isValidAppFolder();
			
		case NO_APP_FOLDER:
			return isValidNoAppFolder(allowAskFolderCreate);
			
		default:
			throw new RuntimeException("Invalid validation method: " + validationMethod);				
		}
	}

	private boolean isValidNoAppFolder(boolean allowAskFolderCreate) {
		File selectedDir = new File(localDir.getText());
		File appDir = new File(selectedDir, Config.DIR_APPLICATION);

		if (localDir.getText().isEmpty()) {
			showWarning(_("org.syncany.gui.wizard.FolderSelectPanel.errorInvalidFolder"));
			WidgetDecorator.markAsInvalid(localDir);
			
			return false;			
		}
		else if (appDir.exists()) {
			showWarning(_("org.syncany.gui.wizard.FolderSelectPanel.errorInitializedFolder"));
			WidgetDecorator.markAsInvalid(localDir);

			return false;
		}
		else {
			if (!selectedDir.isDirectory()) {
				boolean allowCreate = allowAskFolderCreate && askCreateFolder(getShell(), selectedDir);
				
				if (allowCreate) {
					if (selectedDir.mkdirs()) {
						hideWarning();
						WidgetDecorator.markAsValid(localDir);
						
						return true;
					}
					else {
						showWarning(_("org.syncany.gui.wizard.FolderSelectPanel.errorCannotCreateFolder"));
						WidgetDecorator.markAsInvalid(localDir);
						
						return false;
					}
				}
				else {
					hideWarning();
					WidgetDecorator.markAsValid(localDir);

					return false;
				}
			}
			else {
				hideWarning();
				WidgetDecorator.markAsValid(localDir);
				
				return true;
			}
		}		
	}

	private boolean isValidAppFolder() {
		File selectedDir = new File(localDir.getText());
		File appDir = new File(selectedDir, Config.DIR_APPLICATION);		
		
		if (appDir.exists()) {
			hideWarning();
			return true;
		}
		else {
			showWarning(_("org.syncany.gui.wizard.FolderSelectPanel.errorNoValidFolder"));
			return false;			
		}
	}
	
	private void showWarning(String warningStr) {
		warningImageLabel.setVisible(true);
		warningMessageLabel.setVisible(true);			
		warningMessageLabel.setText(warningStr);
		
		WidgetDecorator.markAsInvalid(localDir);
	}
	
	private void hideWarning() {
		warningImageLabel.setVisible(false);
		warningMessageLabel.setVisible(false);

		WidgetDecorator.markAsValid(localDir);
	}

	private boolean askCreateFolder(Shell shell, File selectedDir) {
		MessageBox dialog = new MessageBox(shell, SWT.ICON_QUESTION | SWT.OK | SWT.CANCEL);
		
		dialog.setText(_("org.syncany.gui.wizard.FolderSelectPanel.askCreate.title"));
		dialog.setMessage(_("org.syncany.gui.wizard.FolderSelectPanel.askCreate.description", selectedDir.getAbsolutePath()));

		int returnCode = dialog.open();

		if (returnCode == SWT.OK) {
			return true;
		}
		
		return false;
	}
	
	private void onSelectFolderClick() {
		DirectoryDialog directoryDialog = new DirectoryDialog(getShell());
		directoryDialog.setFilterPath(localDir.getText());
		
		String selectedFolder = directoryDialog.open();

		if (selectedFolder != null && selectedFolder.length() > 0) {
			localDir.setText(selectedFolder);
			validatePanel();
		}
	}

	public File getFolder() {
		return new File(localDir.getText());
	}
}
