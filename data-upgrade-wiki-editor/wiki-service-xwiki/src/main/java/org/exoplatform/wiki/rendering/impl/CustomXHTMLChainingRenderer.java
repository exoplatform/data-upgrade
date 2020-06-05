package org.exoplatform.wiki.rendering.impl;

import org.xwiki.rendering.internal.renderer.xhtml.XHTMLChainingRenderer;
import org.xwiki.rendering.internal.renderer.xhtml.image.XHTMLImageRenderer;
import org.xwiki.rendering.internal.renderer.xhtml.link.XHTMLLinkRenderer;
import org.xwiki.rendering.listener.chaining.ListenerChain;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;

import java.util.Map;

/**
 * Custom HTML Renderer to change the way tables are rendered.
 * CKEditor expects tables to be as follows:
 * <pre>
 *   &#60;figure&#62;
 *     &#60;table&#62;
 *       &#60;thead&#62;
 *         &#60;tr&#62;...&#60;/tr&#62;
 *       &#60;/thead&#62;
 *       &#60;tbody&#62;
 *         &#60;tr&#62;...&#60;/tr&#62;
 *       &#60;/tbody&#62;
 *     &#60;/table&#62;
 *   &#60;/figure&#62;
 * </pre>
 * whereas XWiki renders them by default as follows:
 * <pre>
 *   &#60;table&#62;
 *     &#60;tr&#62;...&#60;/tr&#62;
 *     &#60;tr&#62;...&#60;/tr&#62;
 *   &#60;/table&#62;
 * </pre>
 * so this custom renderer change the way tables are rendered to be CKEditor-compatible.
 */
public class CustomXHTMLChainingRenderer extends XHTMLChainingRenderer {
  private int rowCount = 0;

  public CustomXHTMLChainingRenderer(XHTMLLinkRenderer linkRenderer, XHTMLImageRenderer imageRenderer, ListenerChain listenerChain) {
    super(linkRenderer, imageRenderer, listenerChain);
  }

  @Override
  public void beginTable(Map<String, String> parameters) {
    this.getXHTMLWikiPrinter().printXMLStartElement("figure", parameters);
    rowCount = 0;
    super.beginTable(parameters);
  }

  @Override
  public void endTable(Map<String, String> parameters) {
    this.getXHTMLWikiPrinter().printXMLEndElement("tbody");
    super.endTable(parameters);
    this.getXHTMLWikiPrinter().printXMLEndElement("figure");
  }

  @Override
  public void beginTableRow(Map<String, String> parameters) {
    if(rowCount == 0) {
      this.getXHTMLWikiPrinter().printXMLStartElement("thead");
    } else if(rowCount == 1) {
      this.getXHTMLWikiPrinter().printXMLStartElement("tbody");
    }
    super.beginTableRow(parameters);
  }

  @Override
  public void endTableRow(Map<String, String> parameters) {
    super.endTableRow(parameters);
    if(rowCount == 0) {
      this.getXHTMLWikiPrinter().printXMLEndElement("thead");
    }
    rowCount++;
  }

  @Override
  public void onImage(ResourceReference reference, boolean isFreeStandingURI, Map<String, String> parameters) {
    if(reference.getType().equals(ResourceType.ICON)) {
      switch (reference.getReference()) {
        case "emoticon_smile": this.getXHTMLWikiPrinter().printRaw("&#x1F642️"); break;
        case "emoticon_unhappy": this.getXHTMLWikiPrinter().printRaw("&#x1F641️"); break;
        case "emoticon_tongue": this.getXHTMLWikiPrinter().printRaw("&#x1F61B;"); break;
        case "emoticon_grin": this.getXHTMLWikiPrinter().printRaw("&#x1F600;"); break;
        case "emoticon_wink": this.getXHTMLWikiPrinter().printRaw("&#x1F609;"); break;
        case "thumb_up": this.getXHTMLWikiPrinter().printRaw("&#x1F44D;"); break;
        case "thumb_down": this.getXHTMLWikiPrinter().printRaw("&#x1F44E;"); break;
        case "information": this.getXHTMLWikiPrinter().printRaw("ℹ️"); break;
        case "accept": this.getXHTMLWikiPrinter().printRaw("✅️"); break;
        case "cancel": this.getXHTMLWikiPrinter().printRaw("❌"); break;
        case "error": this.getXHTMLWikiPrinter().printRaw("⚠️"); break;
        case "add": this.getXHTMLWikiPrinter().printRaw("➕"); break;
        case "delete": this.getXHTMLWikiPrinter().printRaw("➖"); break;
        case "help": this.getXHTMLWikiPrinter().printRaw("❓"); break;
        case "lightbulb": this.getXHTMLWikiPrinter().printRaw("&#x1F4A1;"); break;
        case "lightbulb_off": this.getXHTMLWikiPrinter().printRaw("&#x1F3C1;"); break;
        case "star": this.getXHTMLWikiPrinter().printRaw("⭐"); break;
        default: break;
      }
    } else {
      super.onImage(reference, isFreeStandingURI, parameters);
    }
  }
}
