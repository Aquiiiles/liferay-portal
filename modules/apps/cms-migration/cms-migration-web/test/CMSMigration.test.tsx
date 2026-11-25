/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {act, render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

import '@testing-library/jest-dom';

import CMSMigration from '../src/main/resources/META-INF/resources/js/CMSMigration';
import {IMigration} from '../src/main/resources/META-INF/resources/js/types/Migration';
import {
	COMPLETED_MIGRATION,
	IN_PROGRESS_MIGRATION,
	QUEUED_MIGRATION,
} from './mocks/migrations';

const MIGRATIONS_URL = 'http://localhost/get';

async function advanceTimers(milliseconds: number) {
	await act(async () => {
		jest.advanceTimersByTime(milliseconds);
	});
}

function mockGetCMSMigrations(migrations: IMigration[]) {
	(global.fetch as jest.Mock).mockImplementationOnce(() =>
		Promise.resolve({json: () => Promise.resolve({migrations})})
	);
}

function renderCMSMigration(migrations: IMigration[]) {
	return render(
		<CMSMigration
			groups={[{label: 'Site 1', value: '1'}]}
			migrations={migrations}
			migrationsURL={MIGRATIONS_URL}
			portletNamespace="_portletNamespace_"
			spaces={[]}
			startCMSMigrationURL="http://localhost/start"
			title="CMS Migration"
		/>
	);
}

function setVisibilityState(visibilityState: DocumentVisibilityState) {
	jest.spyOn(document, 'visibilityState', 'get').mockReturnValue(
		visibilityState
	);
}

describe('CMSMigration', () => {
	beforeEach(() => {
		jest.clearAllMocks();
	});

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

	describe('polling', () => {
		beforeEach(() => {
			jest.useFakeTimers();
		});

		afterEach(() => {
			jest.restoreAllMocks();
			jest.useRealTimers();
		});

		it('updates the migrations every 5 seconds until every migration is completed', async () => {
			renderCMSMigration([QUEUED_MIGRATION]);

			mockGetCMSMigrations([
				{...IN_PROGRESS_MIGRATION, backgroundTaskId: 3},
			]);

			await advanceTimers(4999);

			expect(global.fetch).not.toHaveBeenCalled();

			await advanceTimers(1);

			expect(global.fetch).toHaveBeenCalledTimes(1);
			expect(global.fetch).toHaveBeenCalledWith(
				MIGRATIONS_URL,
				expect.anything()
			);
			expect(screen.getByText('In Progress')).toBeInTheDocument();
			expect(
				screen.getByRole('button', {name: 'cms-migration-start'})
			).toBeDisabled();

			mockGetCMSMigrations([
				{...COMPLETED_MIGRATION, backgroundTaskId: 3},
			]);

			await advanceTimers(5000);

			expect(global.fetch).toHaveBeenCalledTimes(2);
			expect(screen.getByText('Successful')).toBeInTheDocument();
			expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
			expect(
				screen.getByRole('button', {name: 'cms-migration-start'})
			).toBeEnabled();

			await advanceTimers(15000);

			expect(global.fetch).toHaveBeenCalledTimes(2);
		});

		it('does not poll when every migration is completed', async () => {
			renderCMSMigration([COMPLETED_MIGRATION]);

			await advanceTimers(15000);

			expect(global.fetch).not.toHaveBeenCalled();
		});

		it('does not poll while the page is hidden', async () => {
			setVisibilityState('hidden');

			renderCMSMigration([QUEUED_MIGRATION]);

			await advanceTimers(15000);

			expect(global.fetch).not.toHaveBeenCalled();

			mockGetCMSMigrations([QUEUED_MIGRATION]);

			setVisibilityState('visible');

			act(() => {
				document.dispatchEvent(new Event('visibilitychange'));
			});

			await advanceTimers(5000);

			expect(global.fetch).toHaveBeenCalledTimes(1);
		});

		it('keeps an expanded errors list open after an update', async () => {
			const migrations = [
				{...COMPLETED_MIGRATION, errors: ['Error 1']},
				QUEUED_MIGRATION,
			];

			renderCMSMigration(migrations);

			const user = userEvent.setup({
				advanceTimers: jest.advanceTimersByTime,
			});

			await user.click(
				screen.getByRole('button', {name: 'cms-migration-x-errors'})
			);

			expect(
				screen.getByRole('button', {name: 'cms-migration-x-errors'})
			).toHaveAttribute('aria-expanded', 'true');

			mockGetCMSMigrations(migrations);

			await advanceTimers(5000);

			expect(global.fetch).toHaveBeenCalledTimes(1);
			expect(
				screen.getByRole('button', {name: 'cms-migration-x-errors'})
			).toHaveAttribute('aria-expanded', 'true');
		});
	});
});
