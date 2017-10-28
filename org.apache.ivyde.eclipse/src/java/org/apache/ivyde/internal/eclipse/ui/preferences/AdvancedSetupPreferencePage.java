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
package org.apache.ivyde.internal.eclipse.ui.preferences;

import org.apache.ivyde.internal.eclipse.IvyPlugin;
import org.apache.ivyde.internal.eclipse.ui.AdvancedSetupEditor;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

public class AdvancedSetupPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

    /** the ID of the preference page */
    public static final String PREFERENCE_PAGE_ID = "org.apache.ivyde.eclipse.ui.preferences.AdvancedSetupPreferencePage";

    private AdvancedSetupEditor advancedSetupEditor;

    public AdvancedSetupPreferencePage() {
        setPreferenceStore(IvyPlugin.getDefault().getPreferenceStore());
    }

    public void init(IWorkbench workbench) {
        setPreferenceStore(IvyPlugin.getDefault().getPreferenceStore());
    }

    protected Control createContents(Composite parent) {
        advancedSetupEditor = new AdvancedSetupEditor(parent, SWT.NONE);
        advancedSetupEditor.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, true));

        advancedSetupEditor.init(IvyPlugin.getPreferenceStoreHelper().getAdvancedSetup(), false);

        return advancedSetupEditor;
    }

    public boolean performOk() {
        IvyDEPreferenceStoreHelper helper = IvyPlugin.getPreferenceStoreHelper();
        helper.setAdvancedSetup(advancedSetupEditor.getAdvancedSetup());
        return true;
    }

    protected void performDefaults() {
        advancedSetupEditor.init(PreferenceInitializer.DEFAULT_ADVANCED_SETUP, false);
    }
}
