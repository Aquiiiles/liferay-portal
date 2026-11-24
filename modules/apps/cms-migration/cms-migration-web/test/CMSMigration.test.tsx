/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {render, screen} from '@testing-library/react';
import React from 'react';

import '@testing-library/jest-dom';

import CMSMigration from '../src/main/resources/META-INF/resources/js/CMSMigration';
import {IMigration} from '../src/main/resources/META-INF/resources/js/Migrations';
import {COMPLETED_MIGRATION, QUEUED_MIGRATION} from './mocks/migrations';

function renderCMSMigration(migrations: IMigration[]) {
	return render(
		<CMSMigration
			groups={[{label: 'Site 1', value: '1'}]}
			migrations={migrations}
			migrationsURL="http://localhost/get"
			portletNamespace="_portletNamespace_"
			spaces={[]}
			startCMSMigrationURL="http://localhost/start"
			title="CMS Migration"
		/>
	);
}

describe('CMSMigration', () => {
	it('shows the configuration and the migrations', () => {
		renderCMSMigration([]);

		expect(
			screen.getByRole('heading', {name: 'CMS Migration'})
		).toBeInTheDocument();
		expect(
			screen.getByRole('heading', {name: 'cms-migration-migrations'})
		).toBeInTheDocument();
	});

	it('disables the start button while a migration is not completed', () => {
		renderCMSMigration([QUEUED_MIGRATION, COMPLETED_MIGRATION]);

		expect(
			screen.getByRole('button', {name: 'cms-migration-start'})
		).toBeDisabled();
	});

	it('enables the start button when every migration is completed', () => {
		renderCMSMigration([COMPLETED_MIGRATION]);

		expect(
			screen.getByRole('button', {name: 'cms-migration-start'})
		).toBeEnabled();
	});
});
