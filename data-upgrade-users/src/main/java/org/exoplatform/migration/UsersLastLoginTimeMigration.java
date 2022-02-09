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
  private static final Log    LOG = ExoLogger.getExoLogger(UsersLastLoginTimeMigration.class);

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
    LOG.info("Start upgrade process to add lastLoginTime in user profile");
    long startupTime = System.currentTimeMillis();
    try {
      int limit = 100;
      int offset = 0;
      int totalSize = 0;
      int totalItemsChecked = 0;
      ListAccess<Identity> listIdentity = null;
      ProfileFilter filter = new ProfileFilter();
      filter.setConnected(false);
      listIdentity = identityManager.getIdentitiesByProfileFilter(OrganizationIdentityProvider.NAME, filter, true);
      totalSize = listIdentity.getSize();
      LOG.info("Number of users to check : " + totalSize);
      do {
        int limitToFetch = limit;
        List<Identity> identities;
        if (totalSize < (offset + limitToFetch)) {
          limitToFetch = totalSize - offset;
        }
        identities = Arrays.asList(listIdentity.load(offset, limitToFetch));
        int numberOfModifiedItems = updateLastLoginTime(totalSize, totalItemsChecked, identities);
        totalItemsChecked = totalItemsChecked + identities.size();
        offset = (offset + limitToFetch) - numberOfModifiedItems;
      } while (offset < totalSize);
      LOG.info("Upgrade of {} / {} proceeded successfully.", totalItemsChecked, totalSize);
    } catch (Exception e) {
      LOG.error("Error processUpgrade data-upgrade-users", e);
      throw new IllegalStateException("Upgrade failed when upgrade-users");
    }
    LOG.info("End process to add lastLoginTime in user profile. It took {} ms.", (System.currentTimeMillis() - startupTime));
  }

  public int updateLastLoginTime(int totalSize, int totalItemsChecked, List<Identity> identities) throws Exception {
    int numberOfModifiedItems = 0;
    long startupTime = System.currentTimeMillis();
    for (Identity identity : identities) {
      String username = identity.getRemoteId();
      User user = organizationService.getUserHandler().findUserByName(username);
      Profile profile = identity.getProfile();
      if (user != null && profile != null && !Objects.equals(user.getCreatedDate(), user.getLastLoginTime())) {
        numberOfModifiedItems++;
        profile.setProperty(Profile.LAST_LOGIN_TIME, user.getLastLoginTime());
        identityManager.updateProfile(profile, false);
        IndexingService indexingService = CommonsUtils.getService(IndexingService.class);
        indexingService.reindex(ProfileIndexingServiceConnector.TYPE, identity.getId());
      } else {
        LOG.info("Null user checked");
      }
      totalItemsChecked++;
    }
    LOG.info("Progession : {} users checked on {} total users, {} users fixed in this batch. It tooks {}ms",
             totalItemsChecked,
             totalSize,
             numberOfModifiedItems,
             System.currentTimeMillis() - startupTime);
    return numberOfModifiedItems;
  }

}
