package org.syncany.gui.history;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Scale;
import org.ocpsoft.prettytime.PrettyTime;
import org.syncany.config.GuiEventBus;
import org.syncany.database.DatabaseVersionHeader;
import org.syncany.gui.Panel;
import org.syncany.gui.history.events.ModelSelectedDateUpdatedEvent;
import org.syncany.gui.history.events.ModelSelectedRootUpdatedEvent;
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.SWTResourceManager;
import org.syncany.operations.daemon.Watch;
import org.syncany.operations.daemon.messages.GetDatabaseVersionHeadersFolderRequest;
import org.syncany.operations.daemon.messages.GetDatabaseVersionHeadersFolderResponse;
import org.syncany.operations.daemon.messages.ListWatchesManagementRequest;
import org.syncany.operations.daemon.messages.ListWatchesManagementResponse;

import com.google.common.base.Objects;
import com.google.common.eventbus.Subscribe;

/**
 * The main panel displays the history of a managed folder in form of
 * a file tree ({@link FileTreeComposite}) and a log view ({@link LogComposite}).
 * The panel gives the user the possibility to select the folder to browse, and
 * offers a date slider to choose the date at which to browse the history.
 * 
 * <p>The panel sends out {@link ListWatchesManagementRequest}s to retrieve the 
 * daemon-managed folders (roots), and {@link GetDatabaseVersionHeadersFolderRequest}s
 * to get the dates of which database versions exist. The file tree and log composites
 * update themselves using other requests.
 * 
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class MainPanel extends Panel {
	private static final Logger logger = Logger.getLogger(MainPanel.class.getSimpleName());		
	private static final String IMAGE_RESOURCE_FORMAT = "/" + MainPanel.class.getPackage().getName().replace('.', '/') + "/%s.png";
	private static final int DATE_SLIDER_DELAY = 400;
	
	private HistoryModel historyModel;
	private HistoryDialog historyDialog;
	
	private ListWatchesManagementRequest pendingListWatchesRequest;
	private GuiEventBus eventBus;		

	private boolean dateLabelPrettyTime;
	private AtomicInteger dateSliderValue;
	private Timer dateSliderChangeTimer;
	private List<DatabaseVersionHeader> dateSliderHeaders;

	private Combo rootSelectCombo;
	private SelectionListener rootSelectComboListener;
	private Label dateLabel;
	private Scale dateSlider;
	private StackLayout stackLayout;
	private Composite stackComposite;
	
	private FileTreeComposite fileTreeComposite;
	private LogComposite logComposite;
	private LoadingComposite loadingComposite;
		
	private Button toggleTreeButton;
	private Button toggleLogButton;	

	public MainPanel(Composite composite, int style, HistoryModel historyModel, HistoryDialog historyDialog) {
		super(composite, style);

		this.setBackgroundImage(null);
		this.setBackgroundMode(SWT.INHERIT_DEFAULT);

		this.historyModel = historyModel;
		this.historyDialog = historyDialog;

		this.pendingListWatchesRequest = null;
		this.eventBus = GuiEventBus.getAndRegister(this);

		this.dateLabelPrettyTime = true;
		this.dateSliderValue = new AtomicInteger(-1);
		this.dateSliderChangeTimer = null;	
		this.dateSliderHeaders = Collections.synchronizedList(new ArrayList<DatabaseVersionHeader>());

		createMainComposite();
		createToggleButtons();
		createRootSelectionCombo();
		createRootSelectionComboListener();
		createDateSlider();
		createStackComposite();		
		createComposites();		
		
		showLoadingComposite();		
		sendListWatchesRequest();
	}	

	private void createMainComposite() {
		GridLayout mainCompositeGridLayout = new GridLayout(5, false);
		mainCompositeGridLayout.marginTop = 0;
		mainCompositeGridLayout.marginLeft = 0;
		mainCompositeGridLayout.marginRight = 0;

		setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 5, 1));
		setLayout(mainCompositeGridLayout);
	}
	
	private void createToggleButtons() {	
		toggleLogButton = new Button(this, SWT.TOGGLE);
		toggleLogButton.setEnabled(false);
		toggleLogButton.setSelection(true);
		toggleLogButton.setImage(SWTResourceManager.getImage(String.format(IMAGE_RESOURCE_FORMAT, "log")));
		
		toggleTreeButton = new Button(this, SWT.TOGGLE);
		toggleTreeButton.setEnabled(false);
		toggleTreeButton.setSelection(false);
		toggleTreeButton.setImage(SWTResourceManager.getImage(String.format(IMAGE_RESOURCE_FORMAT, "tree")));
		
		toggleLogButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				showLog();			
			}
		});
		
		toggleTreeButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				showTree();				
			}
		});				
	}
	
	private void createRootSelectionCombo() {
		rootSelectCombo = new Combo(this, SWT.DROP_DOWN | SWT.BORDER | SWT.READ_ONLY);
		
		rootSelectCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		rootSelectCombo.setText(I18n.getText("org.syncany.gui.history.HistoryDialog.retrievingList"));
		rootSelectCombo.setEnabled(false);			
	}
	
	private void createRootSelectionComboListener() {
		rootSelectComboListener = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				onRootSelectComboSelected();
			}			
		};		
	}	
	
	private void createDateSlider() {
		// Label
		GridData dateLabelGridData = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
		dateLabelGridData.minimumWidth = 150;
		
		dateLabel = new Label(this, SWT.CENTER);
		dateLabel.setEnabled(false);
		dateLabel.setCursor(new Cursor(Display.getDefault(), SWT.CURSOR_HAND));
		dateLabel.setLayoutData(dateLabelGridData);
		
		dateLabel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseUp(MouseEvent e) {
				dateLabelPrettyTime = !dateLabelPrettyTime;
				
				if (dateLabel.getData() != null) {
					setDateLabel((Date) dateLabel.getData());
				}
			}
		});
		
		// Slider
		dateSlider = new Scale(this, SWT.HORIZONTAL | SWT.BORDER);
		
		dateSlider.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		dateSlider.setEnabled(false);
		
		dateSlider.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {	
				onDateSliderSelected();				
			}
		});		
	}
	
	private void createStackComposite() {
		stackLayout = new StackLayout();
		stackLayout.marginHeight = 0;
		stackLayout.marginWidth = 0;
		
		GridData stackCompositeGridData = new GridData(SWT.FILL, SWT.FILL, true, true, 5, 1);
		stackCompositeGridData.minimumWidth = 500;

		stackComposite = new Composite(this, SWT.DOUBLE_BUFFERED);
		stackComposite.setLayout(stackLayout);
		stackComposite.setLayoutData(stackCompositeGridData);
	}

	private void createComposites() {
		loadingComposite = new LoadingComposite(stackComposite, SWT.NONE);
		fileTreeComposite = new FileTreeComposite(stackComposite, SWT.NONE, historyModel, historyDialog);
		logComposite = new LogComposite(stackComposite, SWT.NONE, historyModel, this);
	}

	/**
	 * Displays the log composite, toggles the buttons
	 * and disposes the loading composite.
	 */
	public void showLog() {
		setCurrentControl(logComposite);

		enableControls();
		toggleButtons(true);
		
		disposeLoadingComposite();
	}	

	/**
	 * Displays the file tree composite, toggles the buttons
	 * and disposes the loading composite.
	 */
	public void showTree() {		
		setCurrentControl(fileTreeComposite);
		
		enableControls();
		toggleButtons(false);
		
		disposeLoadingComposite();
	}	

	private void setCurrentControl(Control control) {
		stackLayout.topControl = control;
		stackComposite.layout();	
	}
	
	@SuppressWarnings("unchecked")
	private void onRootSelectComboSelected() {
		List<Watch> watches = (List<Watch>) rootSelectCombo.getData();				
		
		if (watches != null) {
			int selectionIndex = rootSelectCombo.getSelectionIndex();

			if (selectionIndex >= 0 && selectionIndex < watches.size()) {
				String newRoot = watches.get(selectionIndex).getFolder().getAbsolutePath();
				
				historyModel.reset();
				historyModel.setSelectedRoot(newRoot);

				sendGetDatabaseVersionHeadersFolderRequest(newRoot);						
			}
		}
	}
	
	private void setDateLabel(final Date dateSliderDate) {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {				
				String dateStrPretty = new PrettyTime().format(dateSliderDate);
				String dateStrExact = dateSliderDate.toString();
				
				dateLabel.setData(dateSliderDate);
				
				if (dateLabelPrettyTime) {
					dateLabel.setText(dateStrPretty);
					dateLabel.setToolTipText(dateStrExact);
				}
				else {
					dateLabel.setText(dateStrExact);
					dateLabel.setToolTipText(dateStrPretty);
				}
			}
		});
	}

	private void onDateSliderSelected() {
		synchronized (dateSlider) {	
			int newDateSliderValue = dateSlider.getSelection();
			Date newSliderDate = getSliderDate();

			boolean dateSliderValueChanged = dateSliderValue.get() != newDateSliderValue;
			
			if (dateSliderValueChanged) {
				// Update cached value
				dateSliderValue.set(newDateSliderValue);
				
				// Update label right away
				setDateLabel(newSliderDate);
				logComposite.highlightByDate(newSliderDate);

				// Update file tree after a while  
				if (dateSliderChangeTimer != null) {
					dateSliderChangeTimer.cancel();
				}
				
				dateSliderChangeTimer = new Timer();
				dateSliderChangeTimer.schedule(createDateSliderTimerTask(), DATE_SLIDER_DELAY);
				
				logger.log(Level.INFO, "Main: Date slider value changed to " + newSliderDate + "; setting timer to refresh views in 800ms ...");
			}
		}
	}

	private TimerTask createDateSliderTimerTask() {
		return new TimerTask() {			
			@Override
			public void run() {		
				Display.getDefault().syncExec(new Runnable() {
					@Override
					public void run() {							
						logger.log(Level.INFO, "Main: Date slider timer fired.");
						onDateChanged(getSliderDate());
					}					
				});
			}
		};
	}	
	
	private Date getSliderDate() {
		int dateSelectionIndex = dateSlider.getSelection();
		
		if (dateSelectionIndex >= 0 && dateSelectionIndex < dateSliderHeaders.size()) {
			return dateSliderHeaders.get(dateSelectionIndex).getDate();
		}
		else {
			return null;
		}
	}
	
	private void setSliderDate(Date newDate) {
		for (int i = 0; i < dateSliderHeaders.size(); i++) {
			DatabaseVersionHeader header = dateSliderHeaders.get(i);
			
			if (header.getDate().equals(newDate)) {
				dateSlider.setSelection(i);
			}
		}
	}
	
	private void showLoadingComposite() {
		setCurrentControl(loadingComposite);
	}
		
	private void enableControls() {
		toggleLogButton.setEnabled(true);
		toggleTreeButton.setEnabled(true);
		
		rootSelectCombo.setEnabled(true);
		
		dateLabel.setEnabled(true);
		dateSlider.setEnabled(true);
	}

	private void toggleButtons(boolean logEnabled) {
		toggleLogButton.setSelection(logEnabled);
		toggleTreeButton.setSelection(!logEnabled);	
	}
	
	private void disposeLoadingComposite() {
		if (!loadingComposite.isDisposed()) {
			loadingComposite.dispose();
		}
	}

	@Override
	public boolean validatePanel() {
		return true;
	}

	private void updateSlider(final List<DatabaseVersionHeader> headers) {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				if (headers.size() > 0) {
					int maxValue = headers.size() - 1;
					Date newSelectedDate = headers.get(headers.size()-1).getDate();
					
					dateSliderHeaders.clear();
					dateSliderHeaders.addAll(headers);
					
					dateSlider.setMinimum(0);
					dateSlider.setMaximum(maxValue);
					dateSlider.setSelection(maxValue);
					
					setDateLabel(newSelectedDate);

					// setMaximum does not actually work if it sets 0, hence we disable the slider.
					if (headers.size() == 1) {
						dateSlider.setEnabled(false);
					}
				}
				else {
					// Disable slider and set today as date to clear fields
					dateSlider.setSelection(0);
					dateSlider.setEnabled(false);
					setDateLabel(new Date());
				}				
			}
		});		
	}	
	
	private void sendListWatchesRequest() {
		pendingListWatchesRequest = new ListWatchesManagementRequest();
		eventBus.post(pendingListWatchesRequest);		
	}
	
	@Subscribe
	public void onListWatchesManagementResponse(final ListWatchesManagementResponse listWatchesResponse) {
		if (pendingListWatchesRequest != null && pendingListWatchesRequest.getId() == listWatchesResponse.getRequestId()) {
			// Nullify pending request
			pendingListWatchesRequest = null;

			// Update combo box
			updateRootsCombo(listWatchesResponse.getWatches());		
		}
	}
	
	private void updateRootsCombo(final ArrayList<Watch> watches) {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				rootSelectCombo.removeSelectionListener(rootSelectComboListener);
				rootSelectCombo.removeAll();
				
				for (Watch watch : watches) {
					rootSelectCombo.add(watch.getFolder().getName());
				}
				
				rootSelectCombo.setData(watches);
				
				if (rootSelectCombo.getItemCount() > 0) {
					historyModel.reset();
					
					rootSelectCombo.addSelectionListener(rootSelectComboListener);
					rootSelectCombo.select(0);	
					
					onRootSelectComboSelected();
				}
			}
		});
	}

	@Subscribe
	public void onModelSelectedRootUpdatedEvent(final ModelSelectedRootUpdatedEvent event) {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				sendGetDatabaseVersionHeadersFolderRequest(event.getSelectedRoot());
			}
		});
	}

	private void sendGetDatabaseVersionHeadersFolderRequest(String newRoot) {		
		GetDatabaseVersionHeadersFolderRequest getHeadersRequest = new GetDatabaseVersionHeadersFolderRequest();
		getHeadersRequest.setRoot(newRoot);
		
		eventBus.post(getHeadersRequest);		
	}	
	
	@Subscribe
	public void onGetDatabaseVersionHeadersFolderResponse(final GetDatabaseVersionHeadersFolderResponse getHeadersResponse) {
		List<DatabaseVersionHeader> headers = getHeadersResponse.getDatabaseVersionHeaders();

		if (headers.size() > 0) {
			Date newSelectedDate = headers.get(headers.size()-1).getDate();
			historyModel.setSelectedDate(newSelectedDate);	
		}
		else {			
			historyModel.setSelectedDate(null);
		}			
		
		updateSlider(headers);		
	}
	
	@Subscribe
	public void onModelSelectedDateUpdatedEvent(final ModelSelectedDateUpdatedEvent event) {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				onDateChanged(event.getSelectedDate());
				
				if (!Objects.equal(event.getSelectedDate(), getSliderDate())) {
					setSliderDate(event.getSelectedDate());
				}
			}
		});
	}
	
	private void onDateChanged(Date newDate) {
		boolean listUpdateRequired = !newDate.equals(historyModel.getSelectedDate());
			
		if (listUpdateRequired) {
			logger.log(Level.INFO, "Main: Changing DATE model in model to " + newDate + " ...");			
			historyModel.setSelectedDate(newDate);							
		}			
	}

	public void dispose() {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {	
				eventBus.unregister(MainPanel.this);
				
				if (!logComposite.isDisposed()) {
					logComposite.dispose();
				}		
				
				if (!fileTreeComposite.isDisposed()) {
					fileTreeComposite.dispose();
				}
				
				if (!loadingComposite.isDisposed()) {
					loadingComposite.dispose();
				}
			}
		});
		
		super.dispose();
	}
}
