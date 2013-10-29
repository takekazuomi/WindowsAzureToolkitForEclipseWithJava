/**
 * Copyright 2013 Persistent Systems Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	 http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.persistent.ui.toolbar;

import java.io.File;
import java.io.IOException;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;

import waeclipseplugin.Activator;

import com.interopbridges.tools.windowsazure.WindowsAzureInvalidProjectOperationException;
import com.interopbridges.tools.windowsazure.WindowsAzurePackageType;
import com.interopbridges.tools.windowsazure.WindowsAzureProjectManager;
import com.microsoftopentechnologies.wacommon.utils.PreferenceSetUtil;
import com.microsoftopentechnologies.wacommon.utils.WACommonException;
import com.persistent.util.WAEclipseHelper;

/**
 * This class opens deploy folder in windows explorer
 * where the files were built.
 */
public class WABuildCloud extends AbstractHandler {

	private String errorTitle;
	private String errorMessage;

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		// Get selected WA project
		IProject selProject = WAEclipseHelper.getSelectedProject();
		try {
			WindowsAzureProjectManager waProjManager = WindowsAzureProjectManager.
					load(new File(selProject.getLocation().toOSString()));
			if (waProjManager.getPackageType().equals(WindowsAzurePackageType.LOCAL)) {
				waProjManager.setPackageType(WindowsAzurePackageType.CLOUD);
			}
			try {
				String prefSetUrl = PreferenceSetUtil.getSelectedPortalURL(
						PreferenceSetUtil.getSelectedPreferenceSetName());
				/*
				 * Don't check if URL is empty or null.
				 * As if it is then we remove "portalurl" attribute
				 * from package.xml.
				 */
				waProjManager.setPortalURL(prefSetUrl);
			} catch (WACommonException e1) {
				Display.getDefault().syncExec(new Runnable() {
					public void run() {
						MessageDialog.openError(null,
								Messages.errTtl,
								Messages.getPrefUrlErMsg);
					}
				});
			}
			waProjManager.save();

			waProjManager = WindowsAzureProjectManager.
					load(new File(selProject.getLocation().toOSString()));

			final IProject selProj = selProject;
            Job job = new Job(Messages.cldJobTtl) {
                @Override
                protected IStatus run(IProgressMonitor monitor) {
                    monitor.beginTask(Messages.cldJobTtl, IProgressMonitor.UNKNOWN);
                    String dplyFolderPath = "";
					try {
						selProj.build(IncrementalProjectBuilder.FULL_BUILD, null);

						WindowsAzureProjectManager waProjMngr = WindowsAzureProjectManager.
								load(new File(selProj.getLocation().toOSString()));

						dplyFolderPath = WAEclipseHelper.
								getDeployFolderPath(waProjMngr, selProj);
						String bldFlFilePath = String.format("%s%s%s",
								dplyFolderPath,
								"\\",
								com.persistent.util.Messages.bldErFileName);
						File buildFailFile = new File(bldFlFilePath);
						File deployFile = new File(dplyFolderPath);

						if (deployFile.exists() && deployFile.isDirectory() 
								&& deployFile.listFiles().length > 0
								&& !buildFailFile.exists()) {
							String[] cmd = {"explorer.exe", "\""+dplyFolderPath+"\""};
							new ProcessBuilder(cmd).start();
						} else {
							return Status.CANCEL_STATUS;
						}
						waProjMngr.save();
					} catch (IOException e) {
						errorMessage = String.format("%s %s",
								Messages.dplyFldErrMsg,
								dplyFolderPath);
						Activator.getDefault().log(errorMessage, e);
						return Status.CANCEL_STATUS;
					} catch (Exception e) {
						errorTitle = Messages.bldErrTtl;
						errorMessage = Messages.bldErrMsg;
						Activator.getDefault().log(errorMessage, e);
						Display.getDefault().syncExec(new Runnable() {
							public void run() {
								MessageDialog.openError(null,
										errorTitle, errorMessage);
							}
						});
						return Status.CANCEL_STATUS;
					}
					 monitor.done();
	                 return Status.OK_STATUS;
				}
			};
            job.schedule();
		} catch (WindowsAzureInvalidProjectOperationException e) {
			errorTitle = Messages.bldCldErrTtl;
			errorMessage = String.format("%s %s", Messages.bldCldErrMsg, selProject.getName());
			Activator.getDefault().log(errorMessage, e);
			Display.getDefault().syncExec(new Runnable() {
				public void run() {
					MessageDialog.openError(null,
							errorTitle, errorMessage);
				}
			});
		}
		return null;
	}
}
