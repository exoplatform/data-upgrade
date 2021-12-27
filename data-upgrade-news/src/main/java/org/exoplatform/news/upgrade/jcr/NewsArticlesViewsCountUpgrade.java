/*
 * Copyright (C) 2021 eXo Platform SAS.
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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.news.upgrade.jcr;

import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.wcm.core.NodetypeConstant;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;

public class NewsArticlesViewsCountUpgrade extends UpgradeProductPlugin {

    private static final Log LOG                           = ExoLogger.getLogger(NewsArticlesViewsCountUpgrade.class.getName());

    private RepositoryService repositoryService;
    private SessionProviderService sessionProviderService;

    private int updatedNodes                              = 0 ;

    private static final String EXO_NEWS_VIEWERS          = "exo:viewers";
    private static final String EXO_NEWS_VIEWS_COUNT      = "exo:viewsCount";

    public NewsArticlesViewsCountUpgrade(InitParams initParams,
                                         RepositoryService repositoryService,
                                         SessionProviderService sessionProviderService) {
        super(initParams);
        this.repositoryService = repositoryService;
        this.sessionProviderService = sessionProviderService;
    }

    public int getUpdatedNodes() {
        return updatedNodes;
    }

    @Override
    public void processUpgrade(String oldVersion, String newVersion) {
        long startupTime = System.currentTimeMillis();
        LOG.info("Start upgrade of news articles viewsCount");
        SessionProvider sessionProvider = null;
        try {
            sessionProvider = sessionProviderService.getSystemSessionProvider(null);
            Session session = sessionProvider.getSession(
                    repositoryService.getCurrentRepository()
                            .getConfiguration()
                            .getDefaultWorkspaceName(),
                    repositoryService.getCurrentRepository());
            String queryString = "SELECT * FROM exo:news WHERE jcr:path LIKE '/Groups/spaces/%/News/%'";
            QueryManager queryManager = session.getWorkspace().getQueryManager();
            Query query = queryManager.createQuery(queryString, Query.SQL);

            NodeIterator newsIterator = query.execute().getNodes();
            while (newsIterator.hasNext()) {
                Node news = newsIterator.nextNode();
                String newsTitle = news.getProperty(NodetypeConstant.EXO_TITLE).getValue().getString();
                if (news.hasProperty(EXO_NEWS_VIEWS_COUNT) && news.hasProperty(EXO_NEWS_VIEWERS)) {
                    long viewsCount = news.getProperty(EXO_NEWS_VIEWERS).getValue().getString().split(",").length;
                    long oldViewsCount = news.getProperty(EXO_NEWS_VIEWS_COUNT).getValue().getLong();
                    if (viewsCount > oldViewsCount) {
                        news.setProperty(EXO_NEWS_VIEWS_COUNT, viewsCount);
                        news.save();
                        LOG.info("viewsCount of article {} has been updated: {}", newsTitle, viewsCount);
                        updatedNodes++;
                    }
                }
            }
            LOG.info("End upgrade of news articles viewsCount, {} articles were successfully updated. It took {} ms",
                    updatedNodes, (System.currentTimeMillis() - startupTime));

        } catch (Exception e) {
            LOG.error("An error occurred when upgrading news article viewsCount:", e);

        } finally {
            if (sessionProvider != null) {
                sessionProvider.close();
            }
        }
    }
}
