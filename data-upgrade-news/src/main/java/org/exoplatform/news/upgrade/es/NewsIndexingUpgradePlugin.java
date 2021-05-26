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
package org.exoplatform.news.upgrade.es;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;

import org.exoplatform.commons.search.index.IndexingService;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.news.search.NewsIndexingServiceConnector;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.jpa.search.ActivityIndexingServiceConnector;

public class NewsIndexingUpgradePlugin extends UpgradeProductPlugin {

  private static final Log        log              = ExoLogger.getLogger(NewsIndexingUpgradePlugin.class.getName());

  private final RepositoryService repositoryService;

  private static final String     COLLABORATION_WS = "collaboration";

  private final IndexingService   indexingService;

  private SessionProviderService  sessionProviderService;

  private int                     newsIndexingCount;

  public NewsIndexingUpgradePlugin(InitParams initParams,
                                   RepositoryService repositoryService,
                                   IndexingService indexingService,
                                   SessionProviderService sessionProviderService) {
    super(initParams);
    this.repositoryService = repositoryService;
    this.indexingService = indexingService;
    this.sessionProviderService = sessionProviderService;
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    long startupTime = System.currentTimeMillis();
    log.info("Start unindexing old news activities and indexing old news");
    SessionProvider sessionProvider = null;
    try {
      ManageableRepository currentRepository = repositoryService.getCurrentRepository();
      sessionProvider = sessionProviderService.getSessionProvider(null);
      Session session = sessionProvider.getSession(COLLABORATION_WS, currentRepository);
      QueryManager qm = session.getWorkspace().getQueryManager();
      Query q =
              qm.createQuery("select * from exo:news WHERE publication:currentState = 'published' AND jcr:path LIKE '/Groups/spaces/%'",
                             Query.SQL);
      NodeIterator nodeIterator = q.execute().getNodes();
      if (nodeIterator != null) {
        while (nodeIterator.hasNext()) {
          Node newsNode = nodeIterator.nextNode();
          indexingService.index(NewsIndexingServiceConnector.TYPE, newsNode.getUUID());
          String newsActivityId = newsNode.getProperty("exo:activities").getString().split(";")[0].split(":")[1];
          indexingService.unindex(ActivityIndexingServiceConnector.TYPE, newsActivityId);
          newsIndexingCount++;
        }
      }
      log.info("End indexing of '{}' old news. It took {} ms", newsIndexingCount, (System.currentTimeMillis() - startupTime));
    } catch (Exception e) {
      if (log.isErrorEnabled()) {
        log.error("An unexpected error occurs when unindexing old news activities or indexing old news", e);
      }
    } finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
    }
  }

  /**
   * @return the newsJcrNodesUpdatedCount
   */
  public int getNewsIndexingCount() {
    return newsIndexingCount;
  }
}
