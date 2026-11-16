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
	public void testRewriteWithExistingObjectEntryVariable() {
		_assertRewrite(
			BlogsEntry.class.getName(),
			"<#assign objectEntry = 1 blogsEntry = " +
				"assetRenderer.getAssetObject() />" +
					"${(blogsEntry.getURLTitle(locale)!\"\")}",
			"<#assign objectEntry = 1 blogsEntry = " +
				"assetRenderer.getAssetObject() />${blogsEntry.getUrlTitle()}");
	}

	@Test
	public void testRewriteWithInlineExpression() {
		_assertRewrite(
			BlogsEntry.class.getName(),
			"${(curEntry.getAssetRenderer().getAssetObject().getValues()." +
				"subtitle!\"\")}",
			"${curEntry.getAssetRenderer().getAssetObject().getSubtitle()}");
	}

	@Test
	public void testRewriteWithJournalArticle() {
		_assertRewrite(
			JournalArticle.class.getName(),
			"<#assign objectEntry = assetRenderer.getAssetObject() />" +
				"${objectEntry.getTitleValue(locale.toString())}",
			"<#assign article = assetRenderer.getArticle() />" +
				"${article.getTitle(locale)}");
	}

	@Test
	public void testRewriteWithProperty() {
		_assertRewrite(
			BlogsEntry.class.getName(),
			"<#assign objectEntry = assetRenderer.getAssetObject() />" +
				"${objectEntry.getTitleValue(locale.toString())}" +
					"${objectEntry.titleMap}",
			"<#assign blogsEntry = assetRenderer.getAssetObject() />" +
				"${blogsEntry.title}${blogsEntry.titleMap}");
	}

	@Test
	public void testRewriteWithServiceLocator() {
		String serviceScript = StringBundler.concat(
			"<#assign journalArticleLocalService = ",
			"serviceLocator.findService(\"com.liferay.journal.service.",
			"JournalArticleLocalService\") />");

		_assertRewrite(
			JournalArticle.class.getName(),
			StringBundler.concat(
				serviceScript, "<#list entries as curEntry><#assign ",
				"objectEntry = curEntry.getAssetRenderer().getAssetObject() />",
				"${objectEntry.getTitleValue(locale.toString())}</#list>"),
			StringBundler.concat(
				serviceScript, "<#list entries as curEntry><#assign article = ",
				"journalArticleLocalService.fetchLatestArticle(curEntry.",
				"getClassPK()) />${article.getTitle(locale)}</#list>"));
	}

	@Test
	public void testRewriteWithSeveralVariables() {
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
	}

	@Test
	public void testRewriteWithUnknownClassName() {
		String script =
			"<#assign entry = assetRenderer.getAssetObject() />" +
				"${entry.getUrlTitle()}";

		_assertRewrite(RandomTestUtil.randomString(), script, script);
	}

	@Test
	public void testRewriteWithUnrelatedVariable() {
		_assertRewrite(
			BlogsEntry.class.getName(),
			"<#assign objectEntry = assetRenderer.getAssetObject() />" +
				"${layout.getTitle()}${myblogsEntry.getTitle()}" +
					"${other.blogsEntry.getTitle()}",
			"<#assign blogsEntry = assetRenderer.getAssetObject() />" +
				"${layout.getTitle()}${myblogsEntry.getTitle()}" +
					"${other.blogsEntry.getTitle()}");
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

	@Test
	public void testRewriteWithVariableNameInText() {
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

	private void _assertRewrite(
		String className, String expectedScript, String script) {

		WidgetTemplateScriptRewriter.Result result =
			_widgetTemplateScriptRewriter.rewrite(className, script);

		Assert.assertEquals(expectedScript, result.getScript());
	}

	private WidgetTemplateScriptRewriter _widgetTemplateScriptRewriter;

}