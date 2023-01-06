/*
 * Copyright (C) 2022 eXo Platform SAS.
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

import java.math.BigInteger;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradePluginExecutionContext;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class AppRegistryUpgradeToolsCategoryPlugin extends UpgradeProductPlugin {

  private static final Log     LOG                        = ExoLogger.getExoLogger(AppRegistryUpgradeToolsCategoryPlugin.class);

  private final PortalContainer      container;

  private final EntityManagerService entityManagerService;

  private static final String  APPLICATION_NAME = "app.name";

  private static final String  CATEGORY_NAME = "app.category.name";

  private  int                 appUpdatedCount ;

  private  String              appName;
  private  String              catName;


  public AppRegistryUpgradeToolsCategoryPlugin(PortalContainer container, EntityManagerService entityManagerService, InitParams initParams) {
    super(initParams);
    this.container = container;
    this.entityManagerService = entityManagerService;

    if (initParams.containsKey(APPLICATION_NAME)) {
      appName = initParams.getValueParam(APPLICATION_NAME).getValue();
    }
    if (initParams.containsKey(CATEGORY_NAME)) {
      catName = initParams.getValueParam(CATEGORY_NAME).getValue();
    }
  }

  @Override
  public boolean shouldProceedToUpgrade(String newVersion,
                                        String previousGroupVersion,
                                        UpgradePluginExecutionContext previousUpgradePluginExecution) {
    int executionCount = previousUpgradePluginExecution == null ? 0 : previousUpgradePluginExecution.getExecutionCount();
    return !isExecuteOnlyOnce() || executionCount == 0;
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {

    if (StringUtils.isEmpty(appName) || StringUtils.isEmpty(catName)) {
      LOG.error("Couldn't process upgrade, all parameters are mandatory");
      return;
    }
    long startupTime = System.currentTimeMillis();

    ExoContainerContext.setCurrentContainer(container);
    boolean transactionStarted = false;

    LOG.info("Start remove of app with name  {} inside category {} ", appName, catName);
    RequestLifeCycle.begin(this.entityManagerService);
    EntityManager entityManager = this.entityManagerService.getEntityManager();
    try {
      if (!entityManager.getTransaction().isActive()) {
        entityManager.getTransaction().begin();
        transactionStarted = true;
      }
      String sqlString = "SELECT ID FROM PORTAL_APP_CATEGORIES c WHERE c.NAME = 'Tools';";
      BigInteger catId = (BigInteger) entityManager.createNativeQuery(sqlString).getSingleResult();
      String query = "DELETE FROM PORTAL_APPLICATIONS WHERE APP_NAME = :appName AND CATEGORY_ID = :catId ;";
      Query nativeQuery = entityManager.createNativeQuery(query).setParameter("appName", appName).setParameter("catId", catId);
      this.appUpdatedCount += nativeQuery.executeUpdate();
      LOG.info("End upgrade of '{}'  category with title '{}' . It took {} ms",
              getAppUpdatedCount(),
              catName,
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
    }
  }
  public int getAppUpdatedCount() {
      return appUpdatedCount;
    }
  }
