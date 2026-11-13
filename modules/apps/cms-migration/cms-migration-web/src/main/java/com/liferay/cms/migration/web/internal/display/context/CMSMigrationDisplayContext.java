/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.display.context;

import com.liferay.cms.migration.web.internal.background.task.CMSMigrationBackgroundTaskExecutor;
import com.liferay.cms.migration.web.internal.migration.CMSMigrationReport;
import com.liferay.depot.constants.DepotConstants;
import com.liferay.depot.model.DepotEntry;
import com.liferay.depot.service.DepotEntryLocalService;
import com.liferay.portal.kernel.backgroundtask.BackgroundTask;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskManager;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskStatus;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskStatusRegistryUtil;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.portlet.url.builder.PortletURLBuilder;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.theme.PortletDisplay;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.FastDateFormatFactoryUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;

import jakarta.portlet.RenderRequest;
import jakarta.portlet.RenderResponse;

import jakarta.servlet.http.HttpServletRequest;

import java.text.Format;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Lino Alves
 */
public class CMSMigrationDisplayContext {

	public CMSMigrationDisplayContext(
		BackgroundTaskManager backgroundTaskManager,
		DepotEntryLocalService depotEntryLocalService,
		GroupLocalService groupLocalService,
		HttpServletRequest httpServletRequest, RenderRequest renderRequest,
		RenderResponse renderResponse) {

		_backgroundTaskManager = backgroundTaskManager;
		_depotEntryLocalService = depotEntryLocalService;
		_groupLocalService = groupLocalService;
		_renderRequest = renderRequest;
		_renderResponse = renderResponse;

		_themeDisplay = (ThemeDisplay)httpServletRequest.getAttribute(
			WebKeys.THEME_DISPLAY);
	}

	public Map<String, Object> getProps() throws Exception {
		return HashMapBuilder.<String, Object>put(
			"groups",
			JSONUtil.toJSONArray(
				_getGroups(),
				group -> JSONUtil.put(
					"label", group.getDescriptiveName(_themeDisplay.getLocale())
				).put(
					"value", String.valueOf(group.getGroupId())
				))
		).put(
			"migrations",
			JSONUtil.toJSONArray(_getBackgroundTasks(), this::_toJSONObject)
		).put(
			"portletNamespace", _renderResponse.getNamespace()
		).put(
			"spaces",
			JSONUtil.toJSONArray(
				_getSpaces(),
				depotEntry -> {
					Group group = depotEntry.getGroup();

					return JSONUtil.put(
						"label",
						group.getDescriptiveName(_themeDisplay.getLocale())
					).put(
						"value", String.valueOf(depotEntry.getDepotEntryId())
					);
				})
		).put(
			"startCMSMigrationURL",
			PortletURLBuilder.createActionURL(
				PortalUtil.getLiferayPortletResponse(_renderResponse)
			).setActionName(
				"/cms_migration/start_cms_migration"
			).buildString()
		).put(
			"title",
			() -> {
				PortletDisplay portletDisplay =
					_themeDisplay.getPortletDisplay();

				return portletDisplay.getTitle();
			}
		).build();
	}

	public RenderRequest getRenderRequest() {
		return _renderRequest;
	}

	public RenderResponse getRenderResponse() {
		return _renderResponse;
	}

	private List<BackgroundTask> _getBackgroundTasks() {
		return ListUtil.sort(
			_backgroundTaskManager.getBackgroundTasks(
				_themeDisplay.getCompanyGroupId(),
				CMSMigrationBackgroundTaskExecutor.class.getName()),
			(backgroundTask1, backgroundTask2) -> Long.compare(
				backgroundTask2.getBackgroundTaskId(),
				backgroundTask1.getBackgroundTaskId()));
	}

	private CMSMigrationReport _getCMSMigrationReport(
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

	private List<Group> _getGroups() {
		List<Group> groups = new ArrayList<>();

		for (Group group :
				_groupLocalService.getActiveGroups(
					_themeDisplay.getCompanyId(), true, true, QueryUtil.ALL_POS,
					QueryUtil.ALL_POS, null)) {

			if (group.isDepot() || group.isStagingGroup() || !group.isSite()) {
				continue;
			}

			groups.add(group);
		}

		return groups;
	}

	private int _getProgress(BackgroundTask backgroundTask) {
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

	private List<DepotEntry> _getSpaces() {
		return _depotEntryLocalService.getDepotEntries(
			_themeDisplay.getCompanyId(), DepotConstants.TYPE_SPACE);
	}

	private JSONObject _getStatusJSONObject(BackgroundTask backgroundTask)
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

	private JSONObject _toJSONObject(BackgroundTask backgroundTask)
		throws Exception {

		CMSMigrationReport cmsMigrationReport = _getCMSMigrationReport(
			backgroundTask);

		Map<String, Long> counts = cmsMigrationReport.getCounts();

		Format dateTimeFormat = FastDateFormatFactoryUtil.getDateTime(
			_themeDisplay.getLocale(), _themeDisplay.getTimeZone());

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
						_themeDisplay.getLocale(),
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
			"statusLabel",
			LanguageUtil.get(
				_themeDisplay.getLocale(), backgroundTask.getStatusLabel())
		).put(
			"warnings",
			JSONFactoryUtil.createJSONArray(cmsMigrationReport.getWarnings())
		);

		if (backgroundTask.isCompleted()) {
			jsonObject.put(
				"deleteURL",
				PortletURLBuilder.createActionURL(
					PortalUtil.getLiferayPortletResponse(_renderResponse)
				).setActionName(
					"/cms_migration/delete_cms_migration"
				).setRedirect(
					_themeDisplay.getURLCurrent()
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
						_themeDisplay.getLocale(), "cms-migration-" + phase));
			}
		}

		return jsonObject;
	}

	private static final Log _log = LogFactoryUtil.getLog(
		CMSMigrationDisplayContext.class);

	private final BackgroundTaskManager _backgroundTaskManager;
	private final DepotEntryLocalService _depotEntryLocalService;
	private final GroupLocalService _groupLocalService;
	private final RenderRequest _renderRequest;
	private final RenderResponse _renderResponse;
	private final ThemeDisplay _themeDisplay;

}