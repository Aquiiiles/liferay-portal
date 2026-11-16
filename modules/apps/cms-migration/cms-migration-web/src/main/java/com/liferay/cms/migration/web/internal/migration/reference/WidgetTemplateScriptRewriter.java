/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.migration.reference;

import com.liferay.object.model.ObjectEntry;
import com.liferay.petra.string.CharPool;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.json.JSONUtil;
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

			_jsonObjects.put(jsonObject.getString("className"), jsonObject);
		}

		for (Method method : ObjectEntry.class.getMethods()) {
			_objectEntryMethodNames.add(method.getName());
		}
	}

	public Result rewrite(String className, String script) {
		Result result = new Result();

		JSONObject jsonObject = _jsonObjects.get(className);

		if (jsonObject == null) {
			result._script = script;

			return result;
		}

		JSONArray replacementsJSONArray = jsonObject.getJSONArray(
			"replacements");

		for (int i = 0; i < replacementsJSONArray.length(); i++) {
			JSONObject replacementJSONObject =
				replacementsJSONArray.getJSONObject(i);

			String from = replacementJSONObject.getString("from");

			if (from.contains(_PLACEHOLDER)) {
				continue;
			}

			String to = replacementJSONObject.getString("to");

			if (from.contains(_PLACEHOLDER_ASSET_ENTRY)) {
				script = _replaceAssetEntryExpressions(from, script, to);
			}
			else {
				script = StringUtil.replace(script, from, to);
			}
		}

		script = _renameVariables(script);

		Set<String> expressions = _getExpressions(script);

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

		Set<String> changedMethodNames = JSONUtil.toStringSet(
			jsonObject.getJSONArray("changedMethodNames"));

		for (String expression : expressions) {
			Pattern pattern = Pattern.compile(
				_BOUNDARY + Pattern.quote(expression) + "\\.(\\w+)\\(");

			Matcher matcher = pattern.matcher(script);

			while (matcher.find()) {
				String methodName = matcher.group(1);

				if (changedMethodNames.contains(methodName)) {
					result._changedMethodNames.add(methodName);
				}
				else if (!_objectEntryMethodNames.contains(methodName)) {
					result._unsupportedMethodNames.add(methodName);
				}
			}
		}

		for (String unsupportedReference :
				JSONUtil.toStringList(
					jsonObject.getJSONArray("unsupportedReferences"))) {

			if (script.contains(unsupportedReference)) {
				result._unsupportedReferences.add(unsupportedReference);
			}
		}

		result._script = script;

		return result;
	}

	public static class Result {

		public Set<String> getChangedMethodNames() {
			return _changedMethodNames;
		}

		public String getScript() {
			return _script;
		}

		public Set<String> getUnsupportedMethodNames() {
			return _unsupportedMethodNames;
		}

		public Set<String> getUnsupportedReferences() {
			return _unsupportedReferences;
		}

		private final Set<String> _changedMethodNames = new LinkedHashSet<>();
		private String _script;
		private final Set<String> _unsupportedMethodNames =
			new LinkedHashSet<>();
		private final Set<String> _unsupportedReferences =
			new LinkedHashSet<>();

	}

	private int _getExpressionEnd(char closeChar, String script, int start) {
		int depth = 0;

		for (int i = start; i < script.length(); i++) {
			char c = script.charAt(i);

			if ((c == CharPool.APOSTROPHE) || (c == CharPool.QUOTE)) {
				i = _getStringEnd(script, i) - 1;
			}
			else if ((c == closeChar) && (depth == 0)) {
				return i + 1;
			}
			else if ((c == CharPool.OPEN_BRACKET) ||
					 (c == CharPool.OPEN_CURLY_BRACE) ||
					 (c == CharPool.OPEN_PARENTHESIS)) {

				depth++;
			}
			else if ((c == CharPool.CLOSE_BRACKET) ||
					 (c == CharPool.CLOSE_CURLY_BRACE) ||
					 (c == CharPool.CLOSE_PARENTHESIS)) {

				depth--;
			}
		}

		return script.length();
	}

	private Set<String> _getExpressions(String script) {
		Set<String> expressions = _getVariableNames(script);

		Matcher matcher = _assetObjectPattern.matcher(script);

		while (matcher.find()) {
			expressions.add(matcher.group());
		}

		return expressions;
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

	private int _getStringEnd(String script, int start) {
		char quoteChar = script.charAt(start);

		for (int i = start + 1; i < script.length(); i++) {
			char c = script.charAt(i);

			if (c == CharPool.BACK_SLASH) {
				i++;
			}
			else if (c == quoteChar) {
				return i + 1;
			}
		}

		return script.length();
	}

	private Set<String> _getVariableNames(String script) {
		Set<String> variableNames = new LinkedHashSet<>();

		Matcher matcher = _variablePattern.matcher(script);

		while (matcher.find()) {
			variableNames.add(matcher.group(1));
		}

		return variableNames;
	}

	private boolean _hasVariable(String script, String variableName) {
		Pattern pattern = Pattern.compile(
			_VARIABLE_BOUNDARY + Pattern.quote(variableName) + "(?!\\w)");

		Matcher matcher = pattern.matcher(script);

		return matcher.find();
	}

	private String _renameExpressionVariable(
		String expression, String newVariableName, String variableName) {

		StringBundler sb = new StringBundler();

		Pattern pattern = Pattern.compile(
			_VARIABLE_BOUNDARY + Pattern.quote(variableName) + "(?!\\w)");

		int start = 0;

		for (int i = 0; i < expression.length(); i++) {
			char c = expression.charAt(i);

			if ((c != CharPool.APOSTROPHE) && (c != CharPool.QUOTE)) {
				continue;
			}

			Matcher matcher = pattern.matcher(expression.substring(start, i));

			sb.append(matcher.replaceAll(newVariableName));

			int end = _getStringEnd(expression, i);

			sb.append(
				_renameVariable(
					newVariableName, expression.substring(i, end),
					variableName));

			start = end;

			i = end - 1;
		}

		Matcher matcher = pattern.matcher(expression.substring(start));

		sb.append(matcher.replaceAll(newVariableName));

		return sb.toString();
	}

	private String _renameVariable(
		String newVariableName, String script, String variableName) {

		StringBundler sb = new StringBundler();

		int i = 0;

		while (i < script.length()) {
			int end = -1;

			if (script.startsWith("<#--", i)) {
				end = script.indexOf("-->", i);

				if (end == -1) {
					end = script.length();
				}

				sb.append(script.substring(i, end));

				i = end;

				continue;
			}

			if (script.startsWith("${", i) || script.startsWith("#{", i)) {
				end = _getExpressionEnd(
					CharPool.CLOSE_CURLY_BRACE, script, i + 2);
			}
			else if (script.startsWith("<#", i) ||
					 script.startsWith("</#", i) ||
					 script.startsWith("<@", i) ||
					 script.startsWith("</@", i)) {

				end = _getExpressionEnd(CharPool.GREATER_THAN, script, i + 2);
			}

			if (end == -1) {
				sb.append(script.charAt(i));

				i++;

				continue;
			}

			sb.append(script.substring(i, i + 2));
			sb.append(
				_renameExpressionVariable(
					script.substring(i + 2, end), newVariableName,
					variableName));

			i = end;
		}

		return sb.toString();
	}

	private String _renameVariables(String script) {
		int count = 0;

		for (String variableName : _getVariableNames(script)) {
			count++;

			String newVariableName = "objectEntry";

			if (count > 1) {
				newVariableName += count;
			}

			if (variableName.equals(newVariableName) ||
				_hasVariable(script, newVariableName)) {

				continue;
			}

			script = _renameVariable(newVariableName, script, variableName);
		}

		return script;
	}

	private String _replaceAssetEntryExpressions(
		String from, String script, String to) {

		String regex = StringUtil.replace(
			Pattern.quote(from),
			new String[] {_PLACEHOLDER_ASSET_ENTRY, _PLACEHOLDER_SERVICE},
			new String[] {"\\E(\\w+)\\Q", "\\E\\w+\\Q"});

		return script.replaceAll(
			_BOUNDARY + regex,
			StringUtil.replace(
				Matcher.quoteReplacement(to),
				Matcher.quoteReplacement(_PLACEHOLDER_ASSET_ENTRY), "$1"));
	}

	private static final String _BOUNDARY = "(?<![\\w.])";

	private static final String _PLACEHOLDER = "[$ENTRY$]";

	private static final String _PLACEHOLDER_ASSET_ENTRY = "[$ASSET_ENTRY$]";

	private static final String _PLACEHOLDER_SERVICE = "[$SERVICE$]";

	private static final String _VARIABLE_BOUNDARY = "(?<![\\w.?])";

	private static final Pattern _assetObjectPattern = Pattern.compile(
		"[A-Za-z_]\\w*(?:\\.[A-Za-z_]\\w*(?:\\(\\))?)*\\.getAssetObject\\(\\)");
	private static final Pattern _variablePattern = Pattern.compile(
		"(\\w+)\\s*=\\s*[A-Za-z_]\\w*(?:\\.[A-Za-z_]\\w*(?:\\(\\))?)*" +
			"\\.getAssetObject\\(\\)");

	private final Map<String, JSONObject> _jsonObjects = new HashMap<>();
	private final Set<String> _objectEntryMethodNames = new LinkedHashSet<>();

}