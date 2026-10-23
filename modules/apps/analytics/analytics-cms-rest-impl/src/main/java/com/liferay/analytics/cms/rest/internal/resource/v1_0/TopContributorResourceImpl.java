/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.analytics.cms.rest.internal.resource.v1_0;

import com.liferay.analytics.cms.rest.dto.v1_0.TopContributor;
import com.liferay.analytics.cms.rest.internal.depot.entry.util.DepotEntryUtil;
import com.liferay.analytics.cms.rest.internal.resource.v1_0.util.DateRangeUtil;
import com.liferay.analytics.cms.rest.resource.v1_0.TopContributorResource;
import com.liferay.depot.model.DepotEntry;
import com.liferay.object.constants.ObjectFolderConstants;
import com.liferay.object.model.ObjectDefinitionTable;
import com.liferay.object.model.ObjectEntryTable;
import com.liferay.object.model.ObjectEntryVersionTable;
import com.liferay.object.model.ObjectFolderTable;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.petra.sql.dsl.DSLFunctionFactoryUtil;
import com.liferay.petra.sql.dsl.DSLQueryFactoryUtil;
import com.liferay.petra.sql.dsl.expression.Predicate;
import com.liferay.petra.sql.dsl.query.FromStep;
import com.liferay.petra.sql.dsl.query.GroupByStep;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.license.util.LicenseManagerUtil;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.vulcan.pagination.Page;
import com.liferay.portal.vulcan.pagination.Pagination;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ServiceScope;

/**
 * @author Aquiles Duarte
 */
@Component(
	properties = "OSGI-INF/liferay/rest/v1_0/top-contributor.properties",
	scope = ServiceScope.PROTOTYPE, service = TopContributorResource.class
)
public class TopContributorResourceImpl extends BaseTopContributorResourceImpl {

	@Override
	public Page<TopContributor> getTopContributorsPage(
			Long depotEntryId, String rangeEnd, Integer rangeKey,
			String rangeStart, Pagination pagination)
		throws Exception {

		LicenseManagerUtil.checkFreeTier();

		List<DepotEntry> depotEntries = DepotEntryUtil.getDepotEntries(
			ActionKeys.VIEW, contextCompany.getCompanyId(), depotEntryId);

		if (depotEntries.isEmpty()) {
			return Page.of(Collections.emptyList(), pagination, 0);
		}

		Long[] groupIds = DepotEntryUtil.getGroupIds(depotEntries);

		return Page.of(
			transform(
				(List<Object[]>)_objectEntryLocalService.dslQuery(
					_getGroupByStep(
						DSLQueryFactoryUtil.select(
							DSLFunctionFactoryUtil.countDistinct(
								ObjectEntryTable.INSTANCE.objectEntryId
							).as(
								"assetCount"
							),
							ObjectEntryTable.INSTANCE.userId.as("userId")),
						groupIds, rangeEnd, rangeKey, rangeStart
					).groupBy(
						ObjectEntryTable.INSTANCE.userId
					).orderBy(
						DSLFunctionFactoryUtil.countDistinct(
							ObjectEntryTable.INSTANCE.objectEntryId
						).descending(),
						ObjectEntryTable.INSTANCE.userId.ascending()
					).limit(
						pagination.getStartPosition(),
						pagination.getEndPosition()
					)),
				objects -> {
					TopContributor topContributor = new TopContributor();

					long userId = GetterUtil.getLong(objects[1]);

					topContributor.setAssetCount(
						() -> GetterUtil.getLong(objects[0]));
					topContributor.setUserId(() -> userId);
					topContributor.setUserName(() -> _getUserName(userId));

					return topContributor;
				}),
			pagination,
			_objectEntryLocalService.dslQueryCount(
				_getGroupByStep(
					DSLQueryFactoryUtil.countDistinct(
						ObjectEntryTable.INSTANCE.userId),
					groupIds, rangeEnd, rangeKey, rangeStart)));
	}

	private GroupByStep _getGroupByStep(
		FromStep fromStep, Long[] groupIds, String rangeEnd, Integer rangeKey,
		String rangeStart) {

		return fromStep.from(
			ObjectFolderTable.INSTANCE
		).innerJoinON(
			ObjectDefinitionTable.INSTANCE,
			ObjectDefinitionTable.INSTANCE.objectFolderId.eq(
				ObjectFolderTable.INSTANCE.objectFolderId)
		).innerJoinON(
			ObjectEntryTable.INSTANCE,
			ObjectEntryTable.INSTANCE.objectDefinitionId.eq(
				ObjectDefinitionTable.INSTANCE.objectDefinitionId)
		).innerJoinON(
			ObjectEntryVersionTable.INSTANCE,
			ObjectEntryVersionTable.INSTANCE.objectEntryId.eq(
				ObjectEntryTable.INSTANCE.objectEntryId
			).and(
				ObjectEntryVersionTable.INSTANCE.version.eq(
					ObjectEntryTable.INSTANCE.version)
			).and(
				ObjectEntryVersionTable.INSTANCE.status.eq(
					WorkflowConstants.STATUS_APPROVED)
			)
		).where(
			_getPredicate(groupIds, rangeEnd, rangeKey, rangeStart)
		);
	}

	private Predicate _getPredicate(
		Long[] groupIds, String rangeEnd, Integer rangeKey, String rangeStart) {

		Predicate predicate =
			ObjectFolderTable.INSTANCE.externalReferenceCode.in(
				new String[] {
					ObjectFolderConstants.
						EXTERNAL_REFERENCE_CODE_CONTENT_STRUCTURES,
					ObjectFolderConstants.EXTERNAL_REFERENCE_CODE_FILE_TYPES
				}
			).and(
				ObjectEntryTable.INSTANCE.status.neq(
					WorkflowConstants.STATUS_IN_TRASH)
			);

		if (ArrayUtil.isNotEmpty(groupIds)) {
			predicate = predicate.and(
				ObjectEntryTable.INSTANCE.groupId.in(groupIds));
		}

		Date startDate = DateRangeUtil.getStartDate(
			rangeEnd, rangeKey, rangeStart);

		if (startDate != null) {
			predicate = predicate.and(
				ObjectEntryTable.INSTANCE.createDate.gte(startDate));
		}

		if (Validator.isNotNull(rangeEnd)) {
			predicate = predicate.and(
				ObjectEntryTable.INSTANCE.createDate.lte(
					DateRangeUtil.getEndDate(rangeEnd)));
		}

		return predicate;
	}

	private String _getUserName(long userId) {
		User user = _userLocalService.fetchUser(userId);

		if (user == null) {
			return LanguageUtil.get(
				contextAcceptLanguage.getPreferredLocale(), "unknown");
		}

		return user.getFullName();
	}

	@Reference
	private ObjectEntryLocalService _objectEntryLocalService;

	@Reference
	private UserLocalService _userLocalService;

}