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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;

import org.exoplatform.commons.upgrade.UpgradePluginExecutionContext;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.impl.core.query.QueryImpl;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.wcm.core.NodetypeConstant;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

public class PublishedNewsImagesPermissionsV2UpgradePlugin extends UpgradeProductPlugin {

  private static final Log             LOG                           =
          ExoLogger.getLogger(PublishedNewsImagesPermissionsV2UpgradePlugin.class.getName());

  private static final String          IMAGE_SRC_REGEX               = "src=\"/portal/rest/images/?(.+)?\"";

  private final RepositoryService      repositoryService;

  private final SessionProviderService sessionProviderService;
  private final SpaceService spaceService;

  private int                          imageNewsUpdatedCount;

  private int                          newsCount;

  public PublishedNewsImagesPermissionsV2UpgradePlugin(InitParams initParams,
                                                     RepositoryService repositoryService,
                                                       SessionProviderService sessionProviderService,
                                                       SpaceService spaceService) {
    super(initParams);
    this.repositoryService = repositoryService;
    this.sessionProviderService = sessionProviderService;
    this.spaceService = spaceService;
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
      int limit = 10, offset = 0;
      String stringQuery =
                         "select * from exo:news WHERE publication:currentState = 'published' AND jcr:path LIKE '/Groups/spaces/%'";
      Query jcrQuery = qm.createQuery(stringQuery, Query.SQL);
      boolean hasMoreElements = true;
      while (hasMoreElements) {
        ((QueryImpl) jcrQuery).setOffset(offset);
        ((QueryImpl) jcrQuery).setLimit(limit);
        NodeIterator nodeIterator = jcrQuery.execute().getNodes();
        if (nodeIterator != null) {
          while (nodeIterator.hasNext()) {
            Node newsNode = nodeIterator.nextNode();
            updateNewsImagesPermissions(newsNode, session);
          }
          if (nodeIterator.getSize() < limit) {
            // no more elements
            hasMoreElements = false;
          } else {
            offset += limit;
          }
        }
      }
      LOG.info("End updating of '{}' images for '{}' published news . It took {} ms.",
               this.imageNewsUpdatedCount,
               this.newsCount,
               (System.currentTimeMillis() - startupTime));
    } catch (Exception e) {
      LOG.error("An error occurred when upgrading published images news permissions :", e);
    } finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
    }
  }

  private void updateNewsImagesPermissions(Node newsNode, Session session) throws RepositoryException {
    Matcher matcher = Pattern.compile(IMAGE_SRC_REGEX).matcher(getStringProperty(newsNode, "exo:body"));
    int imagesCount = 0;
    ExtendedNode image = null;
    while (matcher.find()) {
      String match = matcher.group(1);
      String imageUUID = match.substring(match.lastIndexOf("/") + 1);
      image = (ExtendedNode) session.getNodeByUUID(imageUUID);
      imagesCount = updateNodePermissions(newsNode, image, imagesCount);
    }
    String existingUploadImagesSrcRegex = "src=\"" + CommonsUtils.getCurrentDomain() + "/"
            + PortalContainer.getCurrentPortalContainerName() + "/" + CommonsUtils.getRestContextName() + "/jcr/?(.+)?\"";
    matcher = Pattern.compile(existingUploadImagesSrcRegex).matcher(getStringProperty(newsNode, "exo:body"));
    while (matcher.find()) {
      String match = matcher.group(1);
      String imagePath = match.substring(match.indexOf("/Groups"));
      image = (ExtendedNode) getNodeByPath(imagePath, session);
      imagesCount = updateNodePermissions(newsNode, image, imagesCount);
    }
    if (imagesCount > 0) {
      this.newsCount += 1;
      this.imageNewsUpdatedCount += imagesCount;
    }
  }

  private int updateNodePermissions(Node newsNode, ExtendedNode image, int imagesCount) throws RepositoryException {
    if (image != null) {
      if (image.canAddMixin(NodetypeConstant.EXO_PRIVILEGEABLE)) {
        image.addMixin(NodetypeConstant.EXO_PRIVILEGEABLE);
        }
      String spaceId = getStringProperty(newsNode, "exo:spaceId");
      Space space = spaceService.getSpaceById(spaceId);
      if (space != null) {
        ((ExtendedNode) image).setPermission("*:" + space.getGroupId(), new String[]{PermissionType.READ});
        image.save();
      }
      if (getStringProperty(newsNode, "exo:audience").equals("all")) {
        boolean isPublicImage = image.getACL()
                .getPermissionEntries()
                .stream()
                .anyMatch(accessControlEntry -> accessControlEntry.getIdentity()
                        .equals("any"));
        if (!isPublicImage) {
          ((ExtendedNode) image).setPermission("any", new String[]{PermissionType.READ});
          image.save();
          imagesCount += 1;
        }
      }
    }
    return imagesCount;
  }

  private String getStringProperty(Node node, String propertyName) throws RepositoryException {
    if (node.hasProperty(propertyName)) {
      return node.getProperty(propertyName).getString();
    }
    return "";
  }

  private Node getNodeByPath(String path, Session session) {
    try {
      return (Node) session.getItem(URLDecoder.decode(path, StandardCharsets.UTF_8));
    } catch (RepositoryException exception) {
      return null;
    }
  }

}
