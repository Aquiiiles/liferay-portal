/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import '@testing-library/jest-dom';

import {downloadCSV} from '../../src/main/resources/META-INF/resources/js/utils/downloadCSV';

describe('downloadCSV', () => {
	let clickSpy: jest.SpyInstance;
	let createObjectURL: jest.Mock<string, [Blob]>;

	beforeEach(() => {
		jest.clearAllMocks();

		jest.useFakeTimers();
		jest.setSystemTime(new Date(2026, 0, 5, 9, 7));

		createObjectURL = jest.fn((_blob: Blob) => 'blob:csv');

		Object.defineProperty(URL, 'createObjectURL', {
			configurable: true,
			value: createObjectURL,
		});
		Object.defineProperty(URL, 'revokeObjectURL', {
			configurable: true,
			value: jest.fn(),
		});

		clickSpy = jest
			.spyOn(HTMLAnchorElement.prototype, 'click')
			.mockImplementation(() => {});
	});

	afterEach(() => {
		jest.restoreAllMocks();
		jest.useRealTimers();

		Reflect.deleteProperty(URL, 'createObjectURL');
		Reflect.deleteProperty(URL, 'revokeObjectURL');
	});

	function readCSV() {
		const fileReader = new FileReader();

		const csvPromise = new Promise((resolve) => {
			fileReader.onload = () =>
				resolve(
					new TextDecoder('utf-8', {ignoreBOM: true}).decode(
						fileReader.result as ArrayBuffer
					)
				);
		});

		fileReader.readAsArrayBuffer(createObjectURL.mock.calls[0][0]);

		jest.runAllTimers();

		return csvPromise;
	}

	it('downloads the CSV through a temporary link to the file', () => {
		downloadCSV({errors: ['Error 1'], name: 'Migration 1', warnings: []});

		expect(clickSpy).toHaveBeenCalledTimes(1);
		expect(clickSpy.mock.instances[0]).toHaveAttribute('href', 'blob:csv');
		expect(createObjectURL.mock.calls[0][0].type).toBe(
			'text/csv;charset=utf-8'
		);
		expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:csv');
	});

	it('writes a BOM, a header and one row per error and warning, with CRLF line ends', async () => {
		downloadCSV({
			errors: ['Error 1', 'Error 2'],
			name: 'Migration 1',
			warnings: ['Warning 1'],
		});

		expect(await readCSV()).toBe(
			'\uFEFFtype,message\r\n' +
				'error,Error 1\r\n' +
				'error,Error 2\r\n' +
				'warning,Warning 1\r\n'
		);
	});

	it('quotes the values with quotes, commas or line breaks', async () => {
		downloadCSV({
			errors: ['Error "1"', 'Error 2, with a comma'],
			name: 'Migration 1',
			warnings: ['Warning\n1', 'Warning\r2'],
		});

		expect(await readCSV()).toBe(
			'\uFEFFtype,message\r\n' +
				'error,"Error ""1"""\r\n' +
				'error,"Error 2, with a comma"\r\n' +
				'warning,"Warning\n1"\r\n' +
				'warning,"Warning\r2"\r\n'
		);
	});

	it.each(['=', '+', '-', '@'])(
		'prefixes a value starting with %s with an apostrophe',
		async (prefix) => {
			downloadCSV({
				errors: [`${prefix}Error 1`],
				name: 'Migration 1',
				warnings: [],
			});

			expect(await readCSV()).toBe(
				`\uFEFFtype,message\r\nerror,'${prefix}Error 1\r\n`
			);
		}
	);

	it.each([
		[
			'Migração São Paulo!',
			'cms-migration-migração-são-paulo-2026-01-05-0907.csv',
		],
		[
			'Migrac\u0327a\u0303o',
			'cms-migration-migra\u00e7\u00e3o-2026-01-05-0907.csv',
		],
		['Straße & Søn', 'cms-migration-straße-søn-2026-01-05-0907.csv'],
		['[LPCB] Hub', 'cms-migration-lpcb-hub-2026-01-05-0907.csv'],
		['Сайт компании', 'cms-migration-сайт-компании-2026-01-05-0907.csv'],
		['日本語サイト', 'cms-migration-日本語サイト-2026-01-05-0907.csv'],
		['हिन्दी साइट', 'cms-migration-हिन्दी-साइट-2026-01-05-0907.csv'],
		['!!!', 'cms-migration-2026-01-05-0907.csv'],
	])('names the file of the migration %s as %s', (name, fileName) => {
		downloadCSV({errors: ['Error 1'], name, warnings: []});

		expect(clickSpy.mock.instances[0]).toHaveAttribute(
			'download',
			fileName
		);
	});
});
