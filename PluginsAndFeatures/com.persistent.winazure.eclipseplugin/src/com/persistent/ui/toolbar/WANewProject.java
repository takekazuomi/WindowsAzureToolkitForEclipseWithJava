/**
* Copyright 2014 Microsoft Open Technologies, Inc.
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

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.wizard.IWizard;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.wizards.IWizardDescriptor;

import com.microsoftopentechnologies.wacommon.utils.PluginUtil;

/**
 * This class handles the click event on custom context menu of role folder.
 *
 */
public class WANewProject extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent arg0)
			throws ExecutionException {
		try {
			IWizardDescriptor des = PlatformUI.getWorkbench().
					getNewWizardRegistry().
					findWizard(Messages.waWizardId);
			if (des != null) {
				IWizard wizard = des.createWizard();
				WizardDialog wizDialog = new
						WizardDialog(PlatformUI.getWorkbench()
								.getDisplay().getActiveShell(),
								wizard);
				wizDialog.setTitle(wizard.getWindowTitle());
				wizDialog.open();
			}
		} catch (Exception ex) {
			PluginUtil.displayErrorDialogAndLog(new Shell(),
					Messages.errTtl,
					Messages.wzrdCrtErMsg, ex);
		}
		return null;
	}
}

