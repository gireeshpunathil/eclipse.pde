/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.wizards.product;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.osgi.util.NLS;
import org.eclipse.pde.core.plugin.IExtensionsModelFactory;
import org.eclipse.pde.core.plugin.IFragmentModel;
import org.eclipse.pde.core.plugin.IPluginBase;
import org.eclipse.pde.core.plugin.IPluginElement;
import org.eclipse.pde.core.plugin.IPluginExtension;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.internal.core.PDECore;
import org.eclipse.pde.internal.core.iproduct.IProduct;
import org.eclipse.pde.internal.core.plugin.WorkspaceFragmentModel;
import org.eclipse.pde.internal.core.plugin.WorkspacePluginModel;
import org.eclipse.pde.internal.core.plugin.WorkspacePluginModelBase;
import org.eclipse.pde.internal.ui.PDEPlugin;
import org.eclipse.pde.internal.ui.PDEUIMessages;
import org.eclipse.pde.internal.ui.model.IDocumentNode;
import org.eclipse.pde.internal.ui.model.plugin.FragmentModel;
import org.eclipse.pde.internal.ui.model.plugin.PluginModel;
import org.eclipse.pde.internal.ui.model.plugin.PluginModelBase;
import org.eclipse.pde.internal.ui.wizards.templates.ControlStack;
import org.eclipse.pde.ui.templates.IVariableProvider;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.text.edits.MalformedTreeException;

public class ProductIntroOperation implements IRunnableWithProgress, IVariableProvider {

	protected String fIntroId;
	protected String fPluginId;
	private Shell fShell;
	private IProduct fProduct;
	private IDocument fDocument;
	private IProject fProject;
	private static final String INTRO_POINT = "org.eclipse.ui.intro"; //$NON-NLS-1$
	private static final String INTRO_CONFIG_POINT = "org.eclipse.ui.intro.config"; //$NON-NLS-1$
	private static final String INTRO_CLASS = "org.eclipse.ui.intro.config.CustomizableIntroPart"; //$NON-NLS-1$
	private static final String KEY_PRODUCT_NAME = "productName"; //$NON-NLS-1$

	public ProductIntroOperation(IProduct product, String pluginId, String introId, Shell shell) {
		fIntroId = introId;
		fShell = shell;
		fProduct = product;
		fPluginId = pluginId;
	}

	public void run(IProgressMonitor monitor) throws InvocationTargetException,
			InterruptedException {
		try {
			IFile file = getFile();
			if (!file.exists()) {
				createNewFile(file);
			} else {
				modifyExistingFile(file, monitor);
			}
			generateFiles(monitor);
		} catch (CoreException e) {
			throw new InvocationTargetException(e);
		} catch (IOException e) {
			throw new InvocationTargetException(e);
		} catch (MalformedTreeException e) {
			throw new InvocationTargetException(e);
		} catch (BadLocationException e) {
			throw new InvocationTargetException(e);
		}
	}

	private IFile getFile() {
		IPluginModelBase model = PDECore.getDefault().getModelManager().findModel(fPluginId);
		IProject project = model.getUnderlyingResource().getProject();
		String filename = model instanceof IFragmentModel ? "fragment.xml" : "plugin.xml"; //$NON-NLS-1$ //$NON-NLS-2$
		return project.getFile(filename);
	}

	private IPluginModelBase getModel(IFile file) {
		if ("plugin.xml".equals(file.getName())) //$NON-NLS-1$
			return new WorkspacePluginModel(file, false);
		return new WorkspaceFragmentModel(file, false);
	}

	private PluginModelBase getEditingModel(boolean isFragment) {
		if (isFragment)
			return new FragmentModel(fDocument, false);
		return new PluginModel(fDocument, false);
	}

	private void createNewFile(IFile file) throws CoreException {
		WorkspacePluginModelBase model = (WorkspacePluginModelBase) getModel(file);
		IPluginBase base = model.getPluginBase();
		base.setSchemaVersion("3.0"); //$NON-NLS-1$
		base.add(createIntroExtension(model));
		base.add(createIntroConfigExtension(model));
		model.save();
	}

	private IPluginExtension createIntroExtension(IPluginModelBase model)
			throws CoreException {
		IPluginExtension extension = model.getFactory().createExtension();
		extension.setPoint(INTRO_POINT);
		extension.add(createIntroExtensionContent(extension));
		extension.add(createIntroBindingExtensionContent(extension));
		return extension;
	}

	private IPluginExtension createIntroConfigExtension(IPluginModelBase model)
			throws CoreException {
		IPluginExtension extension = model.getFactory().createExtension();
		extension.setPoint(INTRO_CONFIG_POINT);
		extension.add(createIntroConfigExtensionContent(extension));
		return extension;
	}

	private IPluginElement createIntroExtensionContent(IPluginExtension extension) throws CoreException {
		IPluginElement element = extension.getModel().getFactory().createElement(extension);
		element.setName("intro"); //$NON-NLS-1$
		element.setAttribute("id", fIntroId); //$NON-NLS-1$
		element.setAttribute("class", INTRO_CLASS); //$NON-NLS-1$
		return element;
	}

	private IPluginElement createIntroBindingExtensionContent(IPluginExtension extension) throws CoreException {
		IPluginElement element = extension.getModel().getFactory().createElement(extension);
		element.setName("introProductBinding"); //$NON-NLS-1$
		element.setAttribute("productId", fProduct.getId()); //$NON-NLS-1$
		element.setAttribute("introId", fIntroId); //$NON-NLS-1$
		return element;
	}
	
	private IPluginElement createIntroConfigExtensionContent(
			IPluginExtension extension) throws CoreException {
		IPluginElement element = extension.getModel().getFactory()
				.createElement(extension);
		element.setName("config"); //$NON-NLS-1$
		element.setAttribute("id", fPluginId + ".introConfigId"); //$NON-NLS-1$ //$NON-NLS-2$
		element.setAttribute("introId", fIntroId); //$NON-NLS-1$
		element.setAttribute("content", "introContent.xml"); //$NON-NLS-1$ //$NON-NLS-2$
		element.add(createPresentationElement(element));

		return element;
	}

	private IPluginElement createPresentationElement(IPluginElement parent)
			throws CoreException {
		IPluginElement presentation = null;
		IPluginElement implementation = null;
		IExtensionsModelFactory factory = parent.getModel().getFactory();
		
		presentation = factory.createElement(parent);
		presentation.setName("presentation"); //$NON-NLS-1$
		presentation.setAttribute("home-page-id", "root"); //$NON-NLS-1$ //$NON-NLS-2$
		
		implementation = factory.createElement(presentation);
		implementation.setName("implementation"); //$NON-NLS-1$
		implementation.setAttribute("kind", "html"); //$NON-NLS-1$ //$NON-NLS-2$
		implementation.setAttribute("style", "content/shared.css"); //$NON-NLS-1$ //$NON-NLS-2$
		implementation.setAttribute("os", "win32,linux,macosx"); //$NON-NLS-1$ //$NON-NLS-2$
		
		presentation.add(implementation);
		
		return presentation;
	}

	private void modifyExistingFile(IFile file, IProgressMonitor monitor)
			throws CoreException, IOException, MalformedTreeException,
			BadLocationException {
		IStatus status = PDEPlugin.getWorkspace().validateEdit(new IFile[] { file }, fShell);
		if (status.getSeverity() != IStatus.OK)
			throw new CoreException(new Status(IStatus.ERROR, "org.eclipse.pde.ui", IStatus.ERROR, NLS.bind(PDEUIMessages.ProductDefinitionOperation_readOnly, fIntroId), null)); //$NON-NLS-1$ 

		ITextFileBufferManager manager = FileBuffers.getTextFileBufferManager();
		try {
			manager.connect(file.getFullPath(), monitor);
			ITextFileBuffer buffer = manager.getTextFileBuffer(file.getFullPath());

			fDocument = buffer.getDocument();
			PluginModelBase model = getEditingModel("fragment.xml".equals(file.getName())); //$NON-NLS-1$
			try {
				model.load();
				if (!model.isLoaded())
					throw new CoreException(new Status(IStatus.ERROR, "org.eclipse.pde.ui", IStatus.ERROR, NLS.bind(PDEUIMessages.ProductDefinitionOperation_malformed, fIntroId), null)); //$NON-NLS-1$ 
			} catch (CoreException e) {
				throw e;
			}
			
			IPluginExtension extension = getExtension(model, INTRO_POINT);
			if (extension == null) {
				extension = createIntroExtension(model);
				model.getPluginBase().add(extension);
			} else {
				extension.add(createIntroExtensionContent(extension));
				extension.add(createIntroBindingExtensionContent(extension));
			}
			TextEditUtilities.getInsertOperation((IDocumentNode) extension, fDocument).apply(fDocument);
			buffer.commit(monitor, true);
			
			extension = getExtension(model, INTRO_CONFIG_POINT);
			if (extension == null) {
				extension = createIntroConfigExtension(model);
				model.getPluginBase().add(extension);
			} else {
				extension.add(createIntroConfigExtensionContent(extension));
			}
			TextEditUtilities.getInsertOperation((IDocumentNode) extension, fDocument).apply(fDocument);
			buffer.commit(monitor, true);
			
		} finally {
			manager.disconnect(file.getFullPath(), monitor);
		}
	}

	private IPluginExtension getExtension(IPluginModelBase model,
			String tPoint) throws CoreException {
		IPluginExtension[] extensions = model.getPluginBase().getExtensions();
		for (int i = 0; i < extensions.length; i++) {
			String point = extensions[i].getPoint();
			if (tPoint.equals(point)) {
				return extensions[i];
			}
		}
		return null;
	}
	
	protected void generateFiles(IProgressMonitor monitor) throws CoreException {
		monitor.setTaskName(PDEUIMessages.AbstractTemplateSection_generating);
		fProject = PDECore.getDefault().getModelManager().findModel(fPluginId).getUnderlyingResource().getProject();
		
		URL locationUrl = null;
		try {
			locationUrl = new URL(PDEPlugin.getDefault().getInstallURL(), "templates_3.0/intro/"); //$NON-NLS-1$
		} catch (MalformedURLException e1) { return; }
		if (locationUrl == null) {
			return;
		}
		try {
			locationUrl = Platform.resolve(locationUrl);
			locationUrl = Platform.asLocalURL(locationUrl);
		} catch (IOException e) {
			return;
		}
		if ("file".equals(locationUrl.getProtocol())) { //$NON-NLS-1$
			File templateDirectory = new File(locationUrl.getFile());
			if (!templateDirectory.exists())
				return;
			generateFiles(templateDirectory, fProject, true, false, monitor);
		}
		monitor.subTask(""); //$NON-NLS-1$
		monitor.worked(1);
	}
	
	
	private void generateFiles(File src, IContainer dst, boolean firstLevel,
			boolean binary, IProgressMonitor monitor) throws CoreException {
		File[] members = src.listFiles();

		for (int i = 0; i < members.length; i++) {
			File member = members[i];
			if (member.getName().equals("ext.xml") || //$NON-NLS-1$
					member.getName().equals("java") || //$NON-NLS-1$
					member.getName().equals("concept3.xhtml") || //$NON-NLS-1$
					member.getName().equals("extContent.xhtml")) //$NON-NLS-1$
				continue;
			else if (member.isDirectory()) {
				IContainer dstContainer = null;
				if (firstLevel) {
					binary = false;
					if (member.getName().equals("bin")) { //$NON-NLS-1$
						binary = true;
						dstContainer = dst;
					}
				}
				if (dstContainer == null) {
					dstContainer = dst.getFolder(new Path(member.getName()));
				}
				if (dstContainer instanceof IFolder && !dstContainer.exists())
					((IFolder) dstContainer).create(true, true, monitor);
				generateFiles(member, dstContainer, false, binary, monitor);
			} else {
				if (firstLevel)
					binary = false;
				InputStream in = null;
				try {
					in = new FileInputStream(member);
					copyFile(member.getName(), in, dst, binary, monitor);
				} catch (IOException ioe) {
				} finally {
					if (in != null)
						try {
							in.close();
						} catch (IOException ioe2) {
						}
				}
			}
		}
	}

	private void copyFile(String fileName, InputStream input, IContainer dst, boolean binary,
			IProgressMonitor monitor) throws CoreException {

		monitor.subTask(fileName);
		IFile dstFile = dst.getFile(new Path(fileName));

		try {
			InputStream stream = getProcessedStream(fileName, input, binary);
			if (dstFile.exists()) {
				dstFile.setContents(stream, true, true, monitor);
			} else {
				dstFile.create(stream, true, monitor);
			}
			stream.close();

		} catch (IOException e) {
		}
	}
	

	private InputStream getProcessedStream(String fileName, InputStream stream,
			boolean binary) throws IOException, CoreException {
		if (binary)
			return stream;

		InputStreamReader reader = new InputStreamReader(stream);
		int bufsize = 1024;
		char[] cbuffer = new char[bufsize];
		int read = 0;
		StringBuffer keyBuffer = new StringBuffer();
		StringBuffer outBuffer = new StringBuffer();
		ControlStack preStack = new ControlStack();
		preStack.setValueProvider(this);

		boolean replacementMode = false;
		while (read != -1) {
			read = reader.read(cbuffer);
			for (int i = 0; i < read; i++) {
				char c = cbuffer[i];

				if (preStack.getCurrentState() == false) {
					continue;
				}

				if (c == '$') {
					if (replacementMode) {
						replacementMode = false;
						String key = keyBuffer.toString();
						String value = key.length() == 0 ? "$" //$NON-NLS-1$
								: getReplacementString(fileName, key);
						outBuffer.append(value);
						keyBuffer.delete(0, keyBuffer.length());
					} else {
						replacementMode = true;
					}
				} else {
					if (replacementMode)
						keyBuffer.append(c);
					else {
						outBuffer.append(c);
					}
				}
			}
		}
		return new ByteArrayInputStream(outBuffer.toString().getBytes(
				fProject.getDefaultCharset()));
	}
	
	private String getReplacementString(String fileName, String key) {
		if (key.equals(KEY_PRODUCT_NAME)) {
			return fProduct.getName();
		}
		return key;
	}

	public Object getValue(String variable) {
		return null;
	}
}
