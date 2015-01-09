package org.syncany.gui.history;

import java.util.ArrayList;
import java.util.Date;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.syncany.config.GuiEventBus;
import org.syncany.operations.ChangeSet;
import org.syncany.operations.daemon.messages.LogFolderRequest;
import org.syncany.operations.daemon.messages.LogFolderResponse;
import org.syncany.operations.log.LightweightDatabaseVersion;
import org.syncany.operations.log.LogOperationOptions;
import org.syncany.operations.log.LogOperationResult;

import com.google.common.collect.Maps;
import com.google.common.eventbus.Subscribe;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class LogComposite extends Composite {
	private MainPanel mainPanel;
	private MainPanelState state;
	
	private ScrolledComposite scrollComposite;
	private Composite logContentComposite;
	
	private Map<Date, TabComposite> tabComposites;
	private TabComposite highlightedTabComposite;
	
	private LogFolderRequest pendingLogFolderRequest;

	private GuiEventBus eventBus;

	public LogComposite(MainPanel mainPanel, MainPanelState state, Composite parent, int style) {
		super(parent, style);

		this.mainPanel = mainPanel;
		this.state = state;		
				
		this.pendingLogFolderRequest = null;
		
		this.tabComposites = Maps.newConcurrentMap();
		this.highlightedTabComposite = null;
		
		this.eventBus = GuiEventBus.getInstance();
		this.eventBus.register(this);	
		
		this.createContents();
	}	
	
	private void createContents() {
		createMainComposite();
		createMainPanel();		
		
		replaceScrollEventHandling();
		
		ChangeSet c1 = new ChangeSet();
		c1.getChangedFiles().add("file1.txt");
		c1.getChangedFiles().add("file2.txt");
		c1.getChangedFiles().add("file3.txt");
		c1.getDeletedFiles().add("deleted1.txt");
		c1.getDeletedFiles().add("deleted2.txt");
		
		LightweightDatabaseVersion d1 = new LightweightDatabaseVersion();
		d1.setDate(new Date());
		d1.setChangeSet(c1);		
		
		ChangeSet c2 = new ChangeSet();
		c2.getNewFiles().add("image1.jpg");
		c2.getNewFiles().add("image4.jpg");
		c2.getNewFiles().add("image3.jpg");
		c2.getDeletedFiles().add("deleted1123.txt");
		
		LightweightDatabaseVersion d2 = new LightweightDatabaseVersion();
		d2.setDate(new Date());
		d2.setChangeSet(c2);			

		ChangeSet c3 = new ChangeSet();
		c3.getNewFiles().add("image1.jpsdg");
		c3.getNewFiles().add("image4.jdpdg");
		c3.getNewFiles().add("image1.jpsg");
		c3.getNewFiles().add("image4.jdspg");
		c3.getNewFiles().add("imagfsde3.jpg");
		c3.getNewFiles().add("image1.jpgds");
		c3.getNewFiles().add("image4sfdds.jpg");
		c3.getNewFiles().add("image3.sdjspg");
		c3.getNewFiles().add("ifsmageds1.jpg");
		c3.getNewFiles().add("imaddgde4.jpg");
		c3.getNewFiles().add("imagfesd3.jpg");
		c3.getNewFiles().add("imagdde1.jpg");
		c3.getNewFiles().add("imagde4.jpg");
		c3.getNewFiles().add("imagedd3.jpg");
		c3.getNewFiles().add("image3d.jpg");
		c3.getDeletedFiles().add("deleted1123.txt");
		
		LightweightDatabaseVersion d3 = new LightweightDatabaseVersion();
		d3.setDate(new Date());
		d3.setChangeSet(c3);			
		
		ArrayList<LightweightDatabaseVersion> dbv = new ArrayList<>();
		
		dbv.add(d1);
		dbv.add(d2);	
		dbv.add(d3);		
		
		LogOperationResult lr = new LogOperationResult();
		lr.setDatabaseVersions(dbv);
		
		LogFolderResponse ls = new LogFolderResponse();
		ls.setResult(lr);
		
		updateTabs(ls);
		redrawAll();
	}	

	private void resetAndDisposeAll() {
		for (Control control : logContentComposite.getChildren()) {
			control.dispose();
		}
		
		tabComposites.clear();			
		highlightedTabComposite = null;
	}
	
	public void resetAndRefresh() {
		pendingLogFolderRequest = new LogFolderRequest();
		pendingLogFolderRequest.setRoot(state.getSelectedRoot());
		pendingLogFolderRequest.setOptions(new LogOperationOptions());
		
		eventBus.post(pendingLogFolderRequest);
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
	
	public void safeDispose() {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {	
				eventBus.unregister(LogComposite.this);
			}
		});
	}			
	
	@Subscribe
	public void onLogFolderResponse(final LogFolderResponse logResponse) {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				if (pendingLogFolderRequest != null && pendingLogFolderRequest.getId() == logResponse.getRequestId()) {
					updateTabs(logResponse);
				}				
			}
		});		
	}

	private void updateTabs(LogFolderResponse logResponse) {
		// Clear all
		resetAndDisposeAll();
		
		// And create new ones		
		for (LightweightDatabaseVersion databaseVersion : logResponse.getResult().getDatabaseVersions()) {
			TabComposite tabComposite = new TabComposite(mainPanel, this, logContentComposite, databaseVersion);			
			tabComposites.put(databaseVersion.getDate(), tabComposite);
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
			TabComposite tabComposite = tabComposites.get(highlightDate);
			
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
}
