package org.exoplatform.migration;

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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;


import java.util.HashSet;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
@ConfiguredBy({ @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/portal/configuration.xml") })
public class SpaceApplicationMigrationTest {

  protected PortalContainer container;

  protected SpaceService    spaceService;

  protected NavigationService navigationService;

  protected IdentityRegistry identityRegistry;

  @Before
  public void setUp() {
    container = PortalContainer.getInstance();
    this.spaceService = container.getComponentInstanceOfType(SpaceService.class);
    this.navigationService = container.getComponentInstanceOfType(NavigationService.class);
    this.identityRegistry = container.getComponentInstanceOfType(IdentityRegistry.class);

    HashSet<MembershipEntry> memberships = new HashSet<MembershipEntry>();
    memberships.add(new MembershipEntry("/platform/users", "*"));
    memberships.add(new MembershipEntry("/platform/administrators", "*"));
    Identity root = new Identity("root", memberships);
    identityRegistry.register(root);
    ConversationState conversationState = new ConversationState(root);
    ConversationState.setCurrent(conversationState);
  }

  @After
  public void tearDown() throws Exception {
    RequestLifeCycle.end();
  }

  @Test
  public void processUpgrade() throws Exception {
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
    SpaceApplicationMigration spaceApplicationMigration = new SpaceApplicationMigration(spaceService, initParams);
    spaceApplicationMigration.processUpgrade(null, null);
    space = spaceService.getSpaceById(space.getId());
    assertFalse(SpaceUtils.isInstalledApp(space, "FileExplorerPortlet"));
    assertTrue(SpaceUtils.isInstalledApp(space, "Documents"));
  }
}
