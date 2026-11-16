/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.migration.reference;

import com.liferay.blogs.model.BlogsEntry;
import com.liferay.journal.model.JournalArticle;
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
			"<#assign blogsEntry = assetRenderer.getAssetObject() />" +
				"${(blogsEntry.getURLTitle(locale)!\"\")}",
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
			"<#assign article = assetRenderer.getAssetObject() />" +
				"${article.getTitleValue(locale.toString())}",
			"<#assign article = assetRenderer.getArticle() />" +
				"${article.getTitle(locale)}");
	}

	@Test
	public void testRewriteWithProperty() {
		_assertRewrite(
			BlogsEntry.class.getName(),
			"<#assign blogsEntry = assetRenderer.getAssetObject() />" +
				"${blogsEntry.getTitleValue(locale.toString())}" +
					"${blogsEntry.titleMap}",
			"<#assign blogsEntry = assetRenderer.getAssetObject() />" +
				"${blogsEntry.title}${blogsEntry.titleMap}");
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
		String script =
			"<#assign blogsEntry = assetRenderer.getAssetObject() />" +
				"${layout.getTitle()}${myblogsEntry.getTitle()}" +
					"${other.blogsEntry.getTitle()}";

		_assertRewrite(BlogsEntry.class.getName(), script, script);
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