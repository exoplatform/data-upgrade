package org.exoplatform.migration;

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

public class PagePropertyMigration extends UpgradeProductPlugin {

    private static final Log LOG                        = ExoLogger.getExoLogger(PagesMigration.class);

    private static final String  PAGE_NAME    = "page.name";
    private static final String  PAGE_PROPERTY_NAME    = "page.property.name";

    private static final String  NEW_PAGE_PROPERTY_VALUE = "new.page.property.value";

    private PortalContainer      container;

    private EntityManagerService entityManagerService;

    private String pagePropertyName;

    private String pagePropertyValue;

    private String pageName;

    private int                  pagesUpdatedCount;


    public PagePropertyMigration(PortalContainer container,
                          EntityManagerService entityManagerService,
                          InitParams initParams) {
        super(initParams);
        this.container = container;
        this.entityManagerService = entityManagerService;

        if (initParams.containsKey(PAGE_NAME)) {
            this.pageName = initParams.getValueParam(PAGE_NAME).getValue();
        }

        if (initParams.containsKey(PAGE_PROPERTY_NAME)) {
             this.pagePropertyName = initParams.getValueParam(PAGE_PROPERTY_NAME).getValue();
            if (initParams.containsKey(NEW_PAGE_PROPERTY_VALUE)) {
                this.pagePropertyValue = initParams.getValueParam(NEW_PAGE_PROPERTY_VALUE).getValue();
            }
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
        if (pageName == null || pageName.isEmpty() || pagePropertyName == null || pagePropertyName.isEmpty() || pagePropertyValue == null || pagePropertyValue.isEmpty()) {
            LOG.error("Couldn't process upgrade, the parameter '{}' is mandatory", PAGE_PROPERTY_NAME);
            return;
        }

        long startupTime = System.currentTimeMillis();

        ExoContainerContext.setCurrentContainer(container);
        boolean transactionStarted = false;

        RequestLifeCycle.begin(this.entityManagerService);
        EntityManager entityManager = this.entityManagerService.getEntityManager();
        try {
            if (!entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().begin();
                transactionStarted = true;
            }

            String sqlString = "UPDATE PORTAL_PAGES  SET " + pagePropertyName + " = " + pagePropertyValue
                    + " WHERE NAME = '" + pageName + "';";
            Query nativeQuery = entityManager.createNativeQuery(sqlString);
            this.pagesUpdatedCount = nativeQuery.executeUpdate();
            LOG.info("End upgrade of '{}' pages with property name '{}' to use '{}' as new value. It took {} ms",
                    pagesUpdatedCount,
                    pagePropertyName,
                    pagePropertyValue,
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
