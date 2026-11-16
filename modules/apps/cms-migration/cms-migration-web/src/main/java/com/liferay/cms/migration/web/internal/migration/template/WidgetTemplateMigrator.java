/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.migration.template;

import com.liferay.asset.kernel.model.AssetEntry;
import com.liferay.blogs.model.BlogsEntry;
import com.liferay.cms.migration.web.internal.constants.CMSMigrationConstants;
import com.liferay.cms.migration.web.internal.migration.CMSMigrationContext;
import com.liferay.dynamic.data.mapping.constants.DDMTemplateConstants;
import com.liferay.dynamic.data.mapping.model.DDMTemplate;
import com.liferay.dynamic.data.mapping.service.DDMTemplateLocalService;
import com.liferay.journal.model.JournalArticle;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.template.TemplateConstants;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portlet.display.template.PortletDisplayTemplate;

import jakarta.portlet.PortletPreferences;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Albert Gomes Cabral
 */
public class WidgetTemplateMigrator {

	public WidgetTemplateMigrator(
		DDMTemplateLocalService ddmTemplateLocalService, Portal portal,
		WidgetTemplateScriptRewriter widgetTemplateScriptRewriter) {

		_ddmTemplateLocalService = ddmTemplateLocalService;
		_portal = portal;
		_widgetTemplateScriptRewriter = widgetTemplateScriptRewriter;
	}

	public String getBlogsWidgetTemplateKey(
			CMSMigrationContext cmsMigrationContext)
		throws Exception {

		long classNameId = _portal.getClassNameId(AssetEntry.class);

		DDMTemplate ddmTemplate = _ddmTemplateLocalService.fetchTemplate(
			cmsMigrationContext.getSourceGroupId(), classNameId,
			_BLOGS_WIDGET_TEMPLATE_KEY);

		if (ddmTemplate != null) {
			return ddmTemplate.getTemplateKey();
		}

		ddmTemplate = _ddmTemplateLocalService.addTemplate(
			CMSMigrationConstants.
				EXTERNAL_REFERENCE_CODE_PREFIX_WIDGET_TEMPLATE + "BLOGS",
			cmsMigrationContext.getUserId(),
			cmsMigrationContext.getSourceGroupId(), classNameId, 0,
			_portal.getClassNameId(PortletDisplayTemplate.class),
			_BLOGS_WIDGET_TEMPLATE_KEY,
			HashMapBuilder.put(
				LocaleUtil.getSiteDefault(), "Blogs (CMS Migration)"
			).build(),
			null, DDMTemplateConstants.TEMPLATE_TYPE_DISPLAY, StringPool.BLANK,
			TemplateConstants.LANG_TYPE_FTL,
			StringUtil.read(
				WidgetTemplateMigrator.class, "dependencies/blogs_entries.ftl"),
			true, false, StringPool.BLANK, null,
			cmsMigrationContext.getServiceContext());

		return ddmTemplate.getTemplateKey();
	}

	public void migrate(
			CMSMigrationContext cmsMigrationContext, String displayStyle,
			String label, PortletPreferences portletPreferences)
		throws Exception {

		String templateKey = displayStyle.substring(
			PortletDisplayTemplate.DISPLAY_STYLE_PREFIX.length());

		DDMTemplate ddmTemplate = _ddmTemplateLocalService.fetchTemplate(
			cmsMigrationContext.getSourceGroupId(),
			_portal.getClassNameId(AssetEntry.class), templateKey);

		if (ddmTemplate == null) {
			return;
		}

		label = StringBundler.concat(
			label, " uses the widget template ",
			CMSMigrationDDMTemplateUtil.getLabel(ddmTemplate), ", which ");

		String script = ddmTemplate.getScript();

		boolean blogsEntryListed = _isListed(
			BlogsEntry.class.getName(), cmsMigrationContext,
			portletPreferences);
		boolean journalArticleListed = _isListed(
			JournalArticle.class.getName(), cmsMigrationContext,
			portletPreferences);

		boolean migrated = false;

		if (CMSMigrationDDMTemplateUtil.isFreeMarker(ddmTemplate) &&
			(blogsEntryListed != journalArticleListed)) {

			if (blogsEntryListed) {
				script = _migrateBlogsEntryWidgetTemplate(
					cmsMigrationContext, ddmTemplate, label);
			}
			else {
				script = _migrateJournalArticleWidgetTemplate(
					cmsMigrationContext, ddmTemplate, label);
			}

			migrated = true;
		}

		_warnUnsupportedAPIs(cmsMigrationContext, label, migrated, script);
	}

	private boolean _isListed(
		String className, CMSMigrationContext cmsMigrationContext,
		PortletPreferences portletPreferences) {

		Map<String, ObjectDefinition> objectDefinitions =
			cmsMigrationContext.getObjectDefinitions();

		for (Map.Entry<String, ObjectDefinition> entry :
				objectDefinitions.entrySet()) {

			String key = entry.getKey();

			if (!key.startsWith(className + StringPool.POUND)) {
				continue;
			}

			ObjectDefinition objectDefinition = entry.getValue();

			if (ArrayUtil.contains(
					portletPreferences.getValues("classNameIds", new String[0]),
					String.valueOf(
						_portal.getClassNameId(
							objectDefinition.getClassName())))) {

				return true;
			}

			for (String assetEntryXml :
					portletPreferences.getValues(
						"assetEntryXml", new String[0])) {

				if (assetEntryXml.contains(objectDefinition.getClassName())) {
					return true;
				}
			}
		}

		return false;
	}

	private String _migrate(
			String className, CMSMigrationContext cmsMigrationContext,
			String contentName, DDMTemplate ddmTemplate, String label)
		throws Exception {

		String script = ddmTemplate.getScript();

		Set<String> unsupportedMethodNames = new LinkedHashSet<>();

		String rewrittenScript = _widgetTemplateScriptRewriter.rewrite(
			className, script, unsupportedMethodNames);

		if (!rewrittenScript.equals(script)) {
			_ddmTemplateLocalService.updateTemplate(
				cmsMigrationContext.getUserId(), ddmTemplate.getTemplateId(),
				ddmTemplate.getClassPK(), ddmTemplate.getNameMap(),
				ddmTemplate.getDescriptionMap(), ddmTemplate.getType(),
				ddmTemplate.getMode(), ddmTemplate.getLanguage(),
				rewrittenScript, ddmTemplate.isCacheable(),
				cmsMigrationContext.getServiceContext());

			cmsMigrationContext.getCMSMigrationReport(
			).addWarning(
				StringBundler.concat(
					label, "was changed to read the migrated ", contentName)
			);
		}

		if (!unsupportedMethodNames.isEmpty()) {
			cmsMigrationContext.getCMSMigrationReport(
			).addWarning(
				StringBundler.concat(
					label, "calls ",
					StringUtil.merge(
						unsupportedMethodNames, StringPool.COMMA_AND_SPACE),
					" on ", contentName,
					", which object entries do not support, so it must be ",
					"adapted to object entries")
			);
		}

		return rewrittenScript;
	}

	private String _migrateBlogsEntryWidgetTemplate(
			CMSMigrationContext cmsMigrationContext, DDMTemplate ddmTemplate,
			String label)
		throws Exception {

		return _migrate(
			BlogsEntry.class.getName(), cmsMigrationContext, "blog entries",
			ddmTemplate, label);
	}

	private String _migrateJournalArticleWidgetTemplate(
			CMSMigrationContext cmsMigrationContext, DDMTemplate ddmTemplate,
			String label)
		throws Exception {

		return _migrate(
			JournalArticle.class.getName(), cmsMigrationContext,
			"web content articles", ddmTemplate, label);
	}

	private void _warnUnsupportedAPIs(
		CMSMigrationContext cmsMigrationContext, String label, boolean migrated,
		String script) {

		if ((!migrated &&
			 (script.contains(".getAssetObject()") ||
			  script.contains("getArticle") ||
			  script.contains("getDocument"))) ||
			script.contains("BlogsEntry") ||
			script.contains("JournalArticle") ||
			script.contains("serviceLocator")) {

			cmsMigrationContext.getCMSMigrationReport(
			).addWarning(
				label +
					"reads Web Content or Blogs APIs and must be adapted to " +
						"object entries"
			);
		}
	}

	private static final String _BLOGS_WIDGET_TEMPLATE_KEY =
		"CMS-MIGRATION-BLOGS";

	private final DDMTemplateLocalService _ddmTemplateLocalService;
	private final Portal _portal;
	private final WidgetTemplateScriptRewriter _widgetTemplateScriptRewriter;

}