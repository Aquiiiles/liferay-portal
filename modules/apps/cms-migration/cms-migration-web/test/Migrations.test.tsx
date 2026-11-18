/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

// eslint-disable-next-line @liferay/portal/no-cross-module-deep-import
import {checkAccessibility} from '@liferay/layout-js-components-web/test/__lib__/index';
import {act, render, screen, within} from '@testing-library/react';
import React from 'react';

import '@testing-library/jest-dom';

import Migrations from '../src/main/resources/META-INF/resources/js/Migrations';
import {
	COMPLETED_MIGRATION,
	IN_PROGRESS_MIGRATION,
	QUEUED_MIGRATION,
} from './mocks/migrations';

describe('Migrations', () => {
	const location = window.location;
	const reload = jest.fn();

	beforeEach(() => {
		jest.clearAllMocks();
		jest.useFakeTimers();

		Object.defineProperty(window, 'location', {
			configurable: true,
			value: {...location, reload},
		});
	});

	afterEach(() => {
		jest.useRealTimers();

		Object.defineProperty(window, 'location', {
			configurable: true,
			value: location,
		});
	});

	it('explains that no migrations have been run yet', () => {
		render(<Migrations migrationIncomplete={false} migrations={[]} />);

		expect(
			screen.getByRole('heading', {name: 'cms-migration-migrations'})
		).toBeInTheDocument();
		expect(
			screen.getByText('cms-migration-no-migrations')
		).toBeInTheDocument();
	});

	it('shows the counts and the delete link of a completed migration without errors or warnings', () => {
		render(
			<Migrations
				migrationIncomplete={false}
				migrations={[COMPLETED_MIGRATION]}
			/>
		);

		expect(screen.getByRole('link', {name: 'delete'})).toHaveAttribute(
			'href',
			'http://localhost/delete'
		);

		const [documents, folders] = screen.getAllByRole('listitem');

		expect(documents).toHaveTextContent('4 Documents');
		expect(folders).toHaveTextContent('11 Folders');

		expect(
			screen.queryByRole('button', {name: 'cms-migration-x-errors'})
		).not.toBeInTheDocument();
		expect(
			screen.queryByRole('button', {name: 'cms-migration-x-warnings'})
		).not.toBeInTheDocument();
	});

	it('does not reload the page when no migration is incomplete', () => {
		render(
			<Migrations
				migrationIncomplete={false}
				migrations={[COMPLETED_MIGRATION]}
			/>
		);

		act(() => {
			jest.runAllTimers();
		});

		expect(reload).not.toHaveBeenCalled();
	});

	it('has no accessibility violations', async () => {
		jest.useRealTimers();

		const {container} = render(
			<Migrations
				migrationIncomplete={true}
				migrations={[
					{
						...COMPLETED_MIGRATION,
						errors: ['Error 1'],
						warnings: ['Warning 1'],
					},
					IN_PROGRESS_MIGRATION,
				]}
			/>
		);

		await checkAccessibility({bestPractices: true, context: container});
	});

	it('lists the errors and the warnings of a completed migration', () => {
		render(
			<Migrations
				migrationIncomplete={false}
				migrations={[
					{
						...COMPLETED_MIGRATION,
						counts: [],
						errors: ['Error 1'],
						warnings: ['Warning 1', 'Warning 2'],
					},
				]}
			/>
		);

		const [errors, warnings] = screen.getAllByRole('region');

		expect(
			screen.getByRole('button', {name: 'cms-migration-x-errors'})
		).toHaveAttribute('aria-expanded', 'false');
		expect(within(errors).getByText('Error 1')).toBeInTheDocument();
		expect(
			screen.getByRole('button', {name: 'cms-migration-x-warnings'})
		).toHaveAttribute('aria-expanded', 'false');
		expect(within(warnings).getAllByRole('listitem')).toHaveLength(2);
	});

	it('shows the progress of a migration in progress and reloads the page', () => {
		render(
			<Migrations
				migrationIncomplete={true}
				migrations={[IN_PROGRESS_MIGRATION]}
			/>
		);

		expect(screen.getByText('In Progress')).toBeInTheDocument();
		expect(screen.getByText('Web Content')).toBeInTheDocument();
		expect(screen.getByText('Welcome')).toBeInTheDocument();
		expect(screen.getByRole('progressbar')).toHaveAttribute(
			'aria-valuenow',
			'40'
		);
		expect(
			screen.queryByRole('link', {name: 'delete'})
		).not.toBeInTheDocument();

		expect(reload).not.toHaveBeenCalled();

		act(() => {
			jest.advanceTimersByTime(5000);
		});

		expect(reload).toHaveBeenCalledTimes(1);
	});

	it('shows only the name, the date, and the status of a queued migration', () => {
		render(
			<Migrations
				migrationIncomplete={true}
				migrations={[QUEUED_MIGRATION]}
			/>
		);

		expect(
			screen.getByRole('heading', {name: 'Migration 3'})
		).toBeInTheDocument();
		expect(screen.getByText('10/11/26, 18:49')).toBeInTheDocument();
		expect(screen.getByText('Queued')).toBeInTheDocument();
		expect(
			screen.queryByText('cms-migration-no-migrations')
		).not.toBeInTheDocument();
		expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
		expect(screen.queryByRole('list')).not.toBeInTheDocument();
		expect(
			screen.queryByRole('link', {name: 'delete'})
		).not.toBeInTheDocument();
	});

	it('renders the status label with its display type', () => {
		render(
			<Migrations
				migrationIncomplete={true}
				migrations={[
					{...QUEUED_MIGRATION, statusDisplayType: 'warning'},
				]}
			/>
		);

		expect(screen.getByText('Queued').closest('.label')).toHaveClass(
			'label-warning'
		);
	});

	it.each([
		['en-US', '27,985 Widgets'],
		['pt-BR', '27.985 Widgets'],
	])(
		'formats the counts with the %s grouping separator',
		(languageId, text) => {
			(
				Liferay.ThemeDisplay.getBCP47LanguageId as jest.Mock
			).mockReturnValueOnce(languageId);

			render(
				<Migrations
					migrationIncomplete={false}
					migrations={[
						{
							...COMPLETED_MIGRATION,
							counts: [{label: 'Widgets', value: 27985}],
						},
					]}
				/>
			);

			expect(screen.getByRole('listitem')).toHaveTextContent(text);
		}
	);
});
