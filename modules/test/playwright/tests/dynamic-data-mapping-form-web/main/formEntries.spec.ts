/**
 * SPDX-FileCopyrightText: (c) 2024 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import { ObjectDefinitionAPI } from '@liferay/object-admin-rest-client-js';
import {Page, expect, mergeTests} from '@playwright/test';

import {formsPagesTest} from '../../../fixtures/formsPagesTest';
import {loginTest} from '../../../fixtures/loginTest';
import {FormFieldsPage} from '../../../pages/dynamic-data-mapping-form-web/FormFieldsPage';
import {getRandomInt} from '../../../utils/getRandomInt';
import getRandomString from '../../../utils/getRandomString';
import {waitForAlert} from '../../../utils/waitForAlert';
import {deleteItems} from './utils/deleteItems';
import { apiHelpersTest } from '../../../fixtures/apiHelpersTest';

export const test = mergeTests(loginTest(), formsPagesTest, apiHelpersTest);

let formPreviewPage: Page;

test.afterEach(async ({formsPage}) => {
	if (formPreviewPage) {
		await formPreviewPage.close();

		formPreviewPage = null;
	}

	await formsPage.goTo();

	await deleteItems(formsPage);
});

test.beforeEach(({page}) => {
	page.setViewportSize({height: 1080, width: 1920});
});

test.describe('Accessibility', () => {
	test('aria-describedby is applied for invalid multiple selection', async ({
		formBuilderPage,
		formBuilderSidePanelPage,
		formsPage,
		page,
	}) => {
		await formsPage.goTo();

		await formsPage.clickManagementToolbarNewButton();

		await formBuilderSidePanelPage.addMultipleSelectionButton.dblclick();

		await formBuilderSidePanelPage.requiredFieldToggleSwitch.click();

		await page.waitForTimeout(1000);

		await formBuilderPage.clickPublishFormButton();

		const formSubmissionURL = await formBuilderPage.getFormSubmissionURL();

		await page.goto(formSubmissionURL, {waitUntil: 'networkidle'});

		await page.getByRole('button', {name: 'Submit'}).click();

		const singleSelect = page.getByRole('checkbox');

		await singleSelect.waitFor();

		const fieldFeedbackElement = page.locator('.field-feedback');

		await expect(fieldFeedbackElement).toBeVisible();

		await expect(fieldFeedbackElement).toHaveText(
			'This field is required.'
		);

		const fieldFeedbackId = await fieldFeedbackElement.getAttribute('id');

		await expect(singleSelect).toHaveAttribute(
			'aria-describedby',
			fieldFeedbackId
		);
	});
});

test('can interact with a large list of fields on the form entries page', async ({
	formBuilderPage,
	formBuilderSidePanelPage,
	formsPage,
	page,
}) => {
	test.slow();

	await formBuilderPage.goToNew();

	for (const index of Array.from(Array(30).keys())) {
		await formBuilderSidePanelPage.addFieldByDoubleClick('Text');

		await formBuilderSidePanelPage.label.fill(`Text ${index}`);

		await formBuilderSidePanelPage.clickBackButton();
	}

	await formBuilderPage.clickPublishFormButton();

	const formSubmissionURL = await formBuilderPage.getFormSubmissionURL();

	await page.goto(formSubmissionURL, {waitUntil: 'networkidle'});

	const formEntry = getRandomString();

	await page.getByLabel('Text 29').fill(formEntry);

	await page.getByRole('button', {name: 'Submit'}).click();

	await expect(
		page.getByText(
			'Your information was successfully received. Thank you for filling out the form.'
		)
	).toBeVisible();

	await formsPage.goTo();

	await formsPage.openForm('Untitled Form');

	await formBuilderPage.entriesTab.click();

	await page.locator('a').filter({hasText: 'Text 29'}).click();

	await expect(page.getByText(formEntry)).toBeVisible();
});

test('can interact with Single Selection options using only keys', async ({
	formBuilderFieldSettingsSidePanelPage,
	formBuilderPage,
	formBuilderSidePanelPage,
	formsPage,
	page,
}) => {
	await formsPage.goTo();

	await formsPage.clickManagementToolbarNewButton();

	for (let index = 0; index < 2; index++) {
		await formBuilderSidePanelPage.addSingleSelectionButton.dblclick();

		await formBuilderFieldSettingsSidePanelPage.addOptions(3);

		await formBuilderFieldSettingsSidePanelPage.advancedTabButton.click();

		await formBuilderFieldSettingsSidePanelPage.inlineToggle.click();

		await formBuilderSidePanelPage.backButton.click();
	}

	const formPreviewPagePromise = page.waitForEvent('popup');

	await formBuilderPage.previewButton.click();

	formPreviewPage = await formPreviewPagePromise;

	// We need to ensure option elements are enabled before tabbing through them.

	await expect(formPreviewPage.getByLabel('Option0').first()).toBeEnabled();

	for (let index = 0; index < 2; index++) {

		// On the second loop iteration, pressing Tab should focus the second group.

		await formPreviewPage.keyboard.press('Tab');

		await expect(
			formPreviewPage.getByLabel('Option0').nth(index)
		).toBeChecked();

		await formPreviewPage.keyboard.press('ArrowDown');

		await expect(
			formPreviewPage.getByLabel('Option1').nth(index)
		).toBeChecked();

		await formPreviewPage.keyboard.press('ArrowDown');

		await expect(
			formPreviewPage.getByLabel('Option2').nth(index)
		).toBeChecked();

		await formPreviewPage.keyboard.press('ArrowDown');

		// After pressing Arrow Down while being in the last option,
		// we should have the first option selected again.

		await expect(
			formPreviewPage.getByLabel('Option0').nth(index)
		).toBeChecked();

		// Then, pressing Arrow Up twice should select the middle option.

		await formPreviewPage.keyboard.press('ArrowUp');

		await formPreviewPage.keyboard.press('ArrowUp');

		await expect(
			formPreviewPage.getByLabel('Option1').nth(index)
		).toBeChecked();
	}
});

test('can add image to repeated Rich Text field', async ({
	formBuilderFieldSettingsSidePanelPage,
	formBuilderPage,
	formBuilderSidePanelPage,
	formsPage,
	page,
}) => {
	await formsPage.goTo();

	await formsPage.clickManagementToolbarNewButton();

	await formBuilderSidePanelPage.addFieldByDoubleClick('Rich Text');

	await formBuilderFieldSettingsSidePanelPage.advancedTabButton.click();

	await formBuilderFieldSettingsSidePanelPage.repeatableToggle.click();

	const formPreviewPagePromise = page.waitForEvent('popup');

	await formBuilderPage.previewButton.click();

	formPreviewPage = await formPreviewPagePromise;

	const formFieldsPage = new FormFieldsPage(formPreviewPage);

	const editorContentFrame = formPreviewPage.frameLocator(
		'iframe[title="editor"]'
	);

	await formFieldsPage.repeatFieldButton.click();

	await formFieldsPage.richTextAddImageButton.nth(1).click();

	await formFieldsPage.richTextselectImage('planet.png');

	const textBox = editorContentFrame.nth(1).getByRole('textbox');

	await expect(
		textBox.locator('img[src="/documents/d/guest/planet-png"]')
	).toBeVisible();
});

test(
	'should delete only the entries returned by the search when "Select All Items on the Page" is checked',
	{tag: ['@LPD-58613']},
	async ({formBuilderPage, formBuilderSidePanelPage, formsPage, page}) => {
		const formTitle = 'Form' + getRandomInt();

		await test.step('publish form with a single text field', async () => {
			await formsPage.goTo();

			await formsPage.clickManagementToolbarNewButton();

			await formBuilderSidePanelPage.addTextButton.dblclick();

			await formBuilderPage.formTitle.fill(formTitle);

			await formBuilderPage.clickPublishFormButton();
		});

		await test.step('create two entries: one bad, one good', async () => {
			const formSubmissionURL =
				await formBuilderPage.getFormSubmissionURL();

			await page.goto(formSubmissionURL, {waitUntil: 'networkidle'});

			await page.getByLabel('Text').fill('Bad entry');

			await page.getByRole('button', {name: 'Submit'}).click();

			await waitForAlert(page);

			await page.getByRole('button', {name: 'Submit Again'}).click();

			await page.getByLabel('Text').fill('Good entry');

			await page.getByRole('button', {name: 'Submit'}).click();

			await waitForAlert(page);
		});

		await test.step('assert that only the entries returned by the search are deleted', async () => {
			await formsPage.goTo();

			await page
				.getByRole('row', {name: `Select ${formTitle}`})
				.getByLabel('Show Actions')
				.click();

			await page.getByRole('menuitem', {name: 'View Entries'}).click();

			await page.waitForTimeout(1000);

			await page.getByPlaceholder('Search for').fill('Bad');

			await page.getByLabel('Search for', {exact: true}).click();

			const badEntryLocator = page.getByRole('cell', {
				exact: true,
				name: 'Bad entry',
			});

			const goodEntryLocator = page.getByRole('cell', {
				exact: true,
				name: 'Good entry',
			});

			await expect(badEntryLocator).toBeVisible();

			await expect(goodEntryLocator).not.toBeVisible();

			await page.getByLabel('Select All Items on the Page').click();

			page.once('dialog', (dialog) => dialog.accept());

			await page.getByRole('button', {name: 'Delete'}).click();

			await page.getByLabel('Clear 0 Results for Bad').click();

			await expect(badEntryLocator).not.toBeVisible();

			await expect(goodEntryLocator).toBeVisible();
		});
	}
);

test(
	'can create a Form with attachment field and submit txt as Guest',
	async ({
		apiHelpers,
		formBuilderPage,
		formBuilderSidePanelPage,
		formsPage,
		page,
	}) => {
		const objectDefinitionAPIClient =
			await apiHelpers.buildRestClient(ObjectDefinitionAPI);

		await objectDefinitionAPIClient.postObjectDefinition({
			active: true,
			externalReferenceCode: getRandomString(),
			label: {en_US: 'Upload Object'},
			name: 'UploadObject' + getRandomInt(),
			objectFields: [
				{
					DBType: 'Long',
					businessType: 'Attachment',
					externalReferenceCode: getRandomString(),
					indexed: true,
					label: {en_US: 'Upload Field'},
					localized: false,
					name: 'uploadField',
					required: false,
					system: false,
					objectFieldSettings: [
						{name: 'acceptedFileExtensions', value: 'txt'},
						{name: 'fileSource', value: 'userComputer'},
						{name: 'maximumFileSize', value: 104857600},
					],
				},
			],
			panelCategoryKey: 'control_panel.object',
			pluralLabel: {en_US: 'Upload Objects'},
			portlet: true,
			scope: 'company',
			status: {code: 0},
		});

		await page.getByLabel('Open Applications MenuCtrl+').click();
		await page.getByRole('tab', {name: 'Control Panel'}).click();
		await page.getByRole('menuitem', {name: 'Roles'}).click();
		await page.getByRole('link', {name: 'Guest'}).click();
		await page.getByRole('link', {name: 'Define Permissions'}).click();

		await page.getByPlaceholder('Search').fill('Upload Objects');
		await page.getByRole('menuitem', {name: 'Upload Objects'}).first().click();
		await page.locator('[id*="ircfSearchContainer_col-rowChecker"] label').click();
		await page.locator('[id*="rkonSearchContainer_col-rowChecker"]').getByLabel('').check();
		await page.locator('[id*="ocerSearchContainer_6"]').getByLabel('View').check();
		await page.getByRole('button', {name: 'Save'}).click();

		await page.waitForTimeout(1000);
		await page.getByPlaceholder('Search').fill('Forms');
		await page.getByRole('menuitem', {name: 'Forms'}).last().click();
		await page.locator('[id*="ocerSearchContainer_6"]').getByLabel('View All Sites and Asset').check();
		await page.locator('[id*="ircfSearchContainer_col-rowChecker"]').click();
		await page.locator('[id*="rkonSearchContainer_col-rowChecker"]').getByLabel('').check();
		await page.locator('[id*="gtvtSearchContainer_col-rowChecker"]').click();
		await page.getByRole('button', {name: 'Save'}).click();

		await formsPage.goTo();
		await formsPage.clickManagementToolbarNewButton();

		await page
			.locator('[id*="DDMFormAdminPortlet_managementToolbar"]')
			.getByRole('button')
			.first()
			.click();
		await page.getByLabel('Select a Storage Type').click();
		await page.getByRole('option', {name: 'Object'}).click();
		await page.getByLabel('Select Object').click();
		await page.getByRole('option', {name: 'Upload Object'}).last().click();
		await page.getByRole('button', {name: 'Done'}).click();

		await formBuilderSidePanelPage.addFieldByDoubleClick('Upload');
		await formBuilderPage.formTitle.fill('Upload Form Test');
		await page.getByText('UploadSelect').click();
		await page.getByLabel('Allow Guest Users to Send').check();
		await page.getByRole('tab', {name: 'Advanced'}).click();
		await page.getByLabel('Object Field').click();
		await page.getByRole('option', {name: 'Upload Field'}).click();
		await formBuilderPage.clickPublishFormButton();

		await page.getByLabel('Open Product Menu').click();
		await page.getByRole('button', {name: 'Page Tree'}).click();
		await page.getByLabel('Add Page').click();
		await page.getByRole('menuitem', {name: 'Add Page'}).click();
		await page.getByRole('button', {name: 'Blank'}).click();

		const addPageFrame = page.frameLocator('iframe[title="Add Page"]');
		await addPageFrame.getByPlaceholder('Add Page Name').fill('guest');
		await addPageFrame.getByRole('button', {name: 'Add'}).click();

		
		const searchInput = page.getByLabel('Search Fragments and Widgets');
		await searchInput.click();
		await searchInput.fill('form');

		const formItem = page.locator('[draggable="true"]', {hasText: /^Form$/}).first();
		await formItem.waitFor({state: 'visible'});
		await formItem.scrollIntoViewIfNeeded();

		await formItem.dragTo(page.locator('.page-editor__drop-zone').first());

		await expect(page.locator('.portlet-forms')).toBeVisible({timeout: 10000});

		const optionsBtn = page.locator('#wrapper').getByRole('button', {name: 'Options'});
		await optionsBtn.click();
		await page.getByRole('menuitem', {name: 'Configuration', exact: true}).click();

		const configFrame = page.frameLocator('iframe[title="Configuration"]');
		await configFrame.getByRole('cell', {name: 'forms'}).click();
		await configFrame.getByRole('link', {name: 'forms'}).click();
		await configFrame.getByRole('button', {name: 'Save'}).click();

		await page.getByLabel('close', {exact: true}).click();
		await page.getByLabel('Publish').click();

		await page.getByLabel('Test Test User Profile').click();
		await page.getByRole('menuitem', {name: 'Sign Out'}).click();
		await page.getByRole('menuitem', {name: 'guest'}).click();

		await page.goto('/guest', {waitUntil: 'networkidle'});
		await formsPage.selectFileFromUserComputer(__dirname, 'sampleFile.txt');

	}
);

