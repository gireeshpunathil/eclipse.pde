package org.eclipse.pde.internal.ui.model.plugin;

import org.eclipse.jface.text.*;
import org.eclipse.pde.core.plugin.*;

/**
 * @author melhem
 *
 */
public class PluginModel extends PluginModelBase implements IPluginModel {
	
	public PluginModel(IDocument document, boolean isReconciling) {
		super(document, isReconciling);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.core.plugin.IPluginModel#getPlugin()
	 */
	public IPlugin getPlugin() {
		return (IPlugin)getPluginBase();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.pde.core.plugin.IPluginModelBase#isFragmentModel()
	 */
	public boolean isFragmentModel() {
		return false;
	}
	
}
