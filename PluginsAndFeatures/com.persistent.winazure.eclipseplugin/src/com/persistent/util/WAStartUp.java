/**
 * Copyright 2013 Persistent Systems Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.persistent.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.eclipse.ui.IStartup;
import org.eclipse.wst.xml.core.internal.XMLCorePlugin;
import org.eclipse.wst.xml.core.internal.catalog.provisional.ICatalog;
import org.eclipse.wst.xml.core.internal.catalog.provisional.ICatalogEntry;
import org.eclipse.wst.xml.core.internal.catalog.provisional.INextCatalog;

import waeclipseplugin.Activator;

import com.interopbridges.tools.windowsazure.WindowsAzureProjectManager;
import com.interopbridges.tools.windowsazure.WindowsAzureRole;
import com.interopbridges.tools.windowsazure.WindowsAzureRoleComponent;
import com.microsoftopentechnologies.wacommon.storageregistry.PreferenceUtilStrg;
import com.microsoftopentechnologies.wacommon.storageregistry.StorageAccount;
import com.microsoftopentechnologies.wacommon.storageregistry.StorageAccountRegistry;
import com.microsoftopentechnologies.wacommon.storageregistry.StorageRegistryUtilMethods;
import com.microsoftopentechnologies.wacommon.utils.PreferenceSetUtil;



/**
 * This class gets executed after the Workbench initialises.
 */
@SuppressWarnings("restriction")
public class WAStartUp implements IStartup {
    private static final int BUFF_SIZE = 1024;
    @Override
    public void earlyStartup() {
        try {
            //Make schema entries in XML Catalog
            makeXMLCatalogEntry();

            //Add resourceChangeListener to listen to changes of resources
            WAResourceChangeListener listener =
                    new WAResourceChangeListener();
            IWorkspace workspace = ResourcesPlugin.getWorkspace();
            workspace.addResourceChangeListener(listener,
                    IResourceChangeEvent.POST_CHANGE);

            IWorkspaceRoot root = workspace.getRoot();
            PreferenceUtilStrg.load();
            //Get all the projects in workspace
            IProject[] projects = root.getProjects();
            for (IProject iProject : projects) {
                Activator.getDefault().log(iProject.getName()
                        + "isOpen" + iProject.isOpen(), null);
                if (iProject.isOpen()) {
                    boolean isNature = iProject.hasNature(
                            Messages.stUpProjNature);
                    Activator.getDefault().log(iProject.getName()
                            + "isNature" + isNature, null);
                    if (isNature) {
                        WindowsAzureProjectManager projMngr =
                                WindowsAzureProjectManager.load(
                                        iProject.getLocation().toFile());
                        if (!projMngr.isCurrVersion()) {
                        	WAEclipseHelper.handleProjectUpgrade(iProject,projMngr);
                        }
                        // Correct name if its invalid
                        if (!iProject.getName().
                        		equalsIgnoreCase(projMngr.getProjectName())) {
                        	WAEclipseHelper.
                        	correctProjectName(iProject, projMngr);
                        }
                        projMngr = initializeStorageAccountRegistry(projMngr);
                        // save object so that access key will get saved in PML.
                        projMngr.save();
                    }
                }
            }
            // save preference file.
            PreferenceUtilStrg.save();
            //this code is for copying componentset.xml in plugins folder
            copyPluginComponents();
            // refresh workspace as package.xml may have got changed.
            WAEclipseHelper.refreshWorkspace(Messages.resCLJobName,
            		Messages.resCLExWkspRfrsh);
        } catch (Exception e) {
            /* This is not a user initiated task
               So user should not get any exception prompt.*/
            Activator.getDefault().log("Exception in early startup", e);
        }
    }

    /**
     * Storage account registry project open logic.
     * Plugin needs to detect and aggregate the information
     * about the different storage accounts used by the components.
     * If account is not there, then add the storage account, with the key to the registry.
     * If it's there, but the access key is different, then update component's cloud key
     * with the key from the registry.
     * @param projMngr
     * @return
     */
    private WindowsAzureProjectManager initializeStorageAccountRegistry(
    		WindowsAzureProjectManager projMngr) {
    	try {
    		List<StorageAccount> strgAccList = StorageAccountRegistry.getStrgList();
    		// get number of roles in one project
    		List<WindowsAzureRole> roleList = projMngr.getRoles();
    		for (int i = 0; i < roleList.size(); i++) {
    			WindowsAzureRole role = roleList.get(i);
    			// check for caching storage account name and key given
    			String key = role.getCacheStorageAccountKey();
    			String name = role.getCacheStorageAccountName();
    			if (key != null
    					&& name != null
    					&& !key.isEmpty()
    					&& !name.isEmpty()) {
    				StorageAccount account = new StorageAccount(name,
    						key,
    						PreferenceSetUtil.getSelectedBlobServiceURL(name));
    				if (strgAccList.contains(account)) {
    					int index = strgAccList.indexOf(account);
    					String keyInReg = strgAccList.get(index).getStrgKey();
    					if (!key.equals(keyInReg)) {
    						// update key of component
    						role.setCacheStorageAccountKey(keyInReg);
    					}
    				} else {
    					// add account in registry.
    					strgAccList.add(account);
    				}
    			}

    			// get list of components in one role.
    			List<WindowsAzureRoleComponent> cmpnntsList =
    					role.getComponents();
    			for (int j = 0; j < cmpnntsList.size(); j++) {
    				WindowsAzureRoleComponent component =
    						cmpnntsList.get(j);
    				// check cloud URL is set or not
    				String url = component.getCloudDownloadURL();
    				if (url != null
    						&& !url.isEmpty()) {
    					String accessKey = component.getCloudKey();
    					/*
    					 * check cloud key is set or not
    					 * if not then URL is publicly accessible
    					 * hence do not add that account in registry.
    					 */
    					if (accessKey != null
    							&& !accessKey.isEmpty()) {
    						StorageAccount account = new StorageAccount(
    								StorageRegistryUtilMethods.
    								getAccNameFromUrl(url),
    								accessKey,
    								StorageRegistryUtilMethods.
    								getServiceEndpointUrl(url));
    						if (strgAccList.contains(account)) {
    							int index = strgAccList.indexOf(account);
    							String keyInReg = strgAccList.get(index).getStrgKey();
    							if (!accessKey.equals(keyInReg)) {
    								// update key of component
    								component.setCloudKey(keyInReg);
    							}
    						} else {
    							// add account in registry.
    							strgAccList.add(account);
    						}
    					}
    				}
    			}
    		}
    	} catch (Exception e) {
    		Activator.getDefault().log("Exception in early startup", e);
    	}
    	return projMngr;
    }

    /**
     * Creates schema entries in XML Catalog.
     *
     */
    private void makeXMLCatalogEntry() {
        ICatalog catalog = XMLCorePlugin.getDefault().getDefaultXMLCatalog();
        INextCatalog[] nextCatalogs = catalog.getNextCatalogs();
        for (int i = 0; i < nextCatalogs.length; i++) {
            INextCatalog nextCatalog = nextCatalogs[i];
            if (XMLCorePlugin.SYSTEM_CATALOG_ID.equals(nextCatalog.getId())) {
                ICatalog userCatalog = nextCatalog.getReferencedCatalog();
                if (userCatalog != null) {
                    createEntries(userCatalog);
                }
            }
        }
    }

    /**
     * Creates schema entries if not present.
     *
     * @param userCatalog catalog to which entry is to be made
     */
    private void createEntries(final ICatalog userCatalog) {
        String str = getSchemasLocation();

        String defFile = str + File.separator
                + Messages.stUpSerDefSchema;
        File file = new File(defFile);
        URI defFileUri = file.toURI();
        String configFile = str + File.separator
                + Messages.stUpSerConfSchma;
        file = new File(configFile);
        URI configFileUri = file.toURI();
        boolean isServiceDef = false;
        boolean isServiceConfig = false;
        ICatalogEntry[] entries = userCatalog.getCatalogEntries();
        for (ICatalogEntry iCatalogEntry : entries) {
            if (iCatalogEntry.getURI().equals(
                    defFileUri.toString())) {
                isServiceDef = true;
            }
            if (iCatalogEntry.getURI().equals(
                    configFileUri.toString())) {
                isServiceConfig = true;
            }
        }
        if (!isServiceDef) {
            ICatalogEntry catalogEntry =
                    (ICatalogEntry) userCatalog.createCatalogElement(
                            ICatalogEntry.ENTRY_TYPE_URI);
            catalogEntry.setKey(
                    Messages.stUpSerDefKey);
            catalogEntry.setURI(defFileUri.toString());
            userCatalog.addCatalogElement(catalogEntry);
        }
        if (!isServiceConfig) {
            ICatalogEntry catalogEntry =
                    (ICatalogEntry) userCatalog.createCatalogElement(
                            ICatalogEntry.ENTRY_TYPE_URI);
            catalogEntry.setKey(
                    Messages.stUpSerConfigKey);
            catalogEntry.setURI(configFileUri.toString());
            userCatalog.addCatalogElement(catalogEntry);
        }
    }

    /**
     * Gives location of schemas directory of Azure SDK.
     *
     * @return location of schemas directory
     */
    private String getSchemasLocation() {
        String progFile = "";
        try {
        progFile = WindowsAzureProjectManager.getLatestAzureSdkDir();
        progFile = String.format("%s%s%s", progFile, File.separator,
        		Messages.stUpSchemaLoc);
        } catch (IOException e) {
            Activator.getDefault().log(e.getMessage(), e);
        }
        return progFile;
    }

    /**
     * Copies all Eclipse plugin for Windows Azure
     * related files in eclipse plugins folder at startup.
     */
    private void copyPluginComponents() {
        try {
            String pluginInstLoc = String.format("%s%s%s%s%s",
            		Platform.getInstallLocation().getURL().getPath().toString(),
                    File.separator, Messages.pluginFolder,
                    File.separator, Messages.pluginId);
            if (!new File(pluginInstLoc).exists()) {
                new File(pluginInstLoc).mkdir();
            }
            String cmpntFile = String.format("%s%s%s", pluginInstLoc,
            		File.separator, Messages.cmpntFileName);
            String starterKit = String.format("%s%s%s", pluginInstLoc,
            		File.separator, Messages.starterKitFileName);
            String restFile = String.format("%s%s%s", pluginInstLoc,
            		File.separator, Messages.restFileName);
            String prefFile = String.format("%s%s%s", pluginInstLoc,
            		File.separator, Messages.prefFileName);

            /*
             *  Check for componentsets.xml.
             *  If exists checks its version , if it has latest version then no need to do anything
             *  else check with older componentsets.xml , if identical then delete existing and copy new one
             *  else rename existing and copy new one. 
             */
            File componentSets = new File(cmpntFile);
            if (componentSets.exists()) { 
            	String componentSetsVersion = null ;
            	try {
            		componentSetsVersion = WindowsAzureProjectManager.getComponentSetsVersion(componentSets);
            	} catch(Exception e ) {
            		Activator.getDefault().log("Error ocured while getting version of componentsets.xml , considering version as null");	
            	}
            	
            	if((componentSetsVersion != null && !componentSetsVersion.isEmpty()) && 
            			componentSetsVersion.equals(WindowsAzureProjectManager.getCurrVerion())) {
            		// Donot do anything
            	} else {
            		// Check with old component sets for upgrade scenarios
            		File    oldCmpntFile       = WAEclipseHelper.getResourceAsFile(Messages.oldCmpntFileEntry);
            		boolean isIdenticalWithOld = WAEclipseHelper.isFilesIdentical(oldCmpntFile,componentSets);
            		
            		if(isIdenticalWithOld) {
            			// Delete old one 
            			componentSets.delete();            			
            		} else {
            			// Rename old one
            			DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
            			Date date = new Date();
            			WAEclipseHelper.copyFile(cmpntFile,cmpntFile+".old"+dateFormat.format(date));
            		}
            		copyResourceFile(Messages.cmpntFileEntry, cmpntFile);
            	}
            } else {
            	copyResourceFile(Messages.cmpntFileEntry, cmpntFile);
            }

            // Check for WAStarterKitForJava.zip
            if (new File(starterKit).exists()) {
            	new File(starterKit).delete();
            }
            copyResourceFile(Messages.starterKitEntry, starterKit);

            // Check for restutil.exe
            if (new File(restFile).exists()) {
            	new File(restFile).delete();
            }
            copyResourceFile(Messages.restFileEntry, restFile);

            // Copy preferencesets.xml only if it is not exist
            if (!new File(prefFile).exists()) {
            	copyResourceFile(Messages.prefFileEntry, prefFile);
            }
        } catch (Exception e) {
            Activator.getDefault().log(e.getMessage(), e);
        }
    }

    /**
     * Method copy specified file in eclipse plugins folder.
     * @param resourceFile
     * @param destFile
     */
    private void copyResourceFile(String resourceFile, String destFile) {
    	URL url = Activator.getDefault().getBundle()
                .getEntry(resourceFile);
        URL fileURL;
		try {
			fileURL = FileLocator.toFileURL(url);
			URL resolve = FileLocator.resolve(fileURL);
	        File file = new File(resolve.getFile());
	        FileInputStream fis = new FileInputStream(file);
	        File outputFile = new File(destFile);
	        FileOutputStream fos = new FileOutputStream(outputFile);
	        writeFile(fis , fos);
		} catch (IOException e) {
			Activator.getDefault().log(e.getMessage(), e);
		}
    }

    /**
     * Method writes contents of file.
     * @param inStream
     * @param outStream
     * @throws IOException
     */
    public void writeFile(InputStream inStream, OutputStream outStream)
    		throws IOException {

        try {
            byte[] buf = new byte[BUFF_SIZE];
            int len = inStream.read(buf);
            while (len > 0) {
                outStream.write(buf, 0, len);
                len = inStream.read(buf);
            }
        } finally {
            if (inStream != null) {
                inStream.close();
            }
            if (outStream != null) {
                outStream.close();
            }
        }
    }
}
