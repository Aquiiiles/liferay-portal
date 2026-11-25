/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import ClayButton from '@clayui/button';
import ClayIcon from '@clayui/icon';
import ClayLabel from '@clayui/label';
import ClayLayout from '@clayui/layout';
import ClayPanel from '@clayui/panel';
import ClayProgressBar from '@clayui/progress-bar';
import {useSessionState} from 'frontend-js-components-web';
import {sub} from 'frontend-js-web';
import React, {useMemo} from 'react';

import {IMigration} from '../../types/Migration';
import {downloadCSV} from '../../utils/downloadCSV';

interface IMigrationProps {
	migration: IMigration;
	numberFormat: Intl.NumberFormat;
}

interface IProps {
	migrationIncomplete: boolean;
	migrations: IMigration[];
}

const Migration = React.memo(
	MigrationRow,
	({migration}) => migration.completed
);

export default function Migrations({migrationIncomplete, migrations}: IProps) {
	const completedMigrations = migrations.filter(
		(migration) => migration.completed
	);
	const [completedMigrationsExpanded, setCompletedMigrationsExpanded] =
		useSessionState<boolean>('cms-migration-completed-migrations-expanded');
	const incompleteMigrations = migrations.filter(
		(migration) => !migration.completed
	);

	const numberFormat = useMemo(() => {
		return new Intl.NumberFormat(Liferay.ThemeDisplay.getBCP47LanguageId());
	}, []);

	return (
		<ClayLayout.Sheet>
			<ClayLayout.SheetHeader>
				<h2 className="sheet-title">
					{Liferay.Language.get('cms-migration-migrations')}
				</h2>
			</ClayLayout.SheetHeader>

			<ClayLayout.SheetSection>
				{!migrations.length && (
					<div className="text-secondary">
						{Liferay.Language.get('cms-migration-no-migrations')}
					</div>
				)}

				{incompleteMigrations.map((migration) => (
					<Migration
						key={migration.backgroundTaskId}
						migration={migration}
						numberFormat={numberFormat}
					/>
				))}

				{!!completedMigrations.length && (
					<ClayPanel
						collapsable
						displayTitle={sub(
							Liferay.Language.get('x-y'),
							Liferay.Language.get('completed'),
							numberFormat.format(completedMigrations.length)
						)}
						displayType="secondary"
						expanded={
							completedMigrationsExpanded ?? !migrationIncomplete
						}
						onExpandedChange={setCompletedMigrationsExpanded}
						showCollapseIcon
						style={
							{
								'--panel-header-collapse-icon-right':
									'var(--spacer-3)',
							} as React.CSSProperties
						}
					>
						<ClayPanel.Body className="text-body">
							{completedMigrations.map((migration) => (
								<Migration
									key={migration.backgroundTaskId}
									migration={migration}
									numberFormat={numberFormat}
								/>
							))}
						</ClayPanel.Body>
					</ClayPanel>
				)}
			</ClayLayout.SheetSection>
		</ClayLayout.Sheet>
	);
}

function MigrationRow({migration, numberFormat}: IMigrationProps) {
	const {
		backgroundTaskId,
		completed,
		counts,
		createDate,
		errors,
		inProgress,
		itemTitle,
		name,
		phaseLabel,
		progress,
		statusDisplayType,
		statusLabel,
		warnings,
	} = migration;

	return (
		<div className="border-bottom mb-3 pb-3">
			<ClayLayout.ContentRow noGutters="x" padded verticalAlign="center">
				<ClayLayout.ContentCol expand>
					<h3 className="h4">{name}</h3>

					<div className="text-secondary">{createDate}</div>
				</ClayLayout.ContentCol>

				<ClayLayout.ContentCol>
					<ClayLabel displayType={statusDisplayType}>
						{statusLabel}
					</ClayLabel>
				</ClayLayout.ContentCol>

				{completed && (!!errors.length || !!warnings.length) && (
					<ClayLayout.ContentCol>
						<ClayButton
							displayType="secondary"
							onClick={() =>
								downloadCSV({errors, name, warnings})
							}
							size="sm"
						>
							<span className="inline-item inline-item-before">
								<ClayIcon symbol="document-default" />
							</span>

							{Liferay.Language.get('open-logs')}

							<span className="sr-only">{` ${name}`}</span>
						</ClayButton>
					</ClayLayout.ContentCol>
				)}
			</ClayLayout.ContentRow>

			{inProgress && (
				<>
					<div className="mt-2">
						{phaseLabel}

						{itemTitle && (
							<span className="text-secondary"> {itemTitle}</span>
						)}
					</div>

					<ClayProgressBar value={progress} />
				</>
			)}

			{completed && (
				<>
					<ul
						className="c-gapx-5 d-md-grid list-unstyled mt-2"
						style={{gridTemplateColumns: 'repeat(2, max-content)'}}
					>
						{counts.map(({label, value}) => (
							<li className="text-nowrap" key={label}>
								<strong>{numberFormat.format(value)}</strong>

								{` ${label}`}
							</li>
						))}
					</ul>

					<Messages
						className="text-danger"
						label={sub(
							Liferay.Language.get('cms-migration-x-errors'),
							numberFormat.format(errors.length)
						)}
						messages={errors}
						sessionKey={`cms-migration-errors-expanded-${backgroundTaskId}`}
					/>

					<Messages
						className="text-warning"
						label={sub(
							Liferay.Language.get('cms-migration-x-warnings'),
							numberFormat.format(warnings.length)
						)}
						messages={warnings}
						sessionKey={`cms-migration-warnings-expanded-${backgroundTaskId}`}
					/>
				</>
			)}
		</div>
	);
}

function Messages({
	className,
	label,
	messages,
	sessionKey,
}: {
	className: string;
	label: string;
	messages: string[];
	sessionKey: string;
}) {
	const [expanded, setExpanded] = useSessionState<boolean>(sessionKey);

	if (!messages.length) {
		return null;
	}

	return (
		<ClayPanel
			className="mt-2"
			collapsable
			displayTitle={
				<ClayPanel.Title className={className}>{label}</ClayPanel.Title>
			}
			displayType="unstyled"
			expanded={!!expanded}
			onExpandedChange={setExpanded}
			showCollapseIcon
			style={
				{
					'--panel-header-collapse-icon-right': '0',
				} as React.CSSProperties
			}
		>
			<ClayPanel.Body>
				<ul>
					{messages.map((message, index) => (
						<li key={index}>{message}</li>
					))}
				</ul>
			</ClayPanel.Body>
		</ClayPanel>
	);
}
