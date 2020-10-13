/*
 * Copyright (C) 2003-2020 eXo Platform SAS.
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
package org.exoplatform.wiki.migration;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.wiki.WikiException;
import org.exoplatform.wiki.jpa.JPADataStorage;
import org.exoplatform.wiki.mow.api.Page;
import org.exoplatform.wiki.mow.api.PermissionEntry;
import org.exoplatform.wiki.mow.api.Wiki;
import org.exoplatform.wiki.mow.api.WikiType;
import org.exoplatform.wiki.service.WikiService;

/**
 * Created by The eXo Platform SAS Author : Ayoub Zayati Sept 14, 2020 
 * This plugin will be executed in order to reset default permissions for all wikis and their related pages
 */
public class WikiPermissionsUpgradePlugin extends UpgradeProductPlugin {

  private static final Log log = ExoLogger.getLogger(WikiPermissionsUpgradePlugin.class.getName());

  private WikiService      wikiService;

  private int              wikiPermissionsUpdatedCount;
  
  private int              wikiPagesPermissionsUpdatedCount;
  
  private JPADataStorage jpaDataStorage;

  public WikiPermissionsUpgradePlugin(InitParams initParams,
                                      WikiService wikiService,
                                      JPADataStorage jpaDataStorage) {
    super(initParams);
    this.wikiService = wikiService;
    this.jpaDataStorage = jpaDataStorage;
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    long startupTime = System.currentTimeMillis();
    log.info("Start upgrade of wiki permissions");
    try {
      
      List<Wiki> allWikiList = wikiService.getAllWikis();
      for (Wiki wiki : allWikiList) {
        setWikiPermissions(wiki);
        setWikiPagesPermissions(wiki);
      }
      log.info("End upgrade of '{}' wiki permissions and '{}' wiki pages permissions. It took {} ms",
               wikiPermissionsUpdatedCount, wikiPagesPermissionsUpdatedCount,
               (System.currentTimeMillis() - startupTime));
    } catch (Exception e) {
      if (log.isErrorEnabled()) {
        log.error("An unexpected error occurs when migrating wiki and pages permissions:", e);
      }
    }
  }

  /**
   * @return the wikiPermissionsUpdatedCount
   */
  public int getWikiPermissionsUpdatedCount() {
    return wikiPermissionsUpdatedCount;
  }
  
  /**
   * @return the wikiPagesPermissionsUpdatedCount
   */
  public int getWikiPagesPermissionsUpdatedCount() {
    return wikiPagesPermissionsUpdatedCount;
  }

  public void setWikiPermissions(Wiki wiki) throws WikiException {
    List<PermissionEntry> defaultPermissions = wikiService.getWikiDefaultPermissions(wiki.getType(), wiki.getOwner());
    wiki.setPermissions(defaultPermissions);
    wikiPermissionsUpdatedCount ++;
  }
  
  public void setWikiPagesPermissions(Wiki wiki) throws WikiException {
    List<Page> wikiPagesList = wikiService.getPagesOfWiki(wiki.getType(), wiki.getOwner());
    List<PermissionEntry> defaultPermissions = jpaDataStorage.getWikiHomePageDefaultPermissions(wiki.getType(), wiki.getOwner());
    for (Page wikiPage : wikiPagesList) {
      wikiPage.setPermissions(defaultPermissions);
      wikiPagesPermissionsUpdatedCount ++;
    }
  }
}
