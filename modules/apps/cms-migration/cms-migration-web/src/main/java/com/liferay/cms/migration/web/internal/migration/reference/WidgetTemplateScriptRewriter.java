/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.migration.reference;

import com.liferay.object.model.ObjectEntry;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.util.StringUtil;

import java.lang.reflect.Method;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Albert Gomes Cabral
 */
public class WidgetTemplateScriptRewriter {

	public WidgetTemplateScriptRewriter(JSONFactory jsonFactory)
		throws Exception {

		for (String fileName :
				new String[] {"blogs_entry.json", "journal_article.json"}) {

			JSONObject jsonObject = jsonFactory.createJSONObject(
				StringUtil.read(
					WidgetTemplateScriptRewriter.class,
					"dependencies/" + fileName));

			_replacementsJSONArrays.put(
				jsonObject.getString("className"),
				jsonObject.getJSONArray("replacements"));
		}

		for (Method method : ObjectEntry.class.getMethods()) {
			_objectEntryMethodNames.add(method.getName());
		}
	}

	public String rewrite(
		String className, String script, Set<String> unsupportedMethodNames) {

		JSONArray replacementsJSONArray = _replacementsJSONArrays.get(
			className);

		if (replacementsJSONArray == null) {
			return script;
		}

		for (int i = 0; i < replacementsJSONArray.length(); i++) {
			JSONObject replacementJSONObject =
				replacementsJSONArray.getJSONObject(i);

			String from = replacementJSONObject.getString("from");

			if (!from.contains(_PLACEHOLDER)) {
				script = StringUtil.replace(
					script, from, replacementJSONObject.getString("to"));
			}
		}

		Set<String> variableNames = new LinkedHashSet<>();

		Matcher matcher = _variablePattern.matcher(script);

		while (matcher.find()) {
			variableNames.add(matcher.group(1));
		}

		Set<String> expressions = new LinkedHashSet<>(variableNames);

		matcher = _assetObjectPattern.matcher(script);

		while (matcher.find()) {
			expressions.add(matcher.group());
		}

		for (String expression : expressions) {
			for (int i = 0; i < replacementsJSONArray.length(); i++) {
				JSONObject replacementJSONObject =
					replacementsJSONArray.getJSONObject(i);

				String from = replacementJSONObject.getString("from");

				if (!from.contains(_PLACEHOLDER)) {
					continue;
				}

				from = StringUtil.replace(from, _PLACEHOLDER, expression);

				String to = Matcher.quoteReplacement(
					StringUtil.replace(
						replacementJSONObject.getString("to"), _PLACEHOLDER,
						expression));

				script = script.replaceAll(_BOUNDARY + Pattern.quote(from), to);

				String propertyName = _getPropertyName(expression, from);

				if (propertyName != null) {
					script = script.replaceAll(
						_BOUNDARY + Pattern.quote(propertyName) + "(?![\\w(])",
						to);
				}
			}
		}

		for (String expression : expressions) {
			Pattern pattern = Pattern.compile(
				_BOUNDARY + Pattern.quote(expression) + "\\.(\\w+)\\(");

			matcher = pattern.matcher(script);

			while (matcher.find()) {
				if (!_objectEntryMethodNames.contains(matcher.group(1))) {
					unsupportedMethodNames.add(matcher.group(1));
				}
			}
		}

		return script;
	}

	private String _getPropertyName(String expression, String from) {
		String prefix = expression + ".get";

		if (!from.startsWith(prefix) ||
			!from.endsWith(
				StringPool.OPEN_PARENTHESIS + StringPool.CLOSE_PARENTHESIS)) {

			return null;
		}

		String name = from.substring(prefix.length(), from.length() - 2);

		return StringBundler.concat(
			expression, StringPool.PERIOD,
			StringUtil.toLowerCase(name.substring(0, 1)), name.substring(1));
	}

	private static final String _BOUNDARY = "(?<![\\w.])";

	private static final String _PLACEHOLDER = "[$ENTRY$]";

	private static final Pattern _assetObjectPattern = Pattern.compile(
		"[A-Za-z_]\\w*(?:\\.[A-Za-z_]\\w*(?:\\(\\))?)*\\.getAssetObject\\(\\)");
	private static final Pattern _variablePattern = Pattern.compile(
		"(\\w+)\\s*=\\s*[A-Za-z_]\\w*(?:\\.[A-Za-z_]\\w*(?:\\(\\))?)*" +
			"\\.getAssetObject\\(\\)");

	private final Set<String> _objectEntryMethodNames = new LinkedHashSet<>();
	private final Map<String, JSONArray> _replacementsJSONArrays =
		new HashMap<>();

}