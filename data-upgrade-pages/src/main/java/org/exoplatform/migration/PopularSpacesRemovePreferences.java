package org.exoplatform.migration;

import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import javax.persistence.EntityManager;
import javax.persistence.Query;

public class PopularSpacesRemovePreferences extends UpgradeProductPlugin {

    private static final Log LOG                              = ExoLogger.getExoLogger(PopularSpacesRemovePreferences.class);

    private static final String APPLICATION_CONTENT_ID        = "gamification-portlets/PopularSpaces";

    private EntityManagerService entityManagerService;

    private boolean portletUpdated = false;

    public PopularSpacesRemovePreferences(EntityManagerService entityManagerService, InitParams initParams) {
        super(initParams);
        this.entityManagerService = entityManagerService;
    }

    @Override
    public void processUpgrade(String oldVersion, String newVersion) {
        LOG.info("Start upgrade of Popular spaces portlet: {}", APPLICATION_CONTENT_ID);
        long startupTime = System.currentTimeMillis();
        boolean transactionStarted = false;

        PortalContainer container = PortalContainer.getInstance();
        RequestLifeCycle.begin(container);
        EntityManager entityManager = this.entityManagerService.getEntityManager();
        try {
            if (!entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().begin();
                transactionStarted = true;
            }
            String sqlString = "UPDATE PORTAL_WINDOWS w SET w.CUSTOMIZATION = NULL WHERE w.CONTENT_ID = '" + APPLICATION_CONTENT_ID + "'";
            Query nativeQuery = entityManager.createNativeQuery(sqlString);
            int update = nativeQuery.executeUpdate();
            if (update != 0) {
                LOG.info("Popular spaces portlet preferences settings removed successfully");
                this.portletUpdated = true;
            }
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
        LOG.info("End upgrade of Popular spaces portlet. It took {} ms", (System.currentTimeMillis() - startupTime));
    }

    public boolean isPortletUpdated() {
        return portletUpdated;
    }
}
