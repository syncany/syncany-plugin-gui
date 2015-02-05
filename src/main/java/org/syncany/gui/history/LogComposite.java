package org.syncany.gui.history;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.MessageBox;
import org.syncany.config.GuiEventBus;
import org.syncany.gui.history.events.ModelSelectedDateUpdatedEvent;
import org.syncany.gui.history.events.ModelSelectedRootUpdatedEvent;
import org.syncany.gui.util.DesktopUtil;
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.WidgetDecorator;
import org.syncany.operations.daemon.messages.LogFolderRequest;
import org.syncany.operations.daemon.messages.LogFolderResponse;
import org.syncany.operations.log.LightweightDatabaseVersion;
import org.syncany.operations.log.LogOperationOptions;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.eventbus.Subscribe;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class LogComposite extends Composite {
	private static final Logger logger = Logger.getLogger(LogComposite.class.getSimpleName());		

	public static final int LOG_REQUEST_DATABASE_COUNT = 15;
	public static final int LOG_REQUEST_FILE_COUNT = 10;
	
	private HistoryModel historyModel;	
	private MainPanel mainPanel;
	
	private LogFolderRequest pendingLogFolderRequest;
	private GuiEventBus eventBus;	
	
	private ScrolledComposite scrollComposite;
	private Composite logContentComposite;
	
	private Map<Date, LogTabComposite> tabComposites;
	private LogTabComposite highlightedTabComposite;
	private List<Composite> loadingTabComposites;	

	public LogComposite(Composite parent, int style, HistoryModel historyModel, MainPanel mainPanel) {
		super(parent, style);
				
		this.historyModel = historyModel;
		this.mainPanel = mainPanel;
		
		this.pendingLogFolderRequest = null;
		this.eventBus = GuiEventBus.getAndRegister(this);
		
		this.scrollComposite = null;
		this.logContentComposite = null;
		
		this.tabComposites = Maps.newConcurrentMap();
		this.highlightedTabComposite = null;
		this.loadingTabComposites = Lists.newArrayList();		
		
		this.createContents();
	}	
	
	private void createContents() {
		createMainComposite();
		createMainPanel();		
		
		replaceScrollEventHandling();
		redrawAll();
	}	

	private void resetAndDisposeAll() {
		for (Control control : logContentComposite.getChildren()) {
			control.dispose();
		}
		
		tabComposites.clear();			
		highlightedTabComposite = null;
	}
	
	private void createMainComposite() {
		// Main composite
		GridLayout mainCompositeGridLayout = new GridLayout(3, false);
		mainCompositeGridLayout.marginTop = 0;
		mainCompositeGridLayout.marginLeft = 0;
		mainCompositeGridLayout.marginRight = 0;
		mainCompositeGridLayout.horizontalSpacing = 0;
		mainCompositeGridLayout.verticalSpacing = 0;
		mainCompositeGridLayout.marginHeight = 0;
		mainCompositeGridLayout.marginWidth = 0;
		
		setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1));
		setLayout(mainCompositeGridLayout);		
	}	
	
	private void createMainPanel() {
		GridLayout mainCompositeGridLayout = new GridLayout(1, false);
		
		scrollComposite = new ScrolledComposite(this, SWT.V_SCROLL);
		scrollComposite.setLayout(mainCompositeGridLayout);
		scrollComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
	   
		logContentComposite = new Composite(scrollComposite, SWT.NONE);	
		logContentComposite.setLayout(mainCompositeGridLayout);
		logContentComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));		
		
	    scrollComposite.setExpandVertical(true);
	    scrollComposite.setExpandHorizontal(true);
		scrollComposite.setContent(logContentComposite);
		scrollComposite.setShowFocusedControl(true);		
	}	

	private void replaceScrollEventHandling() {
		// Disables the default scrolling functionality of the ScrolledComposite
		// and replaces it by manually scrolling.
		
		Display.getDefault().addFilter(SWT.MouseWheel, new Listener() {
			@Override
			public void handleEvent(Event e) {
				if (e.widget.equals(logContentComposite)) {
					e.doit = false;
					scrollBy(e.count);
				}
			}
		});
	}
	
	public void redrawAll() {
		logContentComposite.layout();
		layout();
		
		scrollComposite.setMinSize(logContentComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT));
		scrollComposite.setRedraw(true);
	}	

	@Subscribe
	public void onModelSelectedRootUpdatedEvent(ModelSelectedRootUpdatedEvent event) {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				sendLogFolderRequest(0);
			}
		});
	}
	
	public void sendLogFolderRequest(int startIndex) {
		LogOperationOptions logOptions = new LogOperationOptions();
		logOptions.setMaxDatabaseVersionCount(LOG_REQUEST_DATABASE_COUNT);
		logOptions.setMaxFileHistoryCount(LOG_REQUEST_FILE_COUNT);
		logOptions.setStartDatabaseVersionIndex(startIndex);
		
		pendingLogFolderRequest = new LogFolderRequest();
		pendingLogFolderRequest.setRoot(historyModel.getSelectedRoot());
		pendingLogFolderRequest.setOptions(logOptions);
		
		eventBus.post(pendingLogFolderRequest);
	}
	
	@Subscribe
	public void onLogFolderResponse(final LogFolderResponse logResponse) {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				if (pendingLogFolderRequest != null && pendingLogFolderRequest.getId() == logResponse.getRequestId()) {
					updateTabs(pendingLogFolderRequest, logResponse);
					mainPanel.showLog();
					
					pendingLogFolderRequest = null;
				}				
			}
		});		
	}

	public void updateTabs(LogFolderRequest logRequest, LogFolderResponse logResponse) {
		// Dispose all existing tabs (if this is the first request)
		boolean firstRequest = logRequest.getOptions().getStartDatabaseVersionIndex() == 0;
		
		if (firstRequest) {
			resetAndDisposeAll();
		}
		
		// Dispose all loading tabs
		while (loadingTabComposites.size() > 0) {
			loadingTabComposites.remove(0).dispose();
		}
		
		// And create new tabs		
		List<LightweightDatabaseVersion> newDatabaseVersions = logResponse.getResult().getDatabaseVersions();
		int databaseVersionIndex = logRequest.getOptions().getStartDatabaseVersionIndex();
		
		for (LightweightDatabaseVersion databaseVersion : newDatabaseVersions) {
			if (databaseVersion.getChangeSet().hasChanges()) {
				LogTabComposite tabComposite = new LogTabComposite(this, logContentComposite, historyModel.getSelectedRoot(),
						databaseVersionIndex, databaseVersion);

				tabComposites.put(databaseVersion.getDate(), tabComposite);
			}
			
			databaseVersionIndex++;			
		}
		
		// Add 'Loading ...' panel (if potentially more databases there)
		if (newDatabaseVersions.size() == LOG_REQUEST_DATABASE_COUNT) {
			createLoadingComposite();
		}		
		
		// Highlight
		highlightByDate(historyModel.getSelectedDate());
				
		// Then redraw!
		redrawAll();
	}

	@Subscribe
	public void onModelSelectedDateUpdatedEvent(final ModelSelectedDateUpdatedEvent event) {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				highlightByDate(event.getSelectedDate());
			}
		});
	}
	
	public synchronized void highlightByDate(Date highlightDate) {
		// De-highlight
		if (highlightedTabComposite != null) {
			highlightedTabComposite.setHighlighted(false);
		}
		
		// Highlight new tab
		if (highlightDate != null) {
			LogTabComposite tabComposite = tabComposites.get(highlightDate);
			
			if (tabComposite != null) {
				tabComposite.setHighlighted(true);				
				tabComposite.setFocus(); // The scroll composite will scroll to it.
				
				highlightedTabComposite = tabComposite;
			}
		}
	}

	public void scrollBy(int count) {		
		int increment = scrollComposite.getVerticalBar().getIncrement();
		scrollComposite.setOrigin(0, scrollComposite.getOrigin().y - increment*count);		
	}	
	
	private void createLoadingComposite() {
		GridLayout loadingCompositeGridLayout = new GridLayout(1, false);
		loadingCompositeGridLayout.marginTop = 0;
		loadingCompositeGridLayout.marginLeft = 0;
		loadingCompositeGridLayout.marginRight = 0;
		loadingCompositeGridLayout.marginBottom = 0;
		loadingCompositeGridLayout.horizontalSpacing = 0;
		loadingCompositeGridLayout.verticalSpacing = 0;

		Composite loadingComposite = new Composite(logContentComposite, SWT.BORDER);
		loadingComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 1, 1));		
		loadingComposite.setLayout(loadingCompositeGridLayout);		
		loadingComposite.setBackground(WidgetDecorator.WHITE);	
		loadingComposite.setBackgroundMode(SWT.INHERIT_FORCE);
		
		Label loadMoreLabel = new Label(loadingComposite, SWT.CENTER);
		loadMoreLabel.setText(I18n.getText("org.syncany.gui.history.LogComposite.loading"));
		
		loadingComposite.addPaintListener(new PaintListener() {			
			@Override
			public void paintControl(PaintEvent e) {
				if (pendingLogFolderRequest == null) {
					int newStartDatabaseIndex = tabComposites.size();
					sendLogFolderRequest(newStartDatabaseIndex);
				}
			}
		});
		
		loadingTabComposites.add(loadingComposite);
	}
	
	public void onSelectDatabaseVersion(LightweightDatabaseVersion databaseVersion) {
		historyModel.setSelectedDate(databaseVersion.getDate());
	}
	
	public void onDoubleClickDatabaseVersion(LightweightDatabaseVersion databaseVersion) {
		mainPanel.showTree();
	}

	public void onFileJumpToTree(LightweightDatabaseVersion databaseVersion, String relativeFilePath) {
		historyModel.setSelectedDate(databaseVersion.getDate());
		historyModel.setSelectedFilePath(relativeFilePath);
		
		mainPanel.showTree();				
	}

	public void onFileOpen(LightweightDatabaseVersion databaseVersion, String relativeFilePath) {
		final File file = new File(historyModel.getSelectedRoot(), relativeFilePath);
		launchOrDisplayError(file);
	}

	public void onFileOpenContainingFolder(LightweightDatabaseVersion databaseVersion, String relativeFilePath) {
		final File file = new File(historyModel.getSelectedRoot(), relativeFilePath);
		launchOrDisplayError(file.getParentFile());
	}

	public void onFileCopytoClipboard(LightweightDatabaseVersion databaseVersion, String relativeFilePath) {
		final File file = new File(historyModel.getSelectedRoot(), relativeFilePath);
		DesktopUtil.copyToClipboard(file.getAbsolutePath());
	}

	private void launchOrDisplayError(File file) {
		if (file.exists()) {
			DesktopUtil.launch(file.getAbsolutePath());	
		}
		else {
			MessageBox messageBox = new MessageBox(mainPanel.getShell(), SWT.ICON_WARNING | SWT.OK);	        
	        messageBox.setText(I18n.getText("org.syncany.gui.history.LogTabComposite.warningNotExist.title"));
	        messageBox.setMessage(I18n.getText("org.syncany.gui.history.LogTabComposite.warningNotExist.description", file.getAbsolutePath()));
	        
	        messageBox.open();
		}
	}
	
	public void dispose() {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {	
				eventBus.unregister(LogComposite.this);
				
				for (LogTabComposite tabComposite : tabComposites.values()) {
					tabComposite.dispose();
				}
			}
		});
		
		super.dispose();
	}
}
