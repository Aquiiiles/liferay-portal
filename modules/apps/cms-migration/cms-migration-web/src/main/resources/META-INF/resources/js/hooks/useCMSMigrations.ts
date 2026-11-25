/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {useBrowserTabVisibility} from '@liferay/frontend-js-react-web';
import {fetch} from 'frontend-js-web';
import {useEffect, useState} from 'react';

import {IMigration} from '../types/Migration';

const POLLING_INTERVAL = 5000;

export default function useCMSMigrations(
	initialMigrations: IMigration[],
	migrationsURL: string
) {
	const browserTabVisible = useBrowserTabVisibility();
	const [migrations, setMigrations] = useState(initialMigrations);

	const migrationIncomplete = migrations.some(({completed}) => !completed);

	useEffect(() => {
		if (!browserTabVisible || !migrationIncomplete) {
			return;
		}

		const abortController = new AbortController();

		let timeout: ReturnType<typeof setTimeout>;

		const poll = () => {
			timeout = setTimeout(() => {
				fetch(migrationsURL, {signal: abortController.signal})
					.then((response: Response) => response.json())
					.then(({migrations}: {migrations: IMigration[]}) => {
						if (abortController.signal.aborted) {
							return;
						}

						setMigrations(migrations);

						poll();
					})
					.catch(() => {
						if (!abortController.signal.aborted) {
							poll();
						}
					});
			}, POLLING_INTERVAL);
		};

		poll();

		return () => {
			abortController.abort();

			clearTimeout(timeout);
		};
	}, [browserTabVisible, migrationIncomplete, migrationsURL]);

	return {migrationIncomplete, migrations};
}
