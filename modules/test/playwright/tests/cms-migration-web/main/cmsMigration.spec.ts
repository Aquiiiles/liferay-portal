/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {expect, mergeTests} from '@playwright/test';

import {loginTest} from '../../../fixtures/loginTest';
import {PORTLET_URLS} from '../../../utils/portletUrls';
import {waitForAlert} from '../../../utils/waitForAlert';

const test = mergeTests(loginTest());

test(
	'The error alert stays visible after starting a migration without a new space name',
	{tag: '@ECU-21110'},
	async ({page}) => {
		await page.goto(PORTLET_URLS.cmsMigration);

		await page
			.getByLabel('Target Space')
			.selectOption({label: 'Create New Space'});

		const startMigrationButton = page.getByRole('button', {
			name: 'Start Migration',
		});

		await startMigrationButton.click();

		// The submitted form disables its button, so an enabled button means
		// the React component was mounted again on the response page

		await expect(startMigrationButton).toBeEnabled();

		await waitForAlert(page, 'Please enter a valid name.', {
			autoClose: false,
			type: 'danger',
		});
	}
);
