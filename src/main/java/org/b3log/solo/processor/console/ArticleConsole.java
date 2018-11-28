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
package org.b3log.solo.processor.console;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.logging.Level;
import org.b3log.latke.logging.Logger;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.servlet.HTTPRequestContext;
import org.b3log.latke.servlet.HTTPRequestMethod;
import org.b3log.latke.servlet.annotation.Before;
import org.b3log.latke.servlet.annotation.RequestProcessing;
import org.b3log.latke.servlet.annotation.RequestProcessor;
import org.b3log.latke.servlet.renderer.JSONRenderer;
import org.b3log.latke.util.Requests;
import org.b3log.latke.util.Strings;
import org.b3log.solo.model.Article;
import org.b3log.solo.model.Common;
import org.b3log.solo.service.ArticleMgmtService;
import org.b3log.solo.service.ArticleQueryService;
import org.b3log.solo.service.UserQueryService;
import org.b3log.solo.util.Emotions;
import org.b3log.solo.util.Images;
import org.b3log.solo.util.Markdowns;
import org.b3log.solo.util.Solos;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Article console request processing.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.1.1.4, Oct 5, 2018
 * @since 0.4.0
 */
@RequestProcessor
@Before(adviceClass = ConsoleAuthAdvice.class)
public class ArticleConsole {

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(ArticleConsole.class);

    /**
     * Article management service.
     */
    @Inject
    private ArticleMgmtService articleMgmtService;

    /**
     * Article query service.
     */
    @Inject
    private ArticleQueryService articleQueryService;

    /**
     * User query service.
     */
    @Inject
    private UserQueryService userQueryService;

    /**
     * Language service.
     */
    @Inject
    private LangPropsService langPropsService;

    /**
     * Gets article thumbs.
     * <p>
     * Renders the response with a json object, for example,
     * <pre>
     * {
     *     "sc": true,
     *     "data": [
     *         "https://img.hacpai.com/bing/20171226.jpg?imageView2/1/w/960/h/520/interlace/1/q/100",
     *         "https://img.hacpai.com/bing/20171105.jpg?imageView2/1/w/960/h/520/interlace/1/q/100",
     *         "https://img.hacpai.com/bing/20180105.jpg?imageView2/1/w/960/h/520/interlace/1/q/100",
     *         "https://img.hacpai.com/bing/20171114.jpg?imageView2/1/w/960/h/520/interlace/1/q/100"
     *     ]
     * }
     * </pre>
     * </p>
     *
     * @param request  the specified http servlet request
     * @param response the specified http servlet response
     * @param context  the specified http request context
     */
    @RequestProcessing(value = "/console/thumbs", method = HTTPRequestMethod.GET)
    public void getArticleThumbs(final HttpServletRequest request, final HttpServletResponse response, final HTTPRequestContext context) {
        final JSONRenderer renderer = new JSONRenderer();
        context.setRenderer(renderer);
        final JSONObject result = new JSONObject();
        renderer.setJSONObject(result);
        result.put(Keys.STATUS_CODE, true);

        String strN = request.getParameter("n");
        if (!Strings.isNumeric(strN)) {
            strN = "6";
        }

        final int n = Integer.valueOf(strN);
        final List<String> urls = Images.randomImages(n);
        result.put("data", urls.stream().map(url -> url += "?imageView2/1/w/960/h/520/interlace/1/q/100").collect(Collectors.toList()));
    }

    /**
     * Markdowns.
     * <p>
     * Renders the response with a json object, for example,
     * <pre>
     * {
     *     "html": ""
     * }
     * </pre>
     * </p>
     *
     * @param request  the specified http servlet request, for example,
     *                 {
     *                 "markdownText": ""
     *                 }
     * @param response the specified http servlet response
     * @param context  the specified http request context
     */
    @RequestProcessing(value = "/console/markdown/2html", method = HTTPRequestMethod.POST)
    public void markdown2HTML(final HttpServletRequest request, final HttpServletResponse response, final HTTPRequestContext context) {
        final JSONRenderer renderer = new JSONRenderer();
        context.setRenderer(renderer);
        final JSONObject result = new JSONObject();
        renderer.setJSONObject(result);
        result.put(Keys.STATUS_CODE, true);

        final String markdownText = request.getParameter("markdownText");
        if (StringUtils.isBlank(markdownText)) {
            result.put("html", "");

            return;
        }

        try {
            String html = Emotions.convert(markdownText);
            html = Markdowns.toHTML(html);

            result.put("html", html);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, e.getMessage(), e);

            final JSONObject jsonObject = new JSONObject().put(Keys.STATUS_CODE, false);
            renderer.setJSONObject(jsonObject);
            jsonObject.put(Keys.MSG, langPropsService.get("getFailLabel"));
        }
    }

    /**
     * Gets an article by the specified request json object.
     * <p>
     * Renders the response with a json object, for example,
     * <pre>
     * {
     *     "oId": "",
     *     "articleTitle": "",
     *     "articleAbstract": "",
     *     "articleContent": "",
     *     "articlePermalink": "",
     *     "articleHadBeenPublished": boolean,
     *     "articleTags": [{
     *         "oId": "",
     *         "tagTitle": ""
     *     }, ....],
     *     "articleSignId": "",
     *     "signs": [{
     *         "oId": "",
     *         "signHTML": ""
     *     }, ....]
     *     "sc": "GET_ARTICLE_SUCC"
     * }
     * </pre>
     * </p>
     *
     * @param request  the specified http servlet request
     * @param response the specified http servlet response
     * @param context  the specified http request context
     */
    @RequestProcessing(value = "/console/article/*", method = HTTPRequestMethod.GET)
    public void getArticle(final HttpServletRequest request, final HttpServletResponse response, final HTTPRequestContext context) {
        final JSONRenderer renderer = new JSONRenderer();
        context.setRenderer(renderer);

        try {
            final String articleId = request.getRequestURI().substring((Latkes.getContextPath() + "/console/article/").length());

            final JSONObject result = articleQueryService.getArticle(articleId);
            result.put(Keys.STATUS_CODE, true);
            renderer.setJSONObject(result);
        } catch (final ServiceException e) {
            LOGGER.log(Level.ERROR, e.getMessage(), e);

            final JSONObject jsonObject = new JSONObject().put(Keys.STATUS_CODE, false);
            renderer.setJSONObject(jsonObject);
            jsonObject.put(Keys.MSG, langPropsService.get("getFailLabel"));
        }
    }

    /**
     * Gets articles(by crate date descending) by the specified request json object.
     * <p>
     * The request URI contains the pagination arguments. For example, the request URI is
     * /console/articles/status/published/1/10/20, means the current page is 1, the page size is 10, and the window size
     * is 20.
     * </p>
     * <p>
     * Renders the response with a json object, for example,
     * <pre>
     * {
     *     "sc": boolean,
     *     "pagination": {
     *         "paginationPageCount": 100,
     *         "paginationPageNums": [1, 2, 3, 4, 5]
     *     },
     *     "articles": [{
     *         "oId": "",
     *         "articleTitle": "",
     *         "articleCommentCount": int,
     *         "articleCreateTime"; long,
     *         "articleViewCount": int,
     *         "articleTags": "tag1, tag2, ....",
     *         "articlePutTop": boolean,
     *         "articleIsPublished": boolean
     *      }, ....]
     * }
     * </pre>, order by article update date and sticky(put top).
     * </p>
     *
     * @param request  the specified http servlet request
     * @param response the specified http servlet response
     * @param context  the specified http request context
     */
    @RequestProcessing(value = "/console/articles/status/*/*/*/*"/* Requests.PAGINATION_PATH_PATTERN */,
            method = HTTPRequestMethod.GET)
    public void getArticles(final HttpServletRequest request, final HttpServletResponse response, final HTTPRequestContext context) {
        final JSONRenderer renderer = new JSONRenderer();
        context.setRenderer(renderer);

        try {
            String path = request.getRequestURI().substring((Latkes.getContextPath() + "/console/articles/status/").length());
            final String status = StringUtils.substringBefore(path, "/");

            path = path.substring((status + "/").length());
            final JSONObject requestJSONObject = Requests.buildPaginationRequest(path);
            final boolean published = "published".equals(status);
            requestJSONObject.put(Article.ARTICLE_IS_PUBLISHED, published);

            final JSONArray excludes = new JSONArray();
            excludes.put(Article.ARTICLE_CONTENT);
            excludes.put(Article.ARTICLE_UPDATED);
            excludes.put(Article.ARTICLE_CREATED);
            excludes.put(Article.ARTICLE_AUTHOR_ID);
            excludes.put(Article.ARTICLE_HAD_BEEN_PUBLISHED);
            excludes.put(Article.ARTICLE_IS_PUBLISHED);
            excludes.put(Article.ARTICLE_RANDOM_DOUBLE);
            requestJSONObject.put(Keys.EXCLUDES, excludes);

            final String keyword = StringUtils.trim(request.getParameter("k"));
            if (StringUtils.isNotBlank(keyword)) {
                requestJSONObject.put(Common.KEYWORD, keyword);
            }

            final JSONObject result = articleQueryService.getArticles(requestJSONObject);
            result.put(Keys.STATUS_CODE, true);
            renderer.setJSONObject(result);

            final JSONArray articles = result.optJSONArray(Article.ARTICLES);
            for (int i = 0; i < articles.length(); i++) {
                final JSONObject article = articles.optJSONObject(i);
                String title = article.optString(Article.ARTICLE_TITLE);
                title = StringEscapeUtils.escapeXml(title);
                article.put(Article.ARTICLE_TITLE, title);
            }
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, e.getMessage(), e);

            final JSONObject jsonObject = new JSONObject().put(Keys.STATUS_CODE, false);
            renderer.setJSONObject(jsonObject);
            jsonObject.put(Keys.MSG, langPropsService.get("getFailLabel"));
        }
    }

    /**
     * Removes an article by the specified request.
     * <p>
     * Renders the response with a json object, for example,
     * <pre>
     * {
     *     "sc": boolean,
     *     "msg": ""
     * }
     * </pre>
     * </p>
     *
     * @param context   the specified http request context
     * @param request   the specified http servlet request
     * @param response  the specified http servlet response
     * @param articleId the specified article id
     */
    @RequestProcessing(value = "/console/article/{articleId}", method = HTTPRequestMethod.DELETE)
    public void removeArticle(final HTTPRequestContext context, final HttpServletRequest request, final HttpServletResponse response,
                              final String articleId) {
        final JSONRenderer renderer = new JSONRenderer();
        context.setRenderer(renderer);
        final JSONObject ret = new JSONObject();
        renderer.setJSONObject(ret);

        final JSONObject currentUser = Solos.getCurrentUser(request, response);

        try {
            if (!articleQueryService.canAccessArticle(articleId, currentUser)) {
                ret.put(Keys.STATUS_CODE, false);
                ret.put(Keys.MSG, langPropsService.get("forbiddenLabel"));

                return;
            }

            articleMgmtService.removeArticle(articleId);

            ret.put(Keys.STATUS_CODE, true);
            ret.put(Keys.MSG, langPropsService.get("removeSuccLabel"));
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, e.getMessage(), e);

            final JSONObject jsonObject = new JSONObject();
            renderer.setJSONObject(jsonObject);
            jsonObject.put(Keys.STATUS_CODE, false);
            jsonObject.put(Keys.MSG, langPropsService.get("removeFailLabel"));
        }
    }

    /**
     * Cancels publish an article by the specified request.
     * <p>
     * Renders the response with a json object, for example,
     * <pre>
     * {
     *     "sc": boolean,
     *     "msg": ""
     * }
     * </pre>
     * </p>
     *
     * @param context  the specified http request context
     * @param request  the specified http servlet request
     * @param response the specified http servlet response
     */
    @RequestProcessing(value = "/console/article/unpublish/*", method = HTTPRequestMethod.PUT)
    public void cancelPublishArticle(final HTTPRequestContext context, final HttpServletRequest request, final HttpServletResponse response) {
        final JSONRenderer renderer = new JSONRenderer();
        context.setRenderer(renderer);
        final JSONObject ret = new JSONObject();
        renderer.setJSONObject(ret);

        try {
            final String articleId = request.getRequestURI().substring((Latkes.getContextPath() + "/console/article/unpublish/").length());

            final JSONObject currentUser = Solos.getCurrentUser(request, response);
            if (!articleQueryService.canAccessArticle(articleId, currentUser)) {
                ret.put(Keys.STATUS_CODE, false);
                ret.put(Keys.MSG, langPropsService.get("forbiddenLabel"));

                return;
            }

            articleMgmtService.cancelPublishArticle(articleId);

            ret.put(Keys.STATUS_CODE, true);
            ret.put(Keys.MSG, langPropsService.get("unPulbishSuccLabel"));
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, e.getMessage(), e);

            final JSONObject jsonObject = new JSONObject();
            renderer.setJSONObject(jsonObject);
            jsonObject.put(Keys.STATUS_CODE, false);
            jsonObject.put(Keys.MSG, langPropsService.get("unPulbishFailLabel"));
        }
    }

    /**
     * Cancels an top article by the specified request.
     * <p>
     * Renders the response with a json object, for example,
     * <pre>
     * {
     *     "sc": boolean,
     *     "msg": ""
     * }
     * </pre>
     * </p>
     *
     * @param context  the specified http request context
     * @param request  the specified http servlet request
     * @param response the specified http servlet response
     */
    @RequestProcessing(value = "/console/article/canceltop/*", method = HTTPRequestMethod.PUT)
    public void cancelTopArticle(final HTTPRequestContext context, final HttpServletRequest request, final HttpServletResponse response) {
        final JSONRenderer renderer = new JSONRenderer();
        context.setRenderer(renderer);
        final JSONObject ret = new JSONObject();
        renderer.setJSONObject(ret);

        if (!Solos.isAdminLoggedIn(request, response)) {
            ret.put(Keys.MSG, langPropsService.get("forbiddenLabel"));
            ret.put(Keys.STATUS_CODE, false);

            return;
        }

        try {
            final String articleId = request.getRequestURI().substring((Latkes.getContextPath() + "/console/article/canceltop/").length());
            articleMgmtService.topArticle(articleId, false);
            ret.put(Keys.STATUS_CODE, true);
            ret.put(Keys.MSG, langPropsService.get("cancelTopSuccLabel"));
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, e.getMessage(), e);

            final JSONObject jsonObject = new JSONObject();
            renderer.setJSONObject(jsonObject);
            jsonObject.put(Keys.STATUS_CODE, false);
            jsonObject.put(Keys.MSG, langPropsService.get("cancelTopFailLabel"));
        }
    }

    /**
     * Puts an article to top by the specified request.
     * <p>
     * Renders the response with a json object, for example,
     * <pre>
     * {
     *     "sc": boolean,
     *     "msg": ""
     * }
     * </pre>
     * </p>
     *
     * @param context  the specified http request context
     * @param request  the specified http servlet request
     * @param response the specified http servlet response
     */
    @RequestProcessing(value = "/console/article/puttop/*", method = HTTPRequestMethod.PUT)
    public void putTopArticle(final HTTPRequestContext context, final HttpServletRequest request, final HttpServletResponse response) {
        final JSONRenderer renderer = new JSONRenderer();
        context.setRenderer(renderer);
        final JSONObject ret = new JSONObject();
        renderer.setJSONObject(ret);
        if (!Solos.isAdminLoggedIn(request, response)) {
            ret.put(Keys.MSG, langPropsService.get("forbiddenLabel"));
            ret.put(Keys.STATUS_CODE, false);

            return;
        }

        try {
            final String articleId = request.getRequestURI().substring((Latkes.getContextPath() + "/console/article/puttop/").length());
            articleMgmtService.topArticle(articleId, true);
            ret.put(Keys.STATUS_CODE, true);
            ret.put(Keys.MSG, langPropsService.get("putTopSuccLabel"));
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, e.getMessage(), e);

            final JSONObject jsonObject = new JSONObject();
            renderer.setJSONObject(jsonObject);
            jsonObject.put(Keys.STATUS_CODE, false);
            jsonObject.put(Keys.MSG, langPropsService.get("putTopFailLabel"));
        }
    }

    /**
     * Updates an article by the specified request json object.
     * <p>
     * Renders the response with a json object, for example,
     * <pre>
     * {
     *     "sc": boolean,
     *     "msg": ""
     * }
     * </pre>
     * </p>
     *
     * @param context           the specified http request context
     * @param request           the specified http servlet request
     * @param response          the specified http servlet response
     * @param requestJSONObject the specified request json object, for example,
     *                          {
     *                          "article": {
     *                          "oId": "",
     *                          "articleTitle": "",
     *                          "articleAbstract": "",
     *                          "articleContent": "",
     *                          "articleTags": "tag1,tag2,tag3",
     *                          "articlePermalink": "", // optional
     *                          "articleIsPublished": boolean,
     *                          "articleSignId": "" // optional
     *                          "articleCommentable": boolean,
     *                          "articleViewPwd": "",
     *                          "postToCommunity": boolean
     *                          }
     *                          }
     * @throws Exception exception
     */
    @RequestProcessing(value = "/console/article/", method = HTTPRequestMethod.PUT)
    public void updateArticle(final HTTPRequestContext context, final HttpServletRequest request, final HttpServletResponse response,
                              final JSONObject requestJSONObject) throws Exception {
        final JSONRenderer renderer = new JSONRenderer();
        context.setRenderer(renderer);
        final JSONObject ret = new JSONObject();

        try {
            final JSONObject article = requestJSONObject.getJSONObject(Article.ARTICLE);
            final String articleId = article.getString(Keys.OBJECT_ID);
            renderer.setJSONObject(ret);

            final JSONObject currentUser = Solos.getCurrentUser(request, response);
            if (!articleQueryService.canAccessArticle(articleId, currentUser)) {
                ret.put(Keys.MSG, langPropsService.get("forbiddenLabel"));
                ret.put(Keys.STATUS_CODE, false);

                return;
            }

            articleMgmtService.updateArticle(requestJSONObject);

            ret.put(Keys.MSG, langPropsService.get("updateSuccLabel"));
            ret.put(Keys.STATUS_CODE, true);
        } catch (final ServiceException e) {
            LOGGER.log(Level.ERROR, e.getMessage(), e);

            final JSONObject jsonObject = new JSONObject().put(Keys.STATUS_CODE, false);
            renderer.setJSONObject(jsonObject);
            jsonObject.put(Keys.MSG, e.getMessage());
        }
    }

    /**
     * Adds an article with the specified request.
     * <p>
     * Renders the response with a json object, for example,
     * <pre>
     * {
     *     "sc": boolean,
     *     "oId": "", // Generated article id
     *     "msg": ""
     * }
     * </pre>
     * </p>
     *
     * @param request           the specified http servlet request
     * @param response          the specified http servlet response
     * @param context           the specified http request context
     * @param requestJSONObject the specified request json object, for example,
     *                          {
     *                          "article": {
     *                          "articleTitle": "",
     *                          "articleAbstract": "",
     *                          "articleContent": "",
     *                          "articleTags": "tag1,tag2,tag3",
     *                          "articlePermalink": "", // optional
     *                          "articleIsPublished": boolean,
     *                          "postToCommunity": boolean,
     *                          "articleSignId": "" // optional
     *                          "articleCommentable": boolean,
     *                          "articleViewPwd": ""
     *                          }
     *                          }
     * @throws Exception exception
     */
    @RequestProcessing(value = "/console/article/", method = HTTPRequestMethod.POST)
    public void addArticle(final HttpServletRequest request, final HttpServletResponse response, final HTTPRequestContext context,
                           final JSONObject requestJSONObject) throws Exception {
        final JSONRenderer renderer = new JSONRenderer();
        context.setRenderer(renderer);
        final JSONObject ret = new JSONObject();

        try {
            final JSONObject currentUser = Solos.getCurrentUser(request, response);
            requestJSONObject.getJSONObject(Article.ARTICLE).put(Article.ARTICLE_AUTHOR_ID, currentUser.getString(Keys.OBJECT_ID));

            final String articleId = articleMgmtService.addArticle(requestJSONObject);
            ret.put(Keys.OBJECT_ID, articleId);
            ret.put(Keys.MSG, langPropsService.get("addSuccLabel"));
            ret.put(Keys.STATUS_CODE, true);

            renderer.setJSONObject(ret);
        } catch (final ServiceException e) {
            LOGGER.log(Level.ERROR, e.getMessage());

            final JSONObject jsonObject = new JSONObject().put(Keys.STATUS_CODE, false);
            renderer.setJSONObject(jsonObject);
            jsonObject.put(Keys.MSG, e.getMessage());
        }
    }
}
