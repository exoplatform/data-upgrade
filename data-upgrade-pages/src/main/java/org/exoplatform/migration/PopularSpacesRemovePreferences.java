/*
 * Copyright (C) 2021 eXo Platform SAS.
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
