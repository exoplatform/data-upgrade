package org.exoplatform.migration;

import org.exoplatform.commons.serialization.serial.ObjectReader;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.Group;
import org.exoplatform.services.organization.Membership;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.organization.UserProfile;

import java.util.Arrays;

public class UserSetExternalInGateinPortal extends UpgradeProductPlugin {
  private static final Log LOG = ExoLogger.getExoLogger(UsersLastLoginTimeMigration.class);

  OrganizationService organizationService;
  public UserSetExternalInGateinPortal(OrganizationService organizationService,InitParams initParams) {
    super(initParams);
    this.organizationService=organizationService;

  }
  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    LOG.info("Start upgrade process to add external info in gatein user profile");
    long startupTime = System.currentTimeMillis();
    try {
      Group group = organizationService.getGroupHandler().findGroupById("/platform/externals");
      ListAccess<Membership> externalsMemberships = organizationService.getMembershipHandler().findAllMembershipsByGroup(group);
      int total = externalsMemberships.getSize();
      LOG.info("Number of users to update : " + total);

      int pageSize = 100;
      int current=0;
      while (current<total) {
        RequestLifeCycle.begin(ExoContainerContext.getCurrentContainer());

        Membership[] currentBatch = externalsMemberships.load(0,pageSize);
        Arrays.stream(currentBatch).forEach(membership -> {
          long startTimeForUser = System.currentTimeMillis();
          String username = membership.getUserName();
          try {
            UserProfile profile = organizationService.getUserProfileHandler().findUserProfileByName(username);
            if (profile==null) {
              profile=organizationService.getUserProfileHandler().createUserProfileInstance(username);
            }
            profile.setAttribute(UserProfile.OTHER_KEYS[2],"true");
            organizationService.getUserProfileHandler().saveUserProfile(profile,true);
            LOG.debug("External info added in gatein profile for user {}, {}ms", username, System.currentTimeMillis() - startTimeForUser);
          } catch (Exception e) {
            LOG.error("Unable to get profile for user {}",username);
          }
        });
        current=current+currentBatch.length;
        LOG.info("Progession : {} users updated on {} total users. It tooks {}ms",
                 current,
                 total,
                 System.currentTimeMillis() - startupTime);

        RequestLifeCycle.end();


      }
    } catch (Exception e) {
      LOG.error("Unable to find group externals");
    }
  }
}
