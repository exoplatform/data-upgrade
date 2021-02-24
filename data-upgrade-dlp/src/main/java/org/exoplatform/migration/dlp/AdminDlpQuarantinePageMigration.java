package org.exoplatform.migration.dlp;

import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.portal.config.model.PortalConfig;
import org.exoplatform.portal.mop.SiteKey;
import org.exoplatform.portal.mop.navigation.NavigationContext;
import org.exoplatform.portal.mop.navigation.NavigationService;
import org.exoplatform.portal.mop.navigation.NodeContext;
import org.exoplatform.portal.mop.navigation.NodeModel;
import org.exoplatform.portal.mop.navigation.Scope;
import org.exoplatform.portal.mop.page.PageKey;
import org.exoplatform.portal.mop.page.PageService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class AdminDlpQuarantinePageMigration extends UpgradeProductPlugin {

  private static final String ADMINISTRATOR_GROUP = "/platform/administrators";

  private static final String DLP_QUARANTINE      = "dlp-quarantine";

  private static final Log    LOG                 = ExoLogger.getExoLogger(AdminDlpQuarantinePageMigration.class);

  private NavigationService   navigationService;

  private PageService         pageService;

  public AdminDlpQuarantinePageMigration(InitParams initParams, NavigationService navigationService, PageService pageService) {
    super(initParams);
    this.navigationService = navigationService;
    this.pageService = pageService;
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    NavigationContext administratorsNavigation = navigationService.loadNavigation(SiteKey.group(ADMINISTRATOR_GROUP));
    if (administratorsNavigation != null) {
      long startupTime = System.currentTimeMillis();
      LOG.info("Start upgrade of admin dlp quarantine page");
      NodeContext administratorsNodeContext = navigationService.loadNode(NodeModel.SELF_MODEL,
                                                                         administratorsNavigation,
                                                                         Scope.ALL,
                                                                         null);
      if (administratorsNodeContext.get(DLP_QUARANTINE) != null) {
        administratorsNodeContext.removeNode(DLP_QUARANTINE);
        navigationService.saveNavigation(administratorsNavigation);
        navigationService.saveNode(administratorsNodeContext, null);
        String dlpQuarantinePageKey = PortalConfig.GROUP_TYPE + "::" + ADMINISTRATOR_GROUP + "::" + DLP_QUARANTINE;
        pageService.destroyPage(PageKey.parse(dlpQuarantinePageKey));
      }
      LOG.info("End upgrade of admin dlp quarantine page. It took {} ms", (System.currentTimeMillis() - startupTime));
    }
  }
}
