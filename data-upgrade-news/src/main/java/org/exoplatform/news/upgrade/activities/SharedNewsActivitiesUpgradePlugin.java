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
package org.exoplatform.news.upgrade.activities;

import java.util.Map;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;

import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.manager.ActivityManager;

public class SharedNewsActivitiesUpgradePlugin extends UpgradeProductPlugin {

  private static final Log        log              = ExoLogger.getLogger(SharedNewsActivitiesUpgradePlugin.class.getName());

  private final RepositoryService repositoryService;

  private static final String     COLLABORATION_WS = "collaboration";

  private final ActivityManager activityManager;

  private int                     sharedNewsActivitiesCount;

  public SharedNewsActivitiesUpgradePlugin(InitParams initParams,
                                   RepositoryService repositoryService,
                                   ActivityManager activityManager) {
    super(initParams);
    this.repositoryService = repositoryService;
    this.activityManager = activityManager;
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    long startupTime = System.currentTimeMillis();
    log.info("Start upgrading old shared news activities");
    SessionProvider sessionProvider = null;
    try {
      sessionProvider = SessionProvider.createSystemProvider();
      Session session = sessionProvider.getSession(COLLABORATION_WS, repositoryService.getCurrentRepository());
      QueryManager qm = session.getWorkspace().getQueryManager();
      Query q =
              qm.createQuery("select * from exo:news WHERE publication:currentState = 'published' AND jcr:path LIKE '/Groups/spaces/%'",
                             Query.SQL);
      NodeIterator nodeIterator = q.execute().getNodes();
      if (nodeIterator != null) {
        while (nodeIterator.hasNext()) {
          Node newsNode = nodeIterator.nextNode();
          String[] newsActivities = newsNode.getProperty("exo:activities").getString().split(";");
          String newsActivityId = newsActivities[0].split(":")[1];
          for (int i = 1; i < newsActivities.length; i++) {
            String sharedNewsActivityId = newsActivities[i].split(":")[1];
            ExoSocialActivity sharedNewsActivity = activityManager.getActivity(sharedNewsActivityId);
            Map<String, String> sharedNewsActivityTemplateParams = sharedNewsActivity.getTemplateParams();
            if (sharedNewsActivityTemplateParams.get("originalActivityId") == null) {
              sharedNewsActivityTemplateParams.put("originalActivityId", newsActivityId);
              sharedNewsActivity.setTemplateParams(sharedNewsActivityTemplateParams);
              activityManager.updateActivity(sharedNewsActivity);
              sharedNewsActivitiesCount++;
            }
          }
        }
      }
      log.info("End upgrading of '{}' old shared news activities. It took {} ms", sharedNewsActivitiesCount, (System.currentTimeMillis() - startupTime));
    } catch (Exception e) {
      if (log.isErrorEnabled()) {
        log.error("An unexpected error occurs when upgrading old shared news activities", e);
      }
    } finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
    }
  }

  /**
   * @return the sharedNewsActivitiesCount
   */
  public int getSharedNewsActivitiesCount() {
    return sharedNewsActivitiesCount;
  }
}
