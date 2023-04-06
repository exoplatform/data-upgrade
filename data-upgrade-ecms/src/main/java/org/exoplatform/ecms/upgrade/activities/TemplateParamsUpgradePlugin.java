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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.ecms.upgrade.activities;

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

import javax.persistence.EntityManager;
import javax.persistence.Query;

public class TemplateParamsUpgradePlugin extends UpgradeProductPlugin {
  private static final Log LOG = ExoLogger.getExoLogger(TemplateParamsUpgradePlugin.class);

  private final PortalContainer container;

  private final EntityManagerService entityManagerService;

  private static final String  OLD_TEMPLATE_PARAMS_KEY = "old.template.params.key";

  private static final String  NEW_TEMPLATE_PARAMS_KEY = "new.template.params.key";

  private int                  templatePramasUpdatedCount;

  private String                oldTemplateParamskey;

  private String                newTemplateParamskey;

  public TemplateParamsUpgradePlugin(PortalContainer container, EntityManagerService entityManagerService, InitParams initParams) {
    super(initParams);
    this.container = container;
    this.entityManagerService = entityManagerService;

    if (initParams.containsKey(OLD_TEMPLATE_PARAMS_KEY)) {
      oldTemplateParamskey = initParams.getValueParam(OLD_TEMPLATE_PARAMS_KEY).getValue();
    }
    if (initParams.containsKey(NEW_TEMPLATE_PARAMS_KEY)) {
      newTemplateParamskey = initParams.getValueParam(NEW_TEMPLATE_PARAMS_KEY).getValue();
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
    if (StringUtils.isEmpty(oldTemplateParamskey)||StringUtils.isEmpty(newTemplateParamskey)) {
      LOG.error("Couldn't process upgrade, all parameters are mandatory");
      return;
    }
    long startupTime = System.currentTimeMillis();

    ExoContainerContext.setCurrentContainer(container);
    boolean transactionStarted = false;

    LOG.info("Start upgrade of ACTIVITY_TEMPLATE_PARAMS with key {} to use {}", oldTemplateParamskey,newTemplateParamskey);
    RequestLifeCycle.begin(this.entityManagerService);
    EntityManager entityManager = this.entityManagerService.getEntityManager();
    try {
      if (!entityManager.getTransaction().isActive()) {
        entityManager.getTransaction().begin();
        transactionStarted = true;
      }

      String sqlString = "UPDATE SOC_ACTIVITY_TEMPLATE_PARAMS SET TEMPLATE_PARAM_KEY = TRIM(TEMPLATE_PARAM_KEY) WHERE TEMPLATE_PARAM_KEY LIKE '"+oldTemplateParamskey+"'";
      Query nativeQuery = entityManager.createNativeQuery(sqlString);
      templatePramasUpdatedCount = nativeQuery.executeUpdate();
      LOG.info("End upgrade of '{}' ACTIVITY_TEMPLATE_PARAMS with key {} to use {}. It took {} ms",
               templatePramasUpdatedCount,
               oldTemplateParamskey,
               newTemplateParamskey,
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
  public int getTemplatePramasUpdatedCount() {
    return templatePramasUpdatedCount;
  }

}
