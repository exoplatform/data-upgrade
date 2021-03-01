package org.exoplatform.wiki.upgrade;

import java.util.*;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.portal.config.DataStorage;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.portal.config.model.PortalConfig;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.IdentityConstants;
import org.exoplatform.wiki.WikiException;
import org.exoplatform.wiki.mow.api.*;
import org.exoplatform.wiki.service.*;
import org.exoplatform.wiki.utils.Utils;

public class WikiGlobalPortalSiteUpgradePlugin extends UpgradeProductPlugin {

  private static final Log        LOG = ExoLogger.getLogger(WikiGlobalPortalSiteUpgradePlugin.class);

  private PortalContainer         container;

  private DataStorage             dataStorage;

  private UserPortalConfigService portalConfigService;

  private WikiService             wikiService;

  private EntityManagerService    entityManagerService;

  public WikiGlobalPortalSiteUpgradePlugin(PortalContainer container,
                                           WikiService wikiService,
                                           UserPortalConfigService portalConfigService,
                                           DataStorage dataStorage,
                                           EntityManagerService entityManagerService,
                                           InitParams initParams) {
    super(initParams);
    this.container = container;
    this.wikiService = wikiService;
    this.portalConfigService = portalConfigService;
    this.dataStorage = dataStorage;
    this.entityManagerService = entityManagerService;
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    RequestLifeCycle.begin(container);
    try {
      String globalPortal = portalConfigService.getGlobalPortal();
      Wiki globalSiteWiki = wikiService.getWikiByTypeAndOwner(PortalConfig.PORTAL_TYPE, globalPortal);
      if (globalSiteWiki == null) {
        LOG.info("--- Global Wiki Site migration: no global site wiki, nothing to migrate");
        return;
      }

      String defaultPortal = portalConfigService.getDefaultPortal();

      LOG.info("-- Global Wiki Site migration: Start updating wiki owner '{}' permissions to use owner '{}' default permissions",
               globalPortal,
               defaultPortal);
      updateGlobalSiteWikiPermissions(globalPortal, defaultPortal, globalSiteWiki);
      updateGlobalSiteWikiPagesPermissions(globalPortal, defaultPortal);
      LOG.info("-- Global Wiki Site migration: End updating wiki owner '{}' permissions to use owner '{}' default permissions",
               globalPortal,
               defaultPortal);

      Wiki defaultSiteWiki = wikiService.getWikiByTypeAndOwner(PortalConfig.PORTAL_TYPE, defaultPortal);
      if (defaultSiteWiki != null) {
        LOG.info("-- Global Wiki Site migration: default site has a wiki, the migration will not proceed");
        return;
      }

      LOG.info("-- Global Wiki Site migration: Start updating wiki from owner '{}' to owner '{}'", globalPortal, defaultPortal);
      EntityManager entityManager = entityManagerService.getEntityManager();
      boolean transactionStarted = false;
      if (entityManager.getTransaction() == null || !entityManager.getTransaction().isActive()) {
        entityManager.getTransaction().begin();
        transactionStarted = true;
      }
      try {
        updatePageMoveEntities(globalPortal, defaultPortal);
        updatePageEntities(globalPortal, defaultPortal);
        updateWikiEntity(globalPortal, defaultPortal);
      } finally {
        if (transactionStarted) {
          entityManager.getTransaction().commit();
        }
      }
      LOG.info("-- Global Wiki Site migration: End updating wiki from owner '{}' to owner '{}'", globalPortal, defaultPortal);
    } catch (Exception e) {
      throw new IllegalStateException("Error while migrating global site wiki", e);
    } finally {
      RequestLifeCycle.end();
    }

    restartTransaction();
  }

  private List<PermissionEntry> getDefaultSitePermissions(String defaultPortal) throws WikiException {
    List<PermissionEntry> wikiDefaultPermissions = wikiService.getWikiDefaultPermissions(PortalConfig.PORTAL_TYPE, defaultPortal);
    wikiDefaultPermissions = Collections.unmodifiableList(wikiDefaultPermissions);
    return wikiDefaultPermissions;
  }

  private void updateGlobalSiteWikiPagesPermissions(String globalPortal, String defaultPortal) throws Exception { // NOSONAR
    List<PermissionEntry> pageDefaultPermissions = getPageDefaultPermissions(defaultPortal);
    List<Page> pages = wikiService.getPagesOfWiki(PortalConfig.PORTAL_TYPE, globalPortal);
    LOG.info("---- Global Wiki Site migration: Start updating global site wiki '{}' pages permissions updated to use default site permissions",
             pages.size());
    for (Page page : pages) {
      List<PermissionEntry> pagePermissions = page.getPermissions();
      if (pagePermissions != null && pagePermissions.stream()
                                                    .anyMatch(permissionEntry -> StringUtils.equals(IdentityConstants.ANY,
                                                                                                    permissionEntry.getId()))) {
        page.setPermissions(pageDefaultPermissions);
        wikiService.updatePage(page, PageUpdateType.EDIT_PAGE_PERMISSIONS);

        LOG.info("----- Global Wiki Site migration: global site wiki page '{}' permissions updated to use default site permissions",
                 page.getName());
      } else {
        LOG.info("----- Global Wiki Site migration: global site wiki page '{}' permissions NOT updated since it doesn't have permissions to 'ANY'",
                 page.getName());
      }
    }
    LOG.info("---- Global Wiki Site migration: End updating global site wiki '{}' pages permissions updated to use default site permissions",
             pages.size());
  }

  private void updateGlobalSiteWikiPermissions(String globalPortal,
                                               String defaultPortal,
                                               Wiki globalSiteWiki) throws WikiException {
    List<PermissionEntry> wikiDefaultPermissions = getDefaultSitePermissions(defaultPortal);
    List<PermissionEntry> globalSitePermissions = globalSiteWiki.getPermissions();
    if (globalSitePermissions != null && globalSitePermissions.stream()
                                                              .anyMatch(permissionEntry -> StringUtils.equals(IdentityConstants.ANY,
                                                                                                              permissionEntry.getId()))) {
      wikiService.updateWikiPermission(PortalConfig.PORTAL_TYPE, globalPortal, wikiDefaultPermissions);
      LOG.info("---- Global Wiki Site migration: global site wiki permissions updated to use default site permissions");
    } else {
      LOG.info("---- Global Wiki Site migration: global site wiki permissions NOT updated since it doesn't have permissions to 'ANY'");
    }
  }

  private void updatePageEntities(String globalPortal, String defaultPortal) {
    Query updatePageQuery = entityManagerService.getEntityManager()
                                                .createQuery("UPDATE WikiPageEntity p SET p.owner = '" + defaultPortal
                                                    + "' WHERE p.owner = '" + globalPortal + "'");
    int updatedPageLines = updatePageQuery.executeUpdate();
    LOG.info("---- Global Wiki Site migration: {} pages updated", updatedPageLines);
  }

  private void updateWikiEntity(String globalPortal, String defaultPortal) {
    Query updateWikiQuery = entityManagerService.getEntityManager()
                                                .createQuery("UPDATE WikiWikiEntity w SET w.owner = '" + defaultPortal
                                                    + "' WHERE w.owner = '" + globalPortal + "'");
    int updatedWikiLines = updateWikiQuery.executeUpdate();
    LOG.info("---- Global Wiki Site migration: {} wiki updated", updatedWikiLines);
  }

  private void updatePageMoveEntities(String globalPortal, String defaultPortal) {
    EntityManager entityManager = entityManagerService.getEntityManager();
    Query updatePageMoveQuery = entityManager.createQuery("UPDATE WikiPageMoveEntity pm SET pm.wikiOwner = '"
        + defaultPortal
        + "' WHERE pm.wikiOwner = '" + globalPortal + "'");
    int updatedPageMoveLines = updatePageMoveQuery.executeUpdate();
    LOG.info("---- Global Wiki Site migration: {} PageMove updated", updatedPageMoveLines);
  }

  private void restartTransaction() {
    int i = 0;
    // Close transactions until no encapsulated transaction
    boolean success = true;
    do {
      try {
        RequestLifeCycle.end();
        i++;
      } catch (IllegalStateException e) {
        success = false;
      }
    } while (success);

    // Restart transactions with the same number of encapsulations
    for (int j = 0; j < i; j++) {
      RequestLifeCycle.begin(container);
    }
  }

  private List<PermissionEntry> getPageDefaultPermissions(String wikiOwner) throws Exception {// NOSONAR
    Permission[] allPermissions = new Permission[] {
        new Permission(PermissionType.VIEWPAGE, true),
        new Permission(PermissionType.EDITPAGE, true)
    };
    List<PermissionEntry> permissionEntries = new ArrayList<>();
    Iterator<Map.Entry<String, IDType>> iter = Utils.getACLForAdmins().entrySet().iterator();
    while (iter.hasNext()) {
      Map.Entry<String, IDType> entry = iter.next();
      PermissionEntry permissionEntry = new PermissionEntry(entry.getKey(), "", entry.getValue(), allPermissions);
      permissionEntries.add(permissionEntry);
    }
    PortalConfig portalConfig = dataStorage.getPortalConfig(wikiOwner);
    PermissionEntry portalPermissionEntry = new PermissionEntry(portalConfig.getEditPermission(),
                                                                "",
                                                                IDType.MEMBERSHIP,
                                                                allPermissions);
    permissionEntries.add(portalPermissionEntry);
    String[] accessPermissions = portalConfig.getAccessPermissions();
    if (accessPermissions != null && accessPermissions.length > 0) {
      Permission[] viewPermissions = new Permission[] {
          new Permission(PermissionType.VIEWPAGE, true),
          new Permission(PermissionType.EDITPAGE, false)
      };

      for (String permissionExpression : accessPermissions) {
        IDType idType = null;
        if (StringUtils.equals(UserACL.EVERYONE, permissionExpression)) {
          // Avoid to store ANY in wiki pages
          continue;
        } else if (StringUtils.contains(permissionExpression, "/") && StringUtils.contains(permissionExpression, ":")) {
          idType = IDType.MEMBERSHIP;
        } else if (StringUtils.contains(permissionExpression, "/")) {
          idType = IDType.GROUP;
        } else {
          idType = IDType.USER;
        }
        PermissionEntry accessPermissionEntry = new PermissionEntry(permissionExpression,
                                                                    "",
                                                                    idType,
                                                                    viewPermissions);
        permissionEntries.add(accessPermissionEntry);
      }
    }
    return permissionEntries;
  }

}
