package org.exoplatform.portal;

import org.exoplatform.application.registry.ApplicationRegistryService;
import org.exoplatform.application.registry.impl.ApplicationRegistryServiceImpl;
import org.exoplatform.commons.chromattic.ChromatticManager;
import org.exoplatform.commons.chromattic.Synchronization;
import org.exoplatform.component.test.*;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.portal.config.DataStorage;
import org.exoplatform.portal.config.DataStorageImpl;
import org.exoplatform.portal.jdbc.migration.MigrationContext;
import org.exoplatform.portal.mop.description.DescriptionService;
import org.exoplatform.portal.mop.navigation.NavigationService;
import org.exoplatform.portal.mop.navigation.NavigationServiceWrapper;
import org.exoplatform.portal.mop.page.*;
import org.exoplatform.portal.pom.config.POMDataStorage;
import org.exoplatform.portal.pom.data.ModelDataStorage;

@ConfiguredBy({
  @ConfigurationUnit(scope = ContainerScope.ROOT, path = "conf/configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/portal/configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/exo.portal.component.identity-configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/exo.portal.component.portal-configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/exo.portal.component.application-registry-configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/test.mop.portal.configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/exo.portal.component.test.chromattic-impl-configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/exo.portal.component.test.no-migration-configuration.xml"),
})
public abstract class AbstractJCRImplTest extends AbstractKernelTest {

  public AbstractJCRImplTest() {
  }

  public AbstractJCRImplTest(String name) {
    super(name);
  }

  @Override
  protected void end() {
    end(false);
  }

  protected void end(boolean save) {
    PortalContainer container = getContainer();
    ChromatticManager manager = container.getComponentInstanceOfType(ChromatticManager.class);
    Synchronization synchronization = manager.getSynchronization();
    if (synchronization != null) {
      synchronization.setSaveOnClose(save);
    }
    super.end();
  }

  protected final void sync() {
    restartTransaction(false);
  }

  protected final void sync(boolean save) {
    restartTransaction(save);
  }

  @Override
  protected void setUp() throws Exception {
    begin();

    ApplicationRegistryService applicationRegistryService = getService(ApplicationRegistryService.class);
    NavigationService navigationService = getService(NavigationService.class);
    PageService pageService = getService(PageService.class);
    ModelDataStorage dataStorage = getService(ModelDataStorage.class);
    DescriptionService descriptionService = getService(DescriptionService.class);

    assertTrue("Implementation should be of Chromattic and not RDBMS in current tests. ApplicationRegistryService of type " + applicationRegistryService.getClass().getName(), applicationRegistryService instanceof ApplicationRegistryServiceImpl);
    assertTrue("Implementation should be of Chromattic and not RDBMS in current tests. NavigationService of type " + navigationService.getClass().getName(), navigationService instanceof NavigationServiceWrapper);
    assertTrue("Implementation should be of Chromattic and not RDBMS in current tests. PageService of type " + pageService.getClass().getName(), pageService instanceof PageServiceWrapper);
    assertTrue("Implementation should be of Chromattic and not RDBMS in current tests. ModelDataStorage of type " + dataStorage.getClass().getName(), dataStorage instanceof POMDataStorage);
    assertTrue("Implementation should be of Chromattic and not RDBMS in current tests. DescriptionService of type " + descriptionService.getClass().getName(), descriptionService instanceof DescriptionService);
  }

  @Override
  protected void tearDown() throws Exception {
    end();
  }

  protected <T> T getService(Class<T> clazz) {
    return getContainer().getComponentInstanceOfType(clazz);
  }

  protected void restartTransaction(boolean save) {
    int i = 0;
    // Close transactions until no encapsulated transaction
    boolean success = true;
    do {
      try {
        end(save);
        i++;
      } catch (IllegalStateException e) {
        success = false;
      }
    } while (success);

    // Restart transactions with the same number of encapsulations
    for (int j = 0; j < i; j++) {
      begin();
    }
  }
}
