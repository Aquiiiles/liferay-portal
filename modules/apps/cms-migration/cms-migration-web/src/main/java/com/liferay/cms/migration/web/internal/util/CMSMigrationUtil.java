/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.util;

import com.liferay.cms.migration.web.internal.background.task.CMSMigrationBackgroundTaskExecutor;
import com.liferay.cms.migration.web.internal.migration.CMSMigrationReport;
import com.liferay.portal.kernel.backgroundtask.BackgroundTask;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskManager;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskStatus;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskStatusRegistryUtil;
import com.liferay.portal.kernel.backgroundtask.constants.BackgroundTaskConstants;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.portlet.LiferayPortletResponse;
import com.liferay.portal.kernel.portlet.url.builder.PortletURLBuilder;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.FastDateFormatFactoryUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.Validator;

import java.text.Format;

import java.util.Map;

/**
 * @author Albert Gomes Cabral
 */
public class CMSMigrationUtil {

	public static JSONArray getMigrationsJSONArray(
			BackgroundTaskManager backgroundTaskManager,
			LiferayPortletResponse liferayPortletResponse, String redirect,
			ThemeDisplay themeDisplay)
		throws Exception {

		return JSONUtil.toJSONArray(
			ListUtil.sort(
				backgroundTaskManager.getBackgroundTasks(
					themeDisplay.getCompanyGroupId(),
					CMSMigrationBackgroundTaskExecutor.class.getName()),
				(backgroundTask1, backgroundTask2) -> Long.compare(
					backgroundTask2.getBackgroundTaskId(),
					backgroundTask1.getBackgroundTaskId())),
			backgroundTask -> _toJSONObject(
				backgroundTask, liferayPortletResponse, redirect,
				themeDisplay));
	}

	private static CMSMigrationReport _getCMSMigrationReport(
		BackgroundTask backgroundTask) {

		String statusMessage = backgroundTask.getStatusMessage();

		if (Validator.isNull(statusMessage)) {
			return new CMSMigrationReport();
		}

		try {
			return CMSMigrationReport.of(
				JSONFactoryUtil.createJSONObject(statusMessage));
		}
		catch (Exception exception) {
			if (_log.isDebugEnabled()) {
				_log.debug(exception);
			}

			CMSMigrationReport cmsMigrationReport = new CMSMigrationReport();

			cmsMigrationReport.addError(statusMessage);

			return cmsMigrationReport;
		}
	}

	private static int _getProgress(BackgroundTask backgroundTask) {
		BackgroundTaskStatus backgroundTaskStatus =
			BackgroundTaskStatusRegistryUtil.getBackgroundTaskStatus(
				backgroundTask.getBackgroundTaskId());

		if (backgroundTaskStatus == null) {
			return 0;
		}

		long total = GetterUtil.getLong(
			backgroundTaskStatus.getAttribute("total"));

		if (total == 0) {
			return 0;
		}

		long counter = GetterUtil.getLong(
			backgroundTaskStatus.getAttribute("counter"));

		return (int)Math.min(100, Math.round((double)counter / total * 100));
	}

	private static String _getStatusDisplayType(int status) {
		if (status == BackgroundTaskConstants.STATUS_CANCELLED) {
			return "secondary";
		}
		else if (status ==
					BackgroundTaskConstants.STATUS_COMPLETED_WITH_ERRORS) {

			return "warning";
		}
		else if (status == BackgroundTaskConstants.STATUS_FAILED) {
			return "danger";
		}
		else if ((status == BackgroundTaskConstants.STATUS_IN_PROGRESS) ||
				 (status == BackgroundTaskConstants.STATUS_NEW) ||
				 (status == BackgroundTaskConstants.STATUS_QUEUED)) {

			return "info";
		}
		else if (status == BackgroundTaskConstants.STATUS_SUCCESSFUL) {
			return "success";
		}

		return "secondary";
	}

	private static JSONObject _getStatusJSONObject(
			BackgroundTask backgroundTask)
		throws Exception {

		BackgroundTaskStatus backgroundTaskStatus =
			BackgroundTaskStatusRegistryUtil.getBackgroundTaskStatus(
				backgroundTask.getBackgroundTaskId());

		if (backgroundTaskStatus == null) {
			return JSONFactoryUtil.createJSONObject();
		}

		return JSONFactoryUtil.createJSONObject(
			backgroundTaskStatus.getAttributes());
	}

	private static JSONObject _toJSONObject(
			BackgroundTask backgroundTask,
			LiferayPortletResponse liferayPortletResponse, String redirect,
			ThemeDisplay themeDisplay)
		throws Exception {

		CMSMigrationReport cmsMigrationReport = _getCMSMigrationReport(
			backgroundTask);

		Map<String, Long> counts = cmsMigrationReport.getCounts();

		Format dateTimeFormat = FastDateFormatFactoryUtil.getDateTime(
			themeDisplay.getLocale(), themeDisplay.getTimeZone());

		JSONObject jsonObject = JSONUtil.put(
			"backgroundTaskId", backgroundTask.getBackgroundTaskId()
		).put(
			"completed", backgroundTask.isCompleted()
		).put(
			"counts",
			JSONUtil.toJSONArray(
				counts.entrySet(),
				entry -> JSONUtil.put(
					"label",
					LanguageUtil.get(
						themeDisplay.getLocale(),
						"cms-migration-" + entry.getKey())
				).put(
					"value", entry.getValue()
				))
		).put(
			"createDate", dateTimeFormat.format(backgroundTask.getCreateDate())
		).put(
			"errors",
			JSONFactoryUtil.createJSONArray(cmsMigrationReport.getErrors())
		).put(
			"inProgress", backgroundTask.isInProgress()
		).put(
			"name", backgroundTask.getName()
		).put(
			"progress", _getProgress(backgroundTask)
		).put(
			"statusDisplayType",
			_getStatusDisplayType(backgroundTask.getStatus())
		).put(
			"statusLabel",
			LanguageUtil.get(
				themeDisplay.getLocale(), backgroundTask.getStatusLabel())
		).put(
			"warnings",
			JSONFactoryUtil.createJSONArray(cmsMigrationReport.getWarnings())
		);

		if (backgroundTask.isCompleted()) {
			jsonObject.put(
				"deleteURL",
				PortletURLBuilder.createActionURL(
					liferayPortletResponse
				).setActionName(
					"/cms_migration/delete_cms_migration"
				).setRedirect(
					redirect
				).setParameter(
					"backgroundTaskId", backgroundTask.getBackgroundTaskId()
				).buildString());
		}

		if (backgroundTask.isInProgress()) {
			JSONObject statusJSONObject = _getStatusJSONObject(backgroundTask);

			jsonObject.put(
				"itemTitle", statusJSONObject.getString("itemTitle"));

			String phase = statusJSONObject.getString("phase");

			if (Validator.isNotNull(phase)) {
				jsonObject.put(
					"phaseLabel",
					LanguageUtil.get(
						themeDisplay.getLocale(), "cms-migration-" + phase));
			}
		}

		return jsonObject;
	}

	private static final Log _log = LogFactoryUtil.getLog(
		CMSMigrationUtil.class);

}