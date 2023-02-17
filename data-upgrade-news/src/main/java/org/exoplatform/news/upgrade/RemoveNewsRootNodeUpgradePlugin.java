/*
 * Copyright (C) 2023 eXo Platform SAS.
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

package org.exoplatform.news.upgrade;

import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import javax.jcr.Node;
import javax.jcr.Session;

public class RemoveNewsRootNodeUpgradePlugin extends UpgradeProductPlugin {

  private static final Log       log                   = ExoLogger.getLogger(RemoveNewsRootNodeUpgradePlugin.class.getName());

  private RepositoryService repositoryService;

  private SessionProviderService  sessionProviderService;

  public static final String      APPLICATION_DATA_PATH = "/Application Data";

  public static final String      NEWS_NODES_FOLDER     = "News";

  public RemoveNewsRootNodeUpgradePlugin(InitParams initParams,
                                         RepositoryService repositoryService,
                                         SessionProviderService sessionProviderService) {
    super(initParams);
    this.repositoryService = repositoryService;
    this.sessionProviderService = sessionProviderService;
  }

  @Override
  public void processUpgrade(String s, String s1) {
    long startupTime = System.currentTimeMillis();
    log.info("Start removing news root node");
    SessionProvider sessionProvider = sessionProviderService.getSystemSessionProvider(null);
    try {
      Session session = sessionProvider.getSession(repositoryService.getCurrentRepository().getConfiguration().getDefaultWorkspaceName(),
                                           repositoryService.getCurrentRepository());

      Node applicationDataNode = (Node) session.getItem(APPLICATION_DATA_PATH);
      Node newsRootNode;
      if (applicationDataNode.hasNode(NEWS_NODES_FOLDER)) {
        newsRootNode = applicationDataNode.getNode(NEWS_NODES_FOLDER);
        newsRootNode.remove();
        applicationDataNode.save();
      }
      log.info("End removing news root node", (System.currentTimeMillis() - startupTime));

    } catch (Exception e) {
      if (log.isErrorEnabled()) {
        log.error("An unexpected error occurs when removing news root node ", e);
      }
    } finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
    }
  }
}
