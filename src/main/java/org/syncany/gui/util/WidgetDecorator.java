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
package org.syncany.gui.util;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Text;
import org.syncany.util.EnvironmentUtil;

/**
 * @author Vincent Wiencek <vwiencek@gmail.com>
 *
 */
public class WidgetDecorator {
	public static final int VERTICAL_INDENT = 20;

	public static final int DEFAULT_BUTTON_WIDTH = 100;
	public static final int DEFAULT_BUTTON_HEIGHT = 30;

	public static final Color INVALID_TEXT_COLOR = SWTResourceManager.getColor(255, 218, 185);
	public static final Color RED = SWTResourceManager.getColor(SWT.COLOR_RED);
	public static final Color WHITE = SWTResourceManager.getColor(SWT.COLOR_WHITE);
	public static final Color BLACK = SWTResourceManager.getColor(SWT.COLOR_BLACK);
	public static final Color GRAY = SWTResourceManager.getColor(SWT.COLOR_GRAY);
	public static final Color DARK_GRAY = SWTResourceManager.getColor(SWT.COLOR_DARK_GRAY);
	public static final Color LIGHT_GRAY = SWTResourceManager.getColor(220, 220, 220);
	public static final Color BLUE_LINK = SWTResourceManager.getColor(0, 147, 173);
	
	public static final Color COLOR_WIDGET = SWTResourceManager.getColor(SWT.COLOR_WIDGET_BACKGROUND);

	private static String FONT_NAME = "Segoe UI";
	private static int FONT_SIZE = EnvironmentUtil.isMacOSX() ? 10 : EnvironmentUtil.isWindows() ? 9 : 9;

	private static Font FONT_TITLE = SWTResourceManager.getFont(FONT_NAME, FONT_SIZE + 5, SWT.NORMAL);
	private static Font FONT_BIGGER = SWTResourceManager.getFont(FONT_NAME, FONT_SIZE + 2, SWT.BOLD);
	private static Font FONT_NORMAL = SWTResourceManager.getFont(FONT_NAME, FONT_SIZE, SWT.NORMAL);
	private static Font FONT_SMALLER = SWTResourceManager.getFont(FONT_NAME, FONT_SIZE - 1, SWT.NORMAL);
	private static Font FONT_BOLD = SWTResourceManager.getFont(FONT_NAME, FONT_SIZE, SWT.BOLD);

	public static void title(Control... controls) {
		font(FONT_TITLE, controls);
	}

	public static void bigger(Control... controls) {
		font(FONT_BIGGER, controls);
	}
	
	public static void smaller(Control... controls) {
		font(FONT_SMALLER, controls);
	}

	public static void normal(Control... controls) {
		font(FONT_NORMAL, controls);
	}
	
	public static void bold(Control... controls) {
		font(FONT_BOLD, controls);
	}

	public static void font(Font font, Control... controls) {
		for (Control control : controls) {
			font(font, control);
			
			if (control instanceof Text) {
				enhanceFocus((Text) control);
			}
		}
	}

	private static void font(Font font, Control control) {
		control.setFont(font);
	}

	private static void enhanceFocus(Text control) {
		final Text text = (Text) control;
		
		text.addFocusListener(new FocusAdapter() {
			@Override
			public void focusGained(FocusEvent e) {
				text.selectAll();
			}
		});
	}

	public static void markAsInvalid(Control control) {
		control.setBackground(INVALID_TEXT_COLOR);
	}

	public static void markAsValid(Control control) {
		control.setBackground(WHITE);
	}
}