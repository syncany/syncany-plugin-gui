package org.syncany.gui.history;

import org.eclipse.swt.widgets.Composite;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class LoadingComposite extends ImageComposite {
	private static final String IMAGE_LOADING_RESOURCE = "/" + LoadingComposite.class.getPackage().getName().replace('.', '/') + "/loading-bar.gif";
	private static final int IMAGE_LOADING_FRAME_RATE = 90; // ms per image
	
	public LoadingComposite(Composite composite, int style) {
		super(composite, style);
		setAnimatedImage(IMAGE_LOADING_RESOURCE, IMAGE_LOADING_FRAME_RATE);
	}
}
