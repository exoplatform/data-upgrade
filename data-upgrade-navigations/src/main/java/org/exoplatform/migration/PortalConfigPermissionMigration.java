package org.exoplatform.migration;

import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.portal.jdbc.entity.PermissionEntity;
import org.exoplatform.portal.jdbc.entity.SiteEntity;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import java.util.List;

public class PortalConfigPermissionMigration  extends UpgradeProductPlugin {
    private final PortalContainer      container;

    private final EntityManagerService entityManagerService;
    private static final Log LOG                        = ExoLogger.getExoLogger(PortalConfigPermissionMigration.class);

    public PortalConfigPermissionMigration(PortalContainer container,
                                    EntityManagerService entityManagerService,
                                    InitParams initParams) {
        super(initParams);
        this.container = container;
        this.entityManagerService = entityManagerService;

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

                updateNativeQuery.executeUpdate();
                LOG.info("Update permission from {} to {} for portalConfig {}",permissionEntity.getPermission(), permission, name);

            });


            LOG.info("End upgrade of of permission for portalConfiguration. It took {} ms",
                    (System.currentTimeMillis() - startupTime));

            if (transactionStarted && entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().commit();
            }

        } catch (Exception e) {
            if (transactionStarted && entityManager.getTransaction().isActive() && entityManager.getTransaction().getRollbackOnly()) {
                entityManager.getTransaction().rollback();
            }
            throw new RuntimeException("Unable to update PortalSite",e);
        } finally {
            RequestLifeCycle.end();
        }
    }
}
