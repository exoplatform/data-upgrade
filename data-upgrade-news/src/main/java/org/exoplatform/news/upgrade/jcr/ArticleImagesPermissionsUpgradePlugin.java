/*
 * Copyright (C) 2025 eXo Platform SAS.
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
 *
 */
package org.exoplatform.news.upgrade.jcr;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.collections4.CollectionUtils;

import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.attachments.utils.Utils;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.AccessControlEntry;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.IdentityConstants;
import org.exoplatform.services.wcm.core.NodetypeConstant;
import org.exoplatform.wiki.model.Page;
import org.exoplatform.wiki.service.NoteService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

public class ArticleImagesPermissionsUpgradePlugin extends UpgradeProductPlugin {

  private static final Log LOG = ExoLogger.getLogger(ArticleImagesPermissionsUpgradePlugin.class);
  private static final String IMAGE_SRC_REGEX = "src=\"/portal/rest/images/?(.+)?\"";
  private final PortalContainer container;
  private NoteService noteService;
  private EntityManagerService entityManagerService;
  private RepositoryService repositoryService;

  public ArticleImagesPermissionsUpgradePlugin(InitParams initParams,
                                               EntityManagerService entityManagerService,
                                               NoteService noteService,
                                               RepositoryService repositoryService, PortalContainer container) {
    super(initParams);
    this.entityManagerService = entityManagerService;
    this.noteService = noteService;
    this.repositoryService = repositoryService;
    this.container = container;
  }

  public static boolean hasAnyPermissions(ExtendedNode attachmentNode) throws RepositoryException {
    for (AccessControlEntry ace : attachmentNode.getACL().getPermissionEntries()) {
      if (ace.getIdentity().equals(IdentityConstants.ANY) && ace.getPermission().equals(PermissionType.READ)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    ExoContainerContext.setCurrentContainer(container);
    long startupTime = System.currentTimeMillis();
    int attachmentUpadatedCount = 0;
    int articleCount = 0;
    boolean transactionStarted = false;
    RequestLifeCycle.begin(this.entityManagerService);
    EntityManager entityManager = this.entityManagerService.getEntityManager();
    try {
      if (!entityManager.getTransaction().isActive()) {
        entityManager.getTransaction().begin();
        transactionStarted = true;
      }
      List<Object[]> results = getArticles(entityManager);
      if (results.isEmpty()) {
        return;
      }
      articleCount = results.size();
      LOG.info("Start updating article images permissions, {} articles should be treated", articleCount);
      SessionProvider sessionProvider = SessionProvider.createSystemProvider();
      SessionProviderService sessionProviderService = ExoContainerContext.getService(SessionProviderService.class);
      sessionProviderService.setSessionProvider(null, sessionProvider);
      Session session = Utils.getSystemSession(sessionProviderService, repositoryService);
      for (Object[] result : results) {
        List<String> attachmentsIds = new ArrayList<>();
        Long objectId = (Long) result[0];
        Page page = noteService.getNoteById(Long.toString(objectId));
        String content = page.getContent();
        if (content != null) {
          attachmentsIds.addAll(extractImageAttachmentUuidOrPath(content));
        }
        attachmentUpadatedCount += updateNodePermissions(attachmentsIds, session);
      }
      if (transactionStarted && entityManager.getTransaction().isActive()) {
        entityManager.getTransaction().commit();
      }
      LOG.info("Updating {} images permissions from {} published articles done it took {} ms", attachmentUpadatedCount, articleCount, System.currentTimeMillis() - startupTime);
    } catch (Exception e) {
      if (transactionStarted && entityManager.getTransaction().isActive() && entityManager.getTransaction().getRollbackOnly()) {
        entityManager.getTransaction().rollback();
      }
      LOG.error("Error when processing article images permissions upgrade plugin", e);
    } finally {
      RequestLifeCycle.end();
    }
  }

  private List<Object[]> getArticles(EntityManager entityManager) {
    String selectQuery = "SELECT wp.* FROM WIKI_PAGES wp\n" +
            "JOIN SOC_METADATA_ITEMS smi ON wp.PAGE_ID = smi.OBJECT_ID\n" +
            "JOIN SOC_METADATA_ITEMS_PROPERTIES smip1 ON smi.METADATA_ITEM_ID = smip1.METADATA_ITEM_ID\n" +
            "JOIN SOC_METADATA_ITEMS_PROPERTIES smip2 ON smi.METADATA_ITEM_ID = smip2.METADATA_ITEM_ID\n" +
            "WHERE smi.OBJECT_TYPE = 'newsPage'\n" +
            "AND smi.SPACE_ID IS NOT NULL\n" +
            "AND smip1.NAME = 'audience'\n" +
            "AND smip1.VALUE = 'all'\n" +
            "AND smip2.NAME = 'published'\n" +
            "AND smip2.VALUE = 'true';\n";
    Query nativeQuery = entityManager.createNativeQuery(selectQuery);
    return nativeQuery.getResultList();
  }

  private List<String> extractImageAttachmentUuidOrPath(String content) {
    List<String> imageNodeIds = new ArrayList<>();
    Matcher matcher = Pattern.compile(IMAGE_SRC_REGEX).matcher(content);
    while (matcher.find()) {
      String srcStringPart = matcher.group(1);
      if (srcStringPart.contains("\"")) {
        matcher = Pattern.compile(IMAGE_SRC_REGEX).matcher(srcStringPart);
        srcStringPart = srcStringPart.substring(0, srcStringPart.indexOf("\""));
      }
      String attachmentNodeId = srcStringPart.substring(srcStringPart.lastIndexOf("/") + 1);
      imageNodeIds.add(attachmentNodeId);
    }
    StringBuilder existingUploadImagesSrcRegex = new StringBuilder();
    existingUploadImagesSrcRegex.append("src=\"");
    existingUploadImagesSrcRegex.append(CommonsUtils.getCurrentDomain());
    existingUploadImagesSrcRegex.append("/");
    existingUploadImagesSrcRegex.append(PortalContainer.getCurrentPortalContainerName());
    existingUploadImagesSrcRegex.append("/");
    existingUploadImagesSrcRegex.append(CommonsUtils.getRestContextName());
    existingUploadImagesSrcRegex.append("/jcr/?(.+)?\"");

    String repositoryAndWorkSpaceSuffix = "/repository/collaboration";
    Matcher pathMatcher = Pattern.compile(existingUploadImagesSrcRegex.toString()).matcher(content);
    while (pathMatcher.find()) {
      String srcStringPart = pathMatcher.group(1);
      if (srcStringPart.contains("\"")) {
        pathMatcher = Pattern.compile(existingUploadImagesSrcRegex.toString()).matcher(srcStringPart);
        srcStringPart = srcStringPart.substring(0, srcStringPart.indexOf("\""));
      }
      String imagePath = srcStringPart.substring(srcStringPart.indexOf(repositoryAndWorkSpaceSuffix)
              + repositoryAndWorkSpaceSuffix.length());
      imageNodeIds.add(imagePath);
    }
    return imageNodeIds;
  }

  private int updateNodePermissions(List<String> identifiers, Session session) throws RepositoryException {
    int updatCount = 0;
    if (!CollectionUtils.isEmpty(identifiers)) {
      for (String attachmentId : identifiers) {
        Node attachmentNode;
        boolean isNodeUUID = !attachmentId.contains("/");
        if (isNodeUUID) {
          attachmentNode = session.getNodeByUUID(attachmentId);
        } else {
          attachmentNode = (Node) session.getItem(URLDecoder.decode(attachmentId, StandardCharsets.UTF_8));
        }
        if (attachmentNode.canAddMixin(NodetypeConstant.EXO_PRIVILEGEABLE)) {
          attachmentNode.addMixin(NodetypeConstant.EXO_PRIVILEGEABLE);
        }
        if (!hasAnyPermissions((ExtendedNode) attachmentNode)) {
          ((ExtendedNode) attachmentNode).setPermission("any", new String[]{PermissionType.READ});
          attachmentNode.save();
          updatCount++;
        }
      }
    }
    return updatCount;
  }
}
