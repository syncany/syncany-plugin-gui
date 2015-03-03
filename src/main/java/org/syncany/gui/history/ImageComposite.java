/*
 * Syncany, www.syncany.org
 * Copyright (C) 2011-2015 Philipp C. Heckel <philipp.heckel@gmail.com>
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
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;

/**
 * This composite can display either an animated GIF,
 * or a still image.
 *
 * <p>Using the {@link #setImage(Image) setImage()} method, a still
 * PNG/GIF/JPEG image can be displayed. Using
 * {@link #setAnimatedImage(String, int) setAnimatedImage()}, an animated
 * GIF image can be displayed.
 *
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class ImageComposite extends Composite {
	public ImageComposite(Composite composite, int style) {
		super(composite, style);

		GridLayout mainCompositeGridLayout = new GridLayout(1, false);
		mainCompositeGridLayout.marginTop = 0;
		mainCompositeGridLayout.marginLeft = 0;
		mainCompositeGridLayout.marginRight = 0;
		mainCompositeGridLayout.horizontalSpacing = 0;
		mainCompositeGridLayout.verticalSpacing = 0;
		mainCompositeGridLayout.marginHeight = 0;
		mainCompositeGridLayout.marginWidth = 0;

		setLayout(mainCompositeGridLayout);
		setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
	}

	/**
	 * Set and display a still image on the composite.
	 *
	 * @param image Image to display
	 */
	public void setImage(Image image) {
		disposeControls();

		Label imageLabel = new Label(this, SWT.NONE);
		imageLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
		imageLabel.setImage(image);

		layout();
	}

	/**
	 * Set and display an animated GIF image on the composite.
	 * The animation start immediately and will run at the given frame rate.
	 *
	 * @param resourceStr Resource identifier for the GIF image to play
	 * @param frameRate Frame rate / speed at which to play the image (in ms / image; less is faster)
	 */
	public void setAnimatedImage(String resourceStr, int frameRate) {
		disposeControls();

		AnimatedGifComposite animatedGifComposite = new AnimatedGifComposite(this, SWT.NONE, resourceStr, frameRate);
		animatedGifComposite.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true));

		layout();
	}

	private void disposeControls() {
		for (Control control : getChildren()) {
			control.dispose();
		}
	}

	@Override
	public void dispose() {
		disposeControls();
	}

	private class AnimatedGifComposite extends Composite {
		private String resourceStr;
		private int frameRate;

		private Timer imageTimer;
		private ImageLoader loader;
		private Image image;
		private int imageNumber;
		private Canvas canvas;
		private GC gc;

		public AnimatedGifComposite(Composite composite, int style, String resourceStr, int frameRate) {
			super(composite, style);

			this.resourceStr = resourceStr;
			this.frameRate = frameRate;

			this.setBackgroundImage(null);
			this.setBackgroundMode(SWT.INHERIT_DEFAULT);

			this.createContents();
		}

		private void createContents() {
			createCanvas();
			createAndStartImageTimer();
		}

		private void createCanvas() {
			loader = new ImageLoader();
			loader.load(ImageComposite.class.getResourceAsStream(resourceStr));

			canvas = new Canvas(this, SWT.NONE);
			image = new Image(Display.getDefault(), loader.data[0]);
			gc = new GC(image);

			canvas.addPaintListener(new PaintListener() {
				public void paintControl(PaintEvent event) {
					paintImage(event);
				}
			});

			canvas.setSize(image.getBounds().width, image.getBounds().height);
		}

		private void paintImage(PaintEvent event) {
			int posX = (canvas.getSize().x - image.getBounds().width) / 2;
			int posY = (canvas.getSize().y - image.getBounds().height) / 2;

			event.gc.drawImage(image, posX, posY);
		}

		private void createAndStartImageTimer() {
			imageTimer = new Timer();

			imageTimer.schedule(new TimerTask() {
				@Override
				public void run() {
					nextImage();
				}
			}, 0, frameRate);
		}

		private void nextImage() {
			Display.getDefault().syncExec(new Runnable() {
				public void run() {
					try {
						synchronized(ImageComposite.this) {
							imageNumber = (imageNumber == loader.data.length - 1) ? 0 : imageNumber + 1;

							ImageData nextFrameData = loader.data[imageNumber];
							Image frameImage = new Image(Display.getDefault(), nextFrameData);

							if (!gc.isDisposed()) {
								gc.drawImage(frameImage, nextFrameData.x, nextFrameData.y);
							}

							if (!canvas.isDisposed()) {
								canvas.redraw();
							}

							frameImage.dispose();
						}
					}
					catch (Exception e) {
						// Might be disposed. We don't care, because another image
						// is in the process of being initialized.
					}
	            }
	        });
		}

		@Override
		public void dispose() {
			synchronized(this) {
				imageTimer.cancel();
				super.dispose();
			}
		}
	}
}
