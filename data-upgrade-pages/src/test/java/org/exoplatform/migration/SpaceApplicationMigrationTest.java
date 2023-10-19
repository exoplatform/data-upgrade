package org.exoplatform.migration;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.component.test.AbstractKernelTest;
import org.exoplatform.component.test.ConfigurationUnit;
import org.exoplatform.component.test.ConfiguredBy;
import org.exoplatform.component.test.ContainerScope;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.portal.mop.navigation.NavigationContext;
import org.exoplatform.portal.mop.service.NavigationService;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.services.security.IdentityRegistry;
import org.exoplatform.services.security.MembershipEntry;
import org.exoplatform.social.core.space.*;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.gatein.pc.api.Portlet;
import org.gatein.pc.api.PortletContext;
import org.gatein.pc.api.PortletInvoker;
import org.gatein.pc.api.PortletInvokerException;
import org.gatein.pc.api.info.PortletInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;


import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
@ConfiguredBy({
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/portal/configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/exo.portal.component.portal-configuration-local.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "org/exoplatform/portal/config/conf/configuration.xml")
})
public class SpaceApplicationMigrationTest extends AbstractKernelTest {

  protected PortalContainer         container;

  protected SpaceService            spaceService;

  protected NavigationService       navigationService;

  protected IdentityRegistry        identityRegistry;

  protected EntityManagerService    entityManagerService;

  protected SettingService          settingService;

  private SpaceApplicationMigration spaceApplicationMigration;

  public SpaceApplicationMigrationTest() {
    setForceContainerReload(true);
  }

  @Before
  public void setUp() throws PortletInvokerException {
    container = getContainer();
    this.spaceService = container.getComponentInstanceOfType(SpaceService.class);
    this.navigationService = container.getComponentInstanceOfType(NavigationService.class);
    this.identityRegistry = container.getComponentInstanceOfType(IdentityRegistry.class);
    this.entityManagerService = container.getComponentInstanceOfType(EntityManagerService.class);
    this.settingService = container.getComponentInstanceOfType(SettingService.class);
    PortletInvoker portletInvoker = mock(PortletInvoker.class);
    Portlet portlet = new Portlet() {
      @Override
      public PortletContext getContext() {
        return null;
      }

      @Override
      public PortletInfo getInfo() {
        return null;
      }

      @Override
      public boolean isRemote() {
        return false;
      }
    };
    when(portletInvoker.getPortlets()).thenReturn(Collections.singleton(portlet));
    container.unregisterComponent(PortletInvoker.class);
    container.registerComponentInstance(PortletInvoker.class, portletInvoker);
    HashSet<MembershipEntry> memberships = new HashSet<MembershipEntry>();
    memberships.add(new MembershipEntry("/platform/users", "*"));
    memberships.add(new MembershipEntry("/platform/administrators", "*"));
    Identity root = new Identity("root", memberships);
    identityRegistry.register(root);
    ConversationState conversationState = new ConversationState(root);
    ConversationState.setCurrent(conversationState);

    RequestLifeCycle.begin(container);
    InitParams initParams = new InitParams();
    ValueParam valueParam = new ValueParam();
    valueParam.setName("product.group.id");
    valueParam.setValue("org.exoplatform.platform");
    ValueParam oldAppNamevalueParam = new ValueParam();
    oldAppNamevalueParam.setName("old.app.name");
    oldAppNamevalueParam.setValue("Documents");
    ValueParam oldAppIdvalueParam = new ValueParam();
    oldAppIdvalueParam.setName("old.app.id");
    oldAppIdvalueParam.setValue("FileExplorerPortlet");
    ValueParam newAppIdvalueParam = new ValueParam();
    newAppIdvalueParam.setName("new.app.id");
    newAppIdvalueParam.setValue("Documents");
    initParams.addParameter(valueParam);
    initParams.addParameter(oldAppNamevalueParam);
    initParams.addParameter(oldAppIdvalueParam);
    initParams.addParameter(newAppIdvalueParam);

    spaceApplicationMigration = new SpaceApplicationMigration(spaceService, entityManagerService, settingService, initParams);
  }

  @After
  public void tearDown() throws Exception {
    RequestLifeCycle.end();
  }

  @Test
  public void processUpgrade() throws Exception {
    Space space = new Space();
    space.setDisplayName("testspace");
    space.setPrettyName(space.getDisplayName());
    String shortName = SpaceUtils.cleanString(space.getDisplayName());
    space.setGroupId("/spaces/" + shortName);
    space.setUrl(shortName);
    space.setEditor("root");
    space.setRegistration("validation");
    space.setTemplate("classic");
    space.setVisibility("public");
    space.setPriority("2");
    String[] manager = new String[] { "root" };
    String[] members = new String[] { "root", "john" };
    space.setManagers(manager);
    space.setMembers(members);

    space = spaceService.createSpace(space, "root");
    assertTrue(SpaceUtils.isInstalledApp(space, "FileExplorerPortlet"));
    spaceApplicationMigration.processUpgrade(null, null);
    space = spaceService.getSpaceById(space.getId());
    assertFalse(SpaceUtils.isInstalledApp(space, "FileExplorerPortlet"));
    assertTrue(SpaceUtils.isInstalledApp(space, "Documents"));
  }

  @Test
  public void afterUpgrade() {
    spaceApplicationMigration.afterUpgrade();
    SettingValue<?> settingValue = settingService.get(Context.GLOBAL.id("FileExplorerPortlet:Documents"),
            Scope.APPLICATION.id("FileExplorerPortlet:Documents"),
            "SpaceApplicationMigrationEnded");
    assertEquals(true , settingValue.getValue());
  }
}
