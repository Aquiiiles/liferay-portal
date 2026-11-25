/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {IMigration} from '../../src/main/resources/META-INF/resources/js/types/Migration';

export const COMPLETED_MIGRATION: IMigration = {
	backgroundTaskId: 1,
	completed: true,
	counts: [
		{label: 'Documents', value: 4},
		{label: 'Folders', value: 11},
	],
	createDate: '11/11/26, 10:58',
	errors: [],
	inProgress: false,
	name: 'Migration 1',
	progress: 0,
	statusDisplayType: 'success',
	statusLabel: 'Successful',
	warnings: [],
};

export const IN_PROGRESS_MIGRATION: IMigration = {
	backgroundTaskId: 2,
	completed: false,
	counts: [],
	createDate: '10/11/26, 18:49',
	errors: [],
	inProgress: true,
	itemTitle: 'Welcome',
	name: 'Migration 2',
	phaseLabel: 'Web Content',
	progress: 40,
	statusDisplayType: 'info',
	statusLabel: 'In Progress',
	warnings: [],
};

export const QUEUED_MIGRATION: IMigration = {
	backgroundTaskId: 3,
	completed: false,
	counts: [],
	createDate: '10/11/26, 18:49',
	errors: [],
	inProgress: false,
	name: 'Migration 3',
	progress: 0,
	statusDisplayType: 'info',
	statusLabel: 'Queued',
	warnings: [],
};
