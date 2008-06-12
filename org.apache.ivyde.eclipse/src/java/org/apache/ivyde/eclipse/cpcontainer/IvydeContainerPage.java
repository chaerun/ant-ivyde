/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.ivyde.eclipse.cpcontainer;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.descriptor.Configuration;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivyde.eclipse.IvyPlugin;
import org.apache.ivyde.eclipse.ui.preferences.IvyDEPreferenceStoreHelper;
import org.apache.ivyde.eclipse.ui.preferences.IvyPreferencePage;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ui.wizards.IClasspathContainerPage;
import org.eclipse.jdt.ui.wizards.IClasspathContainerPageExtension;
import org.eclipse.jdt.ui.wizards.NewElementWizardPage;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.ElementTreeSelectionDialog;
import org.eclipse.ui.dialogs.ISelectionStatusValidator;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import org.eclipse.ui.views.navigator.ResourceSorter;

public class IvydeContainerPage extends NewElementWizardPage implements IClasspathContainerPage,
        IClasspathContainerPageExtension {

    IJavaProject project;

    Text ivyFilePathText;

    CheckboxTableViewer confTableViewer;

    Text settingsText;

    Text acceptedTypesText;

    Text sourcesTypesText;

    Text sourcesSuffixesText;

    Text javadocTypesText;

    Text javadocSuffixesText;

    Button doRetrieveButton;

    Text retrievePatternText;

    Button alphaOrderCheck;

    Button projectSpecificButton;

    Button browse;

    Link generalSettingsLink;

    Composite configComposite;

    IvyClasspathContainerConfiguration conf;

    private IClasspathEntry entry;

    private String ivyXmlError;

    private ModuleDescriptor md;

    /**
     * Constructor
     * 
     */
    public IvydeContainerPage() {
        super("IvyDE Container");
    }

    void checkCompleted() {
        String error;
        if (ivyFilePathText.getText().length() == 0) {
            error = "Choose an ivy file";
        } else if (ivyXmlError != null) {
            error = ivyXmlError;
        } else if (confTableViewer.getCheckedElements().length == 0 && md != null
                && md.getConfigurations().length != 0) {
            error = "Choose at least one configuration";
        } else {
            error = null;
        }
        setErrorMessage(error);
        setPageComplete(error == null);
    }

    public boolean finish() {
        conf.confs = getSelectedConfigurations();
        if (projectSpecificButton.getSelection()) {
            conf.ivySettingsPath = settingsText.getText();
            conf.acceptedTypes = IvyClasspathUtil.split(acceptedTypesText.getText());
            conf.sourceTypes = IvyClasspathUtil.split(sourcesTypesText.getText());
            conf.javadocTypes = IvyClasspathUtil.split(javadocTypesText.getText());
            conf.sourceSuffixes = IvyClasspathUtil.split(sourcesSuffixesText.getText());
            conf.javadocSuffixes = IvyClasspathUtil.split(javadocSuffixesText.getText());
            conf.doRetrieve = doRetrieveButton.getSelection();
            conf.retrievePattern = retrievePatternText.getText();
            conf.alphaOrder = alphaOrderCheck.getSelection();
        } else {
            conf.ivySettingsPath = null;
        }
        entry = JavaCore.newContainerEntry(conf.getPath());
        try {
            IClasspathContainer cp = JavaCore.getClasspathContainer(entry.getPath(), project);
            if (cp instanceof IvyClasspathContainer) {
                ((IvyClasspathContainer) cp).scheduleResolve();
            }
        } catch (JavaModelException e) {
            // unless there are issues with the JDT, this should never happen
            IvyPlugin.log(e);
        }
        return true;
    }

    public IClasspathEntry getSelection() {
        return entry;
    }

    public void setSelection(IClasspathEntry entry) {
        if (entry == null) {
            conf = new IvyClasspathContainerConfiguration(project, "ivy.xml");
        } else {
            conf = new IvyClasspathContainerConfiguration(project, entry.getPath());
        }
    }

    private List getSelectedConfigurations() {
        Object[] confs = confTableViewer.getCheckedElements();
        int total = confTableViewer.getTable().getItemCount();
        if (confs.length == total) {
            return Arrays.asList(new String[] {"*"});
        }
        List confList = new ArrayList();
        for (int i = 0; i < confs.length; i++) {
            Configuration c = (Configuration) confs[i];
            confList.add(c.getName());
        }
        return confList;
    }

    public void createControl(Composite parent) {
        setTitle("IvyDE Managed Libraries");
        setDescription("Choose ivy file and its configurations.");

        TabFolder tabs = new TabFolder(parent, SWT.BORDER);
        tabs.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, true));

        TabItem mainTab = new TabItem(tabs, SWT.NONE);
        mainTab.setText("Main");
        mainTab.setControl(createMainTab(tabs));

        TabItem advancedTab = new TabItem(tabs, SWT.NONE);
        advancedTab.setText("Advanced");
        advancedTab.setControl(createAdvancedTab(tabs));

        setControl(tabs);

        loadFromConf();
        checkCompleted();
    }

    private Control createMainTab(Composite parent) {
        Composite composite = new Composite(parent, SWT.NONE);
        composite.setLayout(new GridLayout(3, false));
        composite.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, true));

        // Label for ivy file field
        Label pathLabel = new Label(composite, SWT.NONE);
        pathLabel.setText("Ivy File");

        ivyFilePathText = new Text(composite, SWT.SINGLE | SWT.BORDER);
        ivyFilePathText.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false));
        ivyFilePathText.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent e) {
                conf.ivyXmlPath = ivyFilePathText.getText();
                resolveModuleDescriptor();
                refreshConfigurationTable();
                checkCompleted();
            }
        });

        Button btn = new Button(composite, SWT.NONE);
        btn.setText("Browse");
        btn.addSelectionListener(new SelectionAdapter() {
            public void widgetSelected(SelectionEvent e) {
                String path = null;
                if (project != null) {
                    ElementTreeSelectionDialog dialog = new ElementTreeSelectionDialog(Display
                            .getDefault().getActiveShell(), new WorkbenchLabelProvider(),
                            new WorkbenchContentProvider());
                    dialog.setValidator(new ISelectionStatusValidator() {
                        private final IStatus errorStatus = new Status(IStatus.ERROR, IvyPlugin.ID,
                                0, "", null);

                        public IStatus validate(Object[] selection) {
                            if (selection.length == 0) {
                                return errorStatus;
                            }
                            for (int i = 0; i < selection.length; i++) {
                                Object o = selection[i];
                                if (!(o instanceof IFile)) {
                                    return errorStatus;
                                }
                            }
                            return Status.OK_STATUS;
                        }

                    });
                    dialog.setTitle("choose ivy file");
                    dialog.setMessage("choose the ivy file to use to resolve dependencies");
                    dialog.setInput(project.getProject());
                    dialog.setSorter(new ResourceSorter(ResourceSorter.NAME));

                    if (dialog.open() == Window.OK) {
                        Object[] elements = dialog.getResult();
                        if (elements.length > 0 && elements[0] instanceof IFile) {
                            IPath p = ((IFile) elements[0]).getProjectRelativePath();
                            path = p.toString();
                        }
                    }
                } else {
                    FileDialog dialog = new FileDialog(IvyPlugin.getActiveWorkbenchShell(),
                            SWT.OPEN);
                    dialog.setText("Choose an ivy.xml");
                    path = dialog.open();
                }

                if (path != null) {
                    conf.ivyXmlPath = path;
                    ivyFilePathText.setText(path);
                    resolveModuleDescriptor();
                    refreshConfigurationTable();
                    checkCompleted();
                }
            }
        });

        // Label for ivy configurations field
        Label confLabel = new Label(composite, SWT.NONE);
        confLabel.setText("Configurations");

        // table for configuration selection
        confTableViewer = CheckboxTableViewer.newCheckList(composite, SWT.BORDER | SWT.H_SCROLL
                | SWT.V_SCROLL);
        confTableViewer.getTable().setHeaderVisible(true);
        TableColumn col1 = new TableColumn(confTableViewer.getTable(), SWT.NONE);
        col1.setText("Name");
        col1.setWidth(100);
        TableColumn col2 = new TableColumn(confTableViewer.getTable(), SWT.NONE);
        col2.setText("Description");
        col2.setWidth(300);
        confTableViewer.setColumnProperties(new String[] {"Name", "Description"});
        confTableViewer.getTable().setLayoutData(
            new GridData(GridData.FILL, GridData.FILL, true, true));
        confTableViewer.setContentProvider(new IStructuredContentProvider() {
            public Object[] getElements(Object inputElement) {
                resolveModuleDescriptor();
                if (md != null) {
                    return md.getConfigurations();
                }
                return new Configuration[0];
            }

            public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
                // nothing to do
            }

            public void dispose() {
                // nothing to do
            }
        });
        confTableViewer.setLabelProvider(new ConfigurationLabelProvider());
        confTableViewer.addSelectionChangedListener(new ISelectionChangedListener() {
            public void selectionChanged(SelectionChangedEvent event) {
                checkCompleted();
            }
        });

        // refresh
        Button refreshConf = new Button(composite, SWT.NONE);
        refreshConf.setText("Refresh");
        refreshConf.addSelectionListener(new SelectionAdapter() {
            public void widgetSelected(SelectionEvent e) {
                confTableViewer.setInput(ivyFilePathText.getText());
            }
        });

        // some spacer
        new Composite(composite, SWT.NONE);

        Link select = new Link(composite, SWT.PUSH);
        select.setText("<A>All</A>/<A>None</A>");
        select.addSelectionListener(new SelectionAdapter() {
            public void widgetSelected(SelectionEvent e) {
                if (e.text.equals("All") && md != null) {
                    confTableViewer.setCheckedElements(md.getConfigurations());
                } else {
                    confTableViewer.setCheckedElements(new Configuration[0]);
                }
            }
        });

        return composite;
    }

    private Control createAdvancedTab(Composite parent) {
        Composite composite = new Composite(parent, SWT.NONE);
        composite.setLayout(new GridLayout());
        composite.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, true));

        Composite headerComposite = new Composite(composite, SWT.NONE);
        headerComposite.setLayout(new GridLayout(2, false));
        headerComposite.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false));

        projectSpecificButton = new Button(headerComposite, SWT.CHECK);
        projectSpecificButton.setText("Enable project specific settings");
        projectSpecificButton.addSelectionListener(new SelectionAdapter() {
            public void widgetSelected(SelectionEvent e) {
                updateFieldsStatus();
                conf.ivySettingsPath = settingsText.getText();
                checkCompleted();
            }
        });

        generalSettingsLink = new Link(headerComposite, SWT.NONE);
        generalSettingsLink.setFont(composite.getFont());
        generalSettingsLink.setText("<A>Configure Workspace Settings...</A>");
        generalSettingsLink.addSelectionListener(new SelectionAdapter() {
            public void widgetSelected(SelectionEvent e) {
                PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(getShell(),
                    IvyPreferencePage.PEREFERENCE_PAGE_ID, null, null);
                dialog.open();
            }
        });
        generalSettingsLink.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));

        Label horizontalLine = new Label(headerComposite, SWT.SEPARATOR | SWT.HORIZONTAL);
        horizontalLine.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false, 2, 1));

        configComposite = new Composite(composite, SWT.NONE);
        configComposite.setLayout(new GridLayout(3, false));
        configComposite.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false));

        Label label = new Label(configComposite, SWT.NONE);
        label.setText("Ivy settings path:");

        settingsText = new Text(configComposite, SWT.SINGLE | SWT.BORDER);
        settingsText
                .setToolTipText("The url where your ivysettings file can be found. \nUse 'default' to reference the default ivy settings. \nRelative paths are handled relative to the project. Example: 'file://./ivysettings.xml'.");
        settingsText.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false));
        settingsText.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent e) {
                conf.ivySettingsPath = settingsText.getText();
                checkCompleted();
            }
        });

        browse = new Button(configComposite, SWT.NONE);
        browse.setText("Browse");
        browse.addSelectionListener(new SelectionAdapter() {
            public void widgetSelected(SelectionEvent e) {
                File f = getFile(new File("/"));
                if (f != null) {
                    try {
                        settingsText.setText(f.toURL().toExternalForm());
                    } catch (MalformedURLException ex) {
                        // this cannot happen
                        IvyPlugin.log(IStatus.ERROR,
                            "The file got from the file browser has not a valid URL", ex);
                    }
                }
            }
        });

        label = new Label(configComposite, SWT.NONE);
        label.setText("Accepted types:");

        acceptedTypesText = new Text(configComposite, SWT.SINGLE | SWT.BORDER);
        acceptedTypesText.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false, 2,
                1));
        acceptedTypesText
                .setToolTipText("Comma separated list of artifact types to use in IvyDE Managed Dependencies Library.\nExample: jar, zip");

        label = new Label(configComposite, SWT.NONE);
        label.setText("Sources types:");

        sourcesTypesText = new Text(configComposite, SWT.SINGLE | SWT.BORDER);
        sourcesTypesText.setLayoutData(new GridData(GridData.FILL, GridData.BEGINNING, true, false,
                2, 1));
        sourcesTypesText
                .setToolTipText("Comma separated list of artifact types to be used as sources.\nExample: source, src");

        label = new Label(configComposite, SWT.NONE);
        label.setText("Sources suffixes:");

        sourcesSuffixesText = new Text(configComposite, SWT.SINGLE | SWT.BORDER);
        sourcesSuffixesText.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false,
                2, 1));
        sourcesSuffixesText
                .setToolTipText("Comma separated list of suffixes to match sources to artifacts.\nExample: -source, -src");

        label = new Label(configComposite, SWT.NONE);
        label.setText("Javadoc types:");

        javadocTypesText = new Text(configComposite, SWT.SINGLE | SWT.BORDER);
        javadocTypesText
                .setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false, 2, 1));
        javadocTypesText
                .setToolTipText("Comma separated list of artifact types to be used as javadoc.\nExample: javadoc.");

        label = new Label(configComposite, SWT.NONE);
        label.setText("Javadoc suffixes:");

        javadocSuffixesText = new Text(configComposite, SWT.SINGLE | SWT.BORDER);
        javadocSuffixesText.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false,
                2, 1));
        javadocSuffixesText
                .setToolTipText("Comma separated list of suffixes to match javadocs to artifacts.\nExample: -javadoc, -doc");

        doRetrieveButton = new Button(configComposite, SWT.CHECK);
        doRetrieveButton.setText("Do retrieve after resolve");
        doRetrieveButton.setLayoutData(new GridData(GridData.FILL, GridData.FILL, false, false, 3,
                1));

        label = new Label(configComposite, SWT.NONE);
        label.setText("Retrive pattern:");

        retrievePatternText = new Text(configComposite, SWT.SINGLE | SWT.BORDER);
        retrievePatternText.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false,
                2, 1));
        retrievePatternText.setEnabled(doRetrieveButton.getSelection());
        retrievePatternText
                .setToolTipText("Example: lib/[conf]/[artifact].[ext]\nTo copy artifacts in folder named lib without revision by folder named like configurations");

        doRetrieveButton.addSelectionListener(new SelectionAdapter() {
            public void widgetSelected(SelectionEvent e) {
                retrievePatternText.setEnabled(doRetrieveButton.getSelection());
            }
        });

        alphaOrderCheck = new Button(configComposite, SWT.CHECK);
        alphaOrderCheck
                .setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false, 2, 1));
        alphaOrderCheck.setText("Order alphabetically the classpath entries");
        alphaOrderCheck
                .setToolTipText("Order alphabetically the artifacts in the classpath container");

        return composite;
    }

    private void loadFromConf() {
        ivyFilePathText.setText(conf.ivyXmlPath);

        resolveModuleDescriptor();

        confTableViewer.setInput(conf.ivyXmlPath);
        initTableSelection();

        if (conf.isProjectSpecific()) {
            projectSpecificButton.setSelection(true);
            settingsText.setText(conf.ivySettingsPath);
            acceptedTypesText.setText(IvyClasspathUtil.concat(conf.acceptedTypes));
            sourcesTypesText.setText(IvyClasspathUtil.concat(conf.sourceTypes));
            sourcesSuffixesText.setText(IvyClasspathUtil.concat(conf.sourceSuffixes));
            javadocTypesText.setText(IvyClasspathUtil.concat(conf.javadocTypes));
            javadocSuffixesText.setText(IvyClasspathUtil.concat(conf.javadocSuffixes));
            doRetrieveButton.setSelection(conf.doRetrieve);
            retrievePatternText.setText(conf.retrievePattern);
            alphaOrderCheck.setSelection(conf.alphaOrder);
        } else {
            projectSpecificButton.setSelection(false);
            IvyDEPreferenceStoreHelper helper = IvyPlugin.getPreferenceStoreHelper();
            settingsText.setText(helper.getIvySettingsPath());
            acceptedTypesText.setText(IvyClasspathUtil.concat(helper.getAcceptedTypes()));
            sourcesTypesText.setText(IvyClasspathUtil.concat(helper.getSourceTypes()));
            sourcesSuffixesText.setText(IvyClasspathUtil.concat(helper.getSourceSuffixes()));
            javadocTypesText.setText(IvyClasspathUtil.concat(helper.getJavadocTypes()));
            javadocSuffixesText.setText(IvyClasspathUtil.concat(helper.getJavadocSuffixes()));
            doRetrieveButton.setSelection(helper.getDoRetrieve());
            retrievePatternText.setText(helper.getRetrievePattern());
            alphaOrderCheck.setSelection(helper.isAlphOrder());
        }

        updateFieldsStatus();
    }

    void updateFieldsStatus() {
        boolean projectSpecific = projectSpecificButton.getSelection();
        generalSettingsLink.setEnabled(!projectSpecific);
        configComposite.setEnabled(projectSpecific);
        settingsText.setEnabled(projectSpecific);
        acceptedTypesText.setEnabled(projectSpecific);
        sourcesTypesText.setEnabled(projectSpecific);
        sourcesSuffixesText.setEnabled(projectSpecific);
        javadocTypesText.setEnabled(projectSpecific);
        javadocSuffixesText.setEnabled(projectSpecific);
        doRetrieveButton.setEnabled(projectSpecific);
        retrievePatternText.setEnabled(doRetrieveButton.getSelection() && projectSpecific);
        alphaOrderCheck.setEnabled(projectSpecific);
    }

    File getFile(File startingDirectory) {
        FileDialog dialog = new FileDialog(getShell(), SWT.OPEN);
        if (startingDirectory != null) {
            dialog.setFileName(startingDirectory.getPath());
        }
        dialog.setFilterExtensions(new String[] {"*.xml", "*"});
        String file = dialog.open();
        if (file != null) {
            file = file.trim();
            if (file.length() > 0) {
                return new File(file);
            }
        }
        return null;
    }

    void resolveModuleDescriptor() {
        ivyXmlError = null;
        md = null;
        Ivy ivy = IvyPlugin.getIvy(conf.getInheritedIvySettingsPath());
        if (ivy == null) {
            ivyXmlError = "Error while confiuraing Ivy";
            return;
        }
        try {
            md = conf.getModuleDescriptor(ivy);
        } catch (MalformedURLException e) {
            ivyXmlError = "The path of the ivy.xml is not a valid URL";
        } catch (ParseException e) {
            ivyXmlError = "The ivy.xml has a syntax error: " + e.getMessage();
        } catch (IOException e) {
            ivyXmlError = "The ivy.xml could not be read: " + e.getMessage();
        }
    }

    /**
     * @param ivyFile
     */
    private void initTableSelection() {
        if (md != null) {
            Configuration[] configurations = md.getConfigurations();
            if ("*".equals(conf.confs.get(0))) {
                confTableViewer.setCheckedElements(configurations);
            } else {
                for (int i = 0; i < conf.confs.size(); i++) {
                    Configuration configuration = md.getConfiguration((String) conf.confs.get(i));
                    if (configuration != null) {
                        confTableViewer.setChecked(configuration, true);
                    }
                }
            }
        }
    }

    public void initialize(IJavaProject p, IClasspathEntry currentEntries[]) {
        this.project = p;
    }

    void refreshConfigurationTable() {
        if (confTableViewer.getInput() == null
                || !confTableViewer.getInput().equals(ivyFilePathText.getText())) {
            confTableViewer.setInput(ivyFilePathText.getText());
        }
    }

    static class ConfigurationLabelProvider extends LabelProvider implements ITableLabelProvider {

        public Image getColumnImage(Object element, int columnIndex) {
            return null;
        }

        public String getColumnText(Object element, int columnIndex) {
            if (columnIndex == 0) {
                return ((Configuration) element).getName();
            }
            return ((Configuration) element).getDescription();
        }
    }
}