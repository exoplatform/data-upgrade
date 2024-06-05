/*
 * Copyright (C) 2024 eXo Platform SAS.
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
package org.exoplatform.migration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PortalPagesProfilesMigration extends UpgradeProductPlugin {

  private static final Log     LOG               = ExoLogger.getExoLogger(PortalPagesProfilesMigration.class);

  private static final String  OLD_PAGES_PROFILES = "old.pages.profiles";

  private static final String  NEW_PAGES_PROFILES = "new.pages.profiles";

  private PortalContainer      container;

  private EntityManagerService entityManagerService;

  private Map<String, String>  pagesProfiles     = new HashMap<>();

  private int                  pagesUpdatedCount;

  public PortalPagesProfilesMigration(PortalContainer container, EntityManagerService entityManagerService, InitParams initParams) {
    super(initParams);
    this.container = container;
    this.entityManagerService = entityManagerService;

    if (initParams.containsKey(OLD_PAGES_PROFILES) && initParams.containsKey(NEW_PAGES_PROFILES)) {
      pagesProfiles.put(initParams.getValueParam(OLD_PAGES_PROFILES).getValue(),
                        initParams.getValueParam(NEW_PAGES_PROFILES).getValue());
    }
  }

  @Override
  public void processUpgrade(String s, String s1) {
    if (pagesProfiles.isEmpty()) {
      LOG.error("Couldn't process upgrade, the parameter '{}' is mandatory", OLD_PAGES_PROFILES);
      return;
    }

    long startupTime = System.currentTimeMillis();

    ExoContainerContext.setCurrentContainer(container);
    boolean transactionStarted = false;

    Set<Map.Entry<String, String>> pageProfilesEntrySet = pagesProfiles.entrySet();
    for (Map.Entry<String, String> profiles : pageProfilesEntrySet) {
      String oldPagesProfiles = profiles.getKey().trim();
      String newPagesProfiles = profiles.getValue().trim();
      LOG.info("Start upgrade of pages with profiles '{}' to use profiles '{}'", oldPagesProfiles, newPagesProfiles);
      RequestLifeCycle.begin(this.entityManagerService);
      EntityManager entityManager = this.entityManagerService.getEntityManager();
      try {
        if (!entityManager.getTransaction().isActive()) {
          entityManager.getTransaction().begin();
          transactionStarted = true;
        }

        String sqlString = "UPDATE PORTAL_PAGES  SET PROFILES = '" + newPagesProfiles + "' WHERE PROFILES = '" + oldPagesProfiles
            + "' AND ID > 0;";
        Query nativeQuery = entityManager.createNativeQuery(sqlString);
        this.pagesUpdatedCount = nativeQuery.executeUpdate();
        LOG.info("End upgrade of '{}' pages with profiles '{}' to use profiles '{}'. It took {} ms",
                 pagesUpdatedCount,
                 oldPagesProfiles,
                 newPagesProfiles,
                 (System.currentTimeMillis() - startupTime));
        if (transactionStarted && entityManager.getTransaction().isActive()) {
          entityManager.getTransaction().commit();
          entityManager.flush();
        }
      } catch (Exception e) {
        if (transactionStarted && entityManager.getTransaction().isActive() && entityManager.getTransaction().getRollbackOnly()) {
          entityManager.getTransaction().rollback();
        }
      } finally {
        RequestLifeCycle.end();
      }
    }
  }

  public int getPagesUpdatedCount() {
    return pagesUpdatedCount;
  }
}
