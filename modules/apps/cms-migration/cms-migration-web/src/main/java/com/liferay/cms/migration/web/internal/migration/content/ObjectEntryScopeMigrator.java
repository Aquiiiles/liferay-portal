/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.migration.content;

import com.liferay.asset.kernel.model.AssetEntry;
import com.liferay.asset.kernel.service.AssetEntryLocalService;
import com.liferay.cms.migration.web.internal.constants.CMSMigrationConstants;
import com.liferay.cms.migration.web.internal.migration.CMSMigrationContext;
import com.liferay.cms.migration.web.internal.migration.entry.AttachmentCopier;
import com.liferay.cms.migration.web.internal.migration.entry.LocalizedValuesUtil;
import com.liferay.cms.migration.web.internal.migration.structure.ObjectDefinitionScopeConverter;
import com.liferay.document.library.kernel.model.DLFileEntry;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.object.constants.ObjectDefinitionConstants;
import com.liferay.object.constants.ObjectEntryFolderConstants;
import com.liferay.object.constants.ObjectFieldConstants;
import com.liferay.object.constants.ObjectFieldSettingConstants;
import com.liferay.object.field.setting.util.ObjectFieldSettingUtil;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.model.ObjectEntryFolder;
import com.liferay.object.model.ObjectEntryVersion;
import com.liferay.object.model.ObjectField;
import com.liferay.object.model.ObjectRelationship;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryFolderLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.object.service.ObjectEntryVersionLocalService;
import com.liferay.object.service.ObjectFieldLocalService;
import com.liferay.object.service.ObjectRelationshipLocalService;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.workflow.WorkflowConstants;

import java.io.Serializable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Albert Gomes Cabral
 */
public class ObjectEntryScopeMigrator {

	public static void populateServiceContext(
		AssetEntryLocalService assetEntryLocalService,
		ObjectDefinition objectDefinition, ObjectEntry objectEntry,
		ServiceContext serviceContext) {

		AssetEntry assetEntry = assetEntryLocalService.fetchEntry(
			objectDefinition.getClassName(), objectEntry.getObjectEntryId());

		if (assetEntry != null) {
			serviceContext.setAssetCategoryIds(assetEntry.getCategoryIds());
			serviceContext.setAssetPriority(assetEntry.getPriority());
			serviceContext.setAssetTagNames(assetEntry.getTagNames());
		}

		if (objectEntry.isApproved()) {
			serviceContext.setWorkflowAction(WorkflowConstants.ACTION_PUBLISH);
		}
		else {
			serviceContext.setWorkflowAction(
				WorkflowConstants.ACTION_SAVE_DRAFT);
		}
	}

	public ObjectEntryScopeMigrator(
		AssetEntryLocalService assetEntryLocalService,
		AttachmentCopier attachmentCopier, DLAppLocalService dlAppLocalService,
		ObjectDefinitionLocalService objectDefinitionLocalService,
		ObjectDefinitionScopeConverter objectDefinitionScopeConverter,
		ObjectEntryFolderLocalService objectEntryFolderLocalService,
		ObjectEntryLocalService objectEntryLocalService,
		ObjectEntryVersionLocalService objectEntryVersionLocalService,
		ObjectFieldLocalService objectFieldLocalService,
		ObjectRelationshipLocalService objectRelationshipLocalService) {

		_assetEntryLocalService = assetEntryLocalService;
		_attachmentCopier = attachmentCopier;
		_dlAppLocalService = dlAppLocalService;
		_objectDefinitionLocalService = objectDefinitionLocalService;
		_objectDefinitionScopeConverter = objectDefinitionScopeConverter;
		_objectEntryFolderLocalService = objectEntryFolderLocalService;
		_objectEntryLocalService = objectEntryLocalService;
		_objectEntryVersionLocalService = objectEntryVersionLocalService;
		_objectFieldLocalService = objectFieldLocalService;
		_objectRelationshipLocalService = objectRelationshipLocalService;
	}

	public long getCount(CMSMigrationContext cmsMigrationContext) {
		long count = 0;

		for (ObjectDefinition objectDefinition :
				_getObjectDefinitions(cmsMigrationContext, false)) {

			count += _objectEntryLocalService.getObjectEntriesCount(
				cmsMigrationContext.getSourceGroupId(),
				objectDefinition.getObjectDefinitionId());
		}

		return count;
	}

	public void migrateAll(CMSMigrationContext cmsMigrationContext)
		throws PortalException {

		ObjectEntryFolder objectEntryFolder =
			_objectEntryFolderLocalService.
				getObjectEntryFolderByExternalReferenceCode(
					ObjectEntryFolderConstants.EXTERNAL_REFERENCE_CODE_CONTENTS,
					cmsMigrationContext.getDepotGroupId(),
					cmsMigrationContext.getCompanyId());

		for (ObjectDefinition objectDefinition :
				_getObjectDefinitions(cmsMigrationContext, true)) {

			List<String> titleObjectFieldNames = _getTitleObjectFieldNames(
				cmsMigrationContext, objectDefinition);

			try {
				objectDefinition = _objectDefinitionScopeConverter.convert(
					cmsMigrationContext, objectDefinition);
			}
			catch (Exception exception) {
				cmsMigrationContext.getCMSMigrationReport(
				).addError(
					StringBundler.concat(
						"Unable to convert the object definition ",
						objectDefinition.getName(), ": ",
						exception.getMessage())
				);

				continue;
			}

			List<ObjectField> objectFields =
				_objectFieldLocalService.getObjectFields(
					objectDefinition.getObjectDefinitionId());

			for (ObjectEntry objectEntry :
					_objectEntryLocalService.getObjectEntries(
						cmsMigrationContext.getSourceGroupId(),
						objectDefinition.getObjectDefinitionId(),
						QueryUtil.ALL_POS, QueryUtil.ALL_POS)) {

				cmsMigrationContext.sendStatus(
					objectEntry.getExternalReferenceCode());

				try {
					_migrate(
						cmsMigrationContext, objectDefinition, objectEntry,
						objectEntryFolder.getObjectEntryFolderId(),
						objectFields, titleObjectFieldNames);
				}
				catch (Exception exception) {
					cmsMigrationContext.getCMSMigrationReport(
					).addError(
						StringBundler.concat(
							"Unable to move the object entry ",
							objectEntry.getExternalReferenceCode(), ": ",
							exception.getMessage())
					);
				}
			}
		}
	}

	private long _getMigratedFileEntryId(
		CMSMigrationContext cmsMigrationContext, long fileEntryId,
		ObjectField objectField) {

		if (!StringUtil.equals(
				ObjectFieldSettingUtil.getValue(
					ObjectFieldSettingConstants.NAME_FILE_SOURCE,
					objectField.getObjectFieldSettings()),
				ObjectFieldSettingConstants.VALUE_CMS_BASIC_DOCUMENT)) {

			return 0;
		}

		ObjectEntry objectEntry = cmsMigrationContext.getObjectEntry(
			DLFileEntry.class.getName(), fileEntryId);

		if (objectEntry == null) {
			return 0;
		}

		Map<String, Serializable> values = objectEntry.getValues();

		return GetterUtil.getLong(
			values.get(CMSMigrationConstants.OBJECT_FIELD_NAME_FILE));
	}

	private List<ObjectDefinition> _getObjectDefinitions(
		CMSMigrationContext cmsMigrationContext, boolean warn) {

		List<ObjectDefinition> objectDefinitions = new ArrayList<>();
		Set<Long> objectDefinitionIds = new HashSet<>();

		for (ObjectDefinition objectDefinition :
				_objectDefinitionLocalService.getObjectDefinitions(
					cmsMigrationContext.getCompanyId(), true, false,
					WorkflowConstants.STATUS_APPROVED)) {

			if (!StringUtil.equals(
					objectDefinition.getScope(),
					ObjectDefinitionConstants.SCOPE_DEPOT) &&
				!StringUtil.equals(
					objectDefinition.getScope(),
					ObjectDefinitionConstants.SCOPE_SITE)) {

				continue;
			}

			int objectEntriesCount =
				_objectEntryLocalService.getObjectEntriesCount(
					cmsMigrationContext.getSourceGroupId(),
					objectDefinition.getObjectDefinitionId());

			if (objectEntriesCount == 0) {
				continue;
			}

			objectEntriesCount +=
				_objectEntryLocalService.getObjectEntriesCount(
					cmsMigrationContext.getDepotGroupId(),
					objectDefinition.getObjectDefinitionId());

			if (objectEntriesCount ==
					_objectEntryLocalService.getObjectEntriesCount(
						objectDefinition.getObjectDefinitionId())) {

				objectDefinitions.add(objectDefinition);
				objectDefinitionIds.add(
					objectDefinition.getObjectDefinitionId());
			}
			else if (warn) {
				cmsMigrationContext.getCMSMigrationReport(
				).addWarning(
					StringBundler.concat(
						"Skipped the object definition ",
						objectDefinition.getName(),
						" because other sites have entries of it")
				);
			}
		}

		boolean removed = true;

		while (removed) {
			removed = false;

			for (ObjectDefinition objectDefinition :
					new ArrayList<>(objectDefinitions)) {

				String objectRelationshipName =
					_getUnmigratedObjectRelationshipName(
						objectDefinition.getObjectDefinitionId(),
						objectDefinitionIds);

				if (objectRelationshipName == null) {
					continue;
				}

				objectDefinitions.remove(objectDefinition);

				objectDefinitionIds.remove(
					objectDefinition.getObjectDefinitionId());

				removed = true;

				if (warn) {
					cmsMigrationContext.getCMSMigrationReport(
					).addWarning(
						StringBundler.concat(
							"Skipped the object definition ",
							objectDefinition.getName(),
							" because its relationship ",
							objectRelationshipName,
							" points to an object definition that is not ",
							"migrated")
					);
				}
			}
		}

		return objectDefinitions;
	}

	private String _getTitle(
		ObjectEntry objectEntry, List<String> titleObjectFieldNames,
		Map<String, Serializable> values) {

		for (String titleObjectFieldName : titleObjectFieldNames) {
			String title = _getValue(
				values.get(titleObjectFieldName + "_i18n"));

			if (Validator.isNull(title)) {
				title = _getValue(values.get(titleObjectFieldName));
			}

			if (Validator.isNotNull(title)) {
				return title;
			}
		}

		return objectEntry.getExternalReferenceCode();
	}

	private List<String> _getTitleObjectFieldNames(
		CMSMigrationContext cmsMigrationContext,
		ObjectDefinition objectDefinition) {

		List<String> titleObjectFieldNames = ListUtil.fromArray(
			cmsMigrationContext.getTitleObjectFieldNames());

		titleObjectFieldNames.add(
			CMSMigrationConstants.OBJECT_FIELD_NAME_LEGACY_TITLE);

		ObjectField objectField = _objectFieldLocalService.fetchObjectField(
			objectDefinition.getTitleObjectFieldId());

		if ((objectField != null) &&
			!StringUtil.equals(
				objectField.getName(),
				CMSMigrationConstants.OBJECT_FIELD_NAME_TITLE)) {

			titleObjectFieldNames.add(objectField.getName());
		}

		return titleObjectFieldNames;
	}

	private String _getUnmigratedObjectRelationshipName(
		long objectDefinitionId, Set<Long> objectDefinitionIds) {

		List<ObjectRelationship> objectRelationships = new ArrayList<>(
			_objectRelationshipLocalService.getObjectRelationships(
				objectDefinitionId));

		objectRelationships.addAll(
			_objectRelationshipLocalService.
				getObjectRelationshipsByObjectDefinitionId2(
					objectDefinitionId));

		for (ObjectRelationship objectRelationship : objectRelationships) {
			if (!objectDefinitionIds.contains(
					objectRelationship.getObjectDefinitionId1()) ||
				!objectDefinitionIds.contains(
					objectRelationship.getObjectDefinitionId2())) {

				return objectRelationship.getName();
			}
		}

		return null;
	}

	private String _getValue(Object value) {
		if (value instanceof Map<?, ?> localizedValues) {
			for (Object localizedValue : localizedValues.values()) {
				if (Validator.isNotNull(localizedValue)) {
					return GetterUtil.getString(localizedValue);
				}
			}

			return null;
		}

		if (Validator.isNull(value)) {
			return null;
		}

		return GetterUtil.getString(value);
	}

	private void _migrate(
			CMSMigrationContext cmsMigrationContext,
			ObjectDefinition objectDefinition, ObjectEntry objectEntry,
			long objectEntryFolderId, List<ObjectField> objectFields,
			List<String> titleObjectFieldNames)
		throws PortalException {

		ObjectEntryVersion objectEntryVersion =
			_objectEntryVersionLocalService.fetchLatestObjectEntryVersion(
				objectEntry.getObjectEntryId());

		if (objectEntryVersion == null) {
			objectEntryVersion =
				_objectEntryVersionLocalService.addObjectEntryVersion(
					cmsMigrationContext.getUserId(), objectEntry);

			objectEntry.setVersion(objectEntryVersion.getVersion());

			objectEntry = _objectEntryLocalService.updateObjectEntry(
				objectEntry);
		}

		Map<String, Serializable> values = new HashMap<>(
			objectEntry.getValues());

		_putAttachmentValues(
			cmsMigrationContext, objectDefinition, objectEntry, objectFields,
			values);
		_putTitleValue(
			cmsMigrationContext, objectEntry, titleObjectFieldNames, values);

		ServiceContext serviceContext =
			(ServiceContext)cmsMigrationContext.getServiceContext(
			).clone();

		populateServiceContext(
			_assetEntryLocalService, objectDefinition, objectEntry,
			serviceContext);

		_objectEntryLocalService.moveObjectEntry(
			cmsMigrationContext.getUserId(), objectEntry.getObjectEntryId(),
			objectEntryFolderId, values, serviceContext);

		cmsMigrationContext.getCMSMigrationReport(
		).increment(
			"object-entries"
		);
	}

	private void _putAttachmentValues(
		CMSMigrationContext cmsMigrationContext,
		ObjectDefinition objectDefinition, ObjectEntry objectEntry,
		List<ObjectField> objectFields, Map<String, Serializable> values) {

		for (ObjectField objectField : objectFields) {
			if (!objectField.compareBusinessType(
					ObjectFieldConstants.BUSINESS_TYPE_ATTACHMENT)) {

				continue;
			}

			long fileEntryId = GetterUtil.getLong(
				values.get(objectField.getName()));

			if (fileEntryId <= 0) {
				continue;
			}

			try {
				FileEntry fileEntry = _dlAppLocalService.getFileEntry(
					fileEntryId);

				if (fileEntry.getGroupId() ==
						cmsMigrationContext.getDepotGroupId()) {

					continue;
				}
			}
			catch (PortalException portalException) {
				cmsMigrationContext.getCMSMigrationReport(
				).addWarning(
					StringBundler.concat(
						"Unable to find the attachment ", fileEntryId, " of ",
						objectEntry.getExternalReferenceCode(), ": ",
						portalException.getMessage())
				);

				continue;
			}

			long migratedFileEntryId = _getMigratedFileEntryId(
				cmsMigrationContext, fileEntryId, objectField);

			if (migratedFileEntryId <= 0) {
				migratedFileEntryId = _attachmentCopier.copy(
					cmsMigrationContext, objectDefinition,
					objectField.getName(),
					StringBundler.concat(
						CMSMigrationConstants.
							EXTERNAL_REFERENCE_CODE_PREFIX_OBJECT_ENTRY,
						objectEntry.getObjectEntryId(), StringPool.UNDERLINE,
						objectField.getObjectFieldId()),
					fileEntryId);
			}

			if (migratedFileEntryId > 0) {
				values.put(objectField.getName(), migratedFileEntryId);
			}
		}
	}

	private void _putTitleValue(
		CMSMigrationContext cmsMigrationContext, ObjectEntry objectEntry,
		List<String> titleObjectFieldNames, Map<String, Serializable> values) {

		String i18nObjectFieldName =
			CMSMigrationConstants.OBJECT_FIELD_NAME_TITLE + "_i18n";

		if (Validator.isNotNull(_getValue(values.get(i18nObjectFieldName)))) {
			return;
		}

		String languageId = objectEntry.getDefaultLanguageId();

		if (Validator.isNull(languageId)) {
			ServiceContext serviceContext =
				cmsMigrationContext.getServiceContext();

			languageId = serviceContext.getLanguageId();
		}

		values.put(
			i18nObjectFieldName,
			HashMapBuilder.put(
				languageId,
				LocalizedValuesUtil.truncate(
					_getTitle(objectEntry, titleObjectFieldNames, values), 280)
			).build());
	}

	private final AssetEntryLocalService _assetEntryLocalService;
	private final AttachmentCopier _attachmentCopier;
	private final DLAppLocalService _dlAppLocalService;
	private final ObjectDefinitionLocalService _objectDefinitionLocalService;
	private final ObjectDefinitionScopeConverter
		_objectDefinitionScopeConverter;
	private final ObjectEntryFolderLocalService _objectEntryFolderLocalService;
	private final ObjectEntryLocalService _objectEntryLocalService;
	private final ObjectEntryVersionLocalService
		_objectEntryVersionLocalService;
	private final ObjectFieldLocalService _objectFieldLocalService;
	private final ObjectRelationshipLocalService
		_objectRelationshipLocalService;

}