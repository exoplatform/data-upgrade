package org.exoplatform.wiki.rendering.macro.related;

import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.wiki.service.WikiContext;
import org.exoplatform.wiki.service.WikiPageParams;
import org.exoplatform.wiki.service.WikiService;
import org.exoplatform.wiki.service.related.RelatedUtil;
import org.exoplatform.wiki.tree.TreeNode;
import org.xwiki.component.annotation.Component;
import org.xwiki.context.Execution;
import org.xwiki.context.ExecutionContext;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.RawBlock;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.syntax.SyntaxType;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import javax.inject.Inject;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.List;

@Component("related")
public class RelatedMacro extends AbstractMacro<RelatedPagesMacroParameters>{
  
  private static final Log      log_               = ExoLogger.getLogger(RelatedMacro.class);
  
  /**
   * The description of the macro.
   */
  private static final String DESCRIPTION = "render related pages of current page";
  
  private static final Syntax XHTML_SYNTAX = new Syntax(SyntaxType.XHTML, "1.0");
  
  @Inject
  private Execution execution;
  
  public RelatedMacro() {
    super("Related pages", DESCRIPTION, RelatedPagesMacroParameters.class);
    setDefaultCategory(DEFAULT_CATEGORY_NAVIGATION);
  }
  
  @Override
  public List<Block> execute(RelatedPagesMacroParameters parameters,
                             String content,
                             MacroTransformationContext context) throws MacroExecutionException {
    WikiContext params = getWikiContext();
    if (params == null) {
      return Collections.emptyList();
    }
    
    Block block = null;
    try {
      block = new RawBlock(createRelationList(params), XHTML_SYNTAX);
      WikiService wikiService = ExoContainerContext.getCurrentContainer()
              .getComponentInstanceOfType(WikiService.class);
      WikiContext wikiContext = getWikiContext();
      wikiService.addPageLink(new WikiPageParams(wikiContext.getType(), wikiContext.getOwner(), wikiContext.getPageName()),
              new WikiPageParams(params.getType(), params.getOwner(), params.getPageName()));
    } catch (Exception e) {
      if (log_.isWarnEnabled()) log_.warn("generate related macro failed", e);
      return Collections.emptyList();
    }
    return Collections.singletonList(block);
  }

  String createRelationList(WikiContext context) throws Exception {
    String redirectURI = context.getRedirectURI();
    StringBuilder restUri = new StringBuilder();
    StringBuilder treeBuilder = new StringBuilder();
    
    String pathParam = URLEncoder.encode(RelatedUtil.getPath(context), "UTF-8");
    restUri.append(context.getRestURI())
      .append("/wiki/related/")
      .append("?")
      .append(TreeNode.PATH)
      .append("=")
      .append(pathParam);
    treeBuilder.append("<div class=\"UITreeExplorer RelatedMacro\">")
      .append("   <input class='info' title=\"hidden\" type='hidden' restUrl='").append(restUri.toString()).append("' redirectUrl='").append(redirectURI).append("' />")
      .append("</div>");
    return treeBuilder.toString();
  }
  
  @Override
  public boolean supportsInlineMode() {
    return true;
  }
  
  private WikiContext getWikiContext() {
    ExecutionContext ec = execution.getContext();
    if (ec != null) {
      WikiContext wikiContext = (WikiContext) ec.getProperty(WikiContext.WIKICONTEXT);
      return wikiContext;
    }
    return null;
  }
  
}
