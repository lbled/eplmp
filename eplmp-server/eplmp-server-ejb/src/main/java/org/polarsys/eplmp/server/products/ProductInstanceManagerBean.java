/*******************************************************************************
  * Copyright (c) 2017 DocDoku.
  * All rights reserved. This program and the accompanying materials
  * are made available under the terms of the Eclipse Public License v1.0
  * which accompanies this distribution, and is available at
  * http://www.eclipse.org/legal/epl-v10.html
  *
  * Contributors:
  *    DocDoku - initial API and implementation
  *******************************************************************************/

package org.polarsys.eplmp.server.products;

import org.polarsys.eplmp.core.common.BinaryResource;
import org.polarsys.eplmp.core.common.User;
import org.polarsys.eplmp.core.configuration.*;
import org.polarsys.eplmp.core.document.DocumentLink;
import org.polarsys.eplmp.core.document.DocumentRevisionKey;
import org.polarsys.eplmp.core.exceptions.*;
import org.polarsys.eplmp.core.meta.InstanceAttribute;
import org.polarsys.eplmp.core.product.*;
import org.polarsys.eplmp.core.security.ACL;
import org.polarsys.eplmp.core.security.UserGroupMapping;
import org.polarsys.eplmp.core.services.IBinaryStorageManagerLocal;
import org.polarsys.eplmp.core.services.IProductInstanceManagerLocal;
import org.polarsys.eplmp.core.services.IUserManagerLocal;
import org.polarsys.eplmp.core.util.NamingConvention;
import org.polarsys.eplmp.core.util.Tools;
import org.polarsys.eplmp.server.LogDocument;
import org.polarsys.eplmp.server.configuration.PSFilterVisitor;
import org.polarsys.eplmp.server.configuration.spec.ProductBaselineConfigSpec;
import org.polarsys.eplmp.server.dao.*;
import org.polarsys.eplmp.server.factory.ACLFactory;
import org.polarsys.eplmp.server.validation.AttributesConsistencyUtils;

import javax.annotation.Resource;
import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Local;
import javax.ejb.SessionContext;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@DeclareRoles({UserGroupMapping.REGULAR_USER_ROLE_ID, UserGroupMapping.ADMIN_ROLE_ID})
@Local(IProductInstanceManagerLocal.class)
@Stateless(name = "ProductInstanceManagerBean")
public class ProductInstanceManagerBean implements IProductInstanceManagerLocal {

    @PersistenceContext
    private EntityManager em;

    @Inject
    private ACLDAO aclDAO;

    @Inject
    private ACLFactory aclFactory;

    @Inject
    private BinaryResourceDAO binaryResourceDAO;

    @Inject
    private ConfigurationItemDAO configurationItemDAO;

    @Inject
    private DocumentCollectionDAO documentCollectionDAO;

    @Inject
    private DocumentLinkDAO documentLinkDAO;

    @Inject
    private DocumentRevisionDAO documentRevisionDAO;

    @Inject
    private PartCollectionDAO partCollectionDAO;

    @Inject
    private PathDataIterationDAO pathDataIterationDAO;

    @Inject
    private PathDataMasterDAO pathDataMasterDAO;

    @Inject
    private PathToPathLinkDAO pathToPathLinkDAO;

    @Inject
    private ProductBaselineDAO productBaselineDAO;

    @Inject
    private ProductInstanceIterationDAO productInstanceIterationDAO;

    @Inject
    private ProductInstanceMasterDAO productInstanceMasterDAO;

    @Resource
    private SessionContext ctx;

    @Inject
    private IUserManagerLocal userManager;

    @Inject
    private IBinaryStorageManagerLocal storageManager;

    private static final Logger LOGGER = Logger.getLogger(ProductInstanceManagerBean.class.getName());

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public List<ProductInstanceMaster> getProductInstanceMasters(String workspaceId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(workspaceId);

        List<ProductInstanceMaster> productInstanceMasters = productInstanceMasterDAO.findProductInstanceMasters(workspaceId);
        ListIterator<ProductInstanceMaster> ite = productInstanceMasters.listIterator();

        while(ite.hasNext()){
            ProductInstanceMaster next = ite.next();
            try {
                checkProductInstanceReadAccess(workspaceId, next, user);
            } catch (AccessRightException e) {
                ite.remove();
            }
        }

        return productInstanceMasters;
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public List<ProductInstanceMaster> getProductInstanceMasters(ConfigurationItemKey configurationItemKey) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(configurationItemKey.getWorkspace());
        List<ProductInstanceMaster> productInstanceMasters = productInstanceMasterDAO.findProductInstanceMasters(configurationItemKey.getId(), configurationItemKey.getWorkspace());

        ListIterator<ProductInstanceMaster> ite = productInstanceMasters.listIterator();

        while(ite.hasNext()){
            ProductInstanceMaster next = ite.next();
            try {
                checkProductInstanceReadAccess(configurationItemKey.getWorkspace(), next, user);
            } catch (AccessRightException e) {
                ite.remove();
            }
        }

        return productInstanceMasters;
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public ProductInstanceMaster getProductInstanceMaster(ProductInstanceMasterKey productInstanceMasterKey) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ProductInstanceMasterNotFoundException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(productInstanceMasterKey.getInstanceOf().getWorkspace());
        return productInstanceMasterDAO.loadProductInstanceMaster(user.getLocale(), productInstanceMasterKey);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public List<ProductInstanceIteration> getProductInstanceIterations(ProductInstanceMasterKey productInstanceMasterKey) throws ProductInstanceMasterNotFoundException, UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(productInstanceMasterKey.getInstanceOf().getWorkspace());
        ProductInstanceMaster productInstanceMaster = productInstanceMasterDAO.loadProductInstanceMaster(user.getLocale(), productInstanceMasterKey);
        return productInstanceMaster.getProductInstanceIterations();
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public ProductInstanceIteration getProductInstanceIteration(ProductInstanceIterationKey productInstanceIterationKey) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ProductInstanceIterationNotFoundException, ProductInstanceMasterNotFoundException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(productInstanceIterationKey.getProductInstanceMaster().getInstanceOf().getWorkspace());
        return productInstanceIterationDAO.loadProductInstanceIteration(user.getLocale(), productInstanceIterationKey);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public ProductInstanceMaster createProductInstance(String workspaceId, ConfigurationItemKey configurationItemKey, String serialNumber, int baselineId, Map<String, String> aclUserEntries, Map<String, String> aclUserGroupEntries, List<InstanceAttribute> attributes, DocumentRevisionKey[] links, String[] documentLinkComments) throws UserNotFoundException, AccessRightException, WorkspaceNotFoundException, ConfigurationItemNotFoundException, BaselineNotFoundException, CreationException, ProductInstanceAlreadyExistsException, NotAllowedException, EntityConstraintException, UserNotActiveException, PathToPathLinkAlreadyExistsException, PartMasterNotFoundException, ProductInstanceMasterNotFoundException, DocumentRevisionNotFoundException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceWriteAccess(configurationItemKey.getWorkspace());
        Locale userLocale = user.getLocale();

        checkNameValidity(serialNumber,userLocale);

        try {// Check if ths product instance already exist
            ProductInstanceMaster productInstanceMaster = productInstanceMasterDAO.loadProductInstanceMaster(userLocale, new ProductInstanceMasterKey(serialNumber, configurationItemKey.getWorkspace(), configurationItemKey.getId()));
            throw new ProductInstanceAlreadyExistsException(userLocale, productInstanceMaster);
        } catch (ProductInstanceMasterNotFoundException e) {
            LOGGER.log(Level.FINEST, null, e);
        }

        ConfigurationItem configurationItem = configurationItemDAO.loadConfigurationItem(userLocale, configurationItemKey);
        ProductInstanceMaster productInstanceMaster = new ProductInstanceMaster(configurationItem, serialNumber);

        if(aclUserEntries != null && !aclUserEntries.isEmpty() || aclUserGroupEntries != null &&  !aclUserGroupEntries.isEmpty()){
            ACL acl = aclFactory.createACL(workspaceId, aclUserEntries, aclUserGroupEntries);
            productInstanceMaster.setAcl(acl);
        }
        Date now = new Date();
        ProductInstanceIteration productInstanceIteration = productInstanceMaster.createNextIteration();
        productInstanceIteration.setIterationNote("Initial");
        productInstanceIteration.setAuthor(user);
        productInstanceIteration.setCreationDate(now);
        productInstanceIteration.setModificationDate(now);

        PartCollection partCollection = new PartCollection();
        partCollectionDAO.createPartCollection(partCollection);
        partCollection.setAuthor(user);
        partCollection.setCreationDate(now);

        DocumentCollection documentCollection = new DocumentCollection();
        documentCollectionDAO.createDocumentCollection(documentCollection);
        documentCollection.setAuthor(user);
        documentCollection.setCreationDate(now);

        ProductBaseline productBaseline = productBaselineDAO.loadBaseline(userLocale, baselineId);
        productInstanceIteration.setBasedOn(productBaseline);
        productInstanceIteration.setSubstituteLinks(new HashSet<>(productBaseline.getSubstituteLinks()));
        productInstanceIteration.setOptionalUsageLinks(new HashSet<>(productBaseline.getOptionalUsageLinks()));

        productInstanceMasterDAO.createProductInstanceMaster(userLocale, productInstanceMaster);

        for (BaselinedPart baselinedPart : productBaseline.getBaselinedParts().values()) {
            partCollection.addBaselinedPart(baselinedPart.getTargetPart());
        }

        for (BaselinedDocument baselinedDocument : productBaseline.getBaselinedDocuments().values()) {
            documentCollection.addBaselinedDocument(baselinedDocument.getTargetDocument());
        }

        productInstanceIteration.setPartCollection(partCollection);
        productInstanceIteration.setDocumentCollection(documentCollection);

        productInstanceIteration.setInstanceAttributes(attributes);

        if (links != null) {
            Set<DocumentLink> currentLinks = new HashSet<>(productInstanceIteration.getLinkedDocuments());

            for (DocumentLink link : currentLinks) {
                productInstanceIteration.getLinkedDocuments().remove(link);
            }

            int counter = 0;
            for (DocumentRevisionKey link : links) {
                DocumentLink newLink = new DocumentLink(documentRevisionDAO.loadDocR(userLocale, link));
                newLink.setComment(documentLinkComments[counter]);
                documentLinkDAO.createLink(newLink);
                productInstanceIteration.getLinkedDocuments().add(newLink);
                counter++;
            }
        }

        copyPathToPathLinks(user,productInstanceIteration);

        return productInstanceMaster;
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public ProductInstanceMaster updateProductInstance(String workspaceId, int iteration, String iterationNote, ConfigurationItemKey configurationItemKey, String serialNumber, int baselineId, List<InstanceAttribute> attributes, DocumentRevisionKey[] links, String[] documentLinkComments) throws ProductInstanceMasterNotFoundException, UserNotFoundException, AccessRightException, WorkspaceNotFoundException, ProductInstanceIterationNotFoundException, UserNotActiveException, BaselineNotFoundException, DocumentRevisionNotFoundException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(workspaceId);
        Locale userLocale = user.getLocale();

        ProductInstanceMasterKey pInstanceIterationKey = new ProductInstanceMasterKey(serialNumber, workspaceId, configurationItemKey.getId());
        ProductInstanceMaster productInstanceMaster = productInstanceMasterDAO.loadProductInstanceMaster(userLocale, pInstanceIterationKey);

        ProductInstanceIteration lastIteration = productInstanceMaster.getLastIteration();

        ProductInstanceIteration productInstanceIteration = productInstanceMaster.getProductInstanceIterations().get(iteration - 1);
        // Check the access to the product instance
        checkProductInstanceWriteAccess(workspaceId, productInstanceMaster, user);

        if (productInstanceIteration != null) {
            productInstanceIteration.setIterationNote(iterationNote);
            productInstanceIteration.setInstanceAttributes(attributes);
            productInstanceIteration.setSubstituteLinks(new HashSet<>(lastIteration.getSubstituteLinks()));
            productInstanceIteration.setOptionalUsageLinks(new HashSet<>(lastIteration.getOptionalUsageLinks()));
            productInstanceIteration.setModificationDate(new Date());
            if (links != null) {

                Set<DocumentLink> currentLinks = new HashSet<>(productInstanceIteration.getLinkedDocuments());

                for (DocumentLink link : currentLinks) {
                    productInstanceIteration.getLinkedDocuments().remove(link);
                }

                int counter = 0;
                for (DocumentRevisionKey link : links) {
                    DocumentLink newLink = new DocumentLink(documentRevisionDAO.loadDocR(userLocale, link));
                    newLink.setComment(documentLinkComments[counter]);
                    documentLinkDAO.createLink(newLink);
                    productInstanceIteration.getLinkedDocuments().add(newLink);
                    counter++;
                }
            }
            ProductBaseline productBaseline = productBaselineDAO.loadBaseline(userLocale, baselineId);
            for (BaselinedDocument baselinedDocument : productBaseline.getBaselinedDocuments().values()) {
                if(!productBaseline.getDocumentCollection().hasBaselinedDocument(baselinedDocument.getTargetDocument().getDocumentRevisionKey())){
                    productBaseline.getDocumentCollection().addBaselinedDocument(baselinedDocument.getTargetDocument());
                }

            }

            return productInstanceMaster;

        } else {
            throw new ProductInstanceIterationNotFoundException(userLocale, new ProductInstanceIterationKey(serialNumber, configurationItemKey.getWorkspace(), configurationItemKey.getId(), iteration));
        }

    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public ProductInstanceMaster rebaseProductInstance(String workspaceId, String serialNumber, ConfigurationItemKey configurationItemKey, int baselineId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ProductInstanceMasterNotFoundException, AccessRightException, BaselineNotFoundException, NotAllowedException, ConfigurationItemNotFoundException, PathToPathLinkAlreadyExistsException, PartMasterNotFoundException, CreationException, EntityConstraintException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(workspaceId);
        Locale userLocale = user.getLocale();
        ProductInstanceMasterKey pInstanceIterationKey = new ProductInstanceMasterKey(serialNumber, workspaceId, configurationItemKey.getId());
        ProductInstanceMaster productInstanceMaster = productInstanceMasterDAO.loadProductInstanceMaster(userLocale, pInstanceIterationKey);

        ProductInstanceIteration lastIteration = productInstanceMaster.getLastIteration();

        // Check the access to the product instance
        checkProductInstanceWriteAccess(workspaceId, productInstanceMaster, user);

        // Load the new baseline
        ProductBaseline baseline = productBaselineDAO.loadBaseline(userLocale, baselineId);

        // Check valid parameters
        // Config key should be baseline product's one, same for product instance
        if (baseline.getConfigurationItem().getKey().equals(configurationItemKey)
                && baseline.getConfigurationItem().getKey().equals(productInstanceMaster.getInstanceOf().getKey())) {


            // Create a new iteration
            ProductInstanceIteration nextIteration = productInstanceMaster.createNextIteration();
            productInstanceIterationDAO.createProductInstanceIteration(nextIteration);

            nextIteration.setIterationNote(lastIteration.getIterationNote());

            Date now = new Date();

            PartCollection partCollection = new PartCollection();
            partCollectionDAO.createPartCollection(partCollection);
            partCollection.setAuthor(user);
            partCollection.setCreationDate(now);

            DocumentCollection documentCollection = new DocumentCollection();
            documentCollectionDAO.createDocumentCollection(documentCollection);
            documentCollection.setAuthor(user);
            documentCollection.setCreationDate(now);

            nextIteration.setAuthor(user);
            nextIteration.setCreationDate(now);
            nextIteration.setModificationDate(now);

            for (BaselinedPart baselinedPart : baseline.getBaselinedParts().values()) {
                partCollection.addBaselinedPart(baselinedPart.getTargetPart());
            }

            for (BaselinedDocument baselinedDocument : baseline.getBaselinedDocuments().values()) {
                documentCollection.addBaselinedDocument(baselinedDocument.getTargetDocument());
            }

            nextIteration.setPartCollection(partCollection);
            nextIteration.setDocumentCollection(documentCollection);

            nextIteration.setBasedOn(baseline);
            nextIteration.setSubstituteLinks(new HashSet<>(baseline.getSubstituteLinks()));
            nextIteration.setOptionalUsageLinks(new HashSet<>(baseline.getOptionalUsageLinks()));

            Set<DocumentLink> linkedDocuments = lastIteration.getLinkedDocuments();
            Set<DocumentLink> newLinks = new HashSet<>();
            for(DocumentLink link : linkedDocuments){
                newLinks.add(link.clone());
            }
            nextIteration.setLinkedDocuments(newLinks);

            copyPathToPathLinks(user, nextIteration);
            copyPathDataMasterList(workspaceId, user, lastIteration, nextIteration);

        } else {
            throw new NotAllowedException(userLocale, "NotAllowedException53");
        }

        return productInstanceMaster;

    }

    private void copyPathDataMasterList(String workspaceId, User user, ProductInstanceIteration lastIteration, ProductInstanceIteration nextIteration) throws NotAllowedException, EntityConstraintException, PartMasterNotFoundException {

        List<PathDataMaster> pathDataMasterList = new ArrayList<>();
        ProductBaseline productBaseline = nextIteration.getBasedOn();
        PartMaster partMaster = productBaseline.getConfigurationItem().getDesignItem();
        String serialNumber = lastIteration.getSerialNumber();

        ProductStructureFilter filter = new ProductBaselineConfigSpec(productBaseline);

        PSFilterVisitor psFilterVisitor = new PSFilterVisitor(em, user, filter) {
            @Override
            public void onIndeterminateVersion(PartMaster partMaster, List<PartIteration> partIterations) throws NotAllowedException {
                // Unused here
            }

            @Override
            public void onIndeterminatePath(List<PartLink> pCurrentPath, List<PartIteration> pCurrentPathPartIterations) {
                // Unused here
            }

            @Override
            public void onUnresolvedPath(List<PartLink> pCurrentPath, List<PartIteration> partIterations) throws NotAllowedException {
                // Unused here
            }

            @Override
            public void onBranchDiscovered(List<PartLink> pCurrentPath, List<PartIteration> copyPartIteration) {
                // Unused here
            }

            @Override
            public void onOptionalPath(List<PartLink> path, List<PartIteration> partIterations) {
                // Unused here
            }

            @Override
            public boolean onPathWalk(List<PartLink> path, List<PartMaster> parts) {
                // Find pathData in previous iteration which is on this path. Copy it.
                String pathAsString = Tools.getPathAsString(path);
                for (PathDataMaster pathDataMaster : lastIteration.getPathDataMasterList()) {
                    if (pathAsString.equals(pathDataMaster.getPath())) {
                        pathDataMasterList.add(clonePathDataMaster(pathDataMaster));
                    }
                }
                return true;
            }

            private PathDataMaster clonePathDataMaster(PathDataMaster pathDataMaster) {
                PathDataMaster clone = new PathDataMaster();

                // Need to persist and flush to get an id
                em.persist(clone);
                em.flush();

                clone.setPath(pathDataMaster.getPath());

                List<PathDataIteration> pathDataIterations = new ArrayList<>();
                for (PathDataIteration pathDataIteration : pathDataMaster.getPathDataIterations()) {
                    PathDataIteration clonedIteration = clonePathDataIteration(workspaceId, clone, pathDataIteration);
                    pathDataIterations.add(clonedIteration);
                }
                clone.setPathDataIterations(pathDataIterations);

                return clone;
            }

            private PathDataIteration clonePathDataIteration(String workspaceId, PathDataMaster newPathDataMaster, PathDataIteration pathDataIteration) {
                PathDataIteration clone = new PathDataIteration();

                clone.setPathDataMaster(newPathDataMaster);
                clone.setDateIteration(pathDataIteration.getDateIteration());
                clone.setIteration(pathDataIteration.getIteration());
                clone.setIterationNote(pathDataIteration.getIterationNote());

                // Attributes
                List<InstanceAttribute> clonedAttributes = new ArrayList<>();
                for (InstanceAttribute attribute : pathDataIteration.getInstanceAttributes()) {
                    InstanceAttribute clonedAttribute = attribute.clone();
                    clonedAttributes.add(clonedAttribute);
                }
                clone.setInstanceAttributes(clonedAttributes);

                // Attached files
                for (BinaryResource sourceFile : pathDataIteration.getAttachedFiles()) {
                    String fileName = sourceFile.getName();
                    long length = sourceFile.getContentLength();
                    Date lastModified = sourceFile.getLastModified();
                    String fullName = workspaceId + "/product-instances/" + serialNumber + "/pathdata/" + newPathDataMaster.getId() + "/iterations/" + pathDataIteration.getIteration() + '/' + fileName;
                    BinaryResource targetFile = new BinaryResource(fullName, length, lastModified);
                    try {
                        copyBinary(sourceFile, targetFile);
                        clone.getAttachedFiles().add(targetFile);
                    } catch (FileAlreadyExistsException | CreationException e) {
                        LOGGER.log(Level.FINEST, null, e);
                    }
                }

                // Linked documents
                Set<DocumentLink> newLinks = new HashSet<>();
                for (DocumentLink documentLink : pathDataIteration.getLinkedDocuments()) {
                    newLinks.add(documentLink.clone());
                }
                clone.setLinkedDocuments(newLinks);

                return clone;
            }

            private void copyBinary(BinaryResource sourceFile, BinaryResource targetFile) throws FileAlreadyExistsException, CreationException {
                binaryResourceDAO.createBinaryResource(user.getLocale(), targetFile);
                try {
                    storageManager.copyData(sourceFile, targetFile);
                } catch (StorageException e) {
                    LOGGER.log(Level.INFO, null, e);
                }
            }

            @Override
            public void onUnresolvedVersion(PartMaster partMaster) {
                // Unused here
            }
        };

        psFilterVisitor.visit(partMaster, -1);

        nextIteration.setPathDataMasterList(pathDataMasterList);

    }

    private void copyPathToPathLinks(User user, ProductInstanceIteration productInstanceIteration) throws PathToPathLinkAlreadyExistsException, CreationException, UserNotFoundException, WorkspaceNotFoundException, UserNotActiveException, ConfigurationItemNotFoundException, BaselineNotFoundException, ProductInstanceMasterNotFoundException, NotAllowedException, EntityConstraintException, PartMasterNotFoundException {
        List<PathToPathLink> links = productInstanceIteration.getBasedOn().getPathToPathLinks();
        for(PathToPathLink link : links){
            PathToPathLink clone = link.clone();
            pathToPathLinkDAO.createPathToPathLink(user.getLocale(), clone);
            productInstanceIteration.addPathToPathLink(clone);
        }
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public ProductInstanceMaster removeFileFromProductInstanceIteration(String workspaceId, int iteration, String fullName, ProductInstanceMasterKey productInstanceMasterKey) throws UserNotFoundException, AccessRightException, WorkspaceNotFoundException, UserNotActiveException, FileNotFoundException, ProductInstanceMasterNotFoundException, WorkspaceNotEnabledException {

        User user = userManager.checkWorkspaceReadAccess(workspaceId);
        ProductInstanceMaster productInstanceMaster =  getProductInstanceMaster(productInstanceMasterKey);

        ProductInstanceIteration productInstanceIteration = productInstanceMaster.getProductInstanceIterations().get(iteration - 1);
        BinaryResource file = binaryResourceDAO.loadBinaryResource(user.getLocale(), fullName);
        checkProductInstanceWriteAccess(workspaceId, productInstanceMaster, user);

        productInstanceIteration.removeFile(file);
        binaryResourceDAO.removeBinaryResource(file);

        try {
            storageManager.deleteData(file);
        } catch (StorageException e) {
            LOGGER.log(Level.INFO, null, e);
        }

        return productInstanceMaster;

    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public BinaryResource renameFileInProductInstance(String pFullName, String pNewName, String serialNumber, String cId, int iteration) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, FileNotFoundException, ProductInstanceMasterNotFoundException, NotAllowedException, AccessRightException, FileAlreadyExistsException, CreationException, StorageException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(BinaryResource.parseWorkspaceId(pFullName));
        Locale userLocale = user.getLocale();

        BinaryResource file = binaryResourceDAO.loadBinaryResource(userLocale, pFullName);

        ProductInstanceMasterKey pInstanceIterationKey = new ProductInstanceMasterKey(serialNumber, user.getWorkspaceId(), cId);
        ProductInstanceMaster productInstanceMaster = productInstanceMasterDAO.loadProductInstanceMaster(userLocale, pInstanceIterationKey);
        checkNameFileValidity(pNewName, userLocale);

        try{

            binaryResourceDAO.loadBinaryResource(userLocale, file.getNewFullName(pNewName));
            throw new FileAlreadyExistsException(userLocale, pNewName);

        }catch(FileNotFoundException e){

            ProductInstanceIteration productInstanceIteration = productInstanceMaster.getProductInstanceIterations().get(iteration - 1);
            //check access rights on product instance
            checkProductInstanceWriteAccess(user.getWorkspaceId(), productInstanceMaster, user);

            storageManager.renameFile(file, pNewName);
            productInstanceIteration.removeFile(file);
            binaryResourceDAO.removeBinaryResource(file);

            BinaryResource newFile = new BinaryResource(file.getNewFullName(pNewName), file.getContentLength(), file.getLastModified());
            binaryResourceDAO.createBinaryResource(userLocale, newFile);
            productInstanceIteration.addFile(newFile);
            return newFile;
        }

    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public void deleteProductInstance(String workspaceId, String configurationItemId, String serialNumber) throws UserNotFoundException, AccessRightException, WorkspaceNotFoundException, UserNotActiveException, ProductInstanceMasterNotFoundException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(workspaceId);
        ProductInstanceMaster prodInstM = productInstanceMasterDAO.loadProductInstanceMaster(user.getLocale(), new ProductInstanceMasterKey(serialNumber, workspaceId, configurationItemId));
        checkProductInstanceWriteAccess(workspaceId, prodInstM, user);

        productInstanceMasterDAO.deleteProductInstanceMaster(prodInstM);

        for (ProductInstanceIteration pii : prodInstM.getProductInstanceIterations()) {
            for (BinaryResource file : pii.getAttachedFiles()) {
                try {
                    storageManager.deleteData(file);
                } catch (StorageException e) {
                    LOGGER.log(Level.INFO, null, e);
                }
            }
        }
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public void updateACLForProductInstanceMaster(String workspaceId, String configurationItemId, String serialNumber, Map<String, String> userEntries, Map<String, String> groupEntries) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ProductInstanceMasterNotFoundException, AccessRightException, WorkspaceNotEnabledException {

        // Check the read access to the workspace
        User user = userManager.checkWorkspaceReadAccess(workspaceId);

        ProductInstanceMaster prodInstM = productInstanceMasterDAO.loadProductInstanceMaster(user.getLocale(), new ProductInstanceMasterKey(serialNumber, workspaceId, configurationItemId));

        // Check the access to the product instance master
        checkProductInstanceWriteAccess(workspaceId, prodInstM, user);

        if (prodInstM.getAcl() == null) {
            ACL acl = aclFactory.createACL(workspaceId, userEntries, groupEntries);
            prodInstM.setAcl(acl);
        } else {
            aclFactory.updateACL(workspaceId, prodInstM.getAcl(), userEntries, groupEntries);
        }
    }


    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public void removeACLFromProductInstanceMaster(String workspaceId, String configurationItemId, String serialNumber) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, AccessRightException, ProductInstanceMasterNotFoundException, WorkspaceNotEnabledException {

        // Check the read access to the workspace
        User user = userManager.checkWorkspaceReadAccess(workspaceId);

        ProductInstanceMaster prodInstM = productInstanceMasterDAO.loadProductInstanceMaster(user.getLocale(), new ProductInstanceMasterKey(serialNumber, workspaceId, configurationItemId));

        // Check the access to the product instance
        checkProductInstanceWriteAccess(workspaceId, prodInstM, user);

        ACL acl = prodInstM.getAcl();
        if (acl != null) {
            aclDAO.removeACLEntries(acl);
            prodInstM.setAcl(null);
        }
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    @Override
    public BinaryResource saveFileInProductInstance(String workspaceId, ProductInstanceIterationKey pdtIterationKey, String fileName, int pSize) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, NotAllowedException, ProductInstanceMasterNotFoundException, AccessRightException, ProductInstanceIterationNotFoundException, FileAlreadyExistsException, CreationException, WorkspaceNotEnabledException {
        // Check the read access to the workspace
        User user = userManager.checkWorkspaceReadAccess(workspaceId);
        Locale userLocale = user.getLocale();
        checkNameFileValidity(fileName, userLocale);

        ProductInstanceMaster prodInstM = productInstanceMasterDAO.loadProductInstanceMaster(userLocale, new ProductInstanceMasterKey(pdtIterationKey.getProductInstanceMaster().getSerialNumber(), workspaceId, pdtIterationKey.getProductInstanceMaster().getInstanceOf().getId()));
        // Check the access to the product instance
        checkProductInstanceWriteAccess(workspaceId, prodInstM, user);

        // Load the product instance iteration
        ProductInstanceIteration productInstanceIteration = this.getProductInstanceIteration(pdtIterationKey);


        BinaryResource binaryResource = null;
        String fullName = workspaceId + "/product-instances/" + prodInstM.getSerialNumber() + "/iterations/" + productInstanceIteration.getIteration() + "/" + fileName;

        for (BinaryResource bin : productInstanceIteration.getAttachedFiles()) {
            if (bin.getFullName().equals(fullName)) {
                binaryResource = bin;
                break;
            }
        }

        if (binaryResource == null) {
            binaryResource = new BinaryResource(fullName, pSize, new Date());
            binaryResourceDAO.createBinaryResource(userLocale, binaryResource);
            productInstanceIteration.addFile(binaryResource);
        } else {
            binaryResource.setContentLength(pSize);
            binaryResource.setLastModified(new Date());
        }
        return binaryResource;

    }


    @LogDocument
    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    @Override
    public BinaryResource getBinaryResource(String fullName) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, AccessRightException, FileNotFoundException, NotAllowedException, WorkspaceNotEnabledException {

        User user = userManager.checkWorkspaceReadAccess(BinaryResource.parseWorkspaceId(fullName));
        Locale userLocale = user.getLocale();
        BinaryResource binaryResource = binaryResourceDAO.loadBinaryResource(userLocale, fullName);

        ProductInstanceIteration productInstanceIteration = binaryResourceDAO.getProductInstanceIterationHolder(binaryResource);
        if (productInstanceIteration != null) {
            ProductInstanceMaster productInstanceMaster = productInstanceIteration.getProductInstanceMaster();

            if (isACLGrantReadAccess(user, productInstanceMaster)) {
                return binaryResource;
            } else {
                throw new NotAllowedException(userLocale, "NotAllowedException34");
            }
        } else {
            throw new FileNotFoundException(userLocale, fullName);
        }
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    @Override
    public PathDataMaster addNewPathDataIteration(String workspaceId, String configurationItemId, String serialNumber, int pathDataId, List<InstanceAttribute> attributes, String note, DocumentRevisionKey[] links, String[] documentLinkComments) throws UserNotFoundException, AccessRightException, WorkspaceNotFoundException, ProductInstanceMasterNotFoundException, UserNotActiveException, NotAllowedException, PathDataAlreadyExistsException, FileAlreadyExistsException, CreationException, PathDataMasterNotFoundException, DocumentRevisionNotFoundException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(workspaceId);
        Locale userLocale = user.getLocale();

        ProductInstanceMaster prodInstM = productInstanceMasterDAO.loadProductInstanceMaster(userLocale, new ProductInstanceMasterKey(serialNumber, workspaceId, configurationItemId));

        // Check the access to the product instance
        checkProductInstanceWriteAccess(workspaceId, prodInstM, user);

        ProductInstanceIteration prodInstI = prodInstM.getLastIteration();
        //PathDataMaster pathDataMaster = pathDataMasterDAO.findByPathIdAndProductInstanceIteration(pathDataId, prodInstI);
        //strangely retrieving pathDataMaster from a query as the side effect of not synchronized its pathDataIterations to the L2 cache
        List<PathDataMaster> pathDataMasterList = prodInstI.getPathDataMasterList();
        PathDataMaster pathDataMaster = null;
        if(pathDataMasterList!=null){
            for(PathDataMaster pd:pathDataMasterList){
                if(pd.getId()==pathDataId){
                    pathDataMaster=pd;
                    break;
                }
            }
        }
        if(pathDataMaster==null)
            throw new PathDataMasterNotFoundException(userLocale, pathDataId);

        Set<BinaryResource> sourceFiles = pathDataMaster.getLastIteration().getAttachedFiles();
        Set<BinaryResource> targetFiles = new HashSet<>();

        if (pathDataMaster.getLastIteration() != null) {
            int iteration = pathDataMaster.getLastIteration().getIteration() + 1;
            if (!sourceFiles.isEmpty()) {
                for (BinaryResource sourceFile : sourceFiles) {
                    String fileName = sourceFile.getName();
                    long length = sourceFile.getContentLength();
                    Date lastModified = sourceFile.getLastModified();
                    String fullName = workspaceId + "/product-instances/" + serialNumber + "/pathdata/" + pathDataId + "/iterations/" + iteration + '/' + fileName;
                    BinaryResource targetFile = new BinaryResource(fullName, length, lastModified);
                    binaryResourceDAO.createBinaryResource(userLocale, targetFile);
                    targetFiles.add(targetFile);
                    try {
                        storageManager.copyData(sourceFile, targetFile);
                    } catch (StorageException e) {
                        LOGGER.log(Level.INFO, null, e);
                    }
                }
            }
        }

        PathDataIteration pathDataIteration = pathDataMaster.createNextIteration();
        pathDataIteration.setInstanceAttributes(attributes);
        pathDataIteration.setIterationNote(note);
        createDocumentLink(userLocale, pathDataIteration, links, documentLinkComments);
        pathDataIteration.setAttachedFiles(targetFiles);
        return pathDataMaster;
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    @Override
    public PathDataMaster updatePathData(String workspaceId, String configurationItemId, String serialNumber, int pathDataMasterId, int iteration, List<InstanceAttribute> attributes, String note, DocumentRevisionKey[] pLinkKeys, String[] documentLinkComments) throws UserNotActiveException, WorkspaceNotFoundException, UserNotFoundException, ProductInstanceMasterNotFoundException, AccessRightException, NotAllowedException, DocumentRevisionNotFoundException, WorkspaceNotEnabledException {

        User user = userManager.checkWorkspaceReadAccess(workspaceId);
        Locale userLocale = user.getLocale();

        ProductInstanceMaster prodInstM = productInstanceMasterDAO.loadProductInstanceMaster(userLocale, new ProductInstanceMasterKey(serialNumber, workspaceId, configurationItemId));

        // Check the access to the product instance
        checkProductInstanceWriteAccess(workspaceId, prodInstM, user);

        PathDataMaster pathDataMaster = em.find(PathDataMaster.class, pathDataMasterId);
        PathDataIteration pathDataIteration = pathDataMaster.getPathDataIterations().get(iteration - 1);

        // This path data isn't owned by product master.
        if (!prodInstM.getLastIteration().getPathDataMasterList().contains(pathDataMaster)) {
            throw new NotAllowedException(userLocale, "NotAllowedException52");
        }

        boolean valid = AttributesConsistencyUtils.hasValidChange(attributes, false, pathDataIteration.getInstanceAttributes());
        if(!valid) {
            throw new NotAllowedException(userLocale, "NotAllowedException59");
        }
        pathDataIteration.setInstanceAttributes(attributes);
        pathDataIteration.setIterationNote(note);

        if (pLinkKeys != null) {
            Set<DocumentLink> currentLinks = new HashSet<>(pathDataIteration.getLinkedDocuments());

            for (DocumentLink link : currentLinks) {
                pathDataIteration.getLinkedDocuments().remove(link);
            }

            int counter = 0;
            for (DocumentRevisionKey link : pLinkKeys) {
                DocumentLink newLink = new DocumentLink(documentRevisionDAO.loadDocR(userLocale, link));
                newLink.setComment(documentLinkComments[counter]);
                documentLinkDAO.createLink(newLink);
                pathDataIteration.getLinkedDocuments().add(newLink);
                counter++;
            }
        }

        return pathDataMaster;
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    @Override
    public void deletePathData(String workspaceId, String configurationItemId, String serialNumber, int pathDataId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ProductInstanceMasterNotFoundException, AccessRightException, NotAllowedException, WorkspaceNotEnabledException {

        User user = userManager.checkWorkspaceReadAccess(workspaceId);
        Locale userLocale = user.getLocale();

        ProductInstanceMaster prodInstM = productInstanceMasterDAO.loadProductInstanceMaster(userLocale, new ProductInstanceMasterKey(serialNumber, workspaceId, configurationItemId));

        checkProductInstanceWriteAccess(workspaceId, prodInstM, user);

        PathDataMaster pathDataMaster = em.find(PathDataMaster.class, pathDataId);

        ProductInstanceIteration prodInstI = prodInstM.getLastIteration();

        // This path data isn't owned by product master.
        if (!prodInstI.getPathDataMasterList().contains(pathDataMaster)) {
            throw new NotAllowedException(userLocale, "NotAllowedException52");
        }

        prodInstI.getPathDataMasterList().remove(pathDataMaster);
        pathDataMasterDAO.removePathData(pathDataMaster);

        for(PathDataIteration pathDataIteration : pathDataMaster.getPathDataIterations()) {
            for (BinaryResource file : pathDataIteration.getAttachedFiles()) {
                try {
                    storageManager.deleteData(file);
                } catch (StorageException e) {
                    LOGGER.log(Level.INFO, null, e);
                }
            }
        }
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    @Override
    public PathDataMaster getPathDataByPath(String workspaceId, String configurationItemId, String serialNumber, String pathAsString) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, AccessRightException, ProductInstanceMasterNotFoundException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(workspaceId);
        Locale userLocale = user.getLocale();

        ProductInstanceMaster prodInstM = productInstanceMasterDAO.loadProductInstanceMaster(userLocale, new ProductInstanceMasterKey(serialNumber, workspaceId, configurationItemId));

        // Check the access to the product instance
        checkProductInstanceReadAccess(workspaceId, prodInstM, user);

        ProductInstanceIteration prodInstI = prodInstM.getLastIteration();
        PathDataMaster pathDataMaster=null;
        try{
            pathDataMaster = pathDataMasterDAO.findByPathAndProductInstanceIteration(userLocale, pathAsString, prodInstI);
        }catch(PathDataMasterNotFoundException pEx){
            //not found return null;
        }
        return pathDataMaster;
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    @Override
    public boolean canWrite(String workspaceId, String configurationItemId, String serialNumber) {
        try {
            User user = userManager.checkWorkspaceReadAccess(workspaceId);
            ProductInstanceMaster prodInstM = productInstanceMasterDAO.loadProductInstanceMaster(user.getLocale(), new ProductInstanceMasterKey(serialNumber, workspaceId, configurationItemId));
            checkProductInstanceWriteAccess(workspaceId, prodInstM, user);
            return true;
        } catch (ProductInstanceMasterNotFoundException | AccessRightException | UserNotActiveException | WorkspaceNotFoundException | WorkspaceNotEnabledException | UserNotFoundException e){
            return false;
        }

    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    @Override
    public BinaryResource saveFileInPathData(String workspaceId, String configurationItemId, String serialNumber, int pathDataId, int iteration, String fileName, int pSize) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, NotAllowedException, AccessRightException, ProductInstanceMasterNotFoundException, FileAlreadyExistsException, CreationException, WorkspaceNotEnabledException {
        // Check the read access to the workspace
        User user = userManager.checkWorkspaceReadAccess(workspaceId);
        Locale userLocale = user.getLocale();
        checkNameFileValidity(fileName, userLocale);

        ProductInstanceMaster prodInstM = productInstanceMasterDAO.loadProductInstanceMaster(userLocale, new ProductInstanceMasterKey(serialNumber, workspaceId, configurationItemId));
        // Check the access to the product instance
        checkProductInstanceWriteAccess(workspaceId, prodInstM, user);

        // Load path data
        PathDataMaster pathDataMaster = em.find(PathDataMaster.class, pathDataId);
        PathDataIteration pathDataIteration = pathDataMaster.getPathDataIterations().get(iteration - 1);

        // This path data isn't owned by product master.
        if (!prodInstM.getLastIteration().getPathDataMasterList().contains(pathDataMaster)) {
            throw new NotAllowedException(userLocale, "NotAllowedException52");
        }

        BinaryResource binaryResource = null;
        String fullName = workspaceId + "/product-instances/" + prodInstM.getSerialNumber() + "/pathdata/" + pathDataMaster.getId() + "/iterations/" + iteration + '/' + fileName;

        for (BinaryResource bin : pathDataIteration.getAttachedFiles()) {
            if (bin.getFullName().equals(fullName)) {
                binaryResource = bin;
                break;
            }
        }

        if (binaryResource == null) {
            binaryResource = new BinaryResource(fullName, pSize, new Date());
            binaryResourceDAO.createBinaryResource(userLocale, binaryResource);
            pathDataIteration.addFile(binaryResource);
        } else {
            binaryResource.setContentLength(pSize);
            binaryResource.setLastModified(new Date());
        }
        return binaryResource;
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    @Override
    public BinaryResource getPathDataBinaryResource(String fullName) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, NotAllowedException, FileNotFoundException, AccessRightException, WorkspaceNotEnabledException {

        User user = userManager.checkWorkspaceReadAccess(BinaryResource.parseWorkspaceId(fullName));
        Locale userLocale = user.getLocale();
        BinaryResource binaryResource = binaryResourceDAO.loadBinaryResource(userLocale, fullName);

        PathDataIteration pathDataIteration = binaryResourceDAO.getPathDataHolder(binaryResource);
        PathDataMaster pathDataMaster = pathDataIteration.getPathDataMaster();

        if (pathDataMaster != null) {

            ProductInstanceMaster productInstanceMaster = pathDataMasterDAO.findByPathData(pathDataMaster);

            String workspaceId = productInstanceMaster.getInstanceOf().getWorkspaceId();
            checkProductInstanceReadAccess(workspaceId, productInstanceMaster, user);

            return binaryResource;

        } else {
            throw new FileNotFoundException(userLocale, fullName);
        }
    }


    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    @Override
    public BinaryResource renameFileInPathData(String workspaceId, String configurationItemId, String serialNumber, int pathDataId, int iteration, String pFullName, String pNewName) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, FileNotFoundException, ProductInstanceMasterNotFoundException, NotAllowedException, AccessRightException, FileAlreadyExistsException, CreationException, WorkspaceNotEnabledException {

        User user = userManager.checkWorkspaceReadAccess(BinaryResource.parseWorkspaceId(pFullName));
        Locale userLocale = user.getLocale();

        BinaryResource file = binaryResourceDAO.loadBinaryResource(userLocale, pFullName);

        ProductInstanceMasterKey pInstanceIterationKey = new ProductInstanceMasterKey(serialNumber, workspaceId, configurationItemId);
        ProductInstanceMaster productInstanceMaster = productInstanceMasterDAO.loadProductInstanceMaster(userLocale, pInstanceIterationKey);

        checkNameFileValidity(pNewName, userLocale);

        try{
            binaryResourceDAO.loadBinaryResource(userLocale, file.getNewFullName(pNewName));
            throw new FileAlreadyExistsException(userLocale, pNewName);
        }catch(FileNotFoundException e){

            //check access rights on product master
            checkProductInstanceWriteAccess(user.getWorkspaceId(), productInstanceMaster, user);

            PathDataMaster pathDataMaster = em.find(PathDataMaster.class, pathDataId);

            //allowed on last iteration only
            if (pathDataMaster.getPathDataIterations().size() != (iteration - 1)) {
                throw new NotAllowedException(userLocale, "NotAllowedException55");
            }

            PathDataIteration pathDataIteration = pathDataMaster.getPathDataIterations().get(iteration - 1);

            // This path data isn't owned by product master.
            if (!productInstanceMaster.getLastIteration().getPathDataMasterList().contains(pathDataMaster)) {
                throw new NotAllowedException(userLocale, "NotAllowedException52");
            }

            try {
                storageManager.renameFile(file, pNewName);
                pathDataIteration.removeFile(file);
                binaryResourceDAO.removeBinaryResource(file);

                BinaryResource newFile = new BinaryResource(file.getNewFullName(pNewName), file.getContentLength(), file.getLastModified());
                binaryResourceDAO.createBinaryResource(userLocale, newFile);
                pathDataIteration.addFile(newFile);
                return newFile;


            } catch (StorageException se) {
                LOGGER.log(Level.INFO, null, se);
                return null;
            }

        }
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    @Override
    public ProductInstanceMaster removeFileFromPathData(String workspaceId, String configurationItemId, String serialNumber, int pathDataId, int iteration, String fullName, ProductInstanceMaster productInstanceMaster) throws UserNotFoundException, AccessRightException, WorkspaceNotFoundException, UserNotActiveException, NotAllowedException, FileNotFoundException, WorkspaceNotEnabledException {

        User user = userManager.checkWorkspaceReadAccess(workspaceId);
        Locale userLocale = user.getLocale();

        BinaryResource file = binaryResourceDAO.loadBinaryResource(userLocale, fullName);
        checkProductInstanceWriteAccess(workspaceId, productInstanceMaster, user);

        PathDataMaster pathDataMaster = em.find(PathDataMaster.class, pathDataId);
        PathDataIteration pathDataIteration = pathDataMaster.getPathDataIterations().get(iteration - 1);

        // This path data isn't owned by product master.
        if (!productInstanceMaster.getLastIteration().getPathDataMasterList().contains(pathDataMaster)) {
            throw new NotAllowedException(userLocale, "NotAllowedException52");
        }

        pathDataIteration.removeFile(file);
        binaryResourceDAO.removeBinaryResource(file);

        try {
            storageManager.deleteData(file);
        } catch (StorageException e) {
            LOGGER.log(Level.INFO, null, e);
        }

        return productInstanceMaster;
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    @Override
    public BinaryResource saveFileInPathDataIteration(String workspaceId, String configurationItemId, String serialNumber, int path, int iteration, String fileName, int pSize) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, NotAllowedException, AccessRightException, ProductInstanceMasterNotFoundException, FileAlreadyExistsException, CreationException, PathDataMasterNotFoundException, WorkspaceNotEnabledException {
        // Check the read access to the workspace
        User user = userManager.checkWorkspaceReadAccess(workspaceId);
        Locale userLocale = user.getLocale();
        checkNameFileValidity(fileName, userLocale);

        ProductInstanceMaster prodInstM = productInstanceMasterDAO.loadProductInstanceMaster(userLocale, new ProductInstanceMasterKey(serialNumber, workspaceId, configurationItemId));
        // Check the access to the product instance
        checkProductInstanceWriteAccess(workspaceId, prodInstM, user);

        ProductInstanceIteration prodInstI = prodInstM.getLastIteration();

        PathDataMaster pathDataMaster = pathDataMasterDAO.findByPathIdAndProductInstanceIteration(userLocale, path, prodInstI);

        PathDataIteration pathDataIteration = pathDataMaster.getPathDataIterations().get(iteration - 1);
        // This path data isn't owned by product master.
        if (!prodInstI.getPathDataMasterList().contains(pathDataMaster)) {
            throw new NotAllowedException(userLocale, "NotAllowedException52");
        }

        BinaryResource binaryResource = null;
        String fullName = workspaceId + "/product-instances/" + prodInstM.getSerialNumber() + "/pathdata/" + pathDataMaster.getId() + "/iterations/" + iteration + '/' + fileName;

        for (BinaryResource bin : pathDataIteration.getAttachedFiles()) {
            if (bin.getFullName().equals(fullName)) {
                binaryResource = bin;
                break;
            }
        }

        if (binaryResource == null) {
            binaryResource = new BinaryResource(fullName, pSize, new Date());
            binaryResourceDAO.createBinaryResource(userLocale, binaryResource);
            pathDataIteration.addFile(binaryResource);
        } else {
            binaryResource.setContentLength(pSize);
            binaryResource.setLastModified(new Date());
        }
        return binaryResource;
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    @Override
    public PathDataMaster createPathDataMaster(String workspaceId, String configurationItemId, String serialNumber, String path, List<InstanceAttribute> attributes, String iterationNote) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ProductInstanceMasterNotFoundException, AccessRightException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(workspaceId);
        Locale userLocale = user.getLocale();

        ProductInstanceMaster prodInstM = productInstanceMasterDAO.loadProductInstanceMaster(userLocale, new ProductInstanceMasterKey(serialNumber, workspaceId, configurationItemId));

        // Check the access to the product instance
        checkProductInstanceWriteAccess(workspaceId, prodInstM, user);

        PathDataMaster pathDataMaster = new PathDataMaster();
        pathDataMaster.setPath(path);
        pathDataMasterDAO.createPathData(pathDataMaster);

        ProductInstanceIteration prodInstI = prodInstM.getLastIteration();

        // Check if not already a path data for this configuration
        for (PathDataMaster master : prodInstI.getPathDataMasterList()) {
            if (master.getPath()!= null && master.getPath().equals(path)) {
                PathDataIteration pathDataIteration = pathDataMaster.createNextIteration();
                pathDataIteration.setInstanceAttributes(attributes);
                pathDataIteration.setIterationNote(iterationNote);
                pathDataIterationDAO.createPathDataIteration(pathDataIteration);

                return pathDataMaster;
            }
        }
        PathDataIteration pathDataIteration = pathDataMaster.createNextIteration();
        pathDataIteration.setInstanceAttributes(attributes);
        pathDataIteration.setIterationNote(iterationNote);
        pathDataIterationDAO.createPathDataIteration(pathDataIteration);
        prodInstI.getPathDataMasterList().add(pathDataMaster);
        return pathDataMaster;
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    @Override
    public PathToPathLink getPathToPathLink(String workspaceId, String configurationItemId, String serialNumber, int pathToPathLinkId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ProductInstanceMasterNotFoundException, AccessRightException, PathToPathLinkNotFoundException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(workspaceId);
        Locale userLocale = user.getLocale();

        ProductInstanceMaster prodInstM = productInstanceMasterDAO.loadProductInstanceMaster(userLocale, new ProductInstanceMasterKey(serialNumber, workspaceId, configurationItemId));

        checkProductInstanceReadAccess(workspaceId, prodInstM, user);
        return pathToPathLinkDAO.loadPathToPathLink(userLocale, pathToPathLinkId);
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    @Override
    public List<String> getPathToPathLinkTypes(String workspaceId, String configurationItemId, String serialNumber) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ProductInstanceMasterNotFoundException, AccessRightException, WorkspaceNotEnabledException {

        User user = userManager.checkWorkspaceReadAccess(workspaceId);
        Locale userLocale = user.getLocale();

        ProductInstanceMaster prodInstM = productInstanceMasterDAO.loadProductInstanceMaster(userLocale, new ProductInstanceMasterKey(serialNumber, workspaceId, configurationItemId));

        checkProductInstanceReadAccess(workspaceId,prodInstM,user);
        return pathToPathLinkDAO.getDistinctPathToPathLinkTypes(prodInstM.getLastIteration());
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    @Override
    public List<PathToPathLink> getPathToPathLinks(String workspaceId, String configurationItemId, String serialNumber) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ProductInstanceMasterNotFoundException, AccessRightException, WorkspaceNotEnabledException {

        User user = userManager.checkWorkspaceReadAccess(workspaceId);
        Locale userLocale = user.getLocale();
        ProductInstanceMaster prodInstM = productInstanceMasterDAO.loadProductInstanceMaster(userLocale, new ProductInstanceMasterKey(serialNumber, workspaceId, configurationItemId));

        checkProductInstanceReadAccess(workspaceId,prodInstM,user);
        return pathToPathLinkDAO.getDistinctPathToPathLink(prodInstM.getLastIteration());
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    @Override
    public List<PathToPathLink> getPathToPathLinkFromSourceAndTarget(String workspaceId, String configurationItemId, String serialNumber, String sourcePath, String targetPath) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ProductInstanceMasterNotFoundException, AccessRightException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(workspaceId);
        Locale userLocale = user.getLocale();

        ProductInstanceMaster prodInstM = productInstanceMasterDAO.loadProductInstanceMaster(userLocale, new ProductInstanceMasterKey(serialNumber, workspaceId, configurationItemId));
        checkProductInstanceReadAccess(workspaceId, prodInstM, user);

        return pathToPathLinkDAO.getPathToPathLinkFromSourceAndTarget(prodInstM.getLastIteration(), sourcePath, targetPath);
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    @Override
    public List<PathToPathLink> getRootPathToPathLinks(String workspaceId, String configurationItemId, String serialNumber, String type) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ProductInstanceMasterNotFoundException, AccessRightException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(workspaceId);
        Locale userLocale = user.getLocale();

        ProductInstanceMaster prodInstM = productInstanceMasterDAO.loadProductInstanceMaster(userLocale, new ProductInstanceMasterKey(serialNumber, workspaceId, configurationItemId));
        checkProductInstanceReadAccess(workspaceId, prodInstM, user);
        return pathToPathLinkDAO.findRootPathToPathLinks(prodInstM.getLastIteration(), type);
    }

    @Override
    public List<ProductInstanceMaster> getProductInstanceMasters(PartRevision pPartRevision) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkspaceNotEnabledException {
        String workspaceId = pPartRevision.getWorkspaceId();
        User user = userManager.checkWorkspaceReadAccess(workspaceId);
        List<ProductInstanceMaster> productInstanceMasters = productInstanceMasterDAO.findProductInstanceMasters(pPartRevision);
        ListIterator<ProductInstanceMaster> ite = productInstanceMasters.listIterator();

        while(ite.hasNext()){
            ProductInstanceMaster next = ite.next();
            try {
                checkProductInstanceWriteAccess(workspaceId, next, user);
            } catch (AccessRightException e) {
                ite.remove();
            }
        }

        return productInstanceMasters;

    }

    private User checkProductInstanceReadAccess(String workspaceId, ProductInstanceMaster prodInstM, User user) throws AccessRightException, WorkspaceNotFoundException, UserNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        if (user.isAdministrator()) {
            // Check if the user is workspace administrator
            return user;
        }
        if (prodInstM.getAcl() == null) {
            // Check if the item has no ACL
            return userManager.checkWorkspaceReadAccess(workspaceId);
        } else if (prodInstM.getAcl().hasReadAccess(user)) {
            // Check if there is a write access
            return user;
        } else {
            // Else throw a AccessRightException
            throw new AccessRightException(user.getLocale(), user);
        }
    }

    private User checkProductInstanceWriteAccess(String workspaceId, ProductInstanceMaster prodInstM, User user) throws AccessRightException, WorkspaceNotFoundException, UserNotFoundException, WorkspaceNotEnabledException {
        if (user.isAdministrator()) {
            // Check if it is the workspace's administrator
            return user;
        }
        if (prodInstM.getAcl() == null) {
            // Check if the item haven't ACL
            return userManager.checkWorkspaceWriteAccess(workspaceId);
        } else if (prodInstM.getAcl().hasWriteAccess(user)) {
            // Check if there is a write access
            return user;
        } else {
            // Else throw a AccessRightException
            throw new AccessRightException(user.getLocale(), user);
        }
    }

    private boolean isACLGrantReadAccess(User user, ProductInstanceMaster productInstanceMaster) {
        return user.isAdministrator() || productInstanceMaster.getAcl().hasReadAccess(user);
    }

    private void checkNameValidity(String name, Locale locale) throws NotAllowedException {
        if (!NamingConvention.correct(name)) {
            throw new NotAllowedException(locale, "NotAllowedException9", name);
        }
    }

    private void checkNameFileValidity(String name, Locale locale) throws NotAllowedException {
        if (name != null) {
            name = name.trim();
        }
        if (!NamingConvention.correctNameFile(name)) {
            throw new NotAllowedException(locale, "NotAllowedException9", name);
        }
    }

    private PathDataIteration createDocumentLink(Locale locale, PathDataIteration pathDataIteration, DocumentRevisionKey[] links, String[] documentLinkComments) throws DocumentRevisionNotFoundException {
        if (links != null) {
            Set<DocumentLink> currentLinks = new HashSet<>(pathDataIteration.getLinkedDocuments());

            for (DocumentLink link : currentLinks) {
                pathDataIteration.getLinkedDocuments().remove(link);
            }

            int counter = 0;
            for (DocumentRevisionKey link : links) {
                DocumentLink newLink = new DocumentLink(documentRevisionDAO.loadDocR(locale, link));
                newLink.setComment(documentLinkComments[counter]);
                documentLinkDAO.createLink(newLink);
                pathDataIteration.getLinkedDocuments().add(newLink);
                counter++;
            }
            return pathDataIteration;
        }
        return pathDataIteration;
    }
}
