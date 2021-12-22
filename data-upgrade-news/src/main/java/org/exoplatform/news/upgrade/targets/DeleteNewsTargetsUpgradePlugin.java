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
package org.exoplatform.news.upgrade.targets;

import java.util.List;

import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.news.rest.NewsTargetingEntity;
import org.exoplatform.news.service.NewsTargetingService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * Created by The eXo Platform SAS Author : Ayoub Zayati December 22, 2021 
 * This plugin will be executed in order to delete created news targets without labels
 */
public class DeleteNewsTargetsUpgradePlugin extends UpgradeProductPlugin {

  private static final Log     log = ExoLogger.getLogger(DeleteNewsTargetsUpgradePlugin.class.getName());

  private NewsTargetingService newsTargetingService;

  private int                  newsTargetsCount;

  public DeleteNewsTargetsUpgradePlugin(InitParams initParams, NewsTargetingService newsTargetingService) {
    super(initParams);
    this.newsTargetingService = newsTargetingService;
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    long startupTime = System.currentTimeMillis();
    log.info("Start upgrade of news targets");
    List<NewsTargetingEntity> newsTargets = newsTargetingService.getTargets();
    for (NewsTargetingEntity newsTarget : newsTargets) {
      newsTargetingService.deleteTargetByName(newsTarget.getName());
      newsTargetsCount++;
    }
    log.info("End upgrade of '{}' news targets. It took {} ms", newsTargetsCount, (System.currentTimeMillis() - startupTime));
  }

  /**
   * @return the newsTargetsCount
   */
  public int getNewsTargetsCount() {
    return newsTargetsCount;
  }

}
