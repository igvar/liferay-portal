/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.fragment.web.internal.handler;

import com.liferay.fragment.exception.DuplicateFragmentCollectionKeyException;
import com.liferay.fragment.exception.DuplicateFragmentEntryKeyException;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.servlet.SessionErrors;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.ResourceBundleLoader;
import com.liferay.portal.kernel.util.WebKeys;

import java.util.ResourceBundle;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Eudaldo Alonso
 */
@Component(
	immediate = true, service = ImportActionExceptionRequestHandler.class
)
public class ImportActionExceptionRequestHandler {

	public void handleException(
			ActionRequest actionRequest, ActionResponse actionResponse,
			Exception e)
		throws Exception {

		ThemeDisplay themeDisplay = (ThemeDisplay)actionRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		String errorMessage =
			"an-unexpected-error-occurred-while-importing-your-file";

		if (e instanceof DuplicateFragmentEntryKeyException) {
			errorMessage = "a-fragment-entry-with-the-key-x-already-exists";
		}
		else if (e instanceof DuplicateFragmentCollectionKeyException) {
			errorMessage =
				"a-fragment-collection-with-the-key-x-already-exists";
		}

		ResourceBundle resourceBundle =
			_resourceBundleLoader.loadResourceBundle(themeDisplay.getLocale());

		SessionErrors.add(
			actionRequest, e.getClass(),
			LanguageUtil.format(resourceBundle, errorMessage, e.getMessage()));
	}

	@Reference(
		target = "(bundle.symbolic.name=com.liferay.fragment.web)", unbind = "-"
	)
	private ResourceBundleLoader _resourceBundleLoader;

}