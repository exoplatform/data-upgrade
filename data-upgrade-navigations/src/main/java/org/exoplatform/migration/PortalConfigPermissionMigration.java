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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.migration;

import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.portal.jdbc.entity.PermissionEntity;
import org.exoplatform.portal.jdbc.entity.SiteEntity;
import org.exoplatform.portal.mop.storage.cache.CacheDescriptionStorage;
import org.exoplatform.portal.mop.storage.cache.CacheNavigationStorage;
import org.exoplatform.services.cache.CacheService;
import org.exoplatform.services.cache.ExoCache;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import java.io.Serializable;
import java.util.List;

public class PortalConfigPermissionMigration  extends UpgradeProductPlugin {
    private final PortalContainer      container;

    private final EntityManagerService entityManagerService;

    int pagesNodesCount;

    private CacheService cacheService;

    private static final Log LOG                        = ExoLogger.getExoLogger(PortalConfigPermissionMigration.class);

    public PortalConfigPermissionMigration(PortalContainer container,
                                    EntityManagerService entityManagerService,
                                    CacheService cacheService,
                                    InitParams initParams) {
        super(initParams);
        this.container = container;
        this.entityManagerService = entityManagerService;
        this.cacheService = cacheService;
        pagesNodesCount=0;
    }
    @Override
    public void processUpgrade(String s, String s1) {
        long startupTime = System.currentTimeMillis();

        ExoContainerContext.setCurrentContainer(container);
        boolean transactionStarted = false;

        LOG.info("Start upgrade of permission for portalConfiguration");
        RequestLifeCycle.begin(this.entityManagerService);
        EntityManager entityManager = this.entityManagerService.getEntityManager();
        try {
            if (!entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().begin();
                transactionStarted = true;
            }
            String sqlString1 ="SELECT p FROM GateInPermission p WHERE p.permission LIKE '%@owner@%'";
            TypedQuery<PermissionEntity> nativeQuery1= entityManager.createQuery(sqlString1, PermissionEntity.class);
            List<PermissionEntity> resultList = nativeQuery1.getResultList();

            resultList.forEach(permissionEntity -> {
                Long portalConfigId = permissionEntity.getReferenceId();
                String sqlString2 ="SELECT s FROM GateInSite s where s.id=:siteId";
                TypedQuery<SiteEntity> nativeQuery2 = entityManager.createQuery(sqlString2, SiteEntity.class);
                nativeQuery2.setParameter("siteId",portalConfigId);
                SiteEntity site = nativeQuery2.getSingleResult();
                String name = site.getName();

                String permission = permissionEntity.getPermission().replace("@owner@", name);

                String updateQuery = "UPDATE PORTAL_PERMISSIONS SET PERMISSION = :permission WHERE PERMISSION_ID=:permissionId";
                Query updateNativeQuery = entityManager.createNativeQuery(updateQuery);
                updateNativeQuery.setParameter("permission",permission);
                updateNativeQuery.setParameter("permissionId",permissionEntity.getId());

                this.pagesNodesCount += updateNativeQuery.executeUpdate();
                LOG.info("Update permission from {} to {} for portalConfig {}",permissionEntity.getPermission(), permission, name);

            });


            LOG.info("End upgrade of of permission for portalConfiguration. It took {} ms",
                    (System.currentTimeMillis() - startupTime));

            if (transactionStarted && entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().commit();
            }
            clearNavigationCache();

        } catch (Exception e) {
            if (transactionStarted && entityManager.getTransaction().isActive() && entityManager.getTransaction().getRollbackOnly()) {
                entityManager.getTransaction().rollback();
            }
            throw new RuntimeException("Unable to update PortalSite",e);
        } finally {
            RequestLifeCycle.end();
        }
    }
    private void clearNavigationCache() {
        if (pagesNodesCount > 0) {

            ExoCache<? extends Serializable, ?> navigationCache = this.cacheService.getCacheInstance(CacheNavigationStorage.NAVIGATION_CACHE_NAME);
            if (navigationCache != null) {
                navigationCache.clearCache();
            }

            ExoCache<? extends Serializable, ?> descriptionCache = this.cacheService.getCacheInstance(CacheDescriptionStorage.DESCRIPTION_CACHE_NAME);
            if (descriptionCache != null) {
                descriptionCache.clearCache();
            }
        }
    }
}
