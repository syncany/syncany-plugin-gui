package org.syncany.gui.wizard;


import java.lang.reflect.Constructor;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Text;
import org.syncany.gui.util.DesktopUtil;
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.WidgetDecorator;
import org.syncany.plugins.transfer.StorageException;
import org.syncany.plugins.transfer.TransferSettings;
import org.syncany.plugins.transfer.oauth.OAuth;
import org.syncany.plugins.transfer.oauth.OAuthGenerator;
import org.syncany.plugins.transfer.oauth.OAuthGenerator.WithExtractor;
import org.syncany.plugins.transfer.oauth.OAuthGenerator.WithInterceptor;
import org.syncany.plugins.transfer.oauth.OAuthTokenFinish;
import org.syncany.plugins.transfer.oauth.OAuthTokenWebListener;

/**
 * @author Christian Roth <christian.roth@port17.de>
 */

class PluginSettingsPanelOAuthHelper {
	private enum StatusCode {
		IDLE, RUNNING, FINISHED, SUCCESS
	}

	private static final Logger logger = Logger.getLogger(PluginSettingsPanelOAuthHelper.class.getName());
	private static final String STRING_BUTTON_CONNECTING = I18n.getText("org.syncany.gui.wizard.PluginSettingsPanel.oauth.button.connecting");
	private static final String STRING_BUTTON_AUTHORIZE = I18n.getText("org.syncany.gui.wizard.PluginSettingsPanel.oauth.button.authorize");
	private static final String STRING_BUTTON_WAITING = I18n.getText("org.syncany.gui.wizard.PluginSettingsPanel.oauth.button.waiting");
	private static final String STRING_BUTTON_ERROR = I18n.getText("org.syncany.gui.wizard.PluginSettingsPanel.oauth.button.error");
	private static final String STRING_BUTTON_OK = I18n.getText("org.syncany.gui.wizard.PluginSettingsPanel.oauth.button.ok");

	private final OAuthTokenWebListener webListener;
	private final ButtonSelectionAdapter buttonSelectionAdapter;
	private final OAuthGenerator generator;
	private final Consumer<String> warningHandler;
	private final Button authorizeButton;
	private final Text tokenText;

	private Future<OAuthTokenFinish> futureTokenFinish;
	private StatusCode statusCode = StatusCode.IDLE;
	private URI redirectUri;
	private URI authUri;

	private Thread tokenArrivalThread;

	public static class Builder {

		private final OAuth settings;
		private final OAuthGenerator generator;

		private Button button;
		private Text text;
		private Consumer<String> warningHandler;

		private <T extends TransferSettings> Builder(T transferSettings) throws UnsupportedOperationException {
			settings = transferSettings.getClass().getAnnotation(OAuth.class);

			if (settings == null) {
				throw new UnsupportedOperationException("Tried to create OAuth helper for non-OAuth class");
			}

			try {
				Constructor<? extends OAuthGenerator> generatorConstructor = settings.value().getDeclaredConstructor(transferSettings.getClass());
				generator = generatorConstructor.newInstance(transferSettings);
			}
			catch (Exception e) {
				throw new RuntimeException("Unable to create generator", e);
			}
		}

		public Builder withButton(Button button) {
			this.button = button;
			return this;
		}

		public Builder withText(Text text) {
			this.text = text;
			return this;
		}

		public Builder withWarningHandler(Consumer<String> warningHandler) {
			this.warningHandler = warningHandler;
			return this;
		}

		public PluginSettingsPanelOAuthHelper build() {
			OAuthTokenWebListener.Builder tokenListerBuilder = OAuthTokenWebListener.forMode(settings.mode());

			if (settings.callbackPort() != OAuth.RANDOM_PORT) {
				tokenListerBuilder.setPort(settings.callbackPort());
			}

			if (!settings.callbackId().equals(OAuth.PLUGIN_ID)) {
				tokenListerBuilder.setId(settings.callbackId());
			}

			// Non standard plugin?
			if (generator instanceof WithInterceptor) {
				tokenListerBuilder.setTokenInterceptor(((WithInterceptor) generator).getInterceptor());
			}

			if (generator instanceof WithExtractor) {
				tokenListerBuilder.setTokenExtractor(((WithExtractor) generator).getExtractor());
			}

			return new PluginSettingsPanelOAuthHelper(tokenListerBuilder.build(), generator, warningHandler, button, text);
		}
	}

	private PluginSettingsPanelOAuthHelper(OAuthTokenWebListener webListener, OAuthGenerator generator, Consumer<String> warningHandler, Button button, Text text) {
		this.webListener = webListener;
		this.generator = generator;
		this.warningHandler = warningHandler;
		this.authorizeButton = button;
		this.tokenText = text;

		this.buttonSelectionAdapter = new ButtonSelectionAdapter();

		disableButton();
		tokenText.setEditable(false);
	}

	/**
	 * Create a helper for a specific OAuth plugin. The helper manages buttons and token collection including validation.
	 *
	 * @param transferSettings The OAuth enabled plugin
	 * @throws UnsupportedOperationException If the plugin is no OAuth plugin (not annotated with {@link OAuth}.
	 */
	static <T extends TransferSettings> Builder forSettings(T transferSettings) throws UnsupportedOperationException {
		return new Builder(transferSettings);
	}

	/**
	 * Stop all running listeners and reset the button to initial state.
	 *
	 * @param clearGui Define if the GUI elements should be reset to their initial state (will cause an Exception if
	 *                  the elements are already disposed)
	 */
	public void reset(boolean clearGui) {
		if (statusCode == StatusCode.IDLE) {
			return;
		}

		logger.log(Level.INFO, "Resetting the OAuth process");

		if (tokenArrivalThread != null) {
			tokenArrivalThread.interrupt();
			tokenArrivalThread = null;
		}
		else {
			webListener.stop();
		}

		redirectUri = null;
		authUri = null;

		if (clearGui) {
			disableButton();
			setButtonText(STRING_BUTTON_CONNECTING);

			setTokenText("");
			markTextAsSuccess(false);
		}

		statusCode = StatusCode.IDLE;
	}

	/**
	 * This method makes the GUI ready to perfom the OAuth process. It starts the webserver, adds the button event and
	 * changes button texts. Implicitly calls {@link #reset(boolean clearGui)} in GUI clearance mode.
	 */
	public void start() {
		reset(true);

		redirectUri = webListener.start();
		statusCode = StatusCode.RUNNING;

		asyncRetrieveOAuthUrlAndEnableAuthButton();

		logger.log(Level.INFO, "OAuth process enabled");
	}

	public boolean isFinished() {
		return statusCode == StatusCode.FINISHED;
	}

	public boolean isSuccess() {
		return statusCode == StatusCode.SUCCESS;
	}

	private void asyncRetrieveOAuthUrlAndEnableAuthButton() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					authUri = generator.generateAuthUrl(redirectUri);
					futureTokenFinish = webListener.getToken();

					enableButton();
					setButtonText(STRING_BUTTON_AUTHORIZE);

					asyncWaitForTokenArrival();
				}
				catch (Exception e) {
					triggerError(I18n.getText("org.syncany.gui.wizard.PluginSettingsPanel.oauth.errorCannotRetrieveOAuthURL", e.getMessage()));
					setButtonText(STRING_BUTTON_ERROR);

					logger.log(Level.SEVERE, "Unable to retrieve auth url", e);
				}
			}

		}, "GetOAuthUrl").start();
	}

	private void asyncWaitForTokenArrival() {
		tokenArrivalThread = new Thread(new Runnable() {
			@Override
			public void run() {
				boolean startOver = false;

				try {
					OAuthTokenFinish tokenResponse = futureTokenFinish.get(); // we dont need a timeout here
					statusCode = StatusCode.FINISHED;

					if (tokenResponse != null) {
						generator.checkToken(tokenResponse.getToken(), tokenResponse.getCsrfState());

						setTokenText(tokenResponse.getToken());
						setButtonText(STRING_BUTTON_OK);
						markTextAsSuccess(true);

						statusCode = StatusCode.SUCCESS;
					}
					else {
						logger.log(Level.WARNING, "Invalid token received, maybe user cancled process.");
						triggerError(I18n.getText("org.syncany.gui.wizard.PluginSettingsPanel.errorExceptionOAuthToken"));
						startOver = true;
					}
				}
				catch (InterruptedException e) {
					// no big deal since that is intended when called #reset
					logger.log(Level.INFO, "Thread was interrupted, maybe some called reset", e);
				}
				catch (ExecutionException | StorageException e) {
					logger.log(Level.SEVERE, "Exception while waiting for OAuth token", e);
					triggerError(I18n.getText("org.syncany.gui.wizard.PluginSettingsPanel.errorExceptionOAuthToken"));
				}
				finally {
					logger.log(Level.INFO, "Finally stopping weblistener");
					webListener.stop();

					if (startOver) {
						logger.log(Level.INFO, "Reenabling OAuth process");
						start();
					}
				}
			}
		}, "WaitTokenArrival");

		tokenArrivalThread.start();
	}

	private void disableButton() {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				authorizeButton.setEnabled(false);
				authorizeButton.removeSelectionListener(buttonSelectionAdapter);
			}
		});
	}

	private void enableButton() {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				authorizeButton.setEnabled(true);
				authorizeButton.addSelectionListener(buttonSelectionAdapter);
			}
		});
	}

	private void setButtonText(final String text) {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				authorizeButton.setText(text);
			}
		});
	}

	private void setTokenText(final String text) {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				tokenText.setText(text);
			}
		});
	}

	private void markTextAsSuccess(final boolean success) {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				if (success) {
					WidgetDecorator.markAsValid(tokenText);
				}
				else {
					WidgetDecorator.normal(tokenText);
				}
			}
		});
	}

	private void triggerError(String warning) {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				WidgetDecorator.markAsInvalid(tokenText);
			}
		});

		if (warningHandler != null) {
			warningHandler.accept(warning);
		}
	}

	private class ButtonSelectionAdapter extends SelectionAdapter {
		@Override
		public void widgetSelected(SelectionEvent e) {
			DesktopUtil.launch(authUri.toString());
			disableButton();
			setButtonText(STRING_BUTTON_WAITING);
		}
	}

	/* Java 7 sadly does not support java.util.function.Consumer<T> from Java 8, backporting */
	public interface Consumer<T> {
		void accept(T t);
	}

}
