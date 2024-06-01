package org.exoplatform.migration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.social.core.space.SpaceFilter;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import jakarta.persistence.Query;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SpaceApplicationMigrationTest {

  @Mock
  private SpaceService                               spaceService;

  @Mock
  private EntityManagerService                       entityManagerService;

  @Mock
  private SettingService                             settingService;

  @Mock
  private PortalContainer                            rootContainer;

  @Mock
  private EntityManager                              entityManager;

  private SpaceApplicationMigration                  spaceApplicationMigration;

  private static final MockedStatic<PortalContainer> portalContainerMockedStatic = mockStatic(PortalContainer.class);

  @Before
  public void setUp() {
    // Initialize InitParams without mocking
    InitParams initParams = new InitParams();
    ValueParam valueParam = new ValueParam();
    valueParam.setName("product.group.id");
    valueParam.setValue("org.exoplatform.platform");
    initParams.addParameter(valueParam);

    ValueParam oldAppNameParam = new ValueParam();
    oldAppNameParam.setName("old.app.name");
    oldAppNameParam.setValue("oldAppName");

    ValueParam oldAppIdParam = new ValueParam();
    oldAppIdParam.setName("old.app.id");
    oldAppIdParam.setValue("oldAppId");

    ValueParam newAppIdParam = new ValueParam();
    newAppIdParam.setName("new.app.id");
    newAppIdParam.setValue("newAppId");

    initParams.addParam(oldAppNameParam);
    initParams.addParam(oldAppIdParam);
    initParams.addParam(newAppIdParam);

    this.spaceApplicationMigration =
                                   new SpaceApplicationMigration(spaceService, entityManagerService, settingService, initParams);
  }

  @After
  public void tearDown() {
    if (!portalContainerMockedStatic.isClosed()) {
      portalContainerMockedStatic.close();
    }
  }

  @Test
  public void testProcessUpgrade() throws Exception {

    Space space = mock(Space.class);
    when(space.getApp()).thenReturn("app");
    Space[] spacesArray = new Space[] { space };
    ListAccess<Space> listAccessMock = mock(ListAccess.class);

    portalContainerMockedStatic.when(PortalContainer::getInstance).thenReturn(rootContainer);
    when(spaceService.getAllSpacesByFilter(any(SpaceFilter.class))).thenReturn(listAccessMock);
    when(listAccessMock.load(anyInt(), anyInt())).thenReturn(spacesArray);
    when(listAccessMock.getSize()).thenReturn(spacesArray.length);
    when(space.getGroupId()).thenReturn("groupId");
    when(space.getId()).thenReturn("spaceId");
    when(space.getDisplayName()).thenReturn("Space Display Name");

    Query queryMock = mock(Query.class);
    when(entityManagerService.getEntityManager()).thenReturn(entityManager);
    EntityTransaction transaction = mock(EntityTransaction.class);
    when(entityManager.getTransaction()).thenReturn(transaction);
    when(transaction.isActive()).thenReturn(true);
    when(entityManager.createNativeQuery(anyString())).thenReturn(queryMock);

    spaceApplicationMigration.processUpgrade("newVersion", "previousGroupVersion");

    // verify remove old application
    verify(entityManager,
           times(1)).createNativeQuery(eq("DELETE FROM PORTAL_PAGES WHERE SITE_ID IN (SELECT ID FROM PORTAL_SITES WHERE NAME ='groupId') AND NAME='oldAppId'"));
    verify(entityManager, times(1)).createNativeQuery(eq("DELETE FROM SOC_APPS WHERE APP_ID='oldAppId' AND SPACE_ID= 'spaceId'"));
    verify(queryMock, times(2)).executeUpdate();

    // verify install new application
    verify(spaceService, times(1)).installApplication(any(Space.class), eq("newAppId"));
    verify(spaceService, times(1)).activateApplication(any(Space.class), eq("newAppId"));

  }

  @Test
  public void testAfterUpgrade() {
    spaceApplicationMigration.afterUpgrade();
    verify(settingService, times(1)).set(any(), any(), eq("SpaceApplicationMigrationEnded"), any());
  }

}
