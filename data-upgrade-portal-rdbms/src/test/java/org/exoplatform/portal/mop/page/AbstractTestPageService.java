package org.exoplatform.portal.mop.page;

import org.exoplatform.commons.chromattic.ChromatticManager;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.portal.AbstractJCRImplTest;
import org.exoplatform.portal.config.DataStorage;
import org.exoplatform.portal.mop.SiteKey;
import org.exoplatform.portal.pom.config.POMSessionManager;
import org.exoplatform.services.cache.CacheService;
import org.exoplatform.services.jcr.RepositoryService;

/** @author <a href="mailto:julien.viet@exoplatform.com">Julien Viet</a> */
public class AbstractTestPageService extends AbstractJCRImplTest {

    /** . */
    static final SiteKey CLASSIC = SiteKey.portal("classic");

    /** . */
    static final PageKey CLASSIC_HOMEPAGE = CLASSIC.page("homepage");

    /** . */
    static final PageKey CLASSIC_FOO = CLASSIC.page("foo");

    /** . */
    protected POMSessionManager mgr;

    /** . */
    protected PageServiceImpl service;

    /** . */
    protected DataStorage dataStorage;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mgr = new POMSessionManager(getService(RepositoryService.class),
                                    getService(ChromatticManager.class),
                                    getService(CacheService.class));
        mgr.start();

        //
        service = new PageServiceImpl(mgr);
    }
}
