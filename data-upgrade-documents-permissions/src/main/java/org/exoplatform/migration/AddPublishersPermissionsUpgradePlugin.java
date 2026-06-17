/*
 * Copyright (C) 2026 eXo Platform SAS.
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

import io.meeds.social.space.constant.SpaceMembershipStatus;
import jakarta.persistence.TypedQuery;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.upgrade.UpgradePluginException;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import io.meeds.common.ContainerTransactional;

import jakarta.persistence.EntityManager;

import java.util.*;

public class AddPublishersPermissionsUpgradePlugin extends UpgradeProductPlugin {

  private static final Log     LOG                  = ExoLogger.getExoLogger(AddPublishersPermissionsUpgradePlugin.class);

  private static final String  PLUGIN_NAME          = "PublishersPermissionsUpgradePlugin";

  private static final String  COMPLETED_SPACES_KEY = "completedSpaceIds";

  private SpaceService         spaceService;

  private DocumentFileService  documentFileService;

  private EntityManagerService entityManagerService;

  private SettingService       settingService;

  public AddPublishersPermissionsUpgradePlugin(InitParams initParams,
                                               SpaceService spaceService,
                                               DocumentFileService documentFileService,
                                               EntityManagerService entityManagerService,
                                               SettingService settingService) {
    super(initParams);
    this.spaceService = spaceService;
    this.documentFileService = documentFileService;
    this.entityManagerService = entityManagerService;
    this.settingService = settingService;
  }

  @Override
  @ContainerTransactional
  public void processUpgrade(String oldVersion, String newVersion) {
    long startupTime = System.currentTimeMillis();
    LOG.info("Start Upgrade of redactional spaces documents");
    int migratedCount = 0;
    int errorCount = 0;
    int alreadyCompletedCount = 0;
    int totalCount;
    Set<String> completedSpaceIds = getCompletedSpaceIds();
    try {
      List<Long> spaceIds = getRedactionalSpaces();
      if (spaceIds.isEmpty()) {
        return;
      }
      totalCount = spaceIds.size();
      List<Long> remainingSpaces = new ArrayList<>();
      for (Long id : spaceIds) {
        if (!completedSpaceIds.contains(id.toString())) {
          remainingSpaces.add(id);
        } else {
          alreadyCompletedCount++;
        }
      }

      if (remainingSpaces.isEmpty()) {
        LOG.info("All redactional spaces were already migrated");
        return;
      }

      LOG.info("Total redactional spaces: {}, remaining to migrate: {}", totalCount, remainingSpaces.size());
      for (Long spaceId : remainingSpaces) {
        try {
          Space space = spaceService.getSpaceById(spaceId.toString());
          LOG.info("Migrating redactional space documents with id '{}'", spaceId);
          documentFileService.synchronizeSpacePermissions(space);
          migratedCount++;
          completedSpaceIds.add(spaceId.toString());
        } catch (Exception e) {
          errorCount++;
          LOG.warn("Error migrating redactional space documents with id '{}'. Continue to migrate other items", spaceId, e);
          continue;
        }
        saveCompletedSpaceIds(completedSpaceIds);
      }
    } catch (Exception e) {
      throw new UpgradePluginException("An error occurred when upgrading redactional spaces documents:", e);
    }
    if (alreadyCompletedCount + migratedCount == totalCount) {
      settingService.remove(Context.GLOBAL.id(PLUGIN_NAME), Scope.APPLICATION.id(PLUGIN_NAME), COMPLETED_SPACES_KEY);
      LOG.info("End redactional spaces documents migration successful migration: total={} succeeded={} error={}. It tooks {} ms.",
               totalCount,
               migratedCount,
               errorCount,
               (System.currentTimeMillis() - startupTime));
    } else {
      throw new UpgradePluginException("Some redactional spaces documents wasn't executed successfully. It will be re-attempted next startup");
    }
  }

  public List<Long> getRedactionalSpaces() {
    EntityManager entityManager = entityManagerService.getEntityManager();
    String selectQuery = "SELECT DISTINCT sm.space.id FROM SocSpaceMember sm "
                         + "WHERE sm.status = :status";
    TypedQuery<Long> query = entityManager.createQuery(selectQuery, Long.class);
    query.setParameter("status", SpaceMembershipStatus.REDACTOR);
    return query.getResultList();
  }

  private Set<String> getCompletedSpaceIds() {
    SettingValue<?> value = settingService.get(Context.GLOBAL.id(PLUGIN_NAME),
                                               Scope.APPLICATION.id(PLUGIN_NAME),
                                               COMPLETED_SPACES_KEY);
    if (value == null || value.getValue() == null) {
      return new HashSet<>();
    }
    String csv = (String) value.getValue();
    if (StringUtils.isBlank(csv)) {
      return new HashSet<>();
    }
    return new HashSet<>(Arrays.asList(csv.split(",")));
  }

  private void saveCompletedSpaceIds(Set<String> completedIds) {
    String csv = String.join(",", completedIds);
    settingService.set(Context.GLOBAL.id(PLUGIN_NAME),
                       Scope.APPLICATION.id(PLUGIN_NAME),
                       COMPLETED_SPACES_KEY,
                       SettingValue.create(csv));
  }
}
