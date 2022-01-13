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
import org.exoplatform.services.organization.UserStatus;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.jpa.search.ProfileIndexingServiceConnector;
import org.exoplatform.social.core.manager.IdentityManager;


import java.util.Objects;

public class UsersMigration extends UpgradeProductPlugin {
    private static final Log LOG                              = ExoLogger.getExoLogger(UsersMigration.class);
    private static final String APPLICATION_CONTENT_ID        ="migration-of-users";
    private OrganizationService organizationService;
    private IdentityManager identityManager;

    public UsersMigration(OrganizationService organizationService,
                          IdentityManager identityManager,
                          InitParams initParams) {
        super(initParams);
        this.organizationService=organizationService;
        this.identityManager=identityManager;
    }

    @Override
    public void processUpgrade(String oldVersion, String newVersion) {
        LOG.info("Start upgrade connected user profile: {}", APPLICATION_CONTENT_ID);
        long startupTime = System.currentTimeMillis();
        int limit=20;
        int offset=0;
        int totalSize = 0;
        ListAccess<User> usersListAccess = null;
        try {
            usersListAccess = organizationService.getUserHandler().findAllUsers(UserStatus.ENABLED);
            totalSize = usersListAccess.getSize();
            LOG.warn("#################totalSize"+totalSize);
            int limitToFetch = limit;
            if (totalSize < (offset + limitToFetch)) {
                limitToFetch = totalSize - offset;
            }
                updateUserProfile( totalSize, limitToFetch, offset,usersListAccess);

        } catch (Exception e) {
            LOG.warn("Error processUpgrade data-upgrade-users", e);
        }
        LOG.info("End upgrade of connected user profile. It took {} ms", (System.currentTimeMillis() - startupTime));
    }
    public void updateUserProfile(int totalSize,int limitToFetch,int offset,ListAccess<User> usersListAccess) throws Exception {
        User[] users;
        do {
            users = usersListAccess.load(offset, limitToFetch);
            for (User user : users) {
                Identity identity = identityManager.getOrCreateUserIdentity(user.getUserName());
                Profile profile = identity.getProfile();
                if (profile != null && !Objects.equals(user.getCreatedDate(), user.getLastLoginTime())) {
                    profile.setProperty(Profile.LAST_LOGIN_TIME,user.getLastLoginTime());
                    identityManager.updateProfile(profile, false);
                    IndexingService indexingService = CommonsUtils.getService(IndexingService.class);
                    indexingService.reindex(ProfileIndexingServiceConnector.TYPE, identity.getId());
                }
            }
            offset+=limitToFetch;
            if (totalSize < (offset + limitToFetch)) {
                limitToFetch = totalSize - offset;
            }
        }while (offset<=totalSize);
    }
}
