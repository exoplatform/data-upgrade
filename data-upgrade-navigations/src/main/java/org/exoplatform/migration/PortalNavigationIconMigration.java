/*
 * Copyright (C) 2003-2024 eXo Platform SAS
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
package org.exoplatform.migration;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * This plugin will be executed in order to set icons for the portal navigation
 * nodes as provided in the configuration
 */
public class PortalNavigationIconMigration extends UpgradeProductPlugin {

  private static final Log           LOG                  = ExoLogger.getExoLogger(PortalNavigationIconMigration.class);

  private static final String        ICON_UPDATE_SQL      =
                                                     """
                                                           UPDATE PORTAL_NAVIGATION_NODES
                                                           SET ICON =
                                                             CASE
                                                               %s
                                                             END
                                                           WHERE ICON IS NULL
                                                           AND EXISTS (SELECT * FROM PORTAL_PAGES p INNER JOIN PORTAL_SITES s ON s.ID = p.SITE_ID WHERE PAGE_ID = p.ID AND s.TYPE = 0 AND s.NAME LIKE 'dw')
                                                         """;

  private static final String        ICON_UPDATE_CASE_SQL = """
         WHEN NAME in (%s) THEN TRIM('%s')
      """;

  private static final String        PORTAL_NODE_NAMES    = "portal.node.names";

  private static final String        PORTAL_NODE_ICONS    = "portal.node.icons";

  private final EntityManagerService entityManagerService;

  private final Map<String, String>  portalNodes          = new HashMap<>();

  private int                        migratedPortalNodeIcons;

  public PortalNavigationIconMigration(EntityManagerService entityManagerService, InitParams initParams) {
    super(initParams);
    this.entityManagerService = entityManagerService;
    if (initParams.containsKey(PORTAL_NODE_ICONS) && initParams.containsKey(PORTAL_NODE_NAMES)) {
      String[] portalNodeNames = initParams.getValueParam(PORTAL_NODE_NAMES).getValue().split(";");
      String[] portalNodeIcons = initParams.getValueParam(PORTAL_NODE_ICONS).getValue().split(";");
      if (portalNodeIcons.length == portalNodeNames.length) {
        for (int i = 0; i < portalNodeNames.length; i++) {
          this.portalNodes.put(portalNodeNames[i], portalNodeIcons[i]);
        }
      }
    }
  }

  @Override
  public boolean shouldProceedToUpgrade(String newVersion, String previousVersion) {
    return !portalNodes.isEmpty();
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {

    long startupTime = System.currentTimeMillis();

    LOG.info("Start:: Upgrade of portal navigation node icons");
    Set<Map.Entry<String, String>> portalNodesEntrySet = portalNodes.entrySet();
    this.migratedPortalNodeIcons = upgradePortalNodeIcons(portalNodesEntrySet);
    LOG.info("End:: Upgrade of '{}' node icons. It tooks {} ms",
             migratedPortalNodeIcons,
             (System.currentTimeMillis() - startupTime));
  }

  @ExoTransactional
  public int upgradePortalNodeIcons(Set<Map.Entry<String, String>> portalNodesEntrySet) {
    EntityManager entityManager = entityManagerService.getEntityManager();

    String sqlStatement = String.format(ICON_UPDATE_SQL, portalNodes.entrySet().stream().map(e -> {
      String keys = Arrays.stream(e.getKey().split(",")).map(key -> String.format("'%s'", key)).collect(Collectors.joining(","));
      return String.format(ICON_UPDATE_CASE_SQL, keys, e.getValue());
    }).collect(Collectors.joining()));
    boolean transactionStarted = false;
    try {
      if (!entityManager.getTransaction().isActive()) {
        entityManager.getTransaction().begin();
        transactionStarted = true;
      }
      Query query = entityManager.createNativeQuery(sqlStatement);
      return query.executeUpdate();
    } catch (Exception e) {
      if (transactionStarted && entityManager.getTransaction().isActive() && entityManager.getTransaction().getRollbackOnly()) {
        entityManager.getTransaction().rollback();
      }
      return 0;
    }
  }

  public int getMigratedPortalNodeIconsNodeIcons() {
    return migratedPortalNodeIcons;
  }
}
