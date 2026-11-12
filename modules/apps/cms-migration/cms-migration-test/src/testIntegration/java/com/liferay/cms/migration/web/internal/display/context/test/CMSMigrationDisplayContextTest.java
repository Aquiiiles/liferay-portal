/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.display.context.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.counter.kernel.service.CounterLocalService;
import com.liferay.portal.background.task.model.BackgroundTask;
import com.liferay.portal.background.task.service.BackgroundTaskLocalService;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskStatus;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskStatusRegistryUtil;
import com.liferay.portal.kernel.backgroundtask.constants.BackgroundTaskConstants;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCPortlet;
import com.liferay.portal.kernel.portlet.bridges.mvc.constants.MVCRenderConstants;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.kernel.test.portlet.MockLiferayPortletRenderRequest;
import com.liferay.portal.kernel.test.portlet.MockLiferayPortletRenderResponse;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.FastDateFormatFactoryUtil;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.TimeZoneUtil;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portlet.test.MockLiferayPortletContext;

import jakarta.portlet.Portlet;

import java.text.Format;

import java.util.Date;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Nara Andrade
 */
@RunWith(Arquillian.class)
public class CMSMigrationDisplayContextTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new LiferayIntegrationTestRule();

	@Before
	public void setUp() throws Exception {
		_company = _companyLocalService.getCompany(
			TestPropsValues.getCompanyId());
	}

	@Test
	public void testGetProps() throws Exception {
		String error = RandomTestUtil.randomString();
		String warning = RandomTestUtil.randomString();

		_completedBackgroundTask = _addBackgroundTask(
			true, BackgroundTaskConstants.STATUS_COMPLETED_WITH_ERRORS,
			JSONUtil.put(
				"counts", JSONUtil.put("file-entries", 4)
			).put(
				"errors", JSONUtil.put(error)
			).put(
				"warnings", JSONUtil.put(warning)
			).toString());

		_inProgressBackgroundTask = _addBackgroundTask(
			false, BackgroundTaskConstants.STATUS_IN_PROGRESS, null);
		_queuedBackgroundTask = _addBackgroundTask(
			false, BackgroundTaskConstants.STATUS_QUEUED, null);

		_cancelledBackgroundTask = _addBackgroundTask(
			true, BackgroundTaskConstants.STATUS_CANCELLED, null);

		String failedError = RandomTestUtil.randomString();

		_failedBackgroundTask = _addBackgroundTask(
			true, BackgroundTaskConstants.STATUS_FAILED,
			JSONUtil.put(
				"counts", JSONUtil.put("folders", 2)
			).put(
				"errors", JSONUtil.put(failedError)
			).toString());

		_successfulBackgroundTask = _addBackgroundTask(
			true, BackgroundTaskConstants.STATUS_SUCCESSFUL, null);

		BackgroundTaskStatus backgroundTaskStatus =
			BackgroundTaskStatusRegistryUtil.registerBackgroundTaskStatus(
				_inProgressBackgroundTask.getBackgroundTaskId(), null);

		String itemTitle = RandomTestUtil.randomString();

		backgroundTaskStatus.setAttribute("counter", 4L);
		backgroundTaskStatus.setAttribute("itemTitle", itemTitle);
		backgroundTaskStatus.setAttribute("phase", "journal-articles");
		backgroundTaskStatus.setAttribute("total", 10L);

		try {
			Map<String, Object> props = _getProps();

			JSONArray migrationsJSONArray = (JSONArray)props.get("migrations");

			JSONObject jsonObject = _getMigrationJSONObject(
				_completedBackgroundTask, migrationsJSONArray);

			Assert.assertTrue(jsonObject.getBoolean("completed"));
			Assert.assertEquals(
				JSONUtil.putAll(
					JSONUtil.put(
						"label",
						LanguageUtil.get(
							LocaleUtil.US, "cms-migration-file-entries")
					).put(
						"value", 4
					)
				).toString(),
				String.valueOf(jsonObject.getJSONArray("counts")));

			Format dateTimeFormat = FastDateFormatFactoryUtil.getDateTime(
				LocaleUtil.US, TimeZoneUtil.getDefault());

			Assert.assertEquals(
				dateTimeFormat.format(_completedBackgroundTask.getCreateDate()),
				jsonObject.getString("createDate"));

			String deleteURL = jsonObject.getString("deleteURL");

			Assert.assertTrue(
				deleteURL.contains("/cms_migration/delete_cms_migration"));
			Assert.assertTrue(
				deleteURL.contains(
					String.valueOf(
						_completedBackgroundTask.getBackgroundTaskId())));

			Assert.assertEquals(
				JSONUtil.put(
					error
				).toString(),
				String.valueOf(jsonObject.getJSONArray("errors")));
			Assert.assertFalse(jsonObject.getBoolean("inProgress"));
			Assert.assertEquals(
				_completedBackgroundTask.getName(),
				jsonObject.getString("name"));
			Assert.assertEquals(
				"warning", jsonObject.getString("statusDisplayType"));
			Assert.assertEquals(
				LanguageUtil.get(
					LocaleUtil.US,
					BackgroundTaskConstants.LABEL_COMPLETED_WITH_ERRORS),
				jsonObject.getString("statusLabel"));
			Assert.assertEquals(
				JSONUtil.put(
					warning
				).toString(),
				String.valueOf(jsonObject.getJSONArray("warnings")));

			jsonObject = _getMigrationJSONObject(
				_inProgressBackgroundTask, migrationsJSONArray);

			Assert.assertFalse(jsonObject.getBoolean("completed"));
			Assert.assertFalse(jsonObject.has("deleteURL"));
			Assert.assertTrue(jsonObject.getBoolean("inProgress"));
			Assert.assertEquals(itemTitle, jsonObject.getString("itemTitle"));
			Assert.assertEquals(
				LanguageUtil.get(
					LocaleUtil.US, "cms-migration-journal-articles"),
				jsonObject.getString("phaseLabel"));
			Assert.assertEquals(40, jsonObject.getInt("progress"));
			Assert.assertEquals(
				"info", jsonObject.getString("statusDisplayType"));

			jsonObject = _getMigrationJSONObject(
				_queuedBackgroundTask, migrationsJSONArray);

			Assert.assertFalse(jsonObject.getBoolean("completed"));
			Assert.assertFalse(jsonObject.has("deleteURL"));
			Assert.assertFalse(jsonObject.getBoolean("inProgress"));
			Assert.assertFalse(jsonObject.has("itemTitle"));
			Assert.assertFalse(jsonObject.has("phaseLabel"));
			Assert.assertEquals(0, jsonObject.getInt("progress"));
			Assert.assertEquals(
				"info", jsonObject.getString("statusDisplayType"));

			jsonObject = _getMigrationJSONObject(
				_cancelledBackgroundTask, migrationsJSONArray);

			Assert.assertEquals(
				"secondary", jsonObject.getString("statusDisplayType"));

			jsonObject = _getMigrationJSONObject(
				_failedBackgroundTask, migrationsJSONArray);

			Assert.assertEquals(
				JSONUtil.putAll(
					JSONUtil.put(
						"label",
						LanguageUtil.get(LocaleUtil.US, "cms-migration-folders")
					).put(
						"value", 2
					)
				).toString(),
				String.valueOf(jsonObject.getJSONArray("counts")));
			Assert.assertEquals(
				JSONUtil.put(
					failedError
				).toString(),
				String.valueOf(jsonObject.getJSONArray("errors")));
			Assert.assertEquals(
				"danger", jsonObject.getString("statusDisplayType"));

			jsonObject = _getMigrationJSONObject(
				_successfulBackgroundTask, migrationsJSONArray);

			Assert.assertEquals(
				"success", jsonObject.getString("statusDisplayType"));
		}
		finally {
			BackgroundTaskStatusRegistryUtil.unregisterBackgroundTaskStatus(
				_inProgressBackgroundTask.getBackgroundTaskId());
		}
	}

	private BackgroundTask _addBackgroundTask(
			boolean completed, int status, String statusMessage)
		throws Exception {

		BackgroundTask backgroundTask =
			_backgroundTaskLocalService.createBackgroundTask(
				_counterLocalService.increment());

		backgroundTask.setGroupId(_company.getGroupId());
		backgroundTask.setCompanyId(_company.getCompanyId());
		backgroundTask.setUserId(TestPropsValues.getUserId());
		backgroundTask.setCreateDate(new Date());
		backgroundTask.setName(RandomTestUtil.randomString());
		backgroundTask.setTaskExecutorClassName(
			"com.liferay.cms.migration.web.internal.background.task." +
				"CMSMigrationBackgroundTaskExecutor");
		backgroundTask.setCompleted(completed);
		backgroundTask.setStatus(status);
		backgroundTask.setStatusMessage(statusMessage);

		return _backgroundTaskLocalService.updateBackgroundTask(backgroundTask);
	}

	private JSONObject _getMigrationJSONObject(
		BackgroundTask backgroundTask, JSONArray migrationsJSONArray) {

		for (int i = 0; i < migrationsJSONArray.length(); i++) {
			JSONObject jsonObject = migrationsJSONArray.getJSONObject(i);

			if (jsonObject.getLong("backgroundTaskId") ==
					backgroundTask.getBackgroundTaskId()) {

				return jsonObject;
			}
		}

		throw new AssertionError(
			"Unable to find migration " + backgroundTask.getBackgroundTaskId());
	}

	private Map<String, Object> _getProps() throws Exception {
		MockLiferayPortletRenderRequest mockLiferayPortletRenderRequest =
			new MockLiferayPortletRenderRequest();

		mockLiferayPortletRenderRequest.setAttribute(
			MVCRenderConstants.
				PORTLET_CONTEXT_OVERRIDE_REQUEST_ATTIBUTE_NAME_PREFIX +
					"/view.jsp",
			new MockLiferayPortletContext("/view.jsp"));

		ThemeDisplay themeDisplay = new ThemeDisplay();

		themeDisplay.setCompany(_company);
		themeDisplay.setLocale(LocaleUtil.US);
		themeDisplay.setTimeZone(TimeZoneUtil.getDefault());

		mockLiferayPortletRenderRequest.setAttribute(
			WebKeys.THEME_DISPLAY, themeDisplay);

		mockLiferayPortletRenderRequest.setParameter("mvcPath", "/view.jsp");

		MVCPortlet mvcPortlet = (MVCPortlet)_portlet;

		mvcPortlet.render(
			mockLiferayPortletRenderRequest,
			new MockLiferayPortletRenderResponse());

		return ReflectionTestUtil.invoke(
			mockLiferayPortletRenderRequest.getAttribute(
				WebKeys.PORTLET_DISPLAY_CONTEXT),
			"getProps", new Class<?>[0]);
	}

	@Inject
	private BackgroundTaskLocalService _backgroundTaskLocalService;

	@DeleteAfterTestRun
	private BackgroundTask _cancelledBackgroundTask;

	private Company _company;

	@Inject
	private CompanyLocalService _companyLocalService;

	@DeleteAfterTestRun
	private BackgroundTask _completedBackgroundTask;

	@Inject
	private CounterLocalService _counterLocalService;

	@DeleteAfterTestRun
	private BackgroundTask _failedBackgroundTask;

	@DeleteAfterTestRun
	private BackgroundTask _inProgressBackgroundTask;

	@Inject(
		filter = "component.name=com.liferay.cms.migration.web.internal.portlet.CMSMigrationPortlet"
	)
	private Portlet _portlet;

	@DeleteAfterTestRun
	private BackgroundTask _queuedBackgroundTask;

	@DeleteAfterTestRun
	private BackgroundTask _successfulBackgroundTask;

}