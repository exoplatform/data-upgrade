/*
 * Copyright (C) 2023 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.news.upgrade.jcr;

import liquibase.repackaged.org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.upgrade.UpgradePluginExecutionContext;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.impl.core.query.QueryImpl;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.container.xml.InitParams;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PublishedNewsImagesPermissionsUpgradePlugin extends UpgradeProductPlugin {
  private static final Log LOG = ExoLogger.getLogger(PublishedNewsImagesPermissionsUpgradePlugin.class.getName());
  private final RepositoryService repositoryService;
  private SessionProviderService sessionProviderService;
  private static final Pattern IMAGE_SRC_PATTERN                = Pattern.compile("src=\"/portal/rest/images/?(.+)?\"");
  public static final String EXO_PRIVILEGEABLE = "exo:privilegeable";
  public static final String READ_PERMISSIONS = "permissions";
  public static final String PLATFORM_USERS_GROUP_IDENTITY = "platform.users.group.identity";
  private  int imageNewsUpdatedCount;
  private  int newsCount;
  private String readPermissions;
  private String platformUsersGroupIdentity;

  public PublishedNewsImagesPermissionsUpgradePlugin(InitParams initParams, RepositoryService repositoryService, SessionProviderService sessionProviderService) {
    super(initParams);
    this.repositoryService = repositoryService;
    this.sessionProviderService = sessionProviderService;

    if (initParams.containsKey(READ_PERMISSIONS)) {
      readPermissions = initParams.getValueParam(READ_PERMISSIONS).getValue();
    }
    if (initParams.containsKey(PLATFORM_USERS_GROUP_IDENTITY)) {
      platformUsersGroupIdentity = initParams.getValueParam(PLATFORM_USERS_GROUP_IDENTITY).getValue();
    }
  }

  @Override
  public boolean shouldProceedToUpgrade(String newVersion,
                                        String previousGroupVersion,
                                        UpgradePluginExecutionContext previousUpgradePluginExecution) {
    int executionCount = previousUpgradePluginExecution == null ? 0 : previousUpgradePluginExecution.getExecutionCount();
    return !isExecuteOnlyOnce() || executionCount == 0;
  }

  @Override
  public void processUpgrade(String s, String s1) {

    if (StringUtils.isEmpty(readPermissions)||StringUtils.isEmpty(platformUsersGroupIdentity)) {
      LOG.error("Couldn't process upgrade, all parameters are mandatory");
      return;
    }
    long startupTime = System.currentTimeMillis();
    LOG.info("Start upgrade of published news images permission");
    SessionProvider sessionProvider = null;
    try {
      sessionProvider = sessionProviderService.getSystemSessionProvider(null);
      Session session = sessionProvider.getSession(
              repositoryService.getCurrentRepository()
                      .getConfiguration()
                      .getDefaultWorkspaceName(),
              repositoryService.getCurrentRepository());
      QueryManager qm = session.getWorkspace().getQueryManager();
      int limit = 10 , offset = 0;
      String stringQuery = "select * from exo:news WHERE publication:currentState = 'published' AND jcr:path LIKE '/Groups/spaces/%'";
      Query jcrQuery = qm.createQuery(stringQuery, Query.SQL);
      boolean hasMoreElements = true;
      while (hasMoreElements) {
        ((QueryImpl)jcrQuery).setOffset(offset);
        ((QueryImpl)jcrQuery).setLimit(limit);
        NodeIterator nodeIterator = jcrQuery.execute().getNodes();
        if (nodeIterator != null){
          while (nodeIterator.hasNext()){
            Node newsNode = nodeIterator.nextNode();
            updateNewsImagesPermissions(newsNode, session);
          }
          if (nodeIterator.getSize() < limit) {
            // no more elements
            hasMoreElements = false ;
          } else {
            offset += limit;
          }
        }
      }
      LOG.info("End updating of '{}' images for '{}' published news . It took {} ms.", this.imageNewsUpdatedCount, this.newsCount, (System.currentTimeMillis() - startupTime));
    } catch (Exception e) {
      LOG.error("An error occurred when upgrading published images news permissions :", e);
    } finally {
      if (sessionProvider != null) {
        sessionProvider.close();      }
    }
  }

  private void updateNewsImagesPermissions(Node newsNode, Session session) throws RepositoryException {
    Matcher matcher = IMAGE_SRC_PATTERN.matcher(getStringProperty(newsNode, "exo:body"));
    int imagesCount = 0;
    while (matcher.find()) {
      String match = matcher.group(1);
      String imageUUID = match.substring(match.lastIndexOf("/") + 1);
      ExtendedNode image = (ExtendedNode) session.getNodeByUUID(imageUUID);
      if (image != null) {
        if (image.canAddMixin(EXO_PRIVILEGEABLE)) {
          image.addMixin(EXO_PRIVILEGEABLE);
        }
        boolean isPublicImage = image.getACL().getPermissionEntries().stream().filter(accessControlEntry -> accessControlEntry.getIdentity().equals(platformUsersGroupIdentity)).toList().size() > 0;
        if (! isPublicImage) {
          // make news images public
          image.setPermission(platformUsersGroupIdentity, new String[] {readPermissions});
          image.save();
          imagesCount += 1;
        }
      }
    }
    if (imagesCount > 0) {
      this.newsCount +=1;
      this.imageNewsUpdatedCount += imagesCount;
    }
  }
  private String getStringProperty(Node node, String propertyName) throws RepositoryException {
    if (node.hasProperty(propertyName)) {
      return node.getProperty(propertyName).getString();
    }
    return "";
  }
}
