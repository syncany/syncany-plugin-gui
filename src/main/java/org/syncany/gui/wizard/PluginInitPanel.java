package org.syncany.gui.wizard;

import java.io.File;
import java.util.List;

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
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.SWTResourceManager;
import org.syncany.plugins.Plugin;
import org.syncany.plugins.Plugins;
import org.syncany.plugins.transfer.StorageException;
import org.syncany.plugins.transfer.TransferPlugin;
import org.syncany.plugins.transfer.TransferPluginOption;
import org.syncany.plugins.transfer.TransferPluginOptions;
import org.syncany.plugins.transfer.TransferSettings;

/**
 * @author Vincent Wiencek <vwiencek@gmail.com>
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class PluginInitPanel extends Panel {
	public enum SelectFolderValidationMethod {
		NO_APP_FOLDER, APP_FOLDER
	};

	//private Map<TransferPluginOption, Text localDir;
	private Label descriptionLabel;
	
	private Label warningImageLabel;
	private Label warningMessageLabel;

	private SelectFolderValidationMethod validationMethod;
	private boolean firstValidationDone;
	
	public PluginInitPanel(WizardDialog wizardParentDialog, Composite parent, int style) {
		super(wizardParentDialog, parent, style);
		
		this.createControls();
		this.firstValidationDone = false;
	}
				
	private void createControls() {
		
		try {
			TransferPlugin plugin = Plugins.get("s3", TransferPlugin.class);
			TransferSettings transferSettings = plugin.createEmptySettings();
			List<TransferPluginOption> pluginOptions = TransferPluginOptions.getOrderedOptions(transferSettings.getClass());
			
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
			titleLabel.setText("Amazon S3 settings");
			
			WidgetDecorator.title(titleLabel);

			descriptionLabel = new Label(this, SWT.WRAP);
			descriptionLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false, 3, 1));
			descriptionLabel.setText("..");

			WidgetDecorator.normal(descriptionLabel);
			
			for (TransferPluginOption pluginOption : pluginOptions) {
				// Label "Folder:"
				GridData selectFolderLabel = new GridData(SWT.LEFT, SWT.CENTER, false, false);
				selectFolderLabel.verticalIndent = WidgetDecorator.VERTICAL_INDENT/2;
				selectFolderLabel.horizontalSpan = 3;

				Label optionLabel = new Label(this, SWT.WRAP);
				optionLabel.setLayoutData(selectFolderLabel);
				optionLabel.setText(pluginOption.getDescription());
				
				// Textfield "Folder"
				GridData optionValueTextGridData = new GridData(SWT.FILL, SWT.CENTER, true, false);
				optionValueTextGridData.verticalIndent = 0;
				optionValueTextGridData.horizontalSpan = 2;
				optionValueTextGridData.minimumWidth = 200;

				Text optionValueText = new Text(this, SWT.BORDER);
				optionValueText.setLayoutData(optionValueTextGridData);
				optionValueText.setBackground(WidgetDecorator.WHITE);
				optionValueText.addModifyListener(new ModifyListener() {			
					@Override
					public void modifyText(ModifyEvent e) {
						if (firstValidationDone) {
							validatePanel();
						}
					}
				});
				
				WidgetDecorator.normal(optionValueText);
			}

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
		catch (StorageException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
	}
	
	public void setValidationMethod(SelectFolderValidationMethod validationMethod) {
		this.validationMethod = validationMethod;
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
		firstValidationDone = true;
		
		switch (validationMethod) {
		case APP_FOLDER:
			return true;
			
		case NO_APP_FOLDER:
			return false;
			
		default:
			throw new RuntimeException("Invalid validation method: " + validationMethod);				
		}
	}
	
	private void showWarning(String warningStr) {
		warningImageLabel.setVisible(true);
		warningMessageLabel.setVisible(true);			
		warningMessageLabel.setText(warningStr);		
	}
	
	private void hideWarning() {
		warningImageLabel.setVisible(false);
		warningMessageLabel.setVisible(false);
	}
}
