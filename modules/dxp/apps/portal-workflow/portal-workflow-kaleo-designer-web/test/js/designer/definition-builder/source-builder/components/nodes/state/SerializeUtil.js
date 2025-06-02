/**
 * SPDX-FileCopyrightText: (c) 2025 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

const {serializeDefinition} = require(
	'../../../../../../../../src/main/resources/META-INF/resources/designer/js/definition-builder/source-builder/serializeUtil'
);

jest.mock(
	'../../../../../../../../src/main/resources/META-INF/resources/designer/js/definition-builder/source-builder/utils/xmlHelpers',
	() => ({
		createTagWithEscapedContent: jest.fn((tag, content) => `<${tag}>${content}</${tag}>`),
	})
);

jest.mock(
	'../../../../../../../../src/main/resources/META-INF/resources/designer/js/definition-builder/source-builder/xmlUtil',
	() => ({
		create: jest.fn((tag, content) => `<${tag}>${content}</${tag}>`),
		createObj: jest.fn((tag) => ({
			close: `</${tag}>`,
			open: `<${tag}>`,
		})),
		format: jest.fn((buffer) => buffer.join('')),
	})
);


global.Liferay = {
	Util: {
		escape: (val) => val,
	},
};

describe('serializeDefinition', () => {
	it('could use item.data.name (not item.id) as the node name if present', () => {
		const metadata = {
			description: 'Test workflow',
			name: 'MyWorkflow',
			version: '1',
		};

		const nodes = [
			{
				data: {
					actions: {},
					assignments: {
						assignmentType: ['user'],
						userId: ['1'],
					},
					description: 'desc',
				id: 'fromId',
					label: {en_US: 'Label'},
					name: 'fromDataName',
					taskTimers: [],
				},
				position: {x: 0, y: 0},
				type: 'task',
			},
		];

		const transitions = [];

		const xml = serializeDefinition({}, metadata, nodes, transitions);

		expect(xml).toContain('<name>fromDataName</name>');
		expect(xml).not.toContain('<name>fromId</name>');
	});
});