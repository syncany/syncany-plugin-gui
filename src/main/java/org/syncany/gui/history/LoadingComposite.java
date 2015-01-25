package org.syncany.gui.history;

import java.util.Timer;
import java.util.TimerTask;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class LoadingComposite extends Composite {
	private static final String IMAGE_LOADING_RESOURCE = "/" + LoadingComposite.class.getPackage().getName().replace('.', '/') + "/loading.gif";

	private Timer imageTimer;
	private ImageLoader loader;
	private Image image;
	private int imageNumber;
	private Canvas canvas;
	private GC gc;
	
	public LoadingComposite(Composite composite, int style) {
		super(composite, style);

		this.setBackgroundImage(null);
		this.setBackgroundMode(SWT.INHERIT_DEFAULT);
		
		this.createContents();
	}
	
	private void createContents() {
		createMainComposite();
		createCanves();
		
		createAndStartImageTimer();	
	}	

	private void createMainComposite() {
		// Main composite
		GridLayout mainCompositeGridLayout = new GridLayout(1, false);
		mainCompositeGridLayout.marginTop = 0;
		mainCompositeGridLayout.marginLeft = 0;
		mainCompositeGridLayout.marginRight = 0;
		mainCompositeGridLayout.horizontalSpacing = 0;
		mainCompositeGridLayout.verticalSpacing = 0;
		mainCompositeGridLayout.marginHeight = 0;
		mainCompositeGridLayout.marginWidth = 0;
		
		setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		setLayout(mainCompositeGridLayout);		
	}	
	
	private void createCanves() {
		canvas = new Canvas(this, SWT.NONE);
		canvas.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		
		loader = new ImageLoader();
		loader.load(LoadingComposite.class.getResourceAsStream(IMAGE_LOADING_RESOURCE));

		image = new Image(Display.getDefault(), loader.data[0]);		
		
		gc = new GC(image);
		canvas.addPaintListener(new PaintListener() {
			public void paintControl(PaintEvent event) {
				int posX = (canvas.getSize().x - image.getBounds().width) / 2;
				int posY = (canvas.getSize().y - image.getBounds().height) / 2;
				
				event.gc.drawImage(image, posX, posY);
			}
		});
	}

	private void createAndStartImageTimer() {
		imageTimer = new Timer();
		
		imageTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				nextImage();
			}
		}, 0, 90);
	}
	
	private void nextImage() {
		Display.getDefault().syncExec(new Runnable(){
			public void run() {
				if (!LoadingComposite.this.isDisposed()) {
					imageNumber = (imageNumber == loader.data.length - 1) ? 0 : imageNumber + 1;
	
					ImageData nextFrameData = loader.data[imageNumber];
					Image frameImage = new Image(Display.getDefault(), nextFrameData);
	
					gc.drawImage(frameImage, nextFrameData.x, nextFrameData.y);
	
					frameImage.dispose();
					canvas.redraw();
				}
            }
        });
	}
	
	@Override
	public void dispose() {
		imageTimer.cancel();		
		super.dispose();
	}
}
