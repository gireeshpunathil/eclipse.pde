/*******************************************************************************
 * Copyright (c) 2000, 2007 IBM Corporation and others. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: IBM - Initial API and implementation
 ******************************************************************************/
package org.eclipse.pde.internal.build;

import java.util.*;
import org.eclipse.core.runtime.CoreException;

public class AssembleScriptGenerator extends AbstractScriptGenerator {
	protected String directory; // representing the directory where to generate the file
	protected AssemblyInformation assemblageInformation;
	protected String featureId;
	protected HashMap archivesFormat;
	protected boolean groupConfigs = false;

	protected AssembleConfigScriptGenerator configScriptGenerator;

	public AssembleScriptGenerator(String directory, AssemblyInformation assemblageInformation, String featureId) {
		this.directory = directory;
		this.assemblageInformation = assemblageInformation;
		this.featureId = featureId;
		configScriptGenerator = getConfigScriptGenerator();
	}

	protected String getScriptName() {
		return DEFAULT_ASSEMBLE_NAME + '.' + (featureId.equals("") ? "" : featureId + '.') + DEFAULT_ASSEMBLE_ALL; //$NON-NLS-1$//$NON-NLS-2$
	}

	protected AssembleConfigScriptGenerator getConfigScriptGenerator() {
		return new AssembleConfigScriptGenerator();
	}

	public void generate() throws CoreException {
		try {
			openScript(directory, getScriptName());
			printProjectDeclaration();
			printAssembleMacroDef();
			generateMainTarget();
			script.printProjectEnd();
		} finally {
			if (script != null)
				script.close();
			script = null;
		}
	}

	protected void printProjectDeclaration() {
		script.printProjectDeclaration("Assemble All Config of " + featureId, TARGET_MAIN, null); //$NON-NLS-1$
	}

	protected void printDefaultAssembleCondition() {
		// packaging may need to print something different if running in a backward compatible mode.
		script.printConditionIsSet("defaultAssemble.@{config}", "defaultAssemble", "defaultAssemblyEnabled", "assemble.@{element}@{dot}@{config}"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}

	protected void printAssembleMacroDef() {
		List attributes = new ArrayList(2);
		attributes.add("config"); //$NON-NLS-1$
		attributes.add("element"); //$NON-NLS-1$
		attributes.add("dot"); //$NON-NLS-1$
		attributes.add("scriptPrefix"); //$NON-NLS-1$
		script.printMacroDef("assemble", attributes); //$NON-NLS-1$
		printDefaultAssembleCondition();
		script.printConditionIsSet("customOrDefault.@{config}", "assemble.@{element}@{dot}@{config}", "assemble.@{element}@{dot}@{config}", "${defaultAssemble.@{config}}"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		Properties properties = new Properties();
		properties.put("assembleScriptName", "@{scriptPrefix}.@{element}@{dot}@{config}.xml"); //$NON-NLS-1$ //$NON-NLS-2$
		script.printAntTask(Utils.getPropertyFormat(DEFAULT_CUSTOM_TARGETS), null, "${customOrDefault.@{config}}", null, null, properties); //$NON-NLS-1$
		script.printEndMacroDef();
	}

	protected void generateMainTarget() throws CoreException {
		script.printTargetDeclaration(TARGET_MAIN, null, null, null, null);

		if (groupConfigs) {
			Collection allPlugins = new HashSet();
			Collection allFeatures = new HashSet();
			Collection features = new HashSet();
			Collection rootFiles = new HashSet();
			for (Iterator allConfigs = getConfigInfos().iterator(); allConfigs.hasNext();) {
				Collection[] configInfo = getConfigInfos((Config) allConfigs.next());
				allPlugins.addAll(configInfo[0]);
				allFeatures.addAll(configInfo[1]);
				features.addAll(configInfo[2]);
				rootFiles.addAll(configInfo[3]);
			}
			basicGenerateAssembleConfigFileTargetCall(new Config("group", "group", "group"), allPlugins, allFeatures, features, rootFiles); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		} else {
			for (Iterator allConfigs = getConfigInfos().iterator(); allConfigs.hasNext();) {
				Config current = (Config) allConfigs.next();
				Collection[] configInfo = getConfigInfos(current);
				basicGenerateAssembleConfigFileTargetCall(current, configInfo[0], configInfo[1], configInfo[2], configInfo[3]);
			}
		}
		script.printTargetEnd();
	}

	protected Collection[] getConfigInfos(Config aConfig) {
		return new Collection[] {assemblageInformation.getCompiledPlugins(aConfig), assemblageInformation.getCompiledFeatures(aConfig), assemblageInformation.getFeatures(aConfig), assemblageInformation.getRootFileProviders(aConfig)};
	}

	protected void basicGenerateAssembleConfigFileTargetCall(Config aConfig, Collection binaryPlugins, Collection binaryFeatures, Collection allFeatures, Collection rootFiles) throws CoreException {
		// generate the script for a configuration
		configScriptGenerator.initialize(directory, featureId, aConfig, binaryPlugins, binaryFeatures, allFeatures, rootFiles);
		configScriptGenerator.setArchiveFormat((String) archivesFormat.get(aConfig));
		configScriptGenerator.setBuildSiteFactory(siteFactory);
		configScriptGenerator.setGroupConfigs(groupConfigs);
		configScriptGenerator.generate();

		script.print("<assemble "); //$NON-NLS-1$
		String config = configScriptGenerator.getTargetConfig();
		script.printAttribute("config", config, true); //$NON-NLS-1$
		script.printAttribute("element", configScriptGenerator.getTargetElement(), true); //$NON-NLS-1$
		script.printAttribute("dot", config.length() > 0 ? "." : "", true); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		script.printAttribute("scriptPrefix", "assemble", true); //$NON-NLS-1$ //$NON-NLS-2$
		script.println("/>"); //$NON-NLS-1$
	}

	public void setSignJars(boolean value) {
		configScriptGenerator.setSignJars(value);
	}

	public void setProduct(String value) {
		configScriptGenerator.setProduct(value);
	}

	public void setGenerateJnlp(boolean value) {
		configScriptGenerator.setGenerateJnlp(value);
	}

	public void setArchivesFormat(HashMap outputFormat) {
		archivesFormat = outputFormat;
	}

	public void setGroupConfigs(boolean group) {
		groupConfigs = group;
	}
}
