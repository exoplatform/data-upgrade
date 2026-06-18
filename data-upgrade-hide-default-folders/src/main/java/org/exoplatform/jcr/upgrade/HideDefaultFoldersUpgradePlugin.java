/*
 * Copyright (C) 2025 eXo Platform SAS
 *
 *  This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.jcr.upgrade;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.upgrade.UpgradePluginExecutionContext;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * plugin will be executed in order to hide already created default users
 * folders
 */
public class HideDefaultFoldersUpgradePlugin extends UpgradeProductPlugin {

  private static final Log             LOG                 = ExoLogger.getLogger(HideDefaultFoldersUpgradePlugin.class.getName());

  private static final String          PLUGIN_NAME         = "HidePersonalDriveDefaultFoldersUpgrade";

  private static final String          PLUGIN_EXECUTED_KEY = String.format("%sExecuted", PLUGIN_NAME);

  private final RepositoryService      repositoryService;

  private final SessionProviderService sessionProviderService;

  private final SettingService         settingService;

  private boolean                      upgradeSacceeded    = false;

  public HideDefaultFoldersUpgradePlugin(InitParams initParams,
                                         RepositoryService repositoryService,
                                         SettingService settingService,
                                         SessionProviderService sessionProviderService) {
    super(initParams);
    this.repositoryService = repositoryService;
    this.sessionProviderService = sessionProviderService;
    this.settingService = settingService;
  }

  @Override
  public boolean shouldProceedToUpgrade(String newVersion,
                                        String previousGroupVersion,
                                        UpgradePluginExecutionContext upgradePluginExecutionContext) {
    SettingValue<?> settingValue = settingService.get(Context.GLOBAL.id(PLUGIN_NAME),
                                                      Scope.APPLICATION.id(PLUGIN_NAME),
                                                      PLUGIN_EXECUTED_KEY);
    return settingValue == null;
  }

  @Override
  public void afterUpgrade() {
    if (upgradeSacceeded) {
      settingService.set(Context.GLOBAL.id(PLUGIN_NAME),
                         Scope.APPLICATION.id(PLUGIN_NAME),
                         PLUGIN_EXECUTED_KEY,
                         SettingValue.create(true));
    }
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {

    long startupTime = System.currentTimeMillis();
    int hidedFoldersCount = 0;
    long totalFoldersCount = 0;
    LOG.info("Start upgrade : Hiding default folders");
    SessionProvider sessionProvider = null;
    RequestLifeCycle.begin(PortalContainer.getInstance());
    try {
      sessionProvider = sessionProviderService.getSystemSessionProvider(null);
      Session session = sessionProvider.getSession(
                                                   repositoryService.getCurrentRepository()
                                                                    .getConfiguration()
                                                                    .getDefaultWorkspaceName(),
                                                   repositoryService.getCurrentRepository());
      Node users = (Node) session.getItem("/Users");
      String queryString =
                         """
                             SELECT * FROM nt:base
                             WHERE jcr:path LIKE '%/Private/%'
                             AND  (jcr:primaryType ='nt:unstructured' OR jcr:primaryType ='nt:folder' OR jcr:primaryType ='exo:symlink')
                             AND (jcr:mixinTypes LIKE 'exo:musicFolder' OR jcr:mixinTypes LIKE 'exo:pictureFolder' OR jcr:mixinTypes LIKE 'exo:videoFolder' OR jcr:mixinTypes LIKE 'exo:favoriteFolder' OR exo:name LIKE 'Public')
                             AND NOT jcr:mixinTypes LIKE 'exo:hiddenable'
                             """;

      Query jcrQuery = users.getSession().getWorkspace().getQueryManager().createQuery(queryString, Query.SQL);
      QueryResult queryResult = jcrQuery.execute();
      NodeIterator nodeIterator = queryResult.getNodes();
      totalFoldersCount = nodeIterator.getSize();
      LOG.info("Total number of folders: {}", totalFoldersCount);
      while (nodeIterator.hasNext()) {
        Node node = nodeIterator.nextNode();
        if (isPublicNode(node)) {
          hidePublicNode(node);
        } else {
          node.addMixin("exo:hiddenable");
          node.save();
        }
        hidedFoldersCount++;
        if (hidedFoldersCount % 1000 == 0) {
          LOG.info("{} folders hidden ", hidedFoldersCount);
        }
      }
      LOG.info("End hiding of {}/{} folders. It took {} ms",
               hidedFoldersCount,
               totalFoldersCount,
               (System.currentTimeMillis() - startupTime));
      upgradeSacceeded = true;
    } catch (Exception e) {
      LOG.error("An unexpected error occurs when hiding folders:", e);
    } finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
      RequestLifeCycle.end();
    }
  }

  private boolean isPublicNode(Node node) {
    try {
      return node.getName().equals("Public");
    } catch (Exception exception) {
      return false;
    }
  }

  private void hidePublicNode(Node node) throws RepositoryException {
    ExtendedNode extNode = (ExtendedNode) node;
    extNode.removePermission("any");
    extNode.addMixin("exo:hiddenable");
    extNode.save();
  }
}
