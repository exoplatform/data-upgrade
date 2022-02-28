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
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.social.core.jpa.search.ProfileIndexingServiceConnector;
import org.exoplatform.social.core.manager.IdentityManager;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class UsersLastLoginTimeMigration extends UpgradeProductPlugin {
  private static final Log    LOG = ExoLogger.getExoLogger(UsersLastLoginTimeMigration.class);

  private static final int     MAX_RESULT = 200;

  private EntityManagerService entityManagerService;

  // @formatter:off
  String                      sqlQuery = "SELECT REMOTE_ID FROM SOC_IDENTITIES "
      + "WHERE ENABLED=true "
      + "AND PROVIDER_ID='organization' "
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
      + "WHERE ENABLED=true "
      + "AND PROVIDER_ID='organization' "
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
      + ") ";

  String getUserLastLoginTimeQueryString = "SELECT jbid_io_attr_text_values.ATTR_VALUE "
      + "FROM jbid_io "
      + "INNER JOIN jbid_io_attr ON jbid_io.ID = jbid_io_attr.IDENTITY_OBJECT_ID "
      + "INNER JOIN jbid_io_attr_text_values ON jbid_io_attr.ATTRIBUTE_ID = jbid_io_attr_text_values.TEXT_ATTR_VALUE_ID "
      + "WHERE jbid_io_attr.name = 'lastLoginTime' AND jbid_io.NAME=:username";

  String getIdentityIdQueryString = "SELECT si.IDENTITY_ID FROM SOC_IDENTITIES si WHERE si.REMOTE_ID=:username";

  String insertProfileLastLoginTimeQueryString = "INSERT INTO SOC_IDENTITY_PROPERTIES VALUES (:identityId, 'lastLoginTime',:lastLoginTime)";

  // @formatter:on

  public UsersLastLoginTimeMigration(EntityManagerService entityManagerService,
                                     InitParams initParams) {
    super(initParams);
    this.entityManagerService = entityManagerService;

  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    LOG.info("Start upgrade process to add lastLoginTime in user profile");
    long startupTime = System.currentTimeMillis();
    try {
      long totalSize;
      int totalItemsFixed = 0;
      int offset = 0;

      // COUNT
      RequestLifeCycle.begin(ExoContainerContext.getCurrentContainer());
      EntityManager entityManager = this.entityManagerService.getEntityManager();
      try {
        LOG.debug("Execute count query {}", countQuery);
        Query countNativeQuery = entityManager.createNativeQuery(countQuery);
        totalSize = ((Number) countNativeQuery.getSingleResult()).intValue();
      } finally {
        RequestLifeCycle.end();
      }

      LOG.info("Number of users to fix : " + totalSize);
      int updatedUsers;
      do {
        // SELECT
        RequestLifeCycle.begin(ExoContainerContext.getCurrentContainer());
        List<Object> remoteIds = new ArrayList<>();
        try {
          long startTimeForBatch = System.currentTimeMillis();
          Query sqlNativeQuery = entityManager.createNativeQuery(sqlQuery);
          sqlNativeQuery.setMaxResults(MAX_RESULT);
          sqlNativeQuery.setFirstResult(offset);
          remoteIds = sqlNativeQuery.getResultList();
          LOG.debug("Query to get users executed in {}ms", System.currentTimeMillis() - startTimeForBatch);
          updatedUsers = updateLastLoginTime(remoteIds);

          // ignore the non treatable users
          offset = offset + (remoteIds.size() - updatedUsers);

          totalItemsFixed = totalItemsFixed + remoteIds.size();
          LOG.info("Progession : {} users fixed on {} total users. It tooks {}ms",
                   totalItemsFixed,
                   totalSize,
                   System.currentTimeMillis() - startTimeForBatch);
        } finally {
          RequestLifeCycle.end();
        }

      } while (totalItemsFixed < totalSize);

      LOG.info("Upgrade of {} / {} proceeded successfully.", totalItemsFixed, totalSize);
    } catch (Exception e) {
      LOG.error("Error processUpgrade data-upgrade-users", e);
      throw new IllegalStateException("Upgrade failed when upgrade-users");
    }
    LOG.info("End process to add lastLoginTime in user profile. It took {} ms.", (System.currentTimeMillis() - startupTime));
  }

  public int updateLastLoginTime(List<Object> remoteIds) throws Exception {
    int numberOfModifiedItems = 0;
    int numberOfNotModifiedItems = 0;

    EntityManager entityManager = this.entityManagerService.getEntityManager();

    for (Object remoteId : remoteIds) {
      try {
        long startTime = System.currentTimeMillis();
        String username = (String) remoteId;
        // getUserIdentityId
        Query getIdentityIdQuery = entityManager.createNativeQuery(getIdentityIdQueryString);
        getIdentityIdQuery.setParameter("username", username);
        List<Object> resultListId = getIdentityIdQuery.getResultList();
        if (resultListId.isEmpty()) {
          // the case identity==null occurs on tribe
          // some users are present twice in jbid_io with a letter in capital :
          // for example : "Adam" and "adam"
          // but only once in identities table with "adam" or "Adam"
          // in this case, orgService is not able to find the related user and return null
          // so, ignore it
          numberOfNotModifiedItems++;

        } else {
          Long identityId = Long.parseLong(resultListId.get(0).toString());

          // get user.lastLoginTime
          Query getUserLastLoginTimeQuery = entityManager.createNativeQuery(getUserLastLoginTimeQueryString);
          getUserLastLoginTimeQuery.setParameter("username", username);
          Long lastLoginTime = Long.parseLong(getUserLastLoginTimeQuery.getResultList().get(0).toString());

          // insert lastLoginTime in profile properties
          entityManager.getTransaction().begin();
          Date lastLoginDate = new Date(lastLoginTime);
          Query insertProfileLastLoginTimeQuery = entityManager.createNativeQuery(insertProfileLastLoginTimeQueryString);
          insertProfileLastLoginTimeQuery.setParameter("identityId", identityId);
          insertProfileLastLoginTimeQuery.setParameter("lastLoginTime", String.valueOf(lastLoginDate));
          insertProfileLastLoginTimeQuery.executeUpdate();
          entityManager.getTransaction().commit();

          IndexingService indexingService = CommonsUtils.getService(IndexingService.class);
          indexingService.reindex(ProfileIndexingServiceConnector.TYPE, identityId.toString());
          numberOfModifiedItems++;
        }
        LOG.debug("User {} fixed in {}ms, {}/{} in current batch.",
                 username,
                 System.currentTimeMillis() - startTime,
                 (numberOfModifiedItems + numberOfNotModifiedItems),
                 remoteIds.size());
      } catch (Exception e) {
        LOG.error("Error when updating user {}", remoteId.toString(), e);
        entityManager.getTransaction().rollback();
        throw new RuntimeException(e);
      }
    }
    return numberOfModifiedItems;
  }

}
