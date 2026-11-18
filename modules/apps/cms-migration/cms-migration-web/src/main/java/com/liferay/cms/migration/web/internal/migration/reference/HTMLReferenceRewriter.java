/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.migration.reference;

import com.liferay.blogs.model.BlogsEntry;
import com.liferay.blogs.service.BlogsEntryLocalService;
import com.liferay.cms.migration.web.internal.constants.CMSMigrationConstants;
import com.liferay.cms.migration.web.internal.migration.CMSMigrationContext;
import com.liferay.document.library.helper.DLURLHelper;
import com.liferay.document.library.kernel.model.DLFileEntry;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.friendly.url.model.FriendlyURLEntry;
import com.liferay.friendly.url.service.FriendlyURLEntryLocalService;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.Validator;

import java.io.Serializable;

import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites the links and file URLs that HTML content stores as plain text, so
 * migrated content and page editables stop pointing at classic content.
 *
 * @author Lino Alves
 */
public class HTMLReferenceRewriter {

	public HTMLReferenceRewriter(
		BlogsEntryLocalService blogsEntryLocalService,
		DLAppLocalService dlAppLocalService, DLURLHelper dlURLHelper,
		FriendlyURLEntryLocalService friendlyURLEntryLocalService,
		GroupLocalService groupLocalService,
		JournalArticleLocalService journalArticleLocalService,
		ObjectDefinitionLocalService objectDefinitionLocalService,
		Portal portal) {

		_blogsEntryLocalService = blogsEntryLocalService;
		_dlAppLocalService = dlAppLocalService;
		_dlURLHelper = dlURLHelper;
		_friendlyURLEntryLocalService = friendlyURLEntryLocalService;
		_groupLocalService = groupLocalService;
		_journalArticleLocalService = journalArticleLocalService;
		_objectDefinitionLocalService = objectDefinitionLocalService;
		_portal = portal;
	}

	public String getFileEntryURL(FileEntry fileEntry) {
		try {
			return _dlURLHelper.getPreviewURL(
				fileEntry, fileEntry.getFileVersion(), null, StringPool.BLANK,
				false, false);
		}
		catch (Exception exception) {
			if (_log.isDebugEnabled()) {
				_log.debug(exception);
			}

			return StringPool.BLANK;
		}
	}

	/**
	 * Returns the site relative path of the display page of a migrated object
	 * entry. Space entries are resolved through the space's friendly URL,
	 * which is the form that object entry friendly URL providers generate.
	 */
	public String getObjectEntryPath(
		CMSMigrationContext cmsMigrationContext, ObjectEntry objectEntry) {

		ObjectDefinition objectDefinition =
			_objectDefinitionLocalService.fetchObjectDefinition(
				objectEntry.getObjectDefinitionId());

		FriendlyURLEntry friendlyURLEntry =
			_friendlyURLEntryLocalService.fetchMainFriendlyURLEntry(
				_portal.getClassNameId(objectDefinition.getClassName()),
				objectEntry.getObjectEntryId());

		String urlTitle = String.valueOf(objectEntry.getObjectEntryId());

		if (friendlyURLEntry != null) {
			urlTitle = friendlyURLEntry.getUrlTitle();
		}

		Group depotGroup = cmsMigrationContext.getDepotGroup();

		return StringBundler.concat(
			StringPool.SLASH, objectDefinition.getFriendlyURLSeparator(),
			depotGroup.getFriendlyURL(), StringPool.SLASH, urlTitle);
	}

	public String rewriteContentLinks(
		CMSMigrationContext cmsMigrationContext, String html) {

		if (Validator.isNull(html)) {
			return html;
		}

		html = _replace(
			_journalArticlePattern, html,
			matcher -> {
				for (long groupId : _getLinkGroupIds(cmsMigrationContext)) {
					JournalArticle journalArticle =
						_journalArticleLocalService.fetchArticleByUrlTitle(
							groupId, matcher.group(1));

					if (journalArticle != null) {
						return _getObjectEntryPath(
							cmsMigrationContext, JournalArticle.class.getName(),
							journalArticle.getResourcePrimKey());
					}
				}

				return null;
			});

		return _replace(
			_blogsEntryPattern, html,
			matcher -> {
				for (long groupId : _getLinkGroupIds(cmsMigrationContext)) {
					BlogsEntry blogsEntry = _blogsEntryLocalService.fetchEntry(
						groupId, matcher.group(1));

					if (blogsEntry != null) {
						return _getObjectEntryPath(
							cmsMigrationContext, BlogsEntry.class.getName(),
							blogsEntry.getEntryId());
					}
				}

				return null;
			});
	}

	public String rewriteFileReferences(
		CMSMigrationContext cmsMigrationContext, String html) {

		if (Validator.isNull(html)) {
			return html;
		}

		return _replace(
			_fileReferencePattern, html,
			matcher -> {
				if (matcher.group("uuid") != null) {
					return _getFileEntryURL(
						cmsMigrationContext,
						_fetchFileEntryByUuidAndGroupId(
							matcher.group("uuid"),
							GetterUtil.getLong(matcher.group("groupId"))));
				}
				else if (matcher.group("fileEntryFriendlyURL") != null) {
					return _getFileEntryURL(
						cmsMigrationContext,
						_fetchFileEntryByFriendlyURL(
							cmsMigrationContext,
							matcher.group("groupFriendlyURL"),
							matcher.group("fileEntryFriendlyURL")));
				}
				else if (matcher.group("adaptiveMediaFileEntryId") != null) {
					return _getFileEntryURL(
						cmsMigrationContext,
						_fetchFileEntry(
							GetterUtil.getLong(
								matcher.group("adaptiveMediaFileEntryId"))));
				}

				FileEntry fileEntry = _getAttachmentFileEntry(
					cmsMigrationContext,
					_fetchFileEntry(
						GetterUtil.getLong(matcher.group("fileEntryId"))));

				if (fileEntry == null) {
					return null;
				}

				return "data-fileentryid=\"" + fileEntry.getFileEntryId() +
					"\"";
			});
	}

	private FileEntry _fetchFileEntry(long fileEntryId) {
		try {
			return _dlAppLocalService.getFileEntry(fileEntryId);
		}
		catch (Exception exception) {
			if (_log.isDebugEnabled()) {
				_log.debug(exception);
			}

			return null;
		}
	}

	private FileEntry _fetchFileEntryByFriendlyURL(
		CMSMigrationContext cmsMigrationContext, String groupFriendlyURL,
		String fileEntryFriendlyURL) {

		Group group = _groupLocalService.fetchFriendlyURLGroup(
			cmsMigrationContext.getCompanyId(),
			StringPool.SLASH + groupFriendlyURL);

		if (group == null) {
			return null;
		}

		FriendlyURLEntry friendlyURLEntry =
			_friendlyURLEntryLocalService.fetchFriendlyURLEntry(
				group.getGroupId(),
				_portal.getClassNameId(FileEntry.class.getName()),
				fileEntryFriendlyURL);

		if (friendlyURLEntry == null) {
			friendlyURLEntry =
				_friendlyURLEntryLocalService.fetchFriendlyURLEntry(
					group.getGroupId(),
					_portal.getClassNameId(DLFileEntry.class.getName()),
					fileEntryFriendlyURL);
		}

		if (friendlyURLEntry == null) {
			return null;
		}

		return _fetchFileEntry(friendlyURLEntry.getClassPK());
	}

	private FileEntry _fetchFileEntryByUuidAndGroupId(
		String uuid, long groupId) {

		try {
			return _dlAppLocalService.getFileEntryByUuidAndGroupId(
				uuid, groupId);
		}
		catch (Exception exception) {
			if (_log.isDebugEnabled()) {
				_log.debug(exception);
			}

			return null;
		}
	}

	private FileEntry _getAttachmentFileEntry(
		CMSMigrationContext cmsMigrationContext, FileEntry fileEntry) {

		if ((fileEntry == null) ||
			(fileEntry.getGroupId() == cmsMigrationContext.getDepotGroupId())) {

			return null;
		}

		try {
			ObjectEntry objectEntry = cmsMigrationContext.getOrMigrateFileEntry(
				fileEntry);

			if (objectEntry == null) {
				return null;
			}

			Map<String, Serializable> values = objectEntry.getValues();

			long fileEntryId = GetterUtil.getLong(
				values.get(CMSMigrationConstants.OBJECT_FIELD_NAME_FILE));

			if (fileEntryId == 0) {
				return null;
			}

			return _dlAppLocalService.getFileEntry(fileEntryId);
		}
		catch (Exception exception) {
			cmsMigrationContext.getCMSMigrationReport(
			).addWarning(
				StringBundler.concat(
					"Unable to migrate the file ", fileEntry.getTitle(),
					" referenced by HTML content: ", exception.getMessage())
			);

			return null;
		}
	}

	private String _getFileEntryURL(
		CMSMigrationContext cmsMigrationContext, FileEntry fileEntry) {

		FileEntry attachmentFileEntry = _getAttachmentFileEntry(
			cmsMigrationContext, fileEntry);

		if (attachmentFileEntry == null) {
			return null;
		}

		return getFileEntryURL(attachmentFileEntry);
	}

	/**
	 * Returns the groups that a content link resolves against, in order. A
	 * link on another group's page resolves against that group before it
	 * falls back to the source group, as friendly URLs do.
	 */
	private long[] _getLinkGroupIds(CMSMigrationContext cmsMigrationContext) {
		if (cmsMigrationContext.isRewriteSourceGroup()) {
			return new long[] {cmsMigrationContext.getSourceGroupId()};
		}

		return new long[] {
			cmsMigrationContext.getRewriteGroupId(),
			cmsMigrationContext.getSourceGroupId()
		};
	}

	private String _getObjectEntryPath(
		CMSMigrationContext cmsMigrationContext, String className,
		long classPK) {

		ObjectEntry objectEntry = cmsMigrationContext.getObjectEntry(
			className, classPK);

		if (objectEntry == null) {
			return null;
		}

		return getObjectEntryPath(cmsMigrationContext, objectEntry);
	}

	private String _replace(
		Pattern pattern, String html, Function<Matcher, String> function) {

		Matcher matcher = pattern.matcher(html);

		StringBuffer sb = new StringBuffer();

		while (matcher.find()) {
			String replacement = function.apply(matcher);

			if (replacement == null) {
				replacement = matcher.group();
			}

			matcher.appendReplacement(
				sb, Matcher.quoteReplacement(replacement));
		}

		matcher.appendTail(sb);

		return sb.toString();
	}

	private static final Log _log = LogFactoryUtil.getLog(
		HTMLReferenceRewriter.class);

	private static final Pattern _blogsEntryPattern = Pattern.compile(
		"/b/([^/\"'?#\\s)]+)");
	private static final Pattern _fileReferencePattern = Pattern.compile(
		StringBundler.concat(
			"/documents/(?<groupId>\\d+)/\\d+/[^/\"'?#\\s)]+",
			"/(?<uuid>[0-9a-fA-F-]{36})[^\"'\\s)]*",
			"|/documents/d/(?<groupFriendlyURL>[^/\"'?#\\s)]+)",
			"/(?<fileEntryFriendlyURL>[^/\"'?#\\s)]+)",
			"|/o/adaptive-media/image/(?<adaptiveMediaFileEntryId>\\d+)",
			"/[^\"'\\s)]*|data-fileentryid=\"(?<fileEntryId>\\d+)\""));
	private static final Pattern _journalArticlePattern = Pattern.compile(
		"/w/([^/\"'?#\\s)]+)");

	private final BlogsEntryLocalService _blogsEntryLocalService;
	private final DLAppLocalService _dlAppLocalService;
	private final DLURLHelper _dlURLHelper;
	private final FriendlyURLEntryLocalService _friendlyURLEntryLocalService;
	private final GroupLocalService _groupLocalService;
	private final JournalArticleLocalService _journalArticleLocalService;
	private final ObjectDefinitionLocalService _objectDefinitionLocalService;
	private final Portal _portal;

}