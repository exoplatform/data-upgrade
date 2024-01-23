/*
 * Copyright (C) 2023 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.application.upgrade;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.exoplatform.application.registry.impl.JDBCApplicationRegistryService;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class CleanAppRegistryCategoryUpgradePlugin extends UpgradeProductPlugin {

  private static final Log           LOG = ExoLogger.getExoLogger(CleanAppRegistryCategoryUpgradePlugin.class);

  private final EntityManagerService entityManagerService;
  
  private final PortalContainer      container;

  private int                        cleanedCategoriesCount;

  public CleanAppRegistryCategoryUpgradePlugin(PortalContainer container, EntityManagerService entityManagerService, InitParams initParams) {
    super(initParams);
    this.container = container;
    this.entityManagerService = entityManagerService;
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    long startupTime = System.currentTimeMillis();

    ExoContainerContext.setCurrentContainer(container);
    boolean transactionStarted = false;

    LOG.info("Start clean application registry categories migration");
    RequestLifeCycle.begin(this.entityManagerService);
    EntityManager entityManager = this.entityManagerService.getEntityManager();
    try {
      if (!entityManager.getTransaction().isActive()) {
        entityManager.getTransaction().begin();
        transactionStarted = true;
      }
      Query nativeQuery = entityManager.createNativeQuery("DELETE FROM PORTAL_APP_CATEGORIES");
      this.cleanedCategoriesCount += nativeQuery.executeUpdate();
      LOG.info("End clean of '{}' application registry categories successful migration. It tooks {} ms.",
               this.cleanedCategoriesCount,
               (System.currentTimeMillis() - startupTime));
      if (transactionStarted && entityManager.getTransaction().isActive()) {
        entityManager.getTransaction().commit();
        entityManager.clear();
        entityManager.flush();
      }
    } catch (Exception e) {
      if (transactionStarted && entityManager.getTransaction().isActive() && entityManager.getTransaction().getRollbackOnly()) {
        entityManager.getTransaction().rollback();
      }
    } finally {
      RequestLifeCycle.end();
      restartApplicationRegistryService();
    }
  }
  public int getCleanedCategoriesCount() {
    return cleanedCategoriesCount;
  }

  private void restartApplicationRegistryService() {
    try {
      JDBCApplicationRegistryService applicationRegistryService = CommonsUtils.getService(JDBCApplicationRegistryService.class);
      applicationRegistryService.stop();
      applicationRegistryService.start();
    } catch (Exception e) {
      LOG.info("Error encountered when restarting {} during the clean application registry upgrade plugin", JDBCApplicationRegistryService.class);
    }
  }
}
