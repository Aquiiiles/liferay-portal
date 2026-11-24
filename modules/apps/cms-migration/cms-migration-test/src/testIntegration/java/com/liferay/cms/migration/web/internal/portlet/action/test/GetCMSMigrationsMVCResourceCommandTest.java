/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.portlet.action.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.counter.kernel.service.CounterLocalService;
import com.liferay.portal.background.task.model.BackgroundTask;
import com.liferay.portal.background.task.service.BackgroundTaskLocalService;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskStatus;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskStatusRegistryUtil;
import com.liferay.portal.kernel.backgroundtask.constants.BackgroundTaskConstants;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCResourceCommand;
import com.liferay.portal.kernel.security.auth.PrincipalException;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactoryUtil;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.test.portlet.MockLiferayResourceRequest;
import com.liferay.portal.kernel.test.portlet.MockLiferayResourceResponse;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.test.util.UserTestUtil;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.TimeZoneUtil;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.test.rule.PermissionCheckerMethodTestRule;

import jakarta.portlet.PortletException;

import java.io.ByteArrayOutputStream;

import java.util.Date;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Átila Rodrigues
 */
@RunWith(Arquillian.class)
public class GetCMSMigrationsMVCResourceCommandTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new LiferayIntegrationTestRule(),
			PermissionCheckerMethodTestRule.INSTANCE);

	@Before
	public void setUp() throws Exception {
		_company = _companyLocalService.getCompany(
			TestPropsValues.getCompanyId());
	}

	@Test
	public void testServeResource() throws Exception {
		_completedBackgroundTask = _addBackgroundTask(
			_company.getCompanyId(), true, _company.getGroupId(),
			BackgroundTaskConstants.STATUS_SUCCESSFUL,
			"com.liferay.cms.migration.web.internal.background.task." +
				"CMSMigrationBackgroundTaskExecutor");
		_inProgressBackgroundTask = _addBackgroundTask(
			_company.getCompanyId(), false, _company.getGroupId(),
			BackgroundTaskConstants.STATUS_IN_PROGRESS,
			"com.liferay.cms.migration.web.internal.background.task." +
				"CMSMigrationBackgroundTaskExecutor");

		_otherCompanyBackgroundTask = _addBackgroundTask(
			RandomTestUtil.randomLong(), false, RandomTestUtil.randomLong(),
			BackgroundTaskConstants.STATUS_IN_PROGRESS,
			"com.liferay.cms.migration.web.internal.background.task." +
				"CMSMigrationBackgroundTaskExecutor");
		_otherExecutorBackgroundTask = _addBackgroundTask(
			_company.getCompanyId(), false, _company.getGroupId(),
			BackgroundTaskConstants.STATUS_IN_PROGRESS,
			RandomTestUtil.randomString());

		BackgroundTaskStatus backgroundTaskStatus =
			BackgroundTaskStatusRegistryUtil.registerBackgroundTaskStatus(
				_inProgressBackgroundTask.getBackgroundTaskId(), null);

		String itemTitle = RandomTestUtil.randomString();

		backgroundTaskStatus.setAttribute("itemTitle", itemTitle);

		try {
			JSONArray migrationsJSONArray = _getMigrationsJSONArray(
				PermissionThreadLocal.getPermissionChecker());

			JSONObject jsonObject = _getMigrationJSONObject(
				_completedBackgroundTask, migrationsJSONArray);

			Assert.assertTrue(jsonObject.getBoolean("completed"));
			Assert.assertFalse(jsonObject.getBoolean("inProgress"));

			jsonObject = _getMigrationJSONObject(
				_inProgressBackgroundTask, migrationsJSONArray);

			Assert.assertFalse(jsonObject.getBoolean("completed"));
			Assert.assertTrue(jsonObject.getBoolean("inProgress"));
			Assert.assertEquals(itemTitle, jsonObject.getString("itemTitle"));

			Assert.assertNull(
				_fetchMigrationJSONObject(
					_otherCompanyBackgroundTask, migrationsJSONArray));
			Assert.assertNull(
				_fetchMigrationJSONObject(
					_otherExecutorBackgroundTask, migrationsJSONArray));

			itemTitle = RandomTestUtil.randomString();

			backgroundTaskStatus.setAttribute("itemTitle", itemTitle);

			jsonObject = _getMigrationJSONObject(
				_inProgressBackgroundTask,
				_getMigrationsJSONArray(
					PermissionThreadLocal.getPermissionChecker()));

			Assert.assertEquals(itemTitle, jsonObject.getString("itemTitle"));
		}
		finally {
			BackgroundTaskStatusRegistryUtil.unregisterBackgroundTaskStatus(
				_inProgressBackgroundTask.getBackgroundTaskId());
		}

		_user = UserTestUtil.addUser();

		try {
			_getMigrationsJSONArray(PermissionCheckerFactoryUtil.create(_user));

			Assert.fail();
		}
		catch (PortletException portletException) {
			Assert.assertTrue(
				portletException.getCause() instanceof
					PrincipalException.MustBeCompanyAdmin);
		}
	}

	private BackgroundTask _addBackgroundTask(
			long companyId, boolean completed, long groupId, int status,
			String taskExecutorClassName)
		throws Exception {

		BackgroundTask backgroundTask =
			_backgroundTaskLocalService.createBackgroundTask(
				_counterLocalService.increment());

		backgroundTask.setGroupId(groupId);
		backgroundTask.setCompanyId(companyId);
		backgroundTask.setUserId(TestPropsValues.getUserId());
		backgroundTask.setCreateDate(new Date());
		backgroundTask.setName(RandomTestUtil.randomString());
		backgroundTask.setTaskExecutorClassName(taskExecutorClassName);
		backgroundTask.setCompleted(completed);
		backgroundTask.setStatus(status);

		return _backgroundTaskLocalService.updateBackgroundTask(backgroundTask);
	}

	private JSONObject _fetchMigrationJSONObject(
		BackgroundTask backgroundTask, JSONArray migrationsJSONArray) {

		for (int i = 0; i < migrationsJSONArray.length(); i++) {
			JSONObject jsonObject = migrationsJSONArray.getJSONObject(i);

			if (jsonObject.getLong("backgroundTaskId") ==
					backgroundTask.getBackgroundTaskId()) {

				return jsonObject;
			}
		}

		return null;
	}

	private JSONObject _getMigrationJSONObject(
		BackgroundTask backgroundTask, JSONArray migrationsJSONArray) {

		JSONObject jsonObject = _fetchMigrationJSONObject(
			backgroundTask, migrationsJSONArray);

		if (jsonObject == null) {
			throw new AssertionError(
				"Unable to find migration " +
					backgroundTask.getBackgroundTaskId());
		}

		return jsonObject;
	}

	private JSONArray _getMigrationsJSONArray(
			PermissionChecker permissionChecker)
		throws Exception {

		MockLiferayResourceRequest mockLiferayResourceRequest =
			new MockLiferayResourceRequest();

		ThemeDisplay themeDisplay = new ThemeDisplay();

		themeDisplay.setCompany(_company);
		themeDisplay.setLocale(LocaleUtil.US);
		themeDisplay.setPermissionChecker(permissionChecker);
		themeDisplay.setTimeZone(TimeZoneUtil.getDefault());

		mockLiferayResourceRequest.setAttribute(
			WebKeys.THEME_DISPLAY, themeDisplay);

		MockLiferayResourceResponse mockLiferayResourceResponse =
			new MockLiferayResourceResponse();

		_mvcResourceCommand.serveResource(
			mockLiferayResourceRequest, mockLiferayResourceResponse);

		ByteArrayOutputStream byteArrayOutputStream =
			(ByteArrayOutputStream)
				mockLiferayResourceResponse.getPortletOutputStream();

		JSONObject jsonObject = _jsonFactory.createJSONObject(
			byteArrayOutputStream.toString());

		return jsonObject.getJSONArray("migrations");
	}

	@Inject
	private BackgroundTaskLocalService _backgroundTaskLocalService;

	private Company _company;

	@Inject
	private CompanyLocalService _companyLocalService;

	@DeleteAfterTestRun
	private BackgroundTask _completedBackgroundTask;

	@Inject
	private CounterLocalService _counterLocalService;

	@DeleteAfterTestRun
	private BackgroundTask _inProgressBackgroundTask;

	@Inject
	private JSONFactory _jsonFactory;

	@Inject(filter = "mvc.command.name=/cms_migration/get_cms_migrations")
	private MVCResourceCommand _mvcResourceCommand;

	@DeleteAfterTestRun
	private BackgroundTask _otherCompanyBackgroundTask;

	@DeleteAfterTestRun
	private BackgroundTask _otherExecutorBackgroundTask;

	@DeleteAfterTestRun
	private User _user;

}