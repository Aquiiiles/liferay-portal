/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.migration.reference;

import com.liferay.blogs.model.BlogsEntry;
import com.liferay.journal.model.JournalArticle;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.json.JSONFactoryImpl;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.util.SetUtil;
import com.liferay.portal.test.rule.LiferayUnitTestRule;

import java.util.Collections;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * @author Albert Gomes Cabral
 */
public class WidgetTemplateScriptRewriterTest {

	@ClassRule
	@Rule
	public static final LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@Before
	public void setUp() throws Exception {
		_widgetTemplateScriptRewriter = new WidgetTemplateScriptRewriter(
			new JSONFactoryImpl());
	}

	@Test
	public void testRewrite() {
		_assertRewrite(
			BlogsEntry.class.getName(),
			"<#assign objectEntry = assetRenderer.getAssetObject() />" +
				"${(objectEntry.getURLTitle(locale)!\"\")}",
			"<#assign blogsEntry = assetRenderer.getAssetObject() />" +
				"${blogsEntry.getUrlTitle()}");
		_assertRewrite(
			BlogsEntry.class.getName(),
			"<#assign objectEntry = 1 blogsEntry = " +
				"assetRenderer.getAssetObject() />" +
					"${(blogsEntry.getURLTitle(locale)!\"\")}",
			"<#assign objectEntry = 1 blogsEntry = " +
				"assetRenderer.getAssetObject() />${blogsEntry.getUrlTitle()}");
		_assertRewrite(
			BlogsEntry.class.getName(),
			"${(curEntry.getAssetRenderer().getAssetObject().getValues()." +
				"subtitle!\"\")}",
			"${curEntry.getAssetRenderer().getAssetObject().getSubtitle()}");
		_assertRewrite(
			JournalArticle.class.getName(),
			"<#assign objectEntry = assetRenderer.getAssetObject() />" +
				"${objectEntry.getTitleValue(locale.toString())}",
			"<#assign article = assetRenderer.getArticle() />" +
				"${article.getTitle(locale)}");
		_assertRewrite(
			BlogsEntry.class.getName(),
			"<#assign objectEntry = assetRenderer.getAssetObject() />" +
				"${objectEntry.getTitleValue(locale.toString())}" +
					"${objectEntry.titleMap}",
			"<#assign blogsEntry = assetRenderer.getAssetObject() />" +
				"${blogsEntry.title}${blogsEntry.titleMap}");

		String serviceScript = StringBundler.concat(
			"<#assign journalArticleLocalService = ",
			"serviceLocator.findService(\"com.liferay.journal.service.",
			"JournalArticleLocalService\") />");

		_assertRewrite(
			JournalArticle.class.getName(),
			StringBundler.concat(
				"<#list entries as curEntry><#assign objectEntry = ",
				"curEntry.getAssetRenderer().getAssetObject() />",
				"${objectEntry.getTitleValue(locale.toString())}</#list>"),
			StringBundler.concat(
				serviceScript, "<#list entries as curEntry><#assign article = ",
				"journalArticleLocalService.fetchLatestArticle(curEntry.",
				"getClassPK()) />${article.getTitle(locale)}</#list>"));
		_assertRewrite(
			JournalArticle.class.getName(), serviceScript, serviceScript);

		_assertRewrite(
			JournalArticle.class.getName(),
			StringBundler.concat(
				"<#list entries as curEntry><#assign objectEntry = ",
				"curEntry.getAssetRenderer().getAssetObject() />",
				"${objectEntry.getTitleValue(locale.toString())}</#list>"),
			StringBundler.concat(
				"<#list entries as curEntry><#assign article = ",
				"serviceLocator.findService(\"com.liferay.journal.service.",
				"JournalArticleLocalService\").fetchLatestArticle(curEntry.",
				"classPK) />${article.getTitle(locale)}</#list>"));
		_assertRewrite(
			JournalArticle.class.getName(),
			StringBundler.concat(
				"<#assign\n\tuserLocalService = serviceLocator.findService(",
				"\"com.liferay.portal.kernel.service.UserLocalService\")\n/>",
				"<#list entries as curEntry><#assign objectEntry = ",
				"curEntry.getAssetRenderer().getAssetObject() />",
				"${userLocalService.getUsersCount()}</#list>"),
			StringBundler.concat(
				"<#assign\n\tjournalArticleLocalService = ",
				"serviceLocator.findService(\"com.liferay.journal.service.",
				"JournalArticleLocalService\")\n\tuserLocalService = ",
				"serviceLocator.findService(",
				"\"com.liferay.portal.kernel.service.UserLocalService\")\n/>",
				"<#list entries as curEntry><#assign article = ",
				"journalArticleLocalService.fetchLatestArticle(curEntry.",
				"getClassPK()) />${userLocalService.getUsersCount()}</#list>"));
		_assertRewrite(
			BlogsEntry.class.getName(),
			StringBundler.concat(
				"<#assign objectEntry = assetRenderer.getAssetObject() ",
				"objectEntry2 = curEntry.getAssetRenderer().getAssetObject() ",
				"/>${objectEntry.getUserName()}",
				"${objectEntry2.getUserName()}"),
			StringBundler.concat(
				"<#assign first = assetRenderer.getAssetObject() second = ",
				"curEntry.getAssetRenderer().getAssetObject() />",
				"${first.getUserName()}${second.getUserName()}"));
		_assertRewrite(
			JournalArticle.class.getName(),
			StringBundler.concat(
				"<#list entries as curEntry><#assign objectEntry = ",
				"curEntry.getAssetRenderer().getAssetObject() />",
				"${objectEntry.getTitleValue(locale.toString())}</#list>"),
			StringBundler.concat(
				"<#assign journalArticleLocalServiceUtil = ",
				"staticUtil[\"com.liferay.journal.service.",
				"JournalArticleLocalServiceUtil\"] />",
				"<#list entries as curEntry><#assign article = ",
				"journalArticleLocalServiceUtil.fetchLatestArticle(curEntry.",
				"getClassPK()) />${article.getTitle(locale)}</#list>"));

		String script =
			"<#assign entry = assetRenderer.getAssetObject() />" +
				"${entry.getUrlTitle()}";

		_assertRewrite(RandomTestUtil.randomString(), script, script);

		_assertRewrite(
			BlogsEntry.class.getName(),
			"<#assign objectEntry = assetRenderer.getAssetObject() />" +
				"${layout.getTitle()}${myblogsEntry.getTitle()}" +
					"${other.blogsEntry.getTitle()}",
			"<#assign blogsEntry = assetRenderer.getAssetObject() />" +
				"${layout.getTitle()}${myblogsEntry.getTitle()}" +
					"${other.blogsEntry.getTitle()}");
		_assertRewrite(
			BlogsEntry.class.getName(),
			StringBundler.concat(
				"<#-- entry --><#assign objectEntry = ",
				"assetRenderer.getAssetObject() /><div class=\"entry\">entry ",
				"<#if objectEntry.getUserName()?has_content>",
				"<a href=\"/entry/${objectEntry.getUserName()}\">",
				"${\"entry ${objectEntry.getUserName()}\"}</a></#if></div>"),
			StringBundler.concat(
				"<#-- entry --><#assign entry = ",
				"assetRenderer.getAssetObject() /><div class=\"entry\">entry ",
				"<#if entry.getUserName()?has_content>",
				"<a href=\"/entry/${entry.getUserName()}\">",
				"${\"entry ${entry.getUserName()}\"}</a></#if></div>"));
	}

	@Test
	public void testRewriteWithChangedMethod() {
		WidgetTemplateScriptRewriter.Result result =
			_widgetTemplateScriptRewriter.rewrite(
				BlogsEntry.class.getName(),
				"<#assign blogsEntry = assetRenderer.getAssetObject() />" +
					"${blogsEntry.getGroupId()}");

		Assert.assertEquals(
			SetUtil.fromArray("getGroupId"), result.getChangedMethodNames());
		Assert.assertEquals(
			Collections.emptySet(), result.getUnsupportedMethodNames());
	}

	@Test
	public void testRewriteWithServiceLocatorAndRemainingCall() {
		String serviceScript = StringBundler.concat(
			"<#assign journalArticleLocalService = ",
			"serviceLocator.findService(\"com.liferay.journal.service.",
			"JournalArticleLocalService\") />");

		WidgetTemplateScriptRewriter.Result result =
			_widgetTemplateScriptRewriter.rewrite(
				JournalArticle.class.getName(),
				StringBundler.concat(
					serviceScript, "<#list entries as curEntry><#assign ",
					"article = journalArticleLocalService.fetchLatestArticle(",
					"curEntry.getClassPK()) />",
					"${journalArticleLocalService.getArticlesCount(groupId)}",
					"</#list>"));

		Assert.assertEquals(
			StringBundler.concat(
				serviceScript, "<#list entries as curEntry><#assign ",
				"objectEntry = curEntry.getAssetRenderer().getAssetObject() />",
				"${journalArticleLocalService.getArticlesCount(groupId)}",
				"</#list>"),
			result.getScript());
		Assert.assertEquals(
			SetUtil.fromArray("JournalArticleLocalService.getArticlesCount"),
			result.getUnsupportedServiceCalls());
	}

	@Test
	public void testRewriteWithServiceLocatorAndUnrelatedVariable() {
		String script = StringBundler.concat(
			"<#list entries as curEntry><#assign article = ",
			"articleFinder.fetchLatestArticle(curEntry.getClassPK()) />",
			"</#list>");

		WidgetTemplateScriptRewriter.Result result =
			_widgetTemplateScriptRewriter.rewrite(
				JournalArticle.class.getName(), script);

		Assert.assertEquals(script, result.getScript());
		Assert.assertEquals(
			Collections.emptySet(), result.getUnsupportedServiceCalls());
	}

	@Test
	public void testRewriteWithStaticUtilAndInlineCall() {
		WidgetTemplateScriptRewriter.Result result =
			_widgetTemplateScriptRewriter.rewrite(
				JournalArticle.class.getName(),
				StringBundler.concat(
					"<#list entries as curEntry><#assign article = ",
					"staticUtil[\"com.liferay.journal.service.",
					"JournalArticleLocalServiceUtil\"].getLatestArticle(",
					"curEntry.classPK) />${staticUtil[\"com.liferay.journal.",
					"service.JournalArticleLocalServiceUtil\"].",
					"getArticlesCount(groupId)}</#list>"));

		Assert.assertEquals(
			StringBundler.concat(
				"<#list entries as curEntry><#assign objectEntry = ",
				"curEntry.getAssetRenderer().getAssetObject() />",
				"${staticUtil[\"com.liferay.journal.service.",
				"JournalArticleLocalServiceUtil\"].getArticlesCount(groupId)}",
				"</#list>"),
			result.getScript());
		Assert.assertEquals(
			SetUtil.fromArray("JournalArticleLocalService.getArticlesCount"),
			result.getUnsupportedServiceCalls());
	}

	@Test
	public void testRewriteWithUnsupportedMethod() {
		WidgetTemplateScriptRewriter.Result result =
			_widgetTemplateScriptRewriter.rewrite(
				BlogsEntry.class.getName(),
				"<#assign blogsEntry = assetRenderer.getAssetObject() />" +
					"${blogsEntry.getCoverImageURL(themeDisplay)}" +
						"${blogsEntry.getUserName()}");

		Assert.assertEquals(
			SetUtil.fromArray("getCoverImageURL"),
			result.getUnsupportedMethodNames());
	}

	@Test
	public void testRewriteWithUnsupportedReference() {
		WidgetTemplateScriptRewriter.Result result =
			_widgetTemplateScriptRewriter.rewrite(
				BlogsEntry.class.getName(),
				"<#if curEntry.getClassName() == " +
					"\"com.liferay.blogs.model.BlogsEntry\"></#if>");

		Assert.assertEquals(
			SetUtil.fromArray("com.liferay.blogs.model.BlogsEntry"),
			result.getUnsupportedReferences());
	}

	private void _assertRewrite(
		String className, String expectedScript, String script) {

		WidgetTemplateScriptRewriter.Result result =
			_widgetTemplateScriptRewriter.rewrite(className, script);

		Assert.assertEquals(expectedScript, result.getScript());
	}

	private WidgetTemplateScriptRewriter _widgetTemplateScriptRewriter;

}