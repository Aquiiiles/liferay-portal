/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.analytics.cms.rest.resource.v1_0.test;

import com.liferay.analytics.cms.rest.client.dto.v1_0.TopContributor;
import com.liferay.analytics.cms.rest.client.pagination.Page;
import com.liferay.analytics.cms.rest.client.pagination.Pagination;
import com.liferay.analytics.cms.rest.client.problem.Problem;
import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.depot.constants.DepotConstants;
import com.liferay.depot.model.DepotEntry;
import com.liferay.depot.service.DepotEntryLocalService;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.test.util.UserTestUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.test.rule.PermissionCheckerMethodTestRule;

import java.io.Serializable;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Aquiles Duarte
 */
@RunWith(Arquillian.class)
public class TopContributorResourceTest
	extends BaseTopContributorResourceTestCase {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new LiferayIntegrationTestRule(),
			PermissionCheckerMethodTestRule.INSTANCE);

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();

		_objectDefinition =
			_objectDefinitionLocalService.
				getObjectDefinitionByExternalReferenceCode(
					"L_CMS_BASIC_WEB_CONTENT", testCompany.getCompanyId());
	}

	@Override
	@Test
	public void testGetTopContributorsPage() throws Exception {
		Page<TopContributor> page =
			topContributorResource.getTopContributorsPage(
				null, null, null, null, Pagination.of(1, 10));

		long totalCount = page.getTotalCount();

		TopContributor topContributor1 =
			testGetTopContributorsPage_addTopContributor(
				randomTopContributor());

		TopContributor topContributor2 =
			testGetTopContributorsPage_addTopContributor(
				randomTopContributor());

		page = topContributorResource.getTopContributorsPage(
			null, null, null, null, Pagination.of(1, (int)totalCount + 2));

		Assert.assertEquals(totalCount + 2, page.getTotalCount());

		assertContains(topContributor1, (List<TopContributor>)page.getItems());
		assertContains(topContributor2, (List<TopContributor>)page.getItems());
		assertValid(page, testGetTopContributorsPage_getExpectedActions());

		_testGetTopContributorsPageRanking();
		_testGetTopContributorsPageWithDateRange();
		_testGetTopContributorsPageWithInvalidDateRange();
	}

	@Override
	@Test
	public void testGetTopContributorsPageWithPagination() throws Exception {
		super.testGetTopContributorsPageWithPagination();

		_testGetTopContributorsPageOrdering();
		_testGetTopContributorsPageOrderingWithTiebreaker();
	}

	@Override
	protected TopContributor testGetTopContributorsPage_addTopContributor(
			TopContributor topContributor)
		throws Exception {

		if (_depotEntry == null) {
			_addDepotEntry();
		}

		User user = UserTestUtil.addUser();

		_addWebContent(user.getUserId());

		topContributor.setAssetCount(1L);
		topContributor.setUserId(user.getUserId());
		topContributor.setUserName(user.getFullName());

		return topContributor;
	}

	private void _addDepotEntry() throws Exception {
		_depotEntry = _depotEntryLocalService.addDepotEntry(
			HashMapBuilder.put(
				LocaleUtil.getDefault(), RandomTestUtil.randomString()
			).build(),
			HashMapBuilder.put(
				LocaleUtil.getDefault(), RandomTestUtil.randomString()
			).build(),
			DepotConstants.TYPE_SPACE,
			ServiceContextTestUtil.getServiceContext(
				testGroup.getGroupId(), TestPropsValues.getUserId()));

		_depotEntries.add(_depotEntry);

		_serviceContext = ServiceContextTestUtil.getServiceContext(
			_depotEntry.getGroupId(), TestPropsValues.getUserId());
	}

	private void _addWebContent(long userId) throws Exception {
		_objectEntries.add(
			_objectEntryLocalService.addObjectEntry(
				_depotEntry.getGroupId(), userId,
				_objectDefinition.getObjectDefinitionId(), 0, "en_US",
				HashMapBuilder.<String, Serializable>put(
					"title_i18n",
					HashMapBuilder.put(
						"en_US", RandomTestUtil.randomString()
					).build()
				).build(),
				_serviceContext));
	}

	private void _testGetTopContributorsPageOrdering() throws Exception {
		_addDepotEntry();

		User user1 = UserTestUtil.addUser();
		User user2 = UserTestUtil.addUser();
		User user3 = UserTestUtil.addUser();

		for (int i = 0; i < 3; i++) {
			_addWebContent(user1.getUserId());
		}

		for (int i = 0; i < 2; i++) {
			_addWebContent(user2.getUserId());
		}

		_addWebContent(user3.getUserId());

		Page<TopContributor> page1 =
			topContributorResource.getTopContributorsPage(
				_depotEntry.getDepotEntryId(), null, null, null,
				Pagination.of(1, 2));

		Assert.assertEquals(3L, (long)page1.getTotalCount());

		List<TopContributor> topContributors1 =
			(List<TopContributor>)page1.getItems();

		Assert.assertEquals(
			topContributors1.toString(), 2, topContributors1.size());

		TopContributor topContributor1 = topContributors1.get(0);

		Assert.assertEquals(
			user1.getUserId(), (long)topContributor1.getUserId());
		Assert.assertEquals(3L, (long)topContributor1.getAssetCount());

		TopContributor topContributor2 = topContributors1.get(1);

		Assert.assertEquals(
			user2.getUserId(), (long)topContributor2.getUserId());
		Assert.assertEquals(2L, (long)topContributor2.getAssetCount());

		Page<TopContributor> page2 =
			topContributorResource.getTopContributorsPage(
				_depotEntry.getDepotEntryId(), null, null, null,
				Pagination.of(2, 2));

		List<TopContributor> topContributors2 =
			(List<TopContributor>)page2.getItems();

		Assert.assertEquals(
			topContributors2.toString(), 1, topContributors2.size());

		TopContributor topContributor3 = topContributors2.get(0);

		Assert.assertEquals(
			user3.getUserId(), (long)topContributor3.getUserId());
		Assert.assertEquals(1L, (long)topContributor3.getAssetCount());
	}

	private void _testGetTopContributorsPageOrderingWithTiebreaker()
		throws Exception {

		_addDepotEntry();

		User user1 = UserTestUtil.addUser();
		User user2 = UserTestUtil.addUser();

		_addWebContent(user1.getUserId());
		_addWebContent(user2.getUserId());

		Page<TopContributor> page1 =
			topContributorResource.getTopContributorsPage(
				_depotEntry.getDepotEntryId(), null, null, null,
				Pagination.of(1, 1));

		List<TopContributor> topContributors1 =
			(List<TopContributor>)page1.getItems();

		Assert.assertEquals(
			topContributors1.toString(), 1, topContributors1.size());

		TopContributor topContributor1 = topContributors1.get(0);

		Assert.assertEquals(
			user1.getUserId(), (long)topContributor1.getUserId());

		Page<TopContributor> page2 =
			topContributorResource.getTopContributorsPage(
				_depotEntry.getDepotEntryId(), null, null, null,
				Pagination.of(2, 1));

		List<TopContributor> topContributors2 =
			(List<TopContributor>)page2.getItems();

		Assert.assertEquals(
			topContributors2.toString(), 1, topContributors2.size());

		TopContributor topContributor2 = topContributors2.get(0);

		Assert.assertEquals(
			user2.getUserId(), (long)topContributor2.getUserId());
	}

	private void _testGetTopContributorsPageRanking() throws Exception {
		_addDepotEntry();

		User user = UserTestUtil.addUser();

		_addWebContent(TestPropsValues.getUserId());
		_addWebContent(TestPropsValues.getUserId());
		_addWebContent(user.getUserId());

		Page<TopContributor> page =
			topContributorResource.getTopContributorsPage(
				_depotEntry.getDepotEntryId(), null, null, null,
				Pagination.of(1, 10));

		Assert.assertEquals(2L, (long)page.getTotalCount());

		List<TopContributor> topContributors =
			(List<TopContributor>)page.getItems();

		Assert.assertEquals(
			topContributors.toString(), 2, topContributors.size());

		TopContributor topContributor1 = topContributors.get(0);

		Assert.assertEquals(
			TestPropsValues.getUserId(), (long)topContributor1.getUserId());
		Assert.assertEquals(2L, (long)topContributor1.getAssetCount());

		TopContributor topContributor2 = topContributors.get(1);

		Assert.assertEquals(
			user.getUserId(), (long)topContributor2.getUserId());
		Assert.assertEquals(1L, (long)topContributor2.getAssetCount());
		Assert.assertEquals(user.getFullName(), topContributor2.getUserName());
	}

	private void _testGetTopContributorsPageWithDateRange() throws Exception {
		_addDepotEntry();

		_addWebContent(TestPropsValues.getUserId());

		Page<TopContributor> page =
			topContributorResource.getTopContributorsPage(
				_depotEntry.getDepotEntryId(), "2000-01-31", null, "2000-01-01",
				Pagination.of(1, 10));

		Assert.assertEquals(0L, (long)page.getTotalCount());
	}

	private void _testGetTopContributorsPageWithInvalidDateRange()
		throws Exception {

		_addDepotEntry();

		try {
			topContributorResource.getTopContributorsPage(
				_depotEntry.getDepotEntryId(), RandomTestUtil.randomString(),
				null, RandomTestUtil.randomString(), Pagination.of(1, 10));

			Assert.fail();
		}
		catch (Problem.ProblemException problemException) {
			Problem problem = problemException.getProblem();

			Assert.assertEquals("BAD_REQUEST", problem.getStatus());
		}
	}

	@DeleteAfterTestRun
	private final List<DepotEntry> _depotEntries = new ArrayList<>();

	private DepotEntry _depotEntry;

	@Inject
	private DepotEntryLocalService _depotEntryLocalService;

	private ObjectDefinition _objectDefinition;

	@Inject
	private ObjectDefinitionLocalService _objectDefinitionLocalService;

	@DeleteAfterTestRun
	private final List<ObjectEntry> _objectEntries = new ArrayList<>();

	@Inject
	private ObjectEntryLocalService _objectEntryLocalService;

	private ServiceContext _serviceContext;

}