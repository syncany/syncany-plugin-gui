package org.syncany.gui.history;

import java.util.Date;
import java.util.List;
import java.util.Map;

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
import org.syncany.config.GuiEventBus;
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
	private ScrolledComposite scrollComposite;
	private Composite logContentComposite;
	
	private Map<Date, LogTabComposite> tabComposites;
	private LogTabComposite highlightedTabComposite;
	private List<Composite> loadingTabComposites;	

	public LogComposite(Composite parent, int style) {
		super(parent, style);
				
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
	
	private void redrawAll() {
		logContentComposite.layout();
		layout();
		
		scrollComposite.setMinSize(logContentComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT));
		scrollComposite.setRedraw(true);
	}
	
	private void updateTabs(LogFolderRequest logRequest, LogFolderResponse logResponse) {
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

		for (LightweightDatabaseVersion databaseVersion : newDatabaseVersions) {
			if (databaseVersion.getChangeSet().hasChanges()) {			
				LogTabComposite tabComposite = new LogTabComposite(mainPanel, this, logContentComposite, state.getSelectedRoot(), databaseVersion);			
				tabComposites.put(databaseVersion.getDate(), tabComposite);
			}
		}
		
		// Add 'Loading ...' panel (if potentially more databases there)
		if (newDatabaseVersions.size() == LOG_REQUEST_DATABASE_COUNT) {
			createLoadingComposite();
		}		
		
		// Highlight
		highlightBySelectedDate();
				
		// Then redraw!
		redrawAll();
	}

	public void highlightBySelectedDate() {
		highlightByDate(state.getSelectedDate());
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
					resetAndRefresh(newStartDatabaseIndex);
				}
			}
		});
		
		loadingTabComposites.add(loadingComposite);
	}
}
