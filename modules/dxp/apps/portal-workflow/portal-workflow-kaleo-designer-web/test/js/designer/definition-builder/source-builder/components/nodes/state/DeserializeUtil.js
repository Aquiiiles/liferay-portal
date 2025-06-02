/**
 * SPDX-FileCopyrightText: (c) 2025 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import DeserializeUtil from '../../../../../../../../src/main/resources/META-INF/resources/designer/js/definition-builder/source-builder/deserializeUtil';
const XMLDefinition = require('../../../../../../../../src/main/resources/META-INF/resources/designer/js/definition-builder/source-builder/xmlDefinition');

jest.mock(
	'../../../../../../../../src/main/resources/META-INF/resources/designer/js/definition-builder/source-builder/xmlDefinition',
	() => {
		return jest.fn().mockImplementation(({value}) => ({
			forEachField: jest.fn(),
			getDefinitionMetadata: jest.fn(() => ({foo: 'bar'})),
			value,
		}));
	}
);

jest.mock(
	'../../../../../../../../src/main/resources/META-INF/resources/designer/js/definition-builder/source-builder/utils',
	() => ({
		parseActions: jest.fn(() => 'parsedActions'),
		parseAssignments: jest.fn(() => 'parsedAssignments'),
		parseNotifications: jest.fn(() => 'parsedNotifications'),
		parseTimers: jest.fn(() => 'parsedTimers'),
	})
);

jest.mock(
	'../../../../../../../../src/main/resources/META-INF/resources/designer/js/definition-builder/util/utils',
	() => ({
		removeNewLine: jest.fn((v) => v),
		replaceTabSpaces: jest.fn((v) => v),
	})
);

jest.mock(
	'../../../../../../../../src/main/resources/META-INF/resources/designer/js/definition-builder/source-builder/constants',
	() => ({
		DEFAULT_LANGUAGE: 'groovy',
	})
);

describe('DeserializeUtil', () => {
	beforeEach(() => {
		jest.clearAllMocks();
	});

	it('could initialize with XMLDefinition', () => {
		new DeserializeUtil('<xml />');
		expect(XMLDefinition).toHaveBeenCalledWith({value: '<xml />'});
	});

	it('getMetadata should call getDefinitionMetadata', () => {
		const util = new DeserializeUtil('<xml />');
		const meta = util.getMetadata();
		expect(meta).toEqual({foo: 'bar'});
	});

	it('updateXMLDefinition should update the definition', () => {
		const util = new DeserializeUtil('<xml />');
		util.updateXMLDefinition('<new />');
		expect(XMLDefinition).toHaveBeenLastCalledWith({value: '<new />'});
	});

	it('getElements should process nodes and transitions', () => {
		const util = new DeserializeUtil('<xml />');
		const forEachField = jest.fn();
		util.definition.forEachField = forEachField;

		forEachField.mockImplementationOnce((callback) => {
			callback('task', {results: [{name: 'node1'}, {id: 'node2'}]});
		});
		forEachField.mockImplementationOnce((callback) => {
			callback('task', {
				results: [
					{
						actions: [{}, {template: 'foo'}],
						assignments: true,
						description: 'desc',
						labels: [{en_US: 'Node 1'}],
						metadata: JSON.stringify({xy: [10, 20]}),
						name: 'node1',
						script: 'script',
						scriptLanguage: 'groovy',
						taskTimers: true,
						transitions: [
							{
								labels: [{en_US: 'T1'}],
								name: 't1',
								target: 'node2',
							},
						],
					},
				],
			});
		});

		const elements = util.getElements();
		expect(elements.length).toBe(2);
		const [node, transition] = elements;
		expect(node).toMatchObject({
			data: expect.objectContaining({
				actions: 'parsedActions',
				assignments: 'parsedAssignments',
				description: 'desc',
				label: {en_US: 'Node 1'},
				notifications: 'parsedNotifications',
				script: 'script',
				scriptLanguage: 'groovy',
				taskTimers: 'parsedTimers',
			}),
			id: 'node1',
			position: {x: 10, y: 20},
			type: 'task',
		});
		expect(transition).toMatchObject({
			arrowHeadType: 'arrowclosed',
			data: expect.objectContaining({
				defaultEdge: true,
				label: {en_US: 'T1'},
			}),
			id: 't1',
			source: 'node1',
			target: 'node2',
			type: 'transition',
		});
	});
});
