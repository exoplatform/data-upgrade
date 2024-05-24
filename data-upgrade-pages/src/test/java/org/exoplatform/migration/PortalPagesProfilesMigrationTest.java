/*
 * Copyright (C) 2024 eXo Platform SAS.
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

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Query;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class PortalPagesProfilesMigrationTest {

  @Mock
  private PortalContainer       container;

  @Mock
  private EntityManagerService  entityManagerService;

  @Mock
  private EntityManager         entityManager;

  @Mock
  private EntityTransaction     transaction;

  @Mock
  private Query                 query;

  private PortalPagesProfilesMigration portalPagesProfilesMigration;

  @Test
  public void testProcessUpgrade() {
    InitParams initParams = new InitParams();
    ValueParam valueParam = new ValueParam();
    valueParam.setName("product.group.id");
    valueParam.setValue("org.exoplatform.platform");
    initParams.addParameter(valueParam);
    valueParam = new ValueParam();
    valueParam.setName("old.pages.profiles");
    valueParam.setValue("oldPagesProfiles");
    initParams.addParameter(valueParam);
    valueParam = new ValueParam();
    valueParam.setName("new.pages.profiles");
    valueParam.setValue("newPagesProfiles");
    initParams.addParameter(valueParam);

    portalPagesProfilesMigration = new PortalPagesProfilesMigration(container, entityManagerService, initParams);

    when(entityManagerService.getEntityManager()).thenReturn(entityManager);
    when(entityManager.getTransaction()).thenReturn(transaction);
    when(transaction.isActive()).thenReturn(false).thenReturn(true);
    when(entityManager.createNativeQuery(anyString())).thenReturn(query);
    when(query.executeUpdate()).thenReturn(10);

    // Invoke the method
    portalPagesProfilesMigration.processUpgrade("6.5", "7.0");

    // Verify the transaction management and query execution
    String expectedQuery = "UPDATE PORTAL_PAGES  SET PROFILES = 'newPagesProfiles' WHERE PROFILES = 'oldPagesProfiles' AND ID > 0;";
    verify(transaction, times(2)).isActive();
    verify(transaction).begin();
    verify(entityManager, times(1)).createNativeQuery(expectedQuery);
    verify(query).executeUpdate();
    verify(transaction, times(1)).commit();
    verify(entityManager, times(1)).flush();

    // Assert the number of updated pages
    assertEquals(10, portalPagesProfilesMigration.getPagesUpdatedCount());
  }
}
