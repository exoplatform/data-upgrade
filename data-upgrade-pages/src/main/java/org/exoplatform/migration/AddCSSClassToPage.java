package org.exoplatform.migration;

import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.portal.config.model.Container;
import org.exoplatform.portal.config.model.ModelObject;
import org.exoplatform.portal.config.model.Page;
import org.exoplatform.portal.mop.SiteType;
import org.exoplatform.portal.mop.page.PageKey;
import org.exoplatform.portal.mop.storage.PageStorage;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class AddCSSClassToPage extends UpgradeProductPlugin {

  private static final Log LOG          = ExoLogger.getExoLogger(AddCSSClassToPage.class);

  private PageStorage      pageStorage;

  private String           cssClasses;

  private String           siteName;

  private String           pageName;

  private String           containerName;

  private String           CONTAINER_ID = "container-id";

  private String           CSS_CLASSES  = "css-classes";

  private String           SITE_NAME    = "site-name";

  private String           PAGE_NAME    = "page-name";

  public AddCSSClassToPage(PageStorage pageStorage, InitParams initParams) {
    super(initParams);
    this.pageStorage = pageStorage;
    if (initParams.containsKey(SITE_NAME) && !initParams.getValueParam(SITE_NAME).getValue().isBlank()) {
      siteName = initParams.getValueParam(SITE_NAME).getValue();
    }
    if (initParams.containsKey(CONTAINER_ID) && !initParams.getValueParam(CONTAINER_ID).getValue().isBlank()) {
      containerName = initParams.getValueParam(CONTAINER_ID).getValue();
    }
    if (initParams.containsKey(CSS_CLASSES) && !initParams.getValueParam(CSS_CLASSES).getValue().isBlank()) {
      cssClasses = initParams.getValueParam(CSS_CLASSES).getValue();
    }
    if (initParams.containsKey(PAGE_NAME) && !initParams.getValueParam(PAGE_NAME).getValue().isBlank()) {
      pageName = initParams.getValueParam(PAGE_NAME).getValue();
    }
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    LOG.info("Start upgrade : adding CSS class {} to {} page", cssClasses, pageName);

    if (StringUtils.isBlank(siteName) || StringUtils.isBlank(pageName) || StringUtils.isBlank(containerName)
        || StringUtils.isBlank(cssClasses)) {
      LOG.warn("upgrade canceled : missing required parameters");
      return;
    }

    long startupTime = System.currentTimeMillis();

    PortalContainer portalContainer = PortalContainer.getInstance();
    RequestLifeCycle.begin(portalContainer);

    try {
      Page page = pageStorage.getPage(new PageKey(SiteType.PORTAL.getName(), siteName, pageName));
      for (ModelObject child : page.getChildren()) {
        if (child instanceof Container container && containerName.equals(container.getId())) {
          container.setCssClass(cssClasses);
        }
      }
      pageStorage.save(page.build());
    } catch (Exception e) {
      LOG.error("Upgrade error : Failed to update the page {}", pageName, e);
    } finally {
      LOG.info("End upgrade : adding CSS class {} to {} page. It took {} ms",
               cssClasses,
               pageName,
               (System.currentTimeMillis() - startupTime));
    }
  }
}
