/*
 * Copyright (C) 2022 eXo Platform SAS.
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

import org.exoplatform.commons.search.index.IndexingService;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.jpa.search.ProfileIndexingServiceConnector;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.profile.ProfileFilter;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class UsersLastLoginTimeMigration extends UpgradeProductPlugin {
  private static final Log    LOG                    = ExoLogger.getExoLogger(UsersLastLoginTimeMigration.class);

  private static final String APPLICATION_CONTENT_ID = "migration-of-users";

  private OrganizationService organizationService;

  private IdentityManager     identityManager;

  public UsersLastLoginTimeMigration(OrganizationService organizationService,
                                     IdentityManager identityManager,
                                     InitParams initParams) {
    super(initParams);
    this.organizationService = organizationService;
    this.identityManager = identityManager;
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    LOG.info("Start upgrade connected user profile: {}", APPLICATION_CONTENT_ID);
    long startupTime = System.currentTimeMillis();
    int limit = 20;
    int offset = 0;
    int totalSize = 0;
    ListAccess<Identity> listIdentity = null;
    try {
      ProfileFilter filter = new ProfileFilter();
      filter.setConnected(false);
      listIdentity = identityManager.getIdentitiesByProfileFilter(OrganizationIdentityProvider.NAME, filter, true);
      LOG.info(" Progression of users  TOTAL_ITEMS_TO_MIGRATE = " + listIdentity.getSize());
      totalSize = listIdentity.getSize();
      int limitToFetch = limit;
      if (totalSize < (offset + limitToFetch)) {
        limitToFetch = totalSize - offset;
      }
      updateUserProfile(totalSize, limitToFetch, offset, listIdentity);
    } catch (Exception e) {
      LOG.error("Error processUpgrade data-upgrade-users", e);
    }
    LOG.info("End upgrade of connected user profile. It took {} ms", (System.currentTimeMillis() - startupTime));
  }

  public void updateUserProfile(int totalSize, int limitToFetch, int offset, ListAccess<Identity> listIdentity) throws Exception {
    List<Identity> identities;
    int totalItemsMigrated = 0;
    int itemsMigratedPerLoop = 0;
    do {
      identities = Arrays.asList(listIdentity.load(offset, limitToFetch));
      for (Identity identity : identities) {
        String username = identity.getRemoteId();
        User user = organizationService.getUserHandler().findUserByName(username);
        Profile profile = identity.getProfile();
        if (profile != null && !Objects.equals(user.getCreatedDate(), user.getLastLoginTime())) {
          totalItemsMigrated++;
          itemsMigratedPerLoop++;
          profile.setProperty(Profile.LAST_LOGIN_TIME, user.getLastLoginTime());
          identityManager.updateProfile(profile, false);
          IndexingService indexingService = CommonsUtils.getService(IndexingService.class);
          indexingService.reindex(ProfileIndexingServiceConnector.TYPE, identity.getId());
        }
      }
      offset = (offset + limitToFetch) - itemsMigratedPerLoop;
      itemsMigratedPerLoop = 0;
      if (totalSize < (offset + limitToFetch)) {
        limitToFetch = totalSize - offset;
      }
    } while (offset < totalSize);
    LOG.info("ALREDAY_MIGRATED_ITEMS_COUNT = " + totalItemsMigrated);
  }
}
