package org.exoplatform.migration;

import com.google.api.client.util.DateTime;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.search.index.IndexingService;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.migration.UsersLastLoginTimeMigration;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.organization.UserHandler;
import org.exoplatform.services.organization.idm.UserImpl;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.profile.ProfileFilter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ CommonsUtils.class })
@PowerMockIgnore({ "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*", "javax.management.*" })
public class UsersLastLoginTimeMigrationTest {

  @Test
  public void testUpgradeLastLoginTime() {
    try {
      InitParams initParams = new InitParams();
      ValueParam valueParam = new ValueParam();
      valueParam.setName("product.group.id");
      valueParam.setValue("org.exoplatform.users");
      initParams.addParameter(valueParam);

      Date today = new Date();
      Date tomorrow = new Date(today.getTime() + (1000 * 60 * 60 * 24));

      User user1 = new UserImpl();
      user1.setUserName("user1");
      user1.setCreatedDate(new Date());
      user1.setLastLoginTime(user1.getCreatedDate());

      User user2 = new UserImpl();
      user2.setUserName("user2");
      user2.setCreatedDate(today);
      user2.setLastLoginTime(tomorrow);

      User user3 = new UserImpl();
      user3.setUserName("user3");
      user3.setCreatedDate(today);
      user3.setLastLoginTime(tomorrow);

      Profile profile1 = new Profile();
      profile1.setId("profile1");
      Profile profile2 = new Profile();
      profile2.setId("profile2");
      Profile profile3 = new Profile();
      profile3.setId("profile3");

      Identity identity1 = new Identity();
      identity1.setId("1");
      identity1.setRemoteId("user1");
      identity1.setProfile(profile1);

      Identity identity2 = new Identity();
      identity2.setId("2");
      identity2.setRemoteId("user2");
      identity2.setProfile(profile2);

      Identity identity3 = new Identity();
      identity3.setId("3");
      identity3.setRemoteId("user3");
      identity3.setProfile(profile3);

      ListAccess<Identity> listIdentity = new ListAccess<Identity>() {
        @Override
        public Identity[] load(int i, int i1) throws Exception, IllegalArgumentException {
          return new Identity[] { identity1, identity2, identity3 };
        }

        @Override
        public int getSize() throws Exception {
          return 0;
        }
      };

      OrganizationService organizationService = Mockito.mock(OrganizationService.class);
      IdentityManager identityManager = Mockito.mock(IdentityManager.class);
      UserHandler userHandler = Mockito.mock(UserHandler.class);
      IndexingService indexingService = Mockito.mock(IndexingService.class);
      PowerMockito.mockStatic(CommonsUtils.class);
      when(CommonsUtils.getService(IndexingService.class)).thenReturn(indexingService);

      when(userHandler.findUserByName("user1")).thenReturn(user1);
      when(userHandler.findUserByName("user2")).thenReturn(user2);
      when(userHandler.findUserByName("user3")).thenReturn(user3);
      when(organizationService.getUserHandler()).thenReturn(userHandler);

      UsersLastLoginTimeMigration usersLastLoginTimeMigration = new UsersLastLoginTimeMigration(organizationService,
                                                                                                identityManager,
                                                                                                initParams);

      assertNotEquals(user1.getLastLoginTime(), identity1.getProfile().getProperty(Profile.LAST_LOGIN_TIME));
      assertNotEquals(user2.getLastLoginTime(), identity2.getProfile().getProperty(Profile.LAST_LOGIN_TIME));
      assertNotEquals(user3.getLastLoginTime(), identity3.getProfile().getProperty(Profile.LAST_LOGIN_TIME));

      List<Identity> identities = null;
      identities = Arrays.asList(listIdentity.load(0, 3));
      usersLastLoginTimeMigration.updateLastLoginTime(3,0,identities);
      verify(indexingService, times(2)).reindex(any(), any());

      assertNotEquals(user1.getLastLoginTime(), identity1.getProfile().getProperty(Profile.LAST_LOGIN_TIME));
      assertEquals(user2.getLastLoginTime(), identity2.getProfile().getProperty(Profile.LAST_LOGIN_TIME));
      assertEquals(user3.getLastLoginTime(), identity3.getProfile().getProperty(Profile.LAST_LOGIN_TIME));
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

}
