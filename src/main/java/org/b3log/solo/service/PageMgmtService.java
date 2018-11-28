/*
 * Solo - A small and beautiful blogging system written in Java.
 * Copyright (c) 2010-2018, b3log.org & hacpai.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.b3log.solo.service;

import org.apache.commons.lang.StringUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.logging.Level;
import org.b3log.latke.logging.Logger;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.solo.model.Comment;
import org.b3log.solo.model.Option;
import org.b3log.solo.model.Page;
import org.b3log.solo.repository.CommentRepository;
import org.b3log.solo.repository.PageRepository;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Page management service.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.1.0.9, Sep 21, 2017
 * @since 0.4.0
 */
@Service
public class PageMgmtService {

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(PageMgmtService.class);

    /**
     * Page repository.
     */
    @Inject
    private PageRepository pageRepository;

    /**
     * Comment repository.
     */
    @Inject
    private CommentRepository commentRepository;

    /**
     * Language service.
     */
    @Inject
    private LangPropsService langPropsService;

    /**
     * Permalink query service.
     */
    @Inject
    private PermalinkQueryService permalinkQueryService;

    /**
     * Preference query service.
     */
    @Inject
    private PreferenceQueryService preferenceQueryService;

    /**
     * Statistic management service.
     */
    @Inject
    private StatisticMgmtService statisticMgmtService;

    /**
     * Statistic query service.
     */
    @Inject
    private StatisticQueryService statisticQueryService;

    /**
     * Updates a page by the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          {
     *                          "page": {
     *                          "oId": "",
     *                          "pageTitle": "",
     *                          "pageContent": "",
     *                          "pageOrder": int,
     *                          "pageCommentCount": int,
     *                          "pagePermalink": "",
     *                          "pageCommentable": boolean,
     *                          "pageType": "",
     *                          "pageOpenTarget": "",
     *                          "pageEditorType": "", // optional, preference specified if not exists this key
     *                          "pageIcon": "" // optional
     *                          }
     *                          }, see {@link Page} for more details
     * @throws ServiceException service exception
     */
    public void updatePage(final JSONObject requestJSONObject) throws ServiceException {

        final Transaction transaction = pageRepository.beginTransaction();

        try {
            final JSONObject page = requestJSONObject.getJSONObject(Page.PAGE);
            final String pageId = page.getString(Keys.OBJECT_ID);
            final JSONObject oldPage = pageRepository.get(pageId);
            final JSONObject newPage = new JSONObject(page, JSONObject.getNames(page));

            newPage.put(Page.PAGE_ORDER, oldPage.getInt(Page.PAGE_ORDER));
            newPage.put(Page.PAGE_COMMENT_COUNT, oldPage.getInt(Page.PAGE_COMMENT_COUNT));
            String permalink = page.optString(Page.PAGE_PERMALINK).trim();

            final String oldPermalink = oldPage.getString(Page.PAGE_PERMALINK);

            if (!oldPermalink.equals(permalink)) {
                if (StringUtils.isBlank(permalink)) {
                    permalink = "/pages/" + pageId + ".html";
                }

                if (Page.PAGE.equals(page.getString(Page.PAGE_TYPE))) {
                    if (!permalink.startsWith("/")) {
                        permalink = "/" + permalink;
                    }

                    if (PermalinkQueryService.invalidPagePermalinkFormat(permalink)) {
                        if (transaction.isActive()) {
                            transaction.rollback();
                        }

                        throw new ServiceException(langPropsService.get("invalidPermalinkFormatLabel"));
                    }

                    if (!oldPermalink.equals(permalink) && permalinkQueryService.exist(permalink)) {
                        if (transaction.isActive()) {
                            transaction.rollback();
                        }

                        throw new ServiceException(langPropsService.get("duplicatedPermalinkLabel"));
                    }
                }
            }

            newPage.put(Page.PAGE_PERMALINK, permalink.replaceAll(" ", "-"));

            if (!oldPage.getString(Page.PAGE_PERMALINK).equals(permalink)) { // The permalink has been updated
                // Updates related comments' links
                processCommentsForPageUpdate(newPage);
            }

            // Set editor type
            if (!newPage.has(Page.PAGE_EDITOR_TYPE)) {
                final JSONObject preference = preferenceQueryService.getPreference();
                newPage.put(Page.PAGE_EDITOR_TYPE, preference.optString(Option.ID_C_EDITOR_TYPE));
            }

            page.put(Page.PAGE_ICON, page.optString(Page.PAGE_ICON));

            pageRepository.update(pageId, newPage);

            transaction.commit();

            LOGGER.log(Level.DEBUG, "Updated a page[id={0}]", pageId);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, e.getMessage(), e);
            if (transaction.isActive()) {
                transaction.rollback();
            }

            throw new ServiceException(e);
        }
    }

    /**
     * Removes a page specified by the given page id.
     *
     * @param pageId the given page id
     * @throws ServiceException service exception
     */
    public void removePage(final String pageId) throws ServiceException {
        final Transaction transaction = pageRepository.beginTransaction();

        try {
            LOGGER.log(Level.DEBUG, "Removing a page[id={0}]", pageId);
            removePageComments(pageId);
            pageRepository.remove(pageId);

            transaction.commit();

        } catch (final Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }

            LOGGER.log(Level.ERROR, "Removes a page[id=" + pageId + "] failed", e);

            throw new ServiceException(e);
        }
    }

    /**
     * Adds a page with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          {
     *                          "page": {
     *                          "pageTitle": "",
     *                          "pageContent": "",
     *                          "pageOpenTarget": "",
     *                          "pageCommentable": boolean,
     *                          "pageType": "",
     *                          "pagePermalink": "", // optional
     *                          "pageEditorType": "", // optional, preference specified if not exists this key
     *                          "pageIcon": "" // optional
     *                          }
     *                          }, see {@link Page} for more details
     * @return generated page id
     * @throws ServiceException if permalink format checks failed or persists failed
     */
    public String addPage(final JSONObject requestJSONObject) throws ServiceException {
        final Transaction transaction = pageRepository.beginTransaction();

        try {
            final JSONObject page = requestJSONObject.getJSONObject(Page.PAGE);
            page.put(Page.PAGE_COMMENT_COUNT, 0);
            final int maxOrder = pageRepository.getMaxOrder();
            page.put(Page.PAGE_ORDER, maxOrder + 1);

            String permalink = page.optString(Page.PAGE_PERMALINK);
            if (StringUtils.isBlank(permalink)) {
                permalink = "/pages/" + Ids.genTimeMillisId() + ".html";
            }

            if (Page.PAGE.equals(page.getString(Page.PAGE_TYPE))) {
                if (!permalink.startsWith("/")) {
                    permalink = "/" + permalink;
                }

                if (PermalinkQueryService.invalidPagePermalinkFormat(permalink)) {
                    if (transaction.isActive()) {
                        transaction.rollback();
                    }

                    throw new ServiceException(langPropsService.get("invalidPermalinkFormatLabel"));
                }

                if (permalinkQueryService.exist(permalink)) {
                    if (transaction.isActive()) {
                        transaction.rollback();
                    }

                    throw new ServiceException(langPropsService.get("duplicatedPermalinkLabel"));
                }
            }

            page.put(Page.PAGE_PERMALINK, permalink.replaceAll(" ", "-"));

            // Set editor type
            if (!page.has(Page.PAGE_EDITOR_TYPE)) {
                final JSONObject preference = preferenceQueryService.getPreference();
                page.put(Page.PAGE_EDITOR_TYPE, preference.optString(Option.ID_C_EDITOR_TYPE));
            }

            page.put(Page.PAGE_ICON, page.optString(Page.PAGE_ICON));

            final String ret = pageRepository.add(page);

            transaction.commit();

            return ret;
        } catch (final JSONException e) {
            LOGGER.log(Level.ERROR, e.getMessage(), e);
            if (transaction.isActive()) {
                transaction.rollback();
            }

            throw new ServiceException(e);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, e.getMessage(), e);
            if (transaction.isActive()) {
                transaction.rollback();
            }

            throw new ServiceException(e);
        }
    }

    /**
     * Changes the order of a page specified by the given page id with the specified direction.
     *
     * @param pageId    the given page id
     * @param direction the specified direction, "up"/"down"
     * @throws ServiceException service exception
     */
    public void changeOrder(final String pageId, final String direction) throws ServiceException {

        final Transaction transaction = pageRepository.beginTransaction();

        try {
            final JSONObject srcPage = pageRepository.get(pageId);
            final int srcPageOrder = srcPage.getInt(Page.PAGE_ORDER);

            JSONObject targetPage;

            if ("up".equals(direction)) {
                targetPage = pageRepository.getUpper(pageId);
            } else { // Down
                targetPage = pageRepository.getUnder(pageId);
            }

            if (null == targetPage) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }

                LOGGER.log(Level.WARN, "Cant not find the target page of source page[order={0}]", srcPageOrder);
                return;
            }

            // Swaps
            srcPage.put(Page.PAGE_ORDER, targetPage.getInt(Page.PAGE_ORDER));
            targetPage.put(Page.PAGE_ORDER, srcPageOrder);

            pageRepository.update(srcPage.getString(Keys.OBJECT_ID), srcPage);
            pageRepository.update(targetPage.getString(Keys.OBJECT_ID), targetPage);

            transaction.commit();
        } catch (final Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }

            LOGGER.log(Level.ERROR, "Changes page's order failed", e);

            throw new ServiceException(e);
        }
    }

    /**
     * Removes page comments by the specified page id.
     * <p>
     * Removes related comments, sets page/blog comment statistic count.
     * </p>
     *
     * @param pageId the specified page id
     * @throws JSONException json exception
     * @throws Exception     exception
     */
    private void removePageComments(final String pageId) throws Exception {
        final int removedCnt = commentRepository.removeComments(pageId);

        int blogCommentCount = statisticQueryService.getBlogCommentCount();

        blogCommentCount -= removedCnt;
        statisticMgmtService.setBlogCommentCount(blogCommentCount);

        int publishedBlogCommentCount = statisticQueryService.getPublishedBlogCommentCount();

        publishedBlogCommentCount -= removedCnt;
        statisticMgmtService.setPublishedBlogCommentCount(publishedBlogCommentCount);
    }

    /**
     * Processes comments for page update.
     *
     * @param page the specified page to update
     * @throws Exception exception
     */
    public void processCommentsForPageUpdate(final JSONObject page) throws Exception {
        final String pageId = page.getString(Keys.OBJECT_ID);

        final List<JSONObject> comments = commentRepository.getComments(pageId, 1, Integer.MAX_VALUE);
        for (final JSONObject comment : comments) {
            final String commentId = comment.getString(Keys.OBJECT_ID);
            final String sharpURL = Comment.getCommentSharpURLForPage(page, commentId);
            comment.put(Comment.COMMENT_SHARP_URL, sharpURL);
            if (StringUtils.isBlank(comment.optString(Comment.COMMENT_ORIGINAL_COMMENT_ID))) {
                comment.put(Comment.COMMENT_ORIGINAL_COMMENT_ID, "");
            }
            if (StringUtils.isBlank(comment.optString(Comment.COMMENT_ORIGINAL_COMMENT_NAME))) {
                comment.put(Comment.COMMENT_ORIGINAL_COMMENT_NAME, "");
            }

            commentRepository.update(commentId, comment);
        }
    }

    /**
     * Sets the permalink query service with the specified permalink query service.
     *
     * @param permalinkQueryService the specified permalink query service
     */
    public void setPermalinkQueryService(final PermalinkQueryService permalinkQueryService) {
        this.permalinkQueryService = permalinkQueryService;
    }

    /**
     * Set the page repository with the specified page repository.
     *
     * @param pageRepository the specified page repository
     */
    public void setPageRepository(final PageRepository pageRepository) {
        this.pageRepository = pageRepository;
    }

    /**
     * Sets the preference query service with the specified preference query service.
     *
     * @param preferenceQueryService the specified preference query service
     */
    public void setPreferenceQueryService(final PreferenceQueryService preferenceQueryService) {
        this.preferenceQueryService = preferenceQueryService;
    }

    /**
     * Sets the statistic query service with the specified statistic query service.
     *
     * @param statisticQueryService the specified statistic query service
     */
    public void setStatisticQueryService(final StatisticQueryService statisticQueryService) {
        this.statisticQueryService = statisticQueryService;
    }

    /**
     * Sets the statistic management service with the specified statistic management service.
     *
     * @param statisticMgmtService the specified statistic management service
     */
    public void setStatisticMgmtService(final StatisticMgmtService statisticMgmtService) {
        this.statisticMgmtService = statisticMgmtService;
    }

    /**
     * Sets the comment repository with the specified comment repository.
     *
     * @param commentRepository the specified comment repository
     */
    public void setCommentRepository(final CommentRepository commentRepository) {
        this.commentRepository = commentRepository;
    }

    /**
     * Sets the language service with the specified language service.
     *
     * @param langPropsService the specified language service
     */
    public void setLangPropsService(final LangPropsService langPropsService) {
        this.langPropsService = langPropsService;
    }
}
