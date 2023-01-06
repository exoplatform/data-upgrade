/*
 * Copyright (C) 2022 eXo Platform SAS.
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
import java.util.Map;

import org.apache.commons.collections4.ListUtils;

import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.news.service.NewsTargetingService;
import org.exoplatform.news.utils.NewsUtils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.metadata.storage.MetadataStorage;
import org.exoplatform.social.metadata.MetadataService;
import org.exoplatform.social.metadata.model.Metadata;

public class TargetsDefaultPermissionUpgrade extends UpgradeProductPlugin {

  private static final Log LOG                                     =
                               ExoLogger.getLogger(TargetsDefaultPermissionUpgrade.class.getName());

  private MetadataService  metadataService;

  private MetadataStorage  metadataStorage;

  private int              migratedNoDefaultPermissionTargetsCount = 0;

  public TargetsDefaultPermissionUpgrade(InitParams initParams,
                                         MetadataService metadataService,
                                         MetadataStorage metadataStorage) {
    super(initParams);
    this.metadataService = metadataService;
    this.metadataStorage = metadataStorage;
  }

  public int getMigratedNoDefaultPermissionTargetsCount() {
    return migratedNoDefaultPermissionTargetsCount;
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    long startupTime = System.currentTimeMillis();
    LOG.info("Start targets migration");
    List<Metadata> noDefaultPermissionTargets = metadataService.getMetadatas(NewsTargetingService.METADATA_TYPE.getName(), 0)
                                                               .stream()
                                                               .filter(target -> target.getProperties()
                                                                                       .get(NewsUtils.TARGET_PERMISSIONS) == null)
                                                               .toList();

    int totalNoDefaultPermissionTargetsCount = noDefaultPermissionTargets.size();
    LOG.info("Total number of no default permission targets to be migrated: {}", totalNoDefaultPermissionTargetsCount);
    int notMigratedNoDefaultPermissionTargetsCount = 0;
    int processedNoDefaultPermissionTargetsCount = 0;
    for (List<Metadata> noDefaultPermissionTargetsChunk : ListUtils.partition(noDefaultPermissionTargets, 10)) {
      int notMigratedNoDefaultPermissionTargetsCountByTransaction =
                                                                  manageNoDefaultPermissionTargets(noDefaultPermissionTargetsChunk);
      int processedNoDefaultPermissionTargetsCountByTransaction = noDefaultPermissionTargetsChunk.size();
      processedNoDefaultPermissionTargetsCount += processedNoDefaultPermissionTargetsCountByTransaction;
      migratedNoDefaultPermissionTargetsCount += processedNoDefaultPermissionTargetsCountByTransaction
          - notMigratedNoDefaultPermissionTargetsCountByTransaction;
      notMigratedNoDefaultPermissionTargetsCount += notMigratedNoDefaultPermissionTargetsCountByTransaction;
      LOG.info("No default permission targets migration progress: processed={}/{} succeeded={} error={}",
               processedNoDefaultPermissionTargetsCount,
               totalNoDefaultPermissionTargetsCount,
               migratedNoDefaultPermissionTargetsCount,
               notMigratedNoDefaultPermissionTargetsCount);
    }
    if (notMigratedNoDefaultPermissionTargetsCount == 0) {
      LOG.info("End no default permission targets successful migration: total={} succeeded={} error={}. It tooks {} ms.",
               totalNoDefaultPermissionTargetsCount,
               migratedNoDefaultPermissionTargetsCount,
               notMigratedNoDefaultPermissionTargetsCount,
               (System.currentTimeMillis() - startupTime));
    } else {
      LOG.warn("End no default permission targets migration with some errors: total={} succeeded={} error={}. It tooks {} ms."
          + "The not migrated no default permission targets will be processed again next startup.",
               totalNoDefaultPermissionTargetsCount,
               migratedNoDefaultPermissionTargetsCount,
               notMigratedNoDefaultPermissionTargetsCount,
               (System.currentTimeMillis() - startupTime));
      throw new IllegalStateException("Some no default permission targets wasn't executed successfully. It will be re-attempted next startup");
    }
  }

  public int manageNoDefaultPermissionTargets(List<Metadata> noDefaultPermissionTargets) {
    int notMigratedNoDefaultPermissionTargetsCount = 0;
    for (Metadata noDefaultPermissionTarget : noDefaultPermissionTargets) {
      try {
        Map<String, String> noDefaultPermissionTargetProperties = noDefaultPermissionTarget.getProperties();
        noDefaultPermissionTargetProperties.put(NewsUtils.TARGET_PERMISSIONS, "/platform/web-contributors");
        noDefaultPermissionTarget.setProperties(noDefaultPermissionTargetProperties);
        metadataStorage.updateMetadata(noDefaultPermissionTarget);
      } catch (Exception e) {
        LOG.warn("Error migrating no default permission targets with id {}. Continue to migrate other items",
                 noDefaultPermissionTarget.getId(),
                 e);
        notMigratedNoDefaultPermissionTargetsCount++;
      }
    }
    return notMigratedNoDefaultPermissionTargetsCount;
  }
}
