/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import ClayLabel, {LabelDisplayType} from '@clayui/label';
import ClayLayout from '@clayui/layout';
import ClayPanel from '@clayui/panel';
import ClayProgressBar from '@clayui/progress-bar';
import {sub} from 'frontend-js-web';
import React, {useEffect, useMemo} from 'react';

export interface IMigration {
	backgroundTaskId: number;
	completed: boolean;
	counts: {label: string; value: number}[];
	createDate: string;
	errors: string[];
	inProgress: boolean;
	itemTitle?: string;
	name: string;
	phaseLabel?: string;
	progress: number;
	statusDisplayType: LabelDisplayType;
	statusLabel: string;
	warnings: string[];
}

interface IProps {
	migrationIncomplete: boolean;
	migrations: IMigration[];
}

export default function Migrations({migrationIncomplete, migrations}: IProps) {
	useEffect(() => {
		if (!migrationIncomplete) {
			return;
		}

		const timeout = setTimeout(() => {
			window.location.reload();
		}, 5000);

		return () => clearTimeout(timeout);
	}, [migrationIncomplete]);

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

				{migrations.map((migration) => (
					<Migration
						key={migration.backgroundTaskId}
						migration={migration}
					/>
				))}
			</ClayLayout.SheetSection>
		</ClayLayout.Sheet>
	);
}

function Migration({migration}: {migration: IMigration}) {
	const {
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

	const numberFormat = useMemo(() => {
		return new Intl.NumberFormat(Liferay.ThemeDisplay.getBCP47LanguageId());
	}, []);

	return (
		<div className="border-bottom mb-3 pb-3">
			<ClayLayout.ContentRow padded verticalAlign="center">
				<ClayLayout.ContentCol expand>
					<h3 className="h4">{name}</h3>

					<div className="text-secondary">{createDate}</div>
				</ClayLayout.ContentCol>

				<ClayLayout.ContentCol>
					<ClayLabel displayType={statusDisplayType}>
						{statusLabel}
					</ClayLabel>
				</ClayLayout.ContentCol>
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
					<ul className="list-inline mt-2">
						{counts.map(({label, value}) => (
							<li
								className="list-inline-item text-nowrap"
								key={label}
							>
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
					/>

					<Messages
						className="text-warning"
						label={sub(
							Liferay.Language.get('cms-migration-x-warnings'),
							numberFormat.format(warnings.length)
						)}
						messages={warnings}
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
}: {
	className: string;
	label: string;
	messages: string[];
}) {
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
