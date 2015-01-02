package org.syncany.gui.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Internationalization class
 * 
 * @author Vincent Wiencek <vwiencek@gmail.com>
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class I18n {
	private static final Logger logger = Logger.getLogger(I18n.class.getSimpleName());

	private static final String DEFAULT_BUNDLE_LANGUAGE = "en";
	private static final String DEFAULT_BUNDLE_COUNTRY = "US";

	private static final HashMap<Locale, Properties> bundles = new HashMap<Locale, Properties>();
	private static final List<String> bundleNames = new ArrayList<String>();

	public static void registerBundleName(String bundle) {
		bundleNames.add(bundle);
	}
		
	/**
	 * Returns the translation for the key String, given the selected
	 * default locale.
	 */
	public static String getText(String key, Object... args) {
		if (!bundles.containsKey(Locale.getDefault())) {
			loadBundle(Locale.getDefault());
		}

		try {
			Properties bundleProperties = bundles.get(Locale.getDefault());
			
			if (bundleProperties != null) {
				String resourceString = bundleProperties.getProperty(key).trim();
				return replaceArgs(resourceString, args);
			}
			else {
				return key;
			}
		}
		catch (NullPointerException e) {
			if (key != null && !key.isEmpty()) {
				logger.log(Level.WARNING, Locale.getDefault() + " : key " + key + " not translated");
			}
			
			return key;
		}
	}

	private static String replaceArgs(String inputString, Object... args) {
		if (args != null && args.length > 0) {
			for (int i = 0; i < args.length; i++) {
				Object replacement = (args[i] != null) ? args[i] : "";
				inputString = inputString.replace("{" + i + "}", replacement.toString());
			}
		}
		
		return inputString;
	}
	

	private static void loadBundle(Locale locale) {
		for (String bundleName : bundleNames) {
			ResourceBundle resourceBundle;

			try {
				resourceBundle = ResourceBundle.getBundle(bundleName, locale, ClassLoader.getSystemClassLoader());
			}
			catch (MissingResourceException e) {
				Locale defaultLocale = new Locale(DEFAULT_BUNDLE_LANGUAGE, DEFAULT_BUNDLE_COUNTRY);
				resourceBundle = ResourceBundle.getBundle(bundleName, defaultLocale, ClassLoader.getSystemClassLoader());
			}

			buildResourceBundle(resourceBundle, locale);
		}
	}

	private static void buildResourceBundle(ResourceBundle resourceBundle, Locale locale) {
		Properties bundleProperties = new Properties();
		
		for (String resourceKey : resourceBundle.keySet()) {
			bundleProperties.put(resourceKey, resourceBundle.getString(resourceKey));
		}

		if (bundles.containsKey(locale)) {
			Properties oldProperties = bundles.get(locale);
			bundleProperties.putAll(oldProperties);
		}

		bundles.put(locale, bundleProperties);
	}
}