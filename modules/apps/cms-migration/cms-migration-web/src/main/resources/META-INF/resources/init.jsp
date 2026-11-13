<%--
/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */
--%>

<%@ taglib uri="jakarta.tags.core" prefix="c" %>

<%@ taglib uri="http://java.sun.com/portlet_2_0" prefix="portlet" %>

<%@ taglib uri="http://liferay.com/tld/clay" prefix="clay" %><%@
taglib uri="http://liferay.com/tld/react" prefix="react" %><%@
taglib uri="http://liferay.com/tld/theme" prefix="liferay-theme" %><%@
taglib uri="http://liferay.com/tld/ui" prefix="liferay-ui" %>

<%@ page import="com.liferay.cms.migration.web.internal.display.context.CMSContentDisplayDisplayContext" %><%@
page import="com.liferay.cms.migration.web.internal.display.context.CMSMigrationDisplayContext" %><%@
page import="com.liferay.depot.exception.DepotEntryNameException" %><%@
page import="com.liferay.object.model.ObjectEntry" %><%@
page import="com.liferay.portal.kernel.util.WebKeys" %><%@
page import="com.liferay.taglib.servlet.PipingServletResponseFactory" %>

<liferay-theme:defineObjects />

<portlet:defineObjects />