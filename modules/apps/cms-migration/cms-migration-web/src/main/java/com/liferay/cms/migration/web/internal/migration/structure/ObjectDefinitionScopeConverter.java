/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.migration.structure;

import com.liferay.cms.migration.web.internal.constants.CMSMigrationConstants;
import com.liferay.cms.migration.web.internal.migration.CMSMigrationContext;
import com.liferay.object.constants.ObjectDefinitionConstants;
import com.liferay.object.constants.ObjectDefinitionSettingConstants;
import com.liferay.object.constants.ObjectFieldConstants;
import com.liferay.object.constants.ObjectFieldSettingConstants;
import com.liferay.object.constants.ObjectFolderConstants;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectDefinitionSetting;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.model.ObjectField;
import com.liferay.object.model.ObjectFieldSetting;
import com.liferay.object.model.ObjectFolder;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectDefinitionSettingLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.object.service.ObjectFieldLocalService;
import com.liferay.object.service.ObjectFieldSettingLocalService;
import com.liferay.object.service.ObjectFolderLocalService;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.workflow.WorkflowConstants;

import java.io.Serializable;

import java.util.Map;

/**
 * @author Albert Gomes Cabral
 */
public class ObjectDefinitionScopeConverter {

	public ObjectDefinitionScopeConverter(
		ObjectDefinitionLocalService objectDefinitionLocalService,
		ObjectDefinitionMigrator objectDefinitionMigrator,
		ObjectDefinitionSettingLocalService objectDefinitionSettingLocalService,
		ObjectEntryLocalService objectEntryLocalService,
		ObjectFieldLocalService objectFieldLocalService,
		ObjectFieldSettingLocalService objectFieldSettingLocalService,
		ObjectFolderLocalService objectFolderLocalService) {

		_objectDefinitionLocalService = objectDefinitionLocalService;
		_objectDefinitionMigrator = objectDefinitionMigrator;
		_objectDefinitionSettingLocalService =
			objectDefinitionSettingLocalService;
		_objectEntryLocalService = objectEntryLocalService;
		_objectFieldLocalService = objectFieldLocalService;
		_objectFieldSettingLocalService = objectFieldSettingLocalService;
		_objectFolderLocalService = objectFolderLocalService;
	}

	public ObjectDefinition convert(
			CMSMigrationContext cmsMigrationContext,
			ObjectDefinition objectDefinition)
		throws PortalException {

		_addTitleObjectField(cmsMigrationContext, objectDefinition);

		_objectDefinitionMigrator.addContentObjectField(
			cmsMigrationContext, objectDefinition);

		_updateAttachmentObjectFields(objectDefinition);

		ObjectField objectField = _objectFieldLocalService.getObjectField(
			objectDefinition.getObjectDefinitionId(),
			CMSMigrationConstants.OBJECT_FIELD_NAME_TITLE);

		objectDefinition =
			_objectDefinitionLocalService.updateTitleObjectFieldId(
				objectDefinition.getObjectDefinitionId(),
				objectField.getObjectFieldId());

		_addAcceptedGroupId(cmsMigrationContext, objectDefinition);

		if (objectDefinition.isEnableIndexSearch() &&
			objectDefinition.isEnableObjectEntryDraft() &&
			objectDefinition.isEnableObjectEntryHistory() &&
			objectDefinition.isEnableObjectEntryVersioning() &&
			(objectDefinition.getPanelCategoryKey() == null) &&
			StringUtil.equals(
				objectDefinition.getScope(),
				ObjectDefinitionConstants.SCOPE_DEPOT)) {

			return objectDefinition;
		}

		objectDefinition.setEnableIndexSearch(true);
		objectDefinition.setEnableObjectEntryDraft(true);
		objectDefinition.setEnableObjectEntryHistory(true);
		objectDefinition.setEnableObjectEntryVersioning(true);
		objectDefinition.setPanelCategoryKey(null);
		objectDefinition.setScope(ObjectDefinitionConstants.SCOPE_DEPOT);

		objectDefinition = _objectDefinitionLocalService.updateObjectDefinition(
			objectDefinition);

		ObjectFolder objectFolder =
			_objectFolderLocalService.getObjectFolderByExternalReferenceCode(
				ObjectFolderConstants.
					EXTERNAL_REFERENCE_CODE_CONTENT_STRUCTURES,
				cmsMigrationContext.getCompanyId());

		objectDefinition = _objectDefinitionLocalService.updateObjectFolderId(
			objectDefinition.getObjectDefinitionId(),
			objectFolder.getObjectFolderId());

		_objectDefinitionLocalService.deployObjectDefinition(objectDefinition);

		cmsMigrationContext.getCMSMigrationReport(
		).increment(
			"object-definitions"
		);

		return objectDefinition;
	}

	private void _addAcceptedGroupId(
			CMSMigrationContext cmsMigrationContext,
			ObjectDefinition objectDefinition)
		throws PortalException {

		ObjectDefinitionSetting objectDefinitionSetting =
			_objectDefinitionSettingLocalService.fetchObjectDefinitionSetting(
				objectDefinition.getObjectDefinitionId(),
				ObjectDefinitionSettingConstants.NAME_ACCEPTED_GROUP_IDS);

		String depotGroupIdString = String.valueOf(
			cmsMigrationContext.getDepotGroupId());

		if (objectDefinitionSetting == null) {
			_objectDefinitionSettingLocalService.addObjectDefinitionSetting(
				cmsMigrationContext.getUserId(),
				objectDefinition.getObjectDefinitionId(),
				ObjectDefinitionSettingConstants.NAME_ACCEPTED_GROUP_IDS,
				depotGroupIdString);

			return;
		}

		String[] acceptedGroupIds = StringUtil.split(
			objectDefinitionSetting.getValue());

		if (ArrayUtil.contains(acceptedGroupIds, depotGroupIdString)) {
			return;
		}

		objectDefinitionSetting.setValue(
			StringUtil.merge(
				ArrayUtil.append(acceptedGroupIds, depotGroupIdString)));

		_objectDefinitionSettingLocalService.updateObjectDefinitionSetting(
			objectDefinitionSetting);
	}

	private void _addTitleObjectField(
			CMSMigrationContext cmsMigrationContext,
			ObjectDefinition objectDefinition)
		throws PortalException {

		ObjectField objectField = _objectFieldLocalService.fetchObjectField(
			objectDefinition.getObjectDefinitionId(),
			CMSMigrationConstants.OBJECT_FIELD_NAME_TITLE);

		if ((objectField != null) &&
			objectField.compareBusinessType(
				ObjectFieldConstants.BUSINESS_TYPE_TEXT) &&
			objectField.isLocalized()) {

			return;
		}

		if (objectField != null) {
			_replaceTitleObjectField(
				cmsMigrationContext, objectDefinition, objectField);
		}

		_objectDefinitionMigrator.addTitleObjectField(
			cmsMigrationContext, objectDefinition);
	}

	private void _copyTitleValue(
			CMSMigrationContext cmsMigrationContext, ObjectEntry objectEntry)
		throws PortalException {

		Map<String, Serializable> values = objectEntry.getValues();

		Serializable value = values.get(
			CMSMigrationConstants.OBJECT_FIELD_NAME_TITLE);

		if (Validator.isNull(value) ||
			Validator.isNotNull(
				values.get(
					CMSMigrationConstants.OBJECT_FIELD_NAME_LEGACY_TITLE))) {

			return;
		}

		ServiceContext serviceContext =
			(ServiceContext)cmsMigrationContext.getServiceContext(
			).clone();

		if (objectEntry.isApproved()) {
			serviceContext.setWorkflowAction(WorkflowConstants.ACTION_PUBLISH);
		}
		else {
			serviceContext.setWorkflowAction(
				WorkflowConstants.ACTION_SAVE_DRAFT);
		}

		_objectEntryLocalService.updateObjectEntry(
			cmsMigrationContext.getUserId(), objectEntry.getObjectEntryId(),
			objectEntry.getObjectEntryFolderId(),
			HashMapBuilder.<String, Serializable>putAll(
				values
			).put(
				CMSMigrationConstants.OBJECT_FIELD_NAME_LEGACY_TITLE, value
			).build(),
			serviceContext);
	}

	private void _replaceTitleObjectField(
			CMSMigrationContext cmsMigrationContext,
			ObjectDefinition objectDefinition, ObjectField objectField)
		throws PortalException {

		if (objectField.isLocalized() ||
			objectField.compareBusinessType(
				ObjectFieldConstants.BUSINESS_TYPE_ATTACHMENT) ||
			Validator.isNotNull(objectField.getRelationshipType())) {

			throw new PortalException(
				StringBundler.concat(
					"The field ", CMSMigrationConstants.OBJECT_FIELD_NAME_TITLE,
					" cannot be replaced by a localized text field"));
		}

		ObjectField legacyTitleObjectField = (ObjectField)objectField.clone();

		legacyTitleObjectField.setExternalReferenceCode(
			CMSMigrationConstants.OBJECT_FIELD_NAME_LEGACY_TITLE);
		legacyTitleObjectField.setName(
			CMSMigrationConstants.OBJECT_FIELD_NAME_LEGACY_TITLE);
		legacyTitleObjectField.setRequired(false);

		_objectDefinitionMigrator.addObjectField(
			cmsMigrationContext, objectDefinition, legacyTitleObjectField);

		for (long groupId :
				new long[] {
					cmsMigrationContext.getDepotGroupId(),
					cmsMigrationContext.getSourceGroupId()
				}) {

			for (ObjectEntry objectEntry :
					_objectEntryLocalService.getObjectEntries(
						groupId, objectDefinition.getObjectDefinitionId(),
						QueryUtil.ALL_POS, QueryUtil.ALL_POS)) {

				_copyTitleValue(cmsMigrationContext, objectEntry);
			}
		}

		_objectFieldLocalService.deleteObjectField(objectField);
	}

	private void _updateAttachmentObjectFields(
			ObjectDefinition objectDefinition)
		throws PortalException {

		for (ObjectField objectField :
				_objectFieldLocalService.getObjectFields(
					objectDefinition.getObjectDefinitionId())) {

			if (!objectField.compareBusinessType(
					ObjectFieldConstants.BUSINESS_TYPE_ATTACHMENT)) {

				continue;
			}

			ObjectFieldSetting objectFieldSetting =
				_objectFieldSettingLocalService.fetchObjectFieldSetting(
					objectField.getObjectFieldId(),
					ObjectFieldSettingConstants.NAME_FILE_SOURCE);

			if ((objectFieldSetting != null) &&
				StringUtil.equals(
					objectFieldSetting.getValue(),
					ObjectFieldSettingConstants.VALUE_DOCS_AND_MEDIA)) {

				_objectFieldSettingLocalService.updateObjectFieldSetting(
					objectFieldSetting.getObjectFieldSettingId(),
					ObjectFieldSettingConstants.VALUE_CMS_BASIC_DOCUMENT);
			}
		}
	}

	private final ObjectDefinitionLocalService _objectDefinitionLocalService;
	private final ObjectDefinitionMigrator _objectDefinitionMigrator;
	private final ObjectDefinitionSettingLocalService
		_objectDefinitionSettingLocalService;
	private final ObjectEntryLocalService _objectEntryLocalService;
	private final ObjectFieldLocalService _objectFieldLocalService;
	private final ObjectFieldSettingLocalService
		_objectFieldSettingLocalService;
	private final ObjectFolderLocalService _objectFolderLocalService;

}