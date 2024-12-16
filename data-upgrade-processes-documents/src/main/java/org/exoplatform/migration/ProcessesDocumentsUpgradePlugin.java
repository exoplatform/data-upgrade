package org.exoplatform.migration;

import jakarta.persistence.EntityManager;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradePluginExecutionContext;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.onlyoffice.OnlyofficeEditorService;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.attachments.model.AttachmentContextEntity;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.container.xml.InitParams;

import javax.jcr.ItemExistsException;
import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProcessesDocumentsUpgradePlugin  extends UpgradeProductPlugin {
  private static final Log      log = ExoLogger.getExoLogger(ProcessesDocumentsUpgradePlugin.class);

  private RepositoryService       repositoryService;
  private OnlyofficeEditorService onlyofficeEditorService;
  private EntityManagerService entityManagerService;
  private UserACL              userACL;

  public ProcessesDocumentsUpgradePlugin(EntityManagerService entityManagerService, RepositoryService repositoryService, OnlyofficeEditorService onlyofficeEditorService, UserACL userACL, InitParams initParams) {
    super(initParams);
    this.repositoryService = repositoryService;
    this.onlyofficeEditorService = onlyofficeEditorService;
    this.userACL = userACL;
    this.entityManagerService = entityManagerService;
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    long startupTime = System.currentTimeMillis();
    log.info("Start Processes documents upgrade");

    List<String> errorPath = new ArrayList<>();

    try {


      List<AttachmentContextEntity> attachments = getAllAttachments();

      SessionProvider sessionProvider = SessionProvider.createSystemProvider();
      Session session = sessionProvider.getSession("collaboration", repositoryService.getDefaultRepository());

      Map<String,String> attachmentIdReplacement = new HashMap<>();

      long total = attachments.size();
      int currentIndex=0;
      log.info("Processes documents upgrade Select query executed in {} ms, {} elements to check", System.currentTimeMillis()-startupTime, total);

      for (AttachmentContextEntity attachment : attachments) {
        if (!attachmentIdReplacement.containsKey(attachment.getAttachmentId())) {

          try {
            Node file = session.getNodeByUUID(attachment.getAttachmentId());


            log.debug("Proceed node {}", file.getPath());

            if (!file.getPath().startsWith("/Trash") && file.isNodeType("nt:file") && (file.getName().endsWith(".docxf")
                || file.getName().endsWith(".oform"))) {
              byte[] convertedContent;
              String owner = userACL.getSuperUser();
              convertedContent = this.onlyofficeEditorService.convertNodeContentToPdf(file, owner);
              if (convertedContent != null) {
                String newFileUuid = createNewFile(convertedContent, file);
                attachmentIdReplacement.put(attachment.getAttachmentId(), newFileUuid);
              } else {
                errorPath.add(file.getPath());
              }

            }
          } catch(ItemNotFoundException e) {
            log.warn("Node with uuid {} not exists in collaboration workspace", attachment.getAttachmentId());
          } catch (UnsupportedRepositoryOperationException e) {
            log.warn("Node with uuid {} cannot be read (must be a symlink, ignore it)", attachment.getAttachmentId());
          } catch (ItemExistsException e) {
            log.warn("Node with uuid {} already converted", attachment.getAttachmentId());
          }
        }
        currentIndex++;
        if (currentIndex % 100 == 0) {
          log.info("{}/{} elements processed", currentIndex, total);
        }
      }

      log.info("{} documents converted. Start step 2 to update attachment table", attachmentIdReplacement.size());

      attachmentIdReplacement.forEach(this::updateAttachementUuId);

      if (!errorPath.isEmpty()) {
        throw new IllegalStateException(String.format("End converting documents. %s documents converted. There are %s errors for files %s. It took %s ms.",
                                                      attachmentIdReplacement.size(),
                                                      errorPath.size(),
                                                      errorPath,
                                                      (System.currentTimeMillis() - startupTime)));
      } else {
        log.info("End Processes documents upgrade, execution time={}ms, {} elements processed, {} converted",
                 System.currentTimeMillis() - startupTime,
                 total,
                 attachmentIdReplacement.size());
      }
    } catch (Exception e) {
      log.error("Error when updating document for OO 8",e);
      throw new IllegalStateException("Upgrade Plugin not executed");
    }
  }

  private String createNewFile(byte[] convertedContent, Node file) throws Exception {
    log.debug("File converted in a byte array of lenght={}", convertedContent.length);
    Node newNode = duplicateItem(file, file.getParent(), file.getParent());
    Node jcrContent = newNode.getNode("jcr:content");
    InputStream stream = new ByteArrayInputStream(convertedContent);
    jcrContent.setProperty("jcr:mimeType","application/pdf");
    jcrContent.setProperty("jcr:data", stream);
    file.getParent().save();
    return newNode.getUUID();
  }

  public boolean shouldProceedToUpgrade(String newVersion, String previousGroupVersion, UpgradePluginExecutionContext previousUpgradePluginExecution) {
    int executionCount = previousUpgradePluginExecution == null ? 0 : previousUpgradePluginExecution.getExecutionCount();
    return !isExecuteOnlyOnce() || executionCount == 0;
  }

  private List<AttachmentContextEntity> getEntityAttached(String uuid) {
    RequestLifeCycle.begin(this.entityManagerService);
    EntityManager entityManager = this.entityManagerService.getEntityManager();
    List<AttachmentContextEntity> results = new ArrayList<>();
    try {
      String sqlString = "SELECT * FROM EXO_ATTACHMENTS_CONTEXT WHERE ATTACHMENT_ID = '"+uuid+"'";
      jakarta.persistence.Query query = entityManager.createNativeQuery(sqlString, AttachmentContextEntity.class);
      results = query.getResultList();
    } catch (Exception e) {
      log.error("Error when reading attachment entity for file with id={}", uuid,e);
    } finally {
      RequestLifeCycle.end();
    }
    return results;
  }

  private List<AttachmentContextEntity> getAllAttachments() {
    RequestLifeCycle.begin(this.entityManagerService);
    EntityManager entityManager = this.entityManagerService.getEntityManager();
    List<AttachmentContextEntity> results = new ArrayList<>();
    try {
      String sqlString = "SELECT * FROM EXO_ATTACHMENTS_CONTEXT";
      jakarta.persistence.Query query = entityManager.createNativeQuery(sqlString, AttachmentContextEntity.class);
      results = query.getResultList();
    } catch (Exception e) {
      log.error("Error when reading all attachments entity",e);
    } finally {
      RequestLifeCycle.end();
    }
    return results;
  }

private void updateAttachementUuId(String oldUUID, String newUUID) {
    RequestLifeCycle.begin(this.entityManagerService);
    EntityManager entityManager = this.entityManagerService.getEntityManager();
    try {
      entityManager.getTransaction().begin();
      String sqlString = "UPDATE EXO_ATTACHMENTS_CONTEXT SET ATTACHMENT_ID = '"+newUUID+"' WHERE ATTACHMENT_ID='"+oldUUID+"'";
      jakarta.persistence.Query query = entityManager.createNativeQuery(sqlString, AttachmentContextEntity.class);
      query.executeUpdate();
      entityManager.getTransaction().commit();
    } catch (Exception e) {
      log.error("Error when updating attachment for attachmentContext with uuid={}", oldUUID,e);
    } finally {
      RequestLifeCycle.end();
    }
  }


  private Node duplicateItem(Node oldNode, Node destinationNode, Node parentNode) throws Exception{
    Node newNode;
    String name = oldNode.getName();
    String title = oldNode.getProperty("exo:title").getString();
    if (((NodeImpl) destinationNode).getIdentifier().equals(((NodeImpl) parentNode).getIdentifier())){
      name = name.replace(".docxf", ".pdf");
      name = name.replace(".oform", ".pdf");
      title = title.replace(".docxf", ".pdf");
      title = title.replace(".oform", ".pdf");
      String newName = name;
      int i =0;
      while((destinationNode.hasNode(newName))){
        i++;
        newName = name + " (" + i + ")";
      }
      name = newName.toLowerCase();
      if(i>0){
        title = title + " (" + i + ")";
      }
    }
    name = URLDecoder.decode(name, "UTF-8");
    newNode = destinationNode.addNode(name, oldNode.getPrimaryNodeType().getName());
    addProperties(oldNode,newNode,title);
    return newNode;
  }

  private void addProperties(Node oldNode, Node newNode, String title) throws RepositoryException {
    if (oldNode.isNodeType("mix:versionable") && !newNode.isNodeType("mix:versionable"))
      newNode.addMixin("mix:versionable");

    if (oldNode.isNodeType("mix:referenceable") && !newNode.isNodeType("mix:referenceable"))
      newNode.addMixin("mix:referenceable");

    if (oldNode.isNodeType("mix:commentable") && !newNode.isNodeType("mix:commentable"))
      newNode.addMixin("mix:commentable");

    if (oldNode.isNodeType("mix:votable") && !newNode.isNodeType("mix:votable"))
      newNode.addMixin("mix:votable");

    if (oldNode.isNodeType("mix:i18n") && !newNode.isNodeType("mix:i18n"))
      newNode.addMixin("mix:i18n");

    newNode.setProperty("exo:title", title);
    newNode.setProperty("exo:lastModifier",oldNode.getProperty("exo:lastModifier").getString());
    newNode.setProperty("exo:dateCreated", oldNode.getProperty("exo:dateCreated").getDate());
    newNode.setProperty("exo:dateModified", oldNode.getProperty("exo:dateModified").getDate());
    newNode.setProperty("exo:lastModifiedDate", oldNode.getProperty("exo:lastModifiedDate").getDate());
    if(oldNode.hasNode("jcr:content")){
      Node resourceNode = newNode.addNode("jcr:content", "nt:resource");
      resourceNode.setProperty("jcr:data",
                               oldNode.getNode("jcr:content")
                                      .getProperty("jcr:data")
                                      .getStream());
      resourceNode.setProperty("jcr:mimeType",
                               oldNode.getNode("jcr:content")
                                      .getProperty("jcr:mimeType")
                                      .getString());
      resourceNode.setProperty("jcr:lastModified", oldNode.getNode("jcr:content").getProperty("jcr:lastModified").getDate());
      resourceNode.setProperty("exo:dateModified", oldNode.getNode("jcr:content").getProperty("exo:dateModified").getDate());
      resourceNode.setProperty("exo:dateCreated", oldNode.getNode("jcr:content").getProperty("exo:dateCreated").getDate());
    }
  }

}
