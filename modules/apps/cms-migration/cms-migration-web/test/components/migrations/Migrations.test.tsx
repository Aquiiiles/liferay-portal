/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

// eslint-disable-next-line @liferay/portal/no-cross-module-deep-import
import {checkAccessibility} from '@liferay/layout-js-components-web/test/__lib__/index';
import {render, screen, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

import '@testing-library/jest-dom';

import Migrations from '../../../src/main/resources/META-INF/resources/js/components/migrations/Migrations';
import {
	COMPLETED_MIGRATION,
	IN_PROGRESS_MIGRATION,
	QUEUED_MIGRATION,
} from '../../mocks/migrations';

describe('Migrations', () => {
	const languageGetImplementation = (
		Liferay.Language.get as jest.Mock
	).getMockImplementation();

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		(Liferay.Language.get as jest.Mock).mockImplementation(
			languageGetImplementation
		);

		window.sessionStorage.clear();
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

	it('shows the counts of a completed migration without errors or warnings', () => {
		render(
			<Migrations
				migrationIncomplete={false}
				migrations={[COMPLETED_MIGRATION]}
			/>
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

	it('has no accessibility violations', async () => {
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

		const [, errors, warnings] = screen.getAllByRole('region');

		expect(
			screen.getByRole('button', {name: 'cms-migration-x-errors'})
		).toHaveAttribute('aria-expanded', 'false');
		expect(within(errors).getByText('Error 1')).toBeInTheDocument();
		expect(
			screen.getByRole('button', {name: 'cms-migration-x-warnings'})
		).toHaveAttribute('aria-expanded', 'false');
		expect(within(warnings).getAllByRole('listitem')).toHaveLength(2);
	});

	it('shows the progress of a migration in progress', () => {
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
	});

	it('does not rerender a completed migration when the migrations are updated', () => {
		const {rerender} = render(
			<Migrations
				migrationIncomplete={true}
				migrations={[COMPLETED_MIGRATION, QUEUED_MIGRATION]}
			/>
		);

		rerender(
			<Migrations
				migrationIncomplete={true}
				migrations={[
					{...COMPLETED_MIGRATION, name: 'Migration 1 Updated'},
					{...QUEUED_MIGRATION, name: 'Migration 3 Updated'},
				]}
			/>
		);

		expect(
			screen.getByRole('heading', {name: 'Migration 1'})
		).toBeInTheDocument();
		expect(
			screen.getByRole('heading', {name: 'Migration 3 Updated'})
		).toBeInTheDocument();
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

	it('groups the completed migrations in a collapsed panel below the migrations not completed', () => {
		render(
			<Migrations
				migrationIncomplete={true}
				migrations={[
					QUEUED_MIGRATION,
					IN_PROGRESS_MIGRATION,
					COMPLETED_MIGRATION,
					{...COMPLETED_MIGRATION, backgroundTaskId: 4},
				]}
			/>
		);

		const [queued, inProgress, ...completed] = screen.getAllByRole(
			'heading',
			{level: 3}
		);

		expect(queued).toHaveTextContent('Migration 3');
		expect(inProgress).toHaveTextContent('Migration 2');
		expect(completed).toHaveLength(2);

		expect(screen.getByRole('button', {name: 'x-y'})).toHaveAttribute(
			'aria-expanded',
			'false'
		);
		expect(
			within(screen.getByRole('region')).getAllByRole('heading', {
				name: 'Migration 1',
			})
		).toHaveLength(2);
	});

	it.each([
		['en-US', 'completed (1,000)'],
		['pt-BR', 'completed (1.000)'],
	])(
		'shows the number of completed migrations in the panel title with the %s grouping separator',
		(languageId, name) => {
			(Liferay.Language.get as jest.Mock).mockImplementation(
				(key: string) => (key === 'x-y' ? '{0} ({1})' : key)
			);
			(
				Liferay.ThemeDisplay.getBCP47LanguageId as jest.Mock
			).mockReturnValueOnce(languageId);

			render(
				<Migrations
					migrationIncomplete={true}
					migrations={Array.from({length: 1000}, (_, index) => ({
						...COMPLETED_MIGRATION,
						backgroundTaskId: index,
					}))}
				/>
			);

			expect(screen.getByRole('button', {name})).toBeInTheDocument();
		}
	);

	it('expands the completed migrations panel when every migration is completed', () => {
		render(
			<Migrations
				migrationIncomplete={false}
				migrations={[COMPLETED_MIGRATION]}
			/>
		);

		expect(screen.getByRole('button', {name: 'x-y'})).toHaveAttribute(
			'aria-expanded',
			'true'
		);
	});

	it('keeps the completed migrations panel expanded by the user after the page reloads', async () => {
		const user = userEvent.setup();

		const migrations = [IN_PROGRESS_MIGRATION, COMPLETED_MIGRATION];

		const {unmount} = render(
			<Migrations migrationIncomplete={true} migrations={migrations} />
		);

		await user.click(screen.getByRole('button', {name: 'x-y'}));

		unmount();

		render(
			<Migrations migrationIncomplete={true} migrations={migrations} />
		);

		expect(screen.getByRole('button', {name: 'x-y'})).toHaveAttribute(
			'aria-expanded',
			'true'
		);
	});

	it('keeps the completed migrations panel collapsed by the user after the page reloads', async () => {
		const user = userEvent.setup();

		const migrations = [COMPLETED_MIGRATION];

		const {unmount} = render(
			<Migrations migrationIncomplete={false} migrations={migrations} />
		);

		await user.click(screen.getByRole('button', {name: 'x-y'}));

		unmount();

		render(
			<Migrations migrationIncomplete={false} migrations={migrations} />
		);

		expect(screen.getByRole('button', {name: 'x-y'})).toHaveAttribute(
			'aria-expanded',
			'false'
		);
	});

	it('keeps the errors and warnings panels as the user left them after the page reloads', async () => {
		const user = userEvent.setup();

		const migrations = [
			{
				...COMPLETED_MIGRATION,
				errors: ['Error 1'],
				warnings: ['Warning 1'],
			},
			{
				...COMPLETED_MIGRATION,
				backgroundTaskId: 4,
				errors: ['Error 2'],
				name: 'Migration 4',
			},
		];

		const {unmount} = render(
			<Migrations migrationIncomplete={false} migrations={migrations} />
		);

		await user.click(
			screen.getAllByRole('button', {name: 'cms-migration-x-errors'})[0]
		);

		unmount();

		render(
			<Migrations migrationIncomplete={false} migrations={migrations} />
		);

		const errorsButtons = screen.getAllByRole('button', {
			name: 'cms-migration-x-errors',
		});

		expect(errorsButtons[0]).toHaveAttribute('aria-expanded', 'true');
		expect(errorsButtons[1]).toHaveAttribute('aria-expanded', 'false');
		expect(
			screen.getByRole('button', {name: 'cms-migration-x-warnings'})
		).toHaveAttribute('aria-expanded', 'false');
	});

	it('does not show the completed migrations panel without completed migrations', () => {
		render(
			<Migrations
				migrationIncomplete={true}
				migrations={[QUEUED_MIGRATION]}
			/>
		);

		expect(
			screen.queryByRole('button', {name: 'x-y'})
		).not.toBeInTheDocument();
	});
});
