/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

export const CONTENT_TYPES = [
	{
		label: Liferay.Language.get('web-content'),
		name: 'migrateJournalArticles',
	},
	{label: Liferay.Language.get('blogs'), name: 'migrateBlogsEntries'},
	{
		label: Liferay.Language.get('documents-and-media'),
		name: 'migrateFileEntries',
	},
	{
		defaultChecked: false,
		help: Liferay.Language.get('cms-migration-object-entries-help'),
		label: Liferay.Language.get('objects'),
		name: 'migrateObjectEntries',
	},
	{
		help: Liferay.Language.get('cms-migration-rewrite-pages-help'),
		label: Liferay.Language.get('cms-migration-rewrite-pages'),
		name: 'rewritePages',
	},
	{
		defaultChecked: false,
		help: Liferay.Language.get('cms-migration-repeatable-fields-help'),
		label: Liferay.Language.get('cms-migration-repeatable-fields'),
		name: 'migrateRepeatableFields',
	},
	{
		defaultChecked: false,
		help: Liferay.Language.get('cms-migration-skip-unchanged-entries-help'),
		label: Liferay.Language.get('cms-migration-skip-unchanged-entries'),
		name: 'skipUnchangedEntries',
	},
];

export const CREATE_NEW_SPACE = '0';
