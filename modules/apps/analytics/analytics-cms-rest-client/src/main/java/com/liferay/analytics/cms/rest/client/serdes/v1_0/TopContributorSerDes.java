/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.analytics.cms.rest.client.serdes.v1_0;

import com.liferay.analytics.cms.rest.client.dto.v1_0.TopContributor;
import com.liferay.analytics.cms.rest.client.json.BaseJSONParser;

import jakarta.annotation.Generated;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * @author Rachael Koestartyo
 * @generated
 */
@Generated("")
public class TopContributorSerDes {

	public static TopContributor toDTO(String json) {
		TopContributorJSONParser topContributorJSONParser =
			new TopContributorJSONParser();

		return topContributorJSONParser.parseToDTO(json);
	}

	public static TopContributor[] toDTOs(String json) {
		TopContributorJSONParser topContributorJSONParser =
			new TopContributorJSONParser();

		return topContributorJSONParser.parseToDTOs(json);
	}

	public static String toJSON(TopContributor topContributor) {
		if (topContributor == null) {
			return "null";
		}

		StringBuilder sb = new StringBuilder();

		sb.append("{");

		if (topContributor.getAssetCount() != null) {
			if (sb.length() > 1) {
				sb.append(", ");
			}

			sb.append("\"assetCount\": ");

			sb.append(topContributor.getAssetCount());
		}

		if (topContributor.getUserId() != null) {
			if (sb.length() > 1) {
				sb.append(", ");
			}

			sb.append("\"userId\": ");

			sb.append(topContributor.getUserId());
		}

		if (topContributor.getUserName() != null) {
			if (sb.length() > 1) {
				sb.append(", ");
			}

			sb.append("\"userName\": ");

			sb.append("\"");

			sb.append(_escape(topContributor.getUserName()));

			sb.append("\"");
		}

		sb.append("}");

		return sb.toString();
	}

	public static Map<String, Object> toMap(String json) {
		TopContributorJSONParser topContributorJSONParser =
			new TopContributorJSONParser();

		return topContributorJSONParser.parseToMap(json);
	}

	public static Map<String, String> toMap(TopContributor topContributor) {
		if (topContributor == null) {
			return null;
		}

		Map<String, String> map = new TreeMap<>();

		if (topContributor.getAssetCount() == null) {
			map.put("assetCount", null);
		}
		else {
			map.put(
				"assetCount", String.valueOf(topContributor.getAssetCount()));
		}

		if (topContributor.getUserId() == null) {
			map.put("userId", null);
		}
		else {
			map.put("userId", String.valueOf(topContributor.getUserId()));
		}

		if (topContributor.getUserName() == null) {
			map.put("userName", null);
		}
		else {
			map.put("userName", String.valueOf(topContributor.getUserName()));
		}

		return map;
	}

	public static class TopContributorJSONParser
		extends BaseJSONParser<TopContributor> {

		@Override
		protected TopContributor createDTO() {
			return new TopContributor();
		}

		@Override
		protected TopContributor[] createDTOArray(int size) {
			return new TopContributor[size];
		}

		@Override
		protected boolean parseMaps(String jsonParserFieldName) {
			if (Objects.equals(jsonParserFieldName, "assetCount")) {
				return false;
			}
			else if (Objects.equals(jsonParserFieldName, "userId")) {
				return false;
			}
			else if (Objects.equals(jsonParserFieldName, "userName")) {
				return false;
			}

			return false;
		}

		@Override
		protected void setField(
			TopContributor topContributor, String jsonParserFieldName,
			Object jsonParserFieldValue) {

			if (Objects.equals(jsonParserFieldName, "assetCount")) {
				if (jsonParserFieldValue != null) {
					topContributor.setAssetCount(
						Long.valueOf((String)jsonParserFieldValue));
				}
			}
			else if (Objects.equals(jsonParserFieldName, "userId")) {
				if (jsonParserFieldValue != null) {
					topContributor.setUserId(
						Long.valueOf((String)jsonParserFieldValue));
				}
			}
			else if (Objects.equals(jsonParserFieldName, "userName")) {
				if (jsonParserFieldValue != null) {
					topContributor.setUserName((String)jsonParserFieldValue);
				}
			}
		}

	}

	private static String _escape(Object object) {
		String string = String.valueOf(object);

		for (String[] strings : BaseJSONParser.JSON_ESCAPE_STRINGS) {
			string = string.replace(strings[0], strings[1]);
		}

		return string;
	}

	private static String _toJSON(Map<String, ?> map) {
		StringBuilder sb = new StringBuilder("{");

		@SuppressWarnings("unchecked")
		Set set = map.entrySet();

		@SuppressWarnings("unchecked")
		Iterator<Map.Entry<String, ?>> iterator = set.iterator();

		while (iterator.hasNext()) {
			Map.Entry<String, ?> entry = iterator.next();

			sb.append("\"");
			sb.append(entry.getKey());
			sb.append("\": ");

			Object value = entry.getValue();

			sb.append(_toJSON(value));

			if (iterator.hasNext()) {
				sb.append(", ");
			}
		}

		sb.append("}");

		return sb.toString();
	}

	private static String _toJSON(Object value) {
		if (value == null) {
			return "null";
		}

		if (value instanceof Collection) {
			Collection<?> collection = (Collection<?>)value;

			return _toJSON(collection.toArray());
		}

		if (value instanceof Map) {
			return _toJSON((Map)value);
		}

		Class<?> clazz = value.getClass();

		if (clazz.isArray()) {
			StringBuilder sb = new StringBuilder("[");

			Object[] values = (Object[])value;

			for (int i = 0; i < values.length; i++) {
				sb.append(_toJSON(values[i]));

				if ((i + 1) < values.length) {
					sb.append(", ");
				}
			}

			sb.append("]");

			return sb.toString();
		}

		if (value instanceof String) {
			return "\"" + _escape(value) + "\"";
		}

		return String.valueOf(value);
	}

}
// LIFERAY-REST-BUILDER-HASH:-1058001963