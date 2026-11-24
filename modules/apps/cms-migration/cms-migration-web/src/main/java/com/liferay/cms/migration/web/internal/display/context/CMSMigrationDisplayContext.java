/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.display.context;

import com.liferay.cms.migration.web.internal.util.CMSMigrationUtil;
import com.liferay.depot.constants.DepotConstants;
import com.liferay.depot.model.DepotEntry;
import com.liferay.depot.service.DepotEntryLocalService;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskManager;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.portlet.url.builder.PortletURLBuilder;
import com.liferay.portal.kernel.portlet.url.builder.ResourceURLBuilder;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.theme.PortletDisplay;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.WebKeys;

import jakarta.portlet.RenderRequest;
import jakarta.portlet.RenderResponse;

import jakarta.servlet.http.HttpServletRequest;

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
			CMSMigrationUtil.getMigrationsJSONArray(
				_backgroundTaskManager,
				PortalUtil.getLiferayPortletResponse(_renderResponse),
				_themeDisplay.getURLCurrent(), _themeDisplay)
		).put(
			"migrationsURL",
			ResourceURLBuilder.createResourceURL(
				PortalUtil.getLiferayPortletResponse(_renderResponse)
			).setRedirect(
				_themeDisplay.getURLCurrent()
			).setResourceID(
				"/cms_migration/get_cms_migrations"
			).buildString()
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

	private List<DepotEntry> _getSpaces() {
		return _depotEntryLocalService.getDepotEntries(
			_themeDisplay.getCompanyId(), DepotConstants.TYPE_SPACE);
	}

	private final BackgroundTaskManager _backgroundTaskManager;
	private final DepotEntryLocalService _depotEntryLocalService;
	private final GroupLocalService _groupLocalService;
	private final RenderRequest _renderRequest;
	private final RenderResponse _renderResponse;
	private final ThemeDisplay _themeDisplay;

}