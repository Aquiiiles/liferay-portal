/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.migration.template;

import com.liferay.dynamic.data.mapping.model.DDMTemplate;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.template.TemplateConstants;
import com.liferay.portal.kernel.util.LocaleUtil;

import java.util.Objects;

/**
 * @author Albert Gomes Cabral
 */
public class CMSMigrationDDMTemplateUtil {

	public static String getLabel(DDMTemplate ddmTemplate) {
		return StringBundler.concat(
			ddmTemplate.getName(LocaleUtil.getSiteDefault()), " (ID ",
			ddmTemplate.getTemplateId(), ", key ", ddmTemplate.getTemplateKey(),
			")");
	}

	public static boolean isFreeMarker(DDMTemplate ddmTemplate) {
		return Objects.equals(
			ddmTemplate.getLanguage(), TemplateConstants.LANG_TYPE_FTL);
	}

}