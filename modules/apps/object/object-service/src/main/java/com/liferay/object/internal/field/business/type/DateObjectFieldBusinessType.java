/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.object.internal.field.business.type;

import com.liferay.dynamic.data.mapping.form.field.type.constants.DDMFormFieldTypeConstants;
import com.liferay.object.constants.ObjectFieldConstants;
import com.liferay.object.field.business.type.ObjectFieldBusinessType;
import com.liferay.object.field.setting.util.ObjectFieldSettingUtil;
import com.liferay.object.field.util.ObjectFieldUtil;
import com.liferay.object.model.ObjectField;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.MapUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.vulcan.extension.PropertyDefinition;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import java.util.Locale;
import java.util.Map;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Marcela Cunha
 */
@Component(
	property = "object.field.business.type.key=" + ObjectFieldConstants.BUSINESS_TYPE_DATE,
	service = ObjectFieldBusinessType.class
)
public class DateObjectFieldBusinessType implements ObjectFieldBusinessType {

	@Override
	public String getDBType() {
		return ObjectFieldConstants.DB_TYPE_DATE;
	}

	@Override
	public String getDDMFormFieldTypeName() {
		return DDMFormFieldTypeConstants.DATE_TIME;
	}

	@Override
	public String getDDMFormFieldTypeName(ObjectField objectField) {
		if (objectField.isMetadata()) {
			return DDMFormFieldTypeConstants.DATE_TIME;
		}

		return DDMFormFieldTypeConstants.DATE;
	}

	@Override
	public String getDescription(Locale locale) {
		return _language.get(locale, "add-a-date");
	}

	@Override
	public Object getDisplayContextValue(
			ObjectField objectField, long userId, Map<String, Object> values)
		throws PortalException {

		DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(
			"yyyy-MM-dd HH:mm");

		User user = _userLocalService.getUser(userId);

		if (objectField.isLocalized()) {
			Map<String, Object> localizedValues =
				ObjectFieldBusinessType.super.getLocalizedValues(
					objectField, userId, values);

			if (localizedValues == null) {
				return null;
			}

			for (Map.Entry<String, Object> entry : localizedValues.entrySet()) {
				localizedValues.put(
					entry.getKey(),
					dateTimeFormatter.format(
						_getLocalDateTime(
							StringPool.UTC,
							ObjectFieldSettingUtil.getTimeZoneId(
								objectField.getObjectFieldSettings(), user),
							GetterUtil.getString(entry.getValue()))));
			}

			return localizedValues;
		}

		String value = MapUtil.getString(values, objectField.getName());

		if (Validator.isNull(value)) {
			return StringPool.BLANK;
		}

		return dateTimeFormatter.format(
			_getLocalDateTime(
				StringPool.UTC,
				ObjectFieldSettingUtil.getTimeZoneId(
					objectField.getObjectFieldSettings(), user),
				value));
	}

	@Override
	public String getLabel(Locale locale) {
		return _language.get(locale, "date");
	}

	@Override
	public String getName() {
		return ObjectFieldConstants.BUSINESS_TYPE_DATE;
	}

	@Override
	public PropertyDefinition.PropertyType getPropertyType() {
		return PropertyDefinition.PropertyType.DATE_TIME;
	}

	private boolean _containsTimeZoneId(String pattern) {
		if (pattern.contains("X") || pattern.contains("Z") ||
			pattern.contains("z")) {

			return true;
		}

		return false;
	}

	private LocalDateTime _getLocalDateTime(
		String sourceTimeZoneId, String targetTimeZoneId, String value) {

		String pattern = StringUtil.replace(
			ObjectFieldUtil.getDateTimePattern(value), "'Z'", "X");

		DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(
			pattern);

		LocalDateTime localDateTime = LocalDateTime.parse(
			value, dateTimeFormatter);

		if (Validator.isNull(sourceTimeZoneId) ||
			Validator.isNull(targetTimeZoneId)) {

			return localDateTime;
		}

		ZonedDateTime zonedDateTime = null;

		if (_containsTimeZoneId(pattern)) {
			zonedDateTime = ZonedDateTime.parse(value, dateTimeFormatter);
		}
		else {
			zonedDateTime = ZonedDateTime.of(
				localDateTime, ZoneId.of(sourceTimeZoneId));
		}

		return LocalDateTime.ofInstant(
			zonedDateTime.toInstant(), ZoneId.of(targetTimeZoneId));
	}

	@Reference
	private Language _language;

	@Reference
	private UserLocalService _userLocalService;

}