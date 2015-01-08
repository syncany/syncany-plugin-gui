package org.syncany.gui.history;

import java.util.ArrayList;
import java.util.Date;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.syncany.config.GuiEventBus;
import org.syncany.operations.ChangeSet;
import org.syncany.operations.daemon.messages.LogFolderRequest;
import org.syncany.operations.daemon.messages.LogFolderResponse;
import org.syncany.operations.log.LightweightDatabaseVersion;
import org.syncany.operations.log.LogOperationOptions;
import org.syncany.operations.log.LogOperationResult;

import com.google.common.eventbus.Subscribe;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class LogComposite extends Composite {
	private MainPanel mainPanel;
	private MainPanelState state;
	
	private Composite mainComposite;
	
	private LogFolderRequest pendingLogFolderRequest;

	private GuiEventBus eventBus;

	public LogComposite(MainPanel mainPanel, MainPanelState state, Composite parent, int style) {
		super(parent, style);

		this.mainPanel = mainPanel;
		this.state = state;		
				
		this.pendingLogFolderRequest = null;

		this.eventBus = GuiEventBus.getInstance();
		this.eventBus.register(this);	
		
		this.createContents();
	}	
	
	private void createContents() {
		createMainComposite();
		createMainPanel();
		
		sendLogRequest();
		
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

		
		ArrayList<LightweightDatabaseVersion> dbv = new ArrayList<>();
		
		dbv.add(d1);
		dbv.add(d2);		
		
		LogOperationResult lr = new LogOperationResult();
		lr.setDatabaseVersions(dbv);
		
		LogFolderResponse ls = new LogFolderResponse();
		ls.setResult(lr);
		
		updateTabs(ls);
	}	

	private void sendLogRequest() {
		pendingLogFolderRequest = new LogFolderRequest();
		pendingLogFolderRequest.setRoot("/home/pheckel/Syncany/Syncany Team");
		pendingLogFolderRequest.setOptions(new LogOperationOptions());
		
		eventBus.post(pendingLogFolderRequest);
	}

	private void createMainComposite() {
		// Main composite
		GridLayout mainCompositeGridLayout = new GridLayout(3, false);
		mainCompositeGridLayout.marginTop = 0;
		mainCompositeGridLayout.marginLeft = 0;
		mainCompositeGridLayout.marginRight = 0;

		setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1));
		setLayout(mainCompositeGridLayout);		
	}	
	
	private void createMainPanel() {
		mainComposite = new Composite(this, SWT.NONE);	
		mainComposite.setLayout(new GridLayout());
		mainComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1));
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
		for (Control control : mainComposite.getChildren()) {
			control.dispose();
		}
		
		for (LightweightDatabaseVersion databaseVersion : logResponse.getResult().getDatabaseVersions()) {
			System.out.println(databaseVersion);
			GridData pluginSelectCompositeGridData = new GridData(SWT.FILL, SWT.TOP, true, false, 3, 1);
			pluginSelectCompositeGridData.horizontalIndent = 0;
			pluginSelectCompositeGridData.minimumHeight = 40;
			
			TabComposite tabComposite = new TabComposite(mainComposite, databaseVersion);
			tabComposite.setLayoutData(pluginSelectCompositeGridData);			
		}		
		
		mainComposite.layout();
		layout();
	}
}
