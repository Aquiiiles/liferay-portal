/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.migration.template;

import com.liferay.asset.kernel.model.AssetEntry;
import com.liferay.asset.publisher.constants.AssetPublisherPortletKeys;
import com.liferay.blogs.model.BlogsEntry;
import com.liferay.cms.migration.web.internal.constants.CMSMigrationConstants;
import com.liferay.cms.migration.web.internal.migration.CMSMigrationContext;
import com.liferay.cms.migration.web.internal.migration.CMSMigrationReport;
import com.liferay.cms.migration.web.internal.migration.reference.WidgetTemplateScriptRewriter;
import com.liferay.dynamic.data.mapping.constants.DDMTemplateConstants;
import com.liferay.dynamic.data.mapping.model.DDMTemplate;
import com.liferay.dynamic.data.mapping.service.DDMTemplateLocalService;
import com.liferay.journal.model.JournalArticle;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.dao.orm.DynamicQuery;
import com.liferay.portal.kernel.dao.orm.RestrictionsFactoryUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.PortletPreferenceValue;
import com.liferay.portal.kernel.portlet.PortletIdCodec;
import com.liferay.portal.kernel.service.PortletPreferenceValueLocalService;
import com.liferay.portal.kernel.service.PortletPreferencesLocalService;
import com.liferay.portal.kernel.template.TemplateConstants;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.SetUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.xml.Document;
import com.liferay.portal.kernel.xml.Element;
import com.liferay.portal.kernel.xml.SAXReader;
import com.liferay.portlet.display.template.PortletDisplayTemplate;

import jakarta.portlet.PortletPreferences;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author Albert Gomes Cabral
 */
public class WidgetTemplateMigrator {

	public WidgetTemplateMigrator(
		DDMTemplateLocalService ddmTemplateLocalService, Portal portal,
		PortletPreferencesLocalService portletPreferencesLocalService,
		PortletPreferenceValueLocalService portletPreferenceValueLocalService,
		SAXReader saxReader,
		WidgetTemplateScriptRewriter widgetTemplateScriptRewriter) {

		_ddmTemplateLocalService = ddmTemplateLocalService;
		_portal = portal;
		_portletPreferencesLocalService = portletPreferencesLocalService;
		_portletPreferenceValueLocalService =
			portletPreferenceValueLocalService;
		_saxReader = saxReader;
		_widgetTemplateScriptRewriter = widgetTemplateScriptRewriter;
	}

	public void addWidgetTemplate(
		CMSMigrationContext cmsMigrationContext, String displayStyle,
		String label) {

		DDMTemplate ddmTemplate = _ddmTemplateLocalService.fetchTemplate(
			cmsMigrationContext.getSourceGroupId(),
			_portal.getClassNameId(AssetEntry.class),
			displayStyle.substring(
				PortletDisplayTemplate.DISPLAY_STYLE_PREFIX.length()));

		if (ddmTemplate == null) {
			return;
		}

		_messagePrefixes.putIfAbsent(
			ddmTemplate.getTemplateId(),
			StringBundler.concat(
				label, " uses the widget template ",
				CMSMigrationDDMTemplateUtil.getLabel(ddmTemplate), ", which "));
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

	public void migrate(CMSMigrationContext cmsMigrationContext) {
		CMSMigrationReport cmsMigrationReport =
			cmsMigrationContext.getCMSMigrationReport();

		for (Map.Entry<Long, String> entry : _messagePrefixes.entrySet()) {
			DDMTemplate ddmTemplate = _ddmTemplateLocalService.fetchDDMTemplate(
				entry.getKey());

			if (ddmTemplate == null) {
				continue;
			}

			try {
				_migrate(cmsMigrationContext, ddmTemplate, entry.getValue());
			}
			catch (Exception exception) {
				cmsMigrationReport.addError(
					StringBundler.concat(
						"Unable to migrate the widget template ",
						CMSMigrationDDMTemplateUtil.getLabel(ddmTemplate), ": ",
						exception.getMessage()));
			}
		}

		_messagePrefixes.clear();
	}

	private Set<String> _getListedSourceClassNames(
			PortletPreferences jxPortletPreferences,
			Map<String, String> sourceClassNames)
		throws Exception {

		Set<String> listedSourceClassNames = new HashSet<>();

		String selectionStyle = jxPortletPreferences.getValue(
			"selectionStyle", "dynamic");

		if (selectionStyle.equals("manual")) {
			for (String assetEntryXml :
					jxPortletPreferences.getValues(
						"assetEntryXml", new String[0])) {

				Document document = _saxReader.read(assetEntryXml);

				Element rootElement = document.getRootElement();

				String sourceClassName = sourceClassNames.get(
					rootElement.elementText("asset-entry-type"));

				if (sourceClassName == null) {
					return null;
				}

				listedSourceClassNames.add(sourceClassName);
			}

			return listedSourceClassNames;
		}

		String anyAssetType = jxPortletPreferences.getValue(
			"anyAssetType", "true");

		if (anyAssetType.equals("true")) {
			return null;
		}

		String[] classNameIds = {anyAssetType};

		if (anyAssetType.equals("false")) {
			classNameIds = jxPortletPreferences.getValues(
				"classNameIds", new String[0]);
		}

		for (String classNameId : classNameIds) {
			String sourceClassName = sourceClassNames.get(
				_portal.getClassName(GetterUtil.getLong(classNameId)));

			if (sourceClassName == null) {
				return null;
			}

			listedSourceClassNames.add(sourceClassName);
		}

		return listedSourceClassNames;
	}

	private String _getSourceClassName(
			CMSMigrationContext cmsMigrationContext, DDMTemplate ddmTemplate)
		throws Exception {

		DynamicQuery dynamicQuery =
			_portletPreferenceValueLocalService.dynamicQuery();

		dynamicQuery.add(
			RestrictionsFactoryUtil.eq(
				"companyId", cmsMigrationContext.getCompanyId()));
		dynamicQuery.add(RestrictionsFactoryUtil.eq("name", "displayStyle"));
		dynamicQuery.add(
			RestrictionsFactoryUtil.eq(
				"smallValue",
				PortletDisplayTemplate.DISPLAY_STYLE_PREFIX +
					ddmTemplate.getTemplateKey()));

		List<PortletPreferenceValue> portletPreferenceValues =
			_portletPreferenceValueLocalService.dynamicQuery(dynamicQuery);

		Map<String, String> sourceClassNames = new HashMap<>();

		Map<String, ObjectDefinition> objectDefinitions =
			cmsMigrationContext.getObjectDefinitions();

		for (Map.Entry<String, ObjectDefinition> entry :
				objectDefinitions.entrySet()) {

			ObjectDefinition objectDefinition = entry.getValue();

			sourceClassNames.put(
				objectDefinition.getClassName(),
				StringUtil.extractFirst(entry.getKey(), StringPool.POUND));
		}

		Set<String> listedSourceClassNames = new HashSet<>();

		for (PortletPreferenceValue portletPreferenceValue :
				portletPreferenceValues) {

			com.liferay.portal.kernel.model.PortletPreferences
				portletPreferences =
					_portletPreferencesLocalService.fetchPortletPreferences(
						portletPreferenceValue.getPortletPreferencesId());

			if ((portletPreferences == null) ||
				!Objects.equals(
					PortletIdCodec.decodePortletName(
						portletPreferences.getPortletId()),
					AssetPublisherPortletKeys.ASSET_PUBLISHER)) {

				continue;
			}

			PortletPreferences jxPortletPreferences =
				_portletPreferencesLocalService.getPreferences(
					portletPreferences.getCompanyId(),
					portletPreferences.getOwnerId(),
					portletPreferences.getOwnerType(),
					portletPreferences.getPlid(),
					portletPreferences.getPortletId());

			if (!_isDisplayStyleGroup(
					cmsMigrationContext, ddmTemplate, jxPortletPreferences)) {

				continue;
			}

			Set<String> portletListedSourceClassNames =
				_getListedSourceClassNames(
					jxPortletPreferences, sourceClassNames);

			if (portletListedSourceClassNames == null) {
				return null;
			}

			listedSourceClassNames.addAll(portletListedSourceClassNames);
		}

		if (listedSourceClassNames.size() != 1) {
			return null;
		}

		Iterator<String> iterator = listedSourceClassNames.iterator();

		return iterator.next();
	}

	private boolean _isDisplayStyleGroup(
		CMSMigrationContext cmsMigrationContext, DDMTemplate ddmTemplate,
		PortletPreferences jxPortletPreferences) {

		String displayStyleGroupExternalReferenceCode =
			jxPortletPreferences.getValue(
				"displayStyleGroupExternalReferenceCode", null);

		Group sourceGroup = cmsMigrationContext.getSourceGroup();

		if (Validator.isNotNull(displayStyleGroupExternalReferenceCode) &&
			!Objects.equals(
				displayStyleGroupExternalReferenceCode,
				sourceGroup.getExternalReferenceCode())) {

			return false;
		}

		long displayStyleGroupId = GetterUtil.getLong(
			jxPortletPreferences.getValue("displayStyleGroupId", null));

		if ((displayStyleGroupId > 0) &&
			(displayStyleGroupId != ddmTemplate.getGroupId())) {

			return false;
		}

		return true;
	}

	private void _migrate(
			CMSMigrationContext cmsMigrationContext, DDMTemplate ddmTemplate,
			String messagePrefix)
		throws Exception {

		String sourceClassName = null;

		if (CMSMigrationDDMTemplateUtil.isFreeMarker(ddmTemplate)) {
			sourceClassName = _getSourceClassName(
				cmsMigrationContext, ddmTemplate);
		}

		if (Objects.equals(sourceClassName, BlogsEntry.class.getName())) {
			_migrateBlogsEntryWidgetTemplate(
				cmsMigrationContext, ddmTemplate, messagePrefix);
		}
		else if (Objects.equals(
					sourceClassName, JournalArticle.class.getName())) {

			_migrateJournalArticleWidgetTemplate(
				cmsMigrationContext, ddmTemplate, messagePrefix);
		}
		else {
			_warnUnmigratedWidgetTemplate(
				cmsMigrationContext, ddmTemplate, messagePrefix);
		}
	}

	private void _migrate(
			String className, CMSMigrationContext cmsMigrationContext,
			String contentName, DDMTemplate ddmTemplate, String messagePrefix)
		throws Exception {

		String script = ddmTemplate.getScript();

		WidgetTemplateScriptRewriter.Result result =
			_widgetTemplateScriptRewriter.rewrite(className, script);

		CMSMigrationReport cmsMigrationReport =
			cmsMigrationContext.getCMSMigrationReport();

		if (!script.equals(result.getScript())) {
			_ddmTemplateLocalService.updateTemplate(
				cmsMigrationContext.getUserId(), ddmTemplate.getTemplateId(),
				ddmTemplate.getClassPK(), ddmTemplate.getNameMap(),
				ddmTemplate.getDescriptionMap(), ddmTemplate.getType(),
				ddmTemplate.getMode(), ddmTemplate.getLanguage(),
				result.getScript(), ddmTemplate.isCacheable(),
				cmsMigrationContext.getServiceContext());

			cmsMigrationReport.addWarning(
				StringBundler.concat(
					messagePrefix, "was changed to read the migrated ",
					contentName));
		}

		Set<String> changedMethodNames = result.getChangedMethodNames();

		if (SetUtil.isNotEmpty(changedMethodNames)) {
			cmsMigrationReport.addWarning(
				StringBundler.concat(
					messagePrefix, "calls ",
					StringUtil.merge(
						changedMethodNames, StringPool.COMMA_AND_SPACE),
					" on ", contentName,
					", which returns a different value for object entries, so ",
					"it must be reviewed"));
		}

		Set<String> unsupportedMethodNames = result.getUnsupportedMethodNames();

		if (SetUtil.isNotEmpty(unsupportedMethodNames)) {
			cmsMigrationReport.addWarning(
				StringBundler.concat(
					messagePrefix, "calls ",
					StringUtil.merge(
						unsupportedMethodNames, StringPool.COMMA_AND_SPACE),
					" on ", contentName,
					", which object entries do not support, so it must be ",
					"adapted to object entries"));
		}

		Set<String> unsupportedReferences = result.getUnsupportedReferences();

		if (SetUtil.isNotEmpty(unsupportedReferences)) {
			cmsMigrationReport.addWarning(
				StringBundler.concat(
					messagePrefix, "references ",
					StringUtil.merge(
						unsupportedReferences, StringPool.COMMA_AND_SPACE),
					", which does not apply to object entries, so it must be ",
					"adapted to object entries"));
		}

		if (script.contains("serviceLocator")) {
			cmsMigrationReport.addWarning(
				messagePrefix +
					"uses serviceLocator to read classic services and must " +
						"be adapted to object entries");
		}
	}

	private void _migrateBlogsEntryWidgetTemplate(
			CMSMigrationContext cmsMigrationContext, DDMTemplate ddmTemplate,
			String messagePrefix)
		throws Exception {

		_migrate(
			BlogsEntry.class.getName(), cmsMigrationContext, "blog entries",
			ddmTemplate, messagePrefix);
	}

	private void _migrateJournalArticleWidgetTemplate(
			CMSMigrationContext cmsMigrationContext, DDMTemplate ddmTemplate,
			String messagePrefix)
		throws Exception {

		_migrate(
			JournalArticle.class.getName(), cmsMigrationContext,
			"web content articles", ddmTemplate, messagePrefix);
	}

	private void _warnUnmigratedWidgetTemplate(
		CMSMigrationContext cmsMigrationContext, DDMTemplate ddmTemplate,
		String messagePrefix) {

		String script = ddmTemplate.getScript();

		if (script.contains(".getAssetObject()") ||
			script.contains("BlogsEntry") ||
			script.contains("JournalArticle") ||
			script.contains("getArticle") || script.contains("getDocument") ||
			script.contains("serviceLocator")) {

			CMSMigrationReport cmsMigrationReport =
				cmsMigrationContext.getCMSMigrationReport();

			cmsMigrationReport.addWarning(
				messagePrefix +
					"reads Web Content or Blogs APIs and must be adapted to " +
						"object entries");
		}
	}

	private static final String _BLOGS_WIDGET_TEMPLATE_KEY =
		"CMS-MIGRATION-BLOGS";

	private final DDMTemplateLocalService _ddmTemplateLocalService;
	private final Map<Long, String> _messagePrefixes = new LinkedHashMap<>();
	private final Portal _portal;
	private final PortletPreferenceValueLocalService
		_portletPreferenceValueLocalService;
	private final PortletPreferencesLocalService
		_portletPreferencesLocalService;
	private final SAXReader _saxReader;
	private final WidgetTemplateScriptRewriter _widgetTemplateScriptRewriter;

}