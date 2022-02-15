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

import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.search.index.IndexingService;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.component.RequestLifeCycle;
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

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.ArrayList;
import java.util.List;

public class UsersLastLoginTimeMigration extends UpgradeProductPlugin {
  private static final Log    LOG = ExoLogger.getExoLogger(UsersLastLoginTimeMigration.class);

  private static final int     MAX_RESULT = 200;

  private OrganizationService organizationService;

  private IdentityManager      identityManager;

  private EntityManagerService entityManagerService;

  // @formatter:off
  String                      sqlQuery = "SELECT REMOTE_ID FROM SOC_IDENTITIES "
      + "WHERE IDENTITY_ID in ("
            //select enabled users
      + "   SELECT IDENTITY_ID FROM SOC_IDENTITIES si WHERE si.ENABLED=true"
      + ") "
      + "AND IDENTITY_ID NOT IN ("
            //select users which have not field profile.lastLoginTime
      + "   SELECT si.IDENTITY_ID FROM SOC_IDENTITIES si"
      + "   INNER JOIN SOC_IDENTITY_PROPERTIES sip ON si.IDENTITY_ID = sip.IDENTITY_ID"
      + "   WHERE sip.NAME = 'lastLoginTime'"
      + ") "
      + "AND REMOTE_ID IN ("
            //select users for which user.createdDate != user.lastLoginTime
      + "   SELECT llt.NAME FROM ("
      + "     SELECT jbid_io.NAME,jbid_io.ID,jbid_io_attr_text_values.ATTR_VALUE from jbid_io"
      + "     INNER JOIN jbid_io_attr ON jbid_io.ID =  jbid_io_attr.IDENTITY_OBJECT_ID"
      + "     INNER JOIN jbid_io_attr_text_values ON jbid_io_attr.ATTRIBUTE_ID = jbid_io_attr_text_values.TEXT_ATTR_VALUE_ID"
      + "     AND jbid_io_attr.name = 'lastLoginTime'"
      + "   ) llt"
      + "   INNER JOIN ("
      + "     SELECT jbid_io.NAME,jbid_io.ID,jbid_io_attr_text_values.ATTR_VALUE from jbid_io"
      + "     INNER JOIN jbid_io_attr ON jbid_io.ID =  jbid_io_attr.IDENTITY_OBJECT_ID "
      + "     INNER JOIN jbid_io_attr_text_values ON jbid_io_attr.ATTRIBUTE_ID = jbid_io_attr_text_values.TEXT_ATTR_VALUE_ID"
      + "     AND jbid_io_attr.name = 'createdDate'"
      + "   ) AS cdt"
      + "   ON llt.ID=cdt.ID"
      + "   WHERE llt.ATTR_VALUE!=cdt.ATTR_VALUE"
      + ") ";

  String                      countQuery = "SELECT COUNT(REMOTE_ID) FROM SOC_IDENTITIES "
      + "WHERE IDENTITY_ID in ("
      //select enabled users
      + "   SELECT IDENTITY_ID FROM SOC_IDENTITIES si WHERE si.ENABLED=true"
      + ") "
      + "AND IDENTITY_ID NOT IN ("
      //select users which have not field profile.lastLoginTime
      + "   SELECT si.IDENTITY_ID FROM SOC_IDENTITIES si"
      + "   INNER JOIN SOC_IDENTITY_PROPERTIES sip ON si.IDENTITY_ID = sip.IDENTITY_ID"
      + "   WHERE sip.NAME = 'lastLoginTime'"
      + ") "
      + "AND REMOTE_ID IN ("
      //select users for which user.createdDate != user.lastLoginTime
      + "   SELECT llt.NAME FROM ("
      + "     SELECT jbid_io.NAME,jbid_io.ID,jbid_io_attr_text_values.ATTR_VALUE from jbid_io"
      + "     INNER JOIN jbid_io_attr ON jbid_io.ID =  jbid_io_attr.IDENTITY_OBJECT_ID "
      + "     INNER JOIN jbid_io_attr_text_values ON jbid_io_attr.ATTRIBUTE_ID = jbid_io_attr_text_values.TEXT_ATTR_VALUE_ID"
      + "     AND jbid_io_attr.name = 'lastLoginTime'"
      + "   ) llt"
      + "   INNER JOIN ("
      + "     SELECT jbid_io.NAME,jbid_io.ID,jbid_io_attr_text_values.ATTR_VALUE from jbid_io"
      + "     INNER JOIN jbid_io_attr ON jbid_io.ID =  jbid_io_attr.IDENTITY_OBJECT_ID "
      + "     INNER JOIN jbid_io_attr_text_values ON jbid_io_attr.ATTRIBUTE_ID = jbid_io_attr_text_values.TEXT_ATTR_VALUE_ID"
      + "     AND jbid_io_attr.name = 'createdDate'"
      + "   ) AS cdt"
      + "   ON llt.ID=cdt.ID"
      + "   WHERE llt.ATTR_VALUE!=cdt.ATTR_VALUE"
      + ");";

  // @formatter:on

  public UsersLastLoginTimeMigration(OrganizationService organizationService,
                                     IdentityManager identityManager,
                                     EntityManagerService entityManagerService,
                                     InitParams initParams) {
    super(initParams);
    this.organizationService = organizationService;
    this.identityManager = identityManager;
    this.entityManagerService = entityManagerService;

  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    LOG.info("Start upgrade process to add lastLoginTime in user profile");
    long startupTime = System.currentTimeMillis();
    try {
      long totalSize = 0;
      int totalItemsFixed = 0;

      // COUNT
      RequestLifeCycle.begin(this.entityManagerService);
      EntityManager entityManager = this.entityManagerService.getEntityManager();
      try {
        Query countNativeQuery = entityManager.createNativeQuery(countQuery);
        totalSize = ((Number) countNativeQuery.getSingleResult()).intValue();
      } finally {
        RequestLifeCycle.end();
      }

      LOG.info("Number of users to fix : " + totalSize);
      int updatedUsers = 0;
      do {
        // SELECT
        RequestLifeCycle.begin(this.entityManagerService);
        List<Object> remoteIds = new ArrayList<>();
        try {
          long startTimeForBatch = System.currentTimeMillis();
          Query sqlNativeQuery = entityManager.createNativeQuery(sqlQuery);
          sqlNativeQuery.setMaxResults(MAX_RESULT);
          remoteIds = sqlNativeQuery.getResultList();
          updatedUsers = updateLastLoginTime(remoteIds);
          totalItemsFixed = totalItemsFixed + updatedUsers;
          LOG.info("Progession : {} users fixed on {} total users. It tooks {}ms",
                   totalItemsFixed,
                   totalSize,
                   System.currentTimeMillis() - startTimeForBatch);
        } finally {
          RequestLifeCycle.end();
        }

      } while (updatedUsers < 0);

      LOG.info("Upgrade of {} / {} proceeded successfully.", totalItemsFixed, totalSize);
    } catch (Exception e) {
      LOG.error("Error processUpgrade data-upgrade-users", e);
      throw new IllegalStateException("Upgrade failed when upgrade-users");
    }
    LOG.info("End process to add lastLoginTime in user profile. It took {} ms.", (System.currentTimeMillis() - startupTime));
  }

  public int updateLastLoginTime(List<Object> remoteIds) throws Exception {
    int numberOfModifiedItems = 0;
    for (Object remoteId : remoteIds) {
      String username = (String) remoteId;
      User user = organizationService.getUserHandler().findUserByName(username);
      Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
      Profile profile = identity.getProfile();
      profile.setProperty(Profile.LAST_LOGIN_TIME, user.getLastLoginTime());
      identityManager.updateProfile(profile, false);
      IndexingService indexingService = CommonsUtils.getService(IndexingService.class);
      indexingService.reindex(ProfileIndexingServiceConnector.TYPE, identity.getId());
      numberOfModifiedItems++;
    }
    return numberOfModifiedItems;
  }

}
