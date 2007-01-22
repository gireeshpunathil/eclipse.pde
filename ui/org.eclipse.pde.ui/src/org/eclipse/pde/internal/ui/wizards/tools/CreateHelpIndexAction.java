/*******************************************************************************
 * Copyright (c) 2005, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.wizards.tools;

import java.io.File;
import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.help.search.HelpIndexBuilder;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.HostSpecification;
import org.eclipse.pde.core.plugin.IFragmentModel;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.core.plugin.PluginRegistry;
import org.eclipse.pde.internal.ui.PDEPlugin;
import org.eclipse.pde.internal.ui.PDEUIMessages;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;

/**
 * Creates the help search index by parsing the selected
 * plugin.xml file and generating index for TOC extensions.
 *
 * @since 3.1
 */

public class CreateHelpIndexAction implements IObjectActionDelegate {

	private HelpIndexBuilder fIndexBuilder;
	private IProject fProject;
	private IStatus fStatus;

	public CreateHelpIndexAction() {
	}

	public void setActivePart(IAction action, IWorkbenchPart targetPart) {
	}

	public void run(IAction action) {
		File file = getManifest();
		if (file == null || !file.exists())
			return;
		if (fIndexBuilder == null)
			fIndexBuilder = new HelpIndexBuilder();
		fIndexBuilder.setManifest(file);
		File target = fProject.getLocation().toFile();
		if (target == null)
			return;
		fIndexBuilder.setDestination(target);
		IRunnableWithProgress op = new IRunnableWithProgress() {
			
			public void run(IProgressMonitor monitor) throws InvocationTargetException {
				try {
					monitor.beginTask(PDEUIMessages.CreateHelpIndexAction_creating, 10);
					fIndexBuilder.execute(new SubProgressMonitor(monitor, 9));
				} catch (CoreException e) {
					IStatus s = e.getStatus();
					fStatus = new Status(IStatus.WARNING, s.getPlugin(), s.getCode(), s.getMessage(), s.getException());
				} finally {
					try {
						refreshTarget(new SubProgressMonitor(monitor, 1));
					} catch (CoreException e) {
						throw new InvocationTargetException(e);
					}
				}
			}
		};
		try {
			PlatformUI.getWorkbench().getProgressService().busyCursorWhile(op);
		} catch (InvocationTargetException e) {
			PDEPlugin.logException(e);
		} catch (InterruptedException e) {
			PDEPlugin.logException(e);
		}
		if (fStatus != null)
			ErrorDialog.openError(null, null, null, fStatus);
		fStatus = null;
	}

	private File getManifest() {
		IPluginModelBase model = PluginRegistry.findModel(fProject);
		return (model != null) ? getManifest(model) : null;
	}
	
	private File getManifest(IPluginModelBase model) {
		if (model instanceof IFragmentModel) {
			BundleDescription desc = model.getBundleDescription();
			HostSpecification hostSpec = desc.getHost();
			BundleDescription host = hostSpec == null ? null : (BundleDescription)hostSpec.getSupplier();
			if (host != null) {
				IPluginModelBase hostModel = PluginRegistry.findModel(host);
				if (hostModel != null && hostModel.getUnderlyingResource() != null) {
					IProject project = hostModel.getUnderlyingResource().getProject();
					IFile pluginFile = project.getFile("plugin.xml"); //$NON-NLS-1$
					return (pluginFile.exists()) ? pluginFile.getLocation().toFile() : null;					
				}
			}
		}
		return fProject.getFile("plugin.xml").getLocation().toFile(); //$NON-NLS-1$
	}

	private void refreshTarget(IProgressMonitor monitor) throws CoreException {
		fProject.refreshLocal(IResource.DEPTH_INFINITE, monitor);
	}

	public void selectionChanged(IAction action, ISelection selection) {
		fProject = (IProject)((IStructuredSelection) selection).getFirstElement();
	}
}
