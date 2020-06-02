package org.exoplatform.wiki.rendering.impl;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.rendering.internal.renderer.xhtml.image.XHTMLImageRenderer;
import org.xwiki.rendering.internal.renderer.xhtml.link.XHTMLLinkRenderer;
import org.xwiki.rendering.listener.chaining.BlockStateChainingListener;
import org.xwiki.rendering.listener.chaining.EmptyBlockChainingListener;
import org.xwiki.rendering.listener.chaining.ListenerChain;
import org.xwiki.rendering.listener.chaining.MetaDataStateChainingListener;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Custom XHTMLRenderer to declare CustomXHTMLChainingRenderer
 */
@Component
@Named("xhtml/1.0")
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
public class XHTMLRenderer extends org.xwiki.rendering.internal.renderer.xhtml.XHTMLRenderer {
  @Inject
  private XHTMLLinkRenderer linkRenderer;
  @Inject
  private XHTMLImageRenderer imageRenderer;

  public void initialize() throws InitializationException {
    ListenerChain chain = new ListenerChain();
    this.setListenerChain(chain);
    chain.addListener(this);
    chain.addListener(new BlockStateChainingListener(chain));
    chain.addListener(new EmptyBlockChainingListener(chain));
    chain.addListener(new MetaDataStateChainingListener(chain));
    chain.addListener(new CustomXHTMLChainingRenderer(this.linkRenderer, this.imageRenderer, chain));
  }
}
