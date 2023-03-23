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

  private final PageStorage      pageStorage;

  private String           cssClasses;

  private String           siteName;

  private String           pageName;

  private String containerId;

  public AddCSSClassToPage(PageStorage pageStorage, InitParams initParams) {
    super(initParams);
    this.pageStorage = pageStorage;
    String siteNameParam = "site-name";
    if (initParams.containsKey(siteNameParam) && !initParams.getValueParam(siteNameParam).getValue().isBlank()) {
      siteName = initParams.getValueParam(siteNameParam).getValue();
    }
    String containerIdParam = "container-id";
    if (initParams.containsKey(containerIdParam) && !initParams.getValueParam(containerIdParam).getValue().isBlank()) {
      containerId = initParams.getValueParam(containerIdParam).getValue();
    }
    String cssClassesParam = "css-classes";
    if (initParams.containsKey(cssClassesParam) && !initParams.getValueParam(cssClassesParam).getValue().isBlank()) {
      cssClasses = initParams.getValueParam(cssClassesParam).getValue();
    }
    String pageNameParam = "page-name";
    if (initParams.containsKey(pageNameParam) && !initParams.getValueParam(pageNameParam).getValue().isBlank()) {
      pageName = initParams.getValueParam(pageNameParam).getValue();
    }
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    LOG.info("Start upgrade : adding CSS class {} to {} page", cssClasses, pageName);

    if (StringUtils.isBlank(siteName) || StringUtils.isBlank(pageName) || StringUtils.isBlank(containerId)
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
        if (child instanceof Container container && containerId.equals(container.getId())) {
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
