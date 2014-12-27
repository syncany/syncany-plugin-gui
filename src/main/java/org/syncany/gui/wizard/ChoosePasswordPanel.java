package org.syncany.gui.wizard;

import static org.syncany.gui.util.I18n._;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.syncany.gui.util.SWTResourceManager;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class ChoosePasswordPanel extends Panel {
	private Text passwordText;
	private Text confirmText;
	
	private Label warningImageLabel;
	private Label warningMessageLabel;
	
	private boolean firstValidationDone;

	public ChoosePasswordPanel(WizardDialog wizardParentDialog, Composite parent, int style) {
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
		titleLabel.setText(_("org.syncany.gui.wizard.ChoosePasswordPanel.title"));
		
		WidgetDecorator.title(titleLabel);

		Label descriptionLabel = new Label(this, SWT.WRAP);
		descriptionLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false, 3, 1));
		descriptionLabel.setText(_("org.syncany.gui.wizard.ChoosePasswordPanel.description"));

		WidgetDecorator.normal(descriptionLabel);

		// Label "Password:"
		GridData passwordLabelGridData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
		passwordLabelGridData.verticalIndent = WidgetDecorator.VERTICAL_INDENT;
		passwordLabelGridData.horizontalSpan = 3;

		Label passwordLabel = new Label(this, SWT.WRAP);
		passwordLabel.setLayoutData(passwordLabelGridData);
		passwordLabel.setText(_("org.syncany.gui.wizard.ChoosePasswordPanel.passwordLabel"));
		
		// Textfield "Password"
		GridData passwordTextGridData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		passwordTextGridData.verticalIndent = 0;
		passwordTextGridData.horizontalSpan = 3;
		passwordTextGridData.minimumWidth = 200;

		passwordText = new Text(this, SWT.BORDER | SWT.PASSWORD);
		passwordText.setLayoutData(passwordTextGridData);
		passwordText.setBackground(WidgetDecorator.WHITE);
		passwordText.addModifyListener(new ModifyListener() {			
			@Override
			public void modifyText(ModifyEvent e) {
				if (firstValidationDone) {
					validatePanel();
				}
			}
		});
		
		WidgetDecorator.normal(passwordText);
		
		// Label "Confirm:"
		GridData confirmLabelGridData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
		confirmLabelGridData.verticalIndent = WidgetDecorator.VERTICAL_INDENT;
		confirmLabelGridData.horizontalSpan = 3;

		Label confirmLabel = new Label(this, SWT.WRAP);
		confirmLabel.setLayoutData(confirmLabelGridData);
		confirmLabel.setText(_("org.syncany.gui.wizard.ChoosePasswordPanel.confirmLabel"));
		
		// Textfield "Confirm"
		GridData confirmTextGridData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		confirmTextGridData.verticalIndent = 0;
		confirmTextGridData.horizontalSpan = 3;
		confirmTextGridData.minimumWidth = 200;

		confirmText = new Text(this, SWT.BORDER | SWT.PASSWORD);
		confirmText.setLayoutData(confirmTextGridData);
		confirmText.setBackground(WidgetDecorator.WHITE);
		confirmText.addModifyListener(new ModifyListener() {			
			@Override
			public void modifyText(ModifyEvent e) {
				if (firstValidationDone) {
					validatePanel();
				}
			}
		});
		
		WidgetDecorator.normal(confirmText);
		
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

	@Override
	public boolean validatePanel() {
		firstValidationDone = true;
		
		boolean validPassword = passwordText.getText().length() >= 10;
		boolean confirmPasswordEquals = passwordText.getText().equals(confirmText.getText());
		
		if (!validPassword && !confirmPasswordEquals) {
			WidgetDecorator.markAsInvalid(passwordText);
			WidgetDecorator.markAsInvalid(confirmText);

			showWarning(_("org.syncany.gui.wizard.ChoosePasswordPanel.errorTooShortAndNoMatch"));
			
			return false;			
		}
		else if (!validPassword) {
			WidgetDecorator.markAsInvalid(passwordText);
			WidgetDecorator.markAsValid(confirmText);

			showWarning(_("org.syncany.gui.wizard.ChoosePasswordPanel.errorTooShort"));
			
			return false;
		}
		else if (!confirmPasswordEquals) {
			WidgetDecorator.markAsValid(passwordText);
			WidgetDecorator.markAsInvalid(confirmText);

			showWarning(_("org.syncany.gui.wizard.ChoosePasswordPanel.errorNoMatch"));

			return false;
		}
		else {
			WidgetDecorator.markAsValid(passwordText);
			WidgetDecorator.markAsValid(confirmText);
			
			hideWarning();
			
			return true;
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

	public String getPassword() {
		return passwordText.getText();
	}
}
