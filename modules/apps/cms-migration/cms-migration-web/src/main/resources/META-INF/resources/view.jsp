<%--
/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */
--%>

<%@ include file="/init.jsp" %>

<%
CMSMigrationDisplayContext cmsMigrationDisplayContext = (CMSMigrationDisplayContext)request.getAttribute(WebKeys.PORTLET_DISPLAY_CONTEXT);
%>

<clay:container-fluid
	cssClass="container-form-lg"
>
	<liferay-ui:success key="migrationStarted" message="cms-migration-started" />

	<liferay-ui:error exception="<%= DepotEntryNameException.class %>" message="please-enter-a-valid-name" />
	<liferay-ui:error exception="<%= Exception.class %>" message="an-unexpected-error-occurred" />

	<div>
		<react:component
			module="{CMSMigration} from cms-migration-web"
			props="<%= cmsMigrationDisplayContext.getProps() %>"
		/>
	</div>
</clay:container-fluid>