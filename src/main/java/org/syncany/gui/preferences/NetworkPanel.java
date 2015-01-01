/*
 * Syncany, www.syncany.org
 * Copyright (C) 2011-2013 Philipp C. Heckel <philipp.heckel@gmail.com> 
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
package org.syncany.gui.preferences;

import static org.syncany.gui.util.I18n._;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;
import org.syncany.config.ConfigException;
import org.syncany.config.UserConfig;
import org.syncany.config.to.UserConfigTO;
import org.syncany.gui.Panel;
import org.syncany.gui.util.WidgetDecorator;

/**
 * 
 * @see http://stackoverflow.com/questions/120797/how-do-i-set-the-proxy-to-be-used-by-the-jvm
 */
public class NetworkPanel extends Panel {
	private static final Logger logger = Logger.getLogger(NetworkPanel.class.getSimpleName());		

	private UserConfigTO userConfig;	
	
	private Button noProxyButton;
	private Button useSystemProxyButton;
	private Button manualProxyButton;
	 
	private Combo proxyTypeCombo;
	private Text proxyServerHostText;
	private Spinner proxyServerPortSpinner;
	
	private Button proxyNeedsAuthCheckButton;
	private Label proxyAuthUserLabel;
	private Text proxyAuthUserText;
	private Label proxyAuthPassLabel;
	private Text proxyAuthPassText;
	
	public NetworkPanel(PreferencesDialog parentDialog, Composite composite, int style) {
		super(parentDialog, composite, style);
				
		createContents();		
		loadConfig();
	}

	private void createContents() {
		// Main composite
		GridLayout mainCompositeGridLayout = new GridLayout(5, false);
		mainCompositeGridLayout.marginTop = 15;
		mainCompositeGridLayout.marginLeft = 10;
		mainCompositeGridLayout.marginRight = 20;

		setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 5, 1));
		setLayout(mainCompositeGridLayout);

		// Title and welcome text
		Label titleLabel = new Label(this, SWT.WRAP);
		titleLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 5, 1));
		titleLabel.setText(_("org.syncany.gui.preferences.NetworkPanel.title"));

		WidgetDecorator.title(titleLabel);

		// Common listener to react on radio button changes, and save the config	
		SelectionListener commonSelectionListener = new SelectionAdapter() {			
			@Override
			public void widgetSelected(SelectionEvent e) {
				updateControls();
				saveConfig();
			}			
		};
		
		// Proxy settings
		Label proxySettingsLabel = new Label(this, SWT.NONE);
		proxySettingsLabel.setText(_("org.syncany.gui.preferences.NetworkPanel.proxySettings"));
		
	    noProxyButton = new Button(this, SWT.RADIO);
	    noProxyButton.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false, 4, 1));
	    noProxyButton.setText(_("org.syncany.gui.preferences.NetworkPanel.noProxy"));	    
	    noProxyButton.addSelectionListener(commonSelectionListener);
	    
		Label fillerBelowProxySettingsLabel = new Label(this, SWT.NONE);
		fillerBelowProxySettingsLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 2));
	    
	    useSystemProxyButton = new Button(this, SWT.RADIO);
	    useSystemProxyButton.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false, 4, 1));
	    useSystemProxyButton.setText(_("org.syncany.gui.preferences.NetworkPanel.useSystemProxy"));
	    useSystemProxyButton.addSelectionListener(commonSelectionListener);
	    
	    manualProxyButton = new Button(this, SWT.RADIO);
	    manualProxyButton.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 4, 1));
	    manualProxyButton.setText(_("org.syncany.gui.preferences.NetworkPanel.manualProxy"));
	    manualProxyButton.addSelectionListener(commonSelectionListener);
	    
	    // Proxy type
		Label proxyTypeLabel = new Label(this, SWT.NONE);
		proxyTypeLabel.setText(_("org.syncany.gui.preferences.NetworkPanel.proxyType"));

	    proxyTypeCombo = new Combo(this, SWT.DROP_DOWN | SWT.BORDER | SWT.READ_ONLY);
	    proxyTypeCombo.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 4, 1));
	    proxyTypeCombo.addSelectionListener(commonSelectionListener);

	    proxyTypeCombo.add("HTTP");
	    proxyTypeCombo.add("SOCKS");
	    proxyTypeCombo.select(0);
	    
		Label proxyServerLabel = new Label(this, SWT.NONE);
		proxyServerLabel.setText(_("org.syncany.gui.preferences.NetworkPanel.proxyServer"));

		GridData proxyServerHostTextGridData = new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1);
		proxyServerHostTextGridData.widthHint = 180;
		
		proxyServerHostText = new Text(this, SWT.BORDER);
		proxyServerHostText.setLayoutData(proxyServerHostTextGridData);
		proxyServerHostText.addSelectionListener(commonSelectionListener);
		
		Label proxyPortColonLabel = new Label(this, SWT.NONE);
		proxyPortColonLabel.setText(":");
		
		proxyServerPortSpinner = new Spinner(this, SWT.BORDER);
		proxyServerPortSpinner.setBackgroundMode(SWT.INHERIT_FORCE);
	    proxyServerPortSpinner.setMinimum(1);
	    proxyServerPortSpinner.setMaximum(65535);
	    proxyServerPortSpinner.setSelection(8080);
	    proxyServerPortSpinner.setIncrement(1);
	    proxyServerPortSpinner.setPageIncrement(100);
	    proxyServerPortSpinner.addSelectionListener(commonSelectionListener);
	    
		Label fillerBelowServerLabel = new Label(this, SWT.NONE);
		fillerBelowServerLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 3));

	    proxyNeedsAuthCheckButton = new Button(this, SWT.CHECK);
	    proxyNeedsAuthCheckButton.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 4, 1));
	    proxyNeedsAuthCheckButton.setText(_("org.syncany.gui.preferences.NetworkPanel.proxyNeedsAuth"));
	    proxyNeedsAuthCheckButton.addSelectionListener(commonSelectionListener);	    

		proxyAuthUserLabel = new Label(this, SWT.NONE);
		proxyAuthUserLabel.setText(_("org.syncany.gui.preferences.NetworkPanel.proxyAuthUser"));

		GridData proxyAuthFieldGridData = new GridData(SWT.LEFT, SWT.CENTER, true, false, 3, 1);
		proxyAuthFieldGridData.widthHint = 110;
		proxyAuthFieldGridData.grabExcessHorizontalSpace = true;
		
		proxyAuthUserText = new Text(this, SWT.BORDER);		
		proxyAuthUserText.setLayoutData(proxyAuthFieldGridData);
		proxyAuthUserText.addSelectionListener(commonSelectionListener);

		proxyAuthPassLabel = new Label(this, SWT.PASSWORD);
		proxyAuthPassLabel.setText(_("org.syncany.gui.preferences.NetworkPanel.proxyAuthPass"));

		proxyAuthPassText = new Text(this, SWT.BORDER);		
		proxyAuthPassText.setLayoutData(proxyAuthFieldGridData);
		proxyAuthPassText.addSelectionListener(commonSelectionListener);		
	}

	private void updateControls() {
		if (noProxyButton.getSelection()) {
			 toggleManualControls(false);
		}
		else if (useSystemProxyButton.getSelection()) {
			 toggleManualControls(false);			
		}
		else {
			 toggleManualControls(true);			
		}
	}
	
	private void loadConfig() {
		try {
			userConfig = UserConfigTO.load(UserConfig.getUserConfigFile());
			
			String useSystemProxiesProperty = userConfig.getSystemProperties().get("java.net.useSystemProxies");
			String httpProxyHostProperty = userConfig.getSystemProperties().get("http.proxyHost");
			String socksProxyHostProperty = userConfig.getSystemProperties().get("socksProxyHost");
						
			if (useSystemProxiesProperty != null) {
				loadUseSystemProxySettings();
			}
			else if (httpProxyHostProperty != null) {
				loadHttpProxySettings();
			}
			else if (socksProxyHostProperty != null) {
				loadSocksProxySettings();
			}
			else {
				loadNoProxySettings();
			}			
		}
		catch (ConfigException e) {
			disableAllControls();
		}
	}

	private void loadNoProxySettings() {
		noProxyButton.setSelection(true);
		toggleManualControls(false);
	}

	private void loadUseSystemProxySettings() {
		useSystemProxyButton.setSelection(true);
		toggleManualControls(false);
	}

	private void loadSocksProxySettings() {
		String socksProxyHostProperty = userConfig.getSystemProperties().get("socksProxyHost");
		String socksProxyPortProperty = userConfig.getSystemProperties().get("socksProxyPort");
		String socksProxyUserProperty = userConfig.getSystemProperties().get("java.net.socks.username");
		String socksProxyPassProperty = userConfig.getSystemProperties().get("java.net.socks.password");

		manualProxyButton.setSelection(true);
		proxyTypeCombo.select(1);
		
		proxyServerHostText.setText(socksProxyHostProperty);
		
		try {
			proxyServerPortSpinner.setSelection(Integer.parseInt(socksProxyPortProperty));
		}
		catch (Exception e) {
			proxyServerPortSpinner.setSelection(8080);
		}
		
		if (socksProxyUserProperty != null && socksProxyPassProperty != null) {
			proxyAuthUserText.setText(socksProxyUserProperty);
			proxyAuthPassText.setText(socksProxyPassProperty);
		}
		
		toggleManualControls(true);
	}

	private void loadHttpProxySettings() {
		String httpProxyHostProperty = userConfig.getSystemProperties().get("http.proxyHost");
		String httpProxyPortProperty = userConfig.getSystemProperties().get("http.proxyPort");
		String httpProxyUserProperty = userConfig.getSystemProperties().get("http.proxyUser");
		String httpProxyPassProperty = userConfig.getSystemProperties().get("http.proxyPass");

		manualProxyButton.setSelection(true);
		proxyTypeCombo.select(0);
		
		proxyServerHostText.setText(httpProxyHostProperty);
		
		try {
			proxyServerPortSpinner.setSelection(Integer.parseInt(httpProxyPortProperty));
		}
		catch (Exception e) {
			proxyServerPortSpinner.setSelection(8080);
		}
		
		if (httpProxyUserProperty != null && httpProxyPassProperty != null) {
			proxyAuthUserText.setText(httpProxyUserProperty);
			proxyAuthPassText.setText(httpProxyPassProperty);
		}
		
		toggleManualControls(true);
	}

	private void disableAllControls() {
		noProxyButton.setEnabled(false);
		useSystemProxyButton.setEnabled(false);
		manualProxyButton.setEnabled(false);
		
		toggleManualControls(false);
	}

	private void toggleManualControls(boolean enable) {
		proxyTypeCombo.setEnabled(enable);
		proxyServerHostText.setEnabled(enable);
		proxyServerPortSpinner.setEnabled(enable);
		proxyNeedsAuthCheckButton.setEnabled(enable);
		
		if (enable) {
			boolean needsAuth = proxyNeedsAuthCheckButton.getSelection();
			
			proxyAuthUserText.setEnabled(needsAuth);
			proxyAuthPassText.setEnabled(needsAuth);
		}
		else {
			proxyAuthUserText.setEnabled(false);
			proxyAuthPassText.setEnabled(false);			
		}
	}
	
	private void saveConfig() {
		clearUserConfigProxySettings();
		
		if (noProxyButton.getSelection()) {
			// Nothing!
		}
		else if (useSystemProxyButton.getSelection()) {
			userConfig.getSystemProperties().put("java.net.useSystemProxies", "true");
		}
		else {
			boolean isHttpProxy = proxyTypeCombo.getSelectionIndex() == 0;
		
			if (isHttpProxy) {
				userConfig.getSystemProperties().put("http.proxyHost", proxyServerHostText.getText());
				userConfig.getSystemProperties().put("http.proxyPort", Integer.toString(proxyServerPortSpinner.getSelection()));
				userConfig.getSystemProperties().put("https.proxyHost", proxyServerHostText.getText());
				userConfig.getSystemProperties().put("https.proxyPort", Integer.toString(proxyServerPortSpinner.getSelection()));

				if (proxyNeedsAuthCheckButton.getSelection()) {
					userConfig.getSystemProperties().put("http.proxyUser", proxyAuthUserText.getText());
					userConfig.getSystemProperties().put("http.proxyPass", proxyAuthPassText.getText());
					userConfig.getSystemProperties().put("https.proxyUser", proxyAuthUserText.getText());
					userConfig.getSystemProperties().put("https.proxyPass", proxyAuthPassText.getText());
				}
			}
			else {
				userConfig.getSystemProperties().put("socksProxyHost", proxyServerHostText.getText());
				userConfig.getSystemProperties().put("socksProxyPort", Integer.toString(proxyServerPortSpinner.getSelection()));

				if (proxyNeedsAuthCheckButton.getSelection()) {
					userConfig.getSystemProperties().put("java.net.socks.username", proxyAuthUserText.getText());
					userConfig.getSystemProperties().put("java.net.socks.password", proxyAuthPassText.getText());
				}
			}
		}
		
		try {
			userConfig.save(UserConfig.getUserConfigFile());
		}
		catch (ConfigException e) {
			logger.log(Level.WARNING, "Unable to save GUI config.", e);
		}
	}

	private void clearUserConfigProxySettings() {
		userConfig.getSystemProperties().remove("java.net.useSystemProxies");

		userConfig.getSystemProperties().remove("http.proxyHost");
		userConfig.getSystemProperties().remove("http.proxyPort");
		userConfig.getSystemProperties().remove("http.proxyUser");
		userConfig.getSystemProperties().remove("http.proxyPass");
		userConfig.getSystemProperties().remove("https.proxyHost");
		userConfig.getSystemProperties().remove("https.proxyPort");
		userConfig.getSystemProperties().remove("https.proxyUser");
		userConfig.getSystemProperties().remove("https.proxyPass");
		
		userConfig.getSystemProperties().remove("socksProxyHost");
		userConfig.getSystemProperties().remove("socksProxyPort");
		userConfig.getSystemProperties().remove("java.net.socks.username");
		userConfig.getSystemProperties().remove("java.net.socks.password");		
	}

	@Override
	public boolean validatePanel() {
		return true;
	}
}
