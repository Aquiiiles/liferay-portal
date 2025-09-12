/**
 * SPDX-FileCopyrightText: (c) 2025 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.object.rest.internal.dto.v1_0.converter.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.headless.admin.taxonomy.client.function.UnsafeSupplier;
import com.liferay.object.constants.ObjectDefinitionConstants;
import com.liferay.object.constants.ObjectEntryFolderConstants;
import com.liferay.object.constants.ObjectFieldSettingConstants;
import com.liferay.object.field.builder.AttachmentObjectFieldBuilder;
import com.liferay.object.field.builder.TextObjectFieldBuilder;
import com.liferay.object.field.setting.builder.ObjectFieldSettingBuilder;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.rest.dto.v1_0.ObjectEntry;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.object.test.util.ObjectDefinitionTestUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.FileUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.TempFileEntryUtil;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.test.rule.PermissionCheckerMethodTestRule;
import com.liferay.portal.vulcan.dto.converter.DTOConverter;
import com.liferay.portal.vulcan.dto.converter.DTOConverterRegistry;
import com.liferay.portal.vulcan.dto.converter.DefaultDTOConverterContext;
import com.liferay.portal.vulcan.util.LocalizedMapUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.Serializable;

import java.nio.charset.StandardCharsets;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Adolfo Pérez
 */
@RunWith(Arquillian.class)
public class ObjectEntryDTOConverterTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new LiferayIntegrationTestRule(),
			PermissionCheckerMethodTestRule.INSTANCE);

	@Test
	public void testToDTO() throws Exception {
		_objectDefinition = ObjectDefinitionTestUtil.publishObjectDefinition(
			Collections.singletonList(
				new TextObjectFieldBuilder(
				).labelMap(
					LocalizedMapUtil.getLocalizedMap(
						RandomTestUtil.randomString())
				).name(
					"name"
				).build()),
			ObjectDefinitionConstants.SCOPE_SITE);

		com.liferay.object.model.ObjectEntry serviceBuilderObjectEntry =
			_objectEntryLocalService.addObjectEntry(
				TestPropsValues.getGroupId(), TestPropsValues.getUserId(),
				_objectDefinition.getObjectDefinitionId(),
				ObjectEntryFolderConstants.
					PARENT_OBJECT_ENTRY_FOLDER_ID_DEFAULT,
				null,
				HashMapBuilder.<String, Serializable>put(
					"name", StringUtil.randomString()
				).build(),
				ServiceContextTestUtil.getServiceContext(
					TestPropsValues.getGroupId()));

		Group group = _groupLocalService.getGroup(
			serviceBuilderObjectEntry.getGroupId());

		ObjectEntry objectEntry = _toDTO(serviceBuilderObjectEntry);

		Assert.assertEquals(
			group.getGroupId(), GetterUtil.getLong(objectEntry.getScopeId()));
		Assert.assertEquals(group.getGroupKey(), objectEntry.getScopeKey());
	}

	@Test
	public void testToDTOWithLocalizedAttachmentDoesNotMutateI18nMap()
		throws Exception {

		_objectDefinition = ObjectDefinitionTestUtil.publishObjectDefinition(
			Collections.singletonList(
				new AttachmentObjectFieldBuilder(
				).labelMap(
					LocalizedMapUtil.getLocalizedMap(
						"localizedAttachment " + RandomTestUtil.randomString())
				).localized(
					true
				).name(
					"localizedAttachment"
				).objectFieldSettings(
					Arrays.asList(
						new ObjectFieldSettingBuilder(
						).name(
							ObjectFieldSettingConstants.
								NAME_ACCEPTED_FILE_EXTENSIONS
						).value(
							"pdf"
						).build(),
						new ObjectFieldSettingBuilder(
						).name(
							ObjectFieldSettingConstants.NAME_FILE_SOURCE
						).value(
							ObjectFieldSettingConstants.VALUE_USER_COMPUTER
						).build(),
						new ObjectFieldSettingBuilder(
						).name(
							ObjectFieldSettingConstants.NAME_MAX_FILE_SIZE
						).value(
							"100"
						).build())
				).build()),
			ObjectDefinitionConstants.SCOPE_SITE);

		long groupId = TestPropsValues.getGroupId();

		String fileName = StringUtil.randomString() + ".pdf";

		FileEntry tempFile = _addTempFileEntry(
			_objectDefinition.getPortletId(), fileName);

		String langId = LocaleUtil.toLanguageId(LocaleUtil.getSiteDefault());

		com.liferay.object.model.ObjectEntry objectEntry =
			_objectEntryLocalService.addObjectEntry(
				groupId, TestPropsValues.getUserId(),
				_objectDefinition.getObjectDefinitionId(),
				ObjectEntryFolderConstants.
					PARENT_OBJECT_ENTRY_FOLDER_ID_DEFAULT,
				null,
				HashMapBuilder.<String, Serializable>put(
					"localizedAttachment_i18n",
					(Serializable)HashMapBuilder.<String, Serializable>put(
						langId, tempFile.getFileEntryId()
					).build()
				).build(),
				ServiceContextTestUtil.getServiceContext(groupId));

		Map<String, Serializable> values = objectEntry.getValues();

		Object originalI18nValue = values.get("localizedAttachment_i18n");

		@SuppressWarnings("unchecked")
		Map<String, Serializable> originalI18nMap =
			(Map<String, Serializable>)originalI18nValue;

		Assert.assertTrue(originalI18nMap.get(langId) instanceof Number);

		ObjectEntry dto = _toDTO(objectEntry);

		Object properties = dto.getProperties();

		@SuppressWarnings("unchecked")
		Map<String, Object> dtoProperties = (Map<String, Object>)properties;

		Object direct = _unwrap(dtoProperties.get("localizedAttachment"));

		Assert.assertNotNull(direct);
		Assert.assertTrue(
			direct instanceof com.liferay.object.rest.dto.v1_0.FileEntry);

		Object i18nValue = _unwrap(
			dtoProperties.get("localizedAttachment_i18n"));

		@SuppressWarnings("unchecked")
		Map<String, Object> dtoI18n = (Map<String, Object>)i18nValue;

		Assert.assertNotNull(dtoI18n);
		Assert.assertTrue(
			dtoI18n.get(langId) instanceof
				com.liferay.object.rest.dto.v1_0.FileEntry);

		Assert.assertTrue(originalI18nMap.get(langId) instanceof Number);
	}

	private FileEntry _addTempFileEntry(String folderName, String fileName)
		throws Exception {

		if (!fileName.endsWith(".pdf")) {
			fileName = fileName + ".pdf";
		}

		File file = FileUtil.createTempFile("pdf");

		try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
			fileOutputStream.write(
				"%PDF-1.4\n%".getBytes(StandardCharsets.UTF_8));
		}

		return (FileEntry)TempFileEntryUtil.addTempFileEntry(
			TestPropsValues.getGroupId(), TestPropsValues.getUserId(),
			folderName, fileName, file, "application/pdf");
	}

	private ObjectEntry _toDTO(
			com.liferay.object.model.ObjectEntry serviceBuilderObjectEntry)
		throws Exception {

		DTOConverter<com.liferay.object.model.ObjectEntry, ObjectEntry>
			dtoConverter =
				(DTOConverter
					<com.liferay.object.model.ObjectEntry, ObjectEntry>)
						_dtoConverterRegistry.getDTOConverter(
							com.liferay.object.model.ObjectEntry.class.
								getName());

		DefaultDTOConverterContext dtoConverterContext =
			new DefaultDTOConverterContext(
				_dtoConverterRegistry,
				serviceBuilderObjectEntry.getObjectEntryId(),
				LocaleUtil.getDefault(), null, null);

		dtoConverterContext.setAttribute("objectDefinition", _objectDefinition);

		return dtoConverter.toDTO(
			dtoConverterContext, serviceBuilderObjectEntry);
	}

	private Object _unwrap(Object maybeSupplier) {
		if (maybeSupplier instanceof UnsafeSupplier) {
			try {
				@SuppressWarnings("unchecked")
				UnsafeSupplier<?, ?> unsafeSupplier =
					(UnsafeSupplier<?, ?>)maybeSupplier;

				return unsafeSupplier.get();
			}
			catch (Throwable throwable) {
				throw new RuntimeException(throwable);
			}
		}

		return maybeSupplier;
	}

	@Inject
	private DTOConverterRegistry _dtoConverterRegistry;

	@Inject
	private GroupLocalService _groupLocalService;

	@DeleteAfterTestRun
	private ObjectDefinition _objectDefinition;

	@Inject
	private ObjectEntryLocalService _objectEntryLocalService;

}