/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

export function downloadCSV({
	errors,
	name,
	warnings,
}: {
	errors: string[];
	name: string;
	warnings: string[];
}) {
	const csv = getCSV([
		[Liferay.Language.get('type'), Liferay.Language.get('message')],
		...errors.map((error) => [Liferay.Language.get('error'), error]),
		...warnings.map((warning) => [
			Liferay.Language.get('warning'),
			warning,
		]),
	]);

	const blobURL = URL.createObjectURL(
		new Blob([csv], {type: 'text/csv;charset=utf-8'})
	);

	const link = document.createElement('a');

	link.download = getCSVFileName(name);
	link.href = blobURL;

	link.click();

	URL.revokeObjectURL(blobURL);
}

function escapeValue(value: string) {
	if (/^[=+\-@]/.test(value)) {
		value = `'${value}`;
	}

	if (/[",\r\n]/.test(value)) {
		return `"${value.replace(/"/g, '""')}"`;
	}

	return value;
}

function getCSV(rows: string[][]) {
	return (
		'\uFEFF' +
		rows.map((row) => row.map(escapeValue).join(',')).join('\r\n') +
		'\r\n'
	);
}

function getCSVFileName(name: string) {
	const slug = name
		.normalize('NFC')
		.toLowerCase()
		.replace(/[^\p{L}\p{M}\p{N}]+/gu, '-')
		.replace(/^-|-$/g, '');

	const date = new Date();

	return `cms-migration-${slug ? `${slug}-` : ''}${date.getFullYear()}-${padNumber(
		date.getMonth() + 1
	)}-${padNumber(date.getDate())}-${padNumber(date.getHours())}${padNumber(
		date.getMinutes()
	)}.csv`;
}

function padNumber(number: number) {
	return String(number).padStart(2, '0');
}
