/*
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */

package org.openmrs.mobile.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.TextView;

import org.openmrs.mobile.R;
import org.openmrs.mobile.activities.fragments.CustomFragmentDialog;
import org.openmrs.mobile.adapters.VisitExpandableListAdapter;
import org.openmrs.mobile.application.OpenMRS;
import org.openmrs.mobile.bundle.CustomDialogBundle;
import org.openmrs.mobile.dao.EncounterDAO;
import org.openmrs.mobile.dao.FormsDAO;
import org.openmrs.mobile.dao.PatientDAO;
import org.openmrs.mobile.dao.VisitDAO;
import org.openmrs.mobile.models.Encounter;
import org.openmrs.mobile.models.Patient;
import org.openmrs.mobile.models.Visit;
import org.openmrs.mobile.net.FormsManger;
import org.openmrs.mobile.net.VisitsManager;
import org.openmrs.mobile.utilities.ApplicationConstants;
import org.openmrs.mobile.utilities.DateUtils;
import org.openmrs.mobile.utilities.FontsUtil;
import org.openmrs.mobile.utilities.ToastUtil;

import java.util.List;

public class VisitDashboardActivity extends ACBaseActivity implements VisitsManager.UpdateVisitEncountersCallback, FormsManger.FormManagerListener {

    public static final int CAPTURE_VITALS_REQUEST_CODE = 1;
    private ExpandableListView mExpandableListView;
    private VisitExpandableListAdapter mExpandableListAdapter;
    private List<Encounter> mVisitEncounters;
    private TextView mEmptyListView;
    private Visit mVisit;
    private String mPatientName;
    private Patient mPatient;
    private VisitsManager mVisitsManager;
    private String mSelectedPatientUUID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_visit_dashboard);
        mVisitsManager = new VisitsManager(this, this);
        Intent intent = getIntent();

        if (savedInstanceState != null) {
            mVisit = (Visit) savedInstanceState.getSerializable("");
        } else {
            mVisit = new VisitDAO().getVisitsByID(intent.getLongExtra(ApplicationConstants.BundleKeys.VISIT_ID, 0));
        }
        mPatient = new PatientDAO().findPatientByID(String.valueOf(mVisit.getPatientID()));

        mPatientName = intent.getStringExtra(ApplicationConstants.BundleKeys.PATIENT_NAME);
        mVisitEncounters = mVisit.getEncounters();
        mExpandableListAdapter = new VisitExpandableListAdapter(this, mVisitEncounters);

        mEmptyListView = (TextView) findViewById(R.id.visitDashboardEmpty);
        FontsUtil.setFont(mEmptyListView, FontsUtil.OpenFonts.OPEN_SANS_BOLD);
        mExpandableListView = (ExpandableListView) findViewById(R.id.visitDashboardExpList);
        mExpandableListView.setEmptyView(mEmptyListView);
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        super.startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onResume() {
        if (!mVisitEncounters.isEmpty() || null != mVisitEncounters) {
            mEmptyListView.setVisibility(View.GONE);
            mExpandableListView.setAdapter(mExpandableListAdapter);
            mExpandableListView.setGroupIndicator(null);
        }
        super.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (DateUtils.ZERO.equals(mVisit.getStopDate())) {
            getMenuInflater().inflate(R.menu.active_visit_menu, menu);
        }
        getSupportActionBar().setSubtitle(mPatientName);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                this.finish();
                break;
            case R.id.actionCaptureVitals:
                this.captureVitals(mPatient.getUuid());
                break;
            case R.id.actionEndVisit:
                this.showEndVisitDialog();
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    public void captureVitals(String patientUUID) {
        mSelectedPatientUUID = patientUUID;
        try {
            Intent intent = new Intent(this, FormEntryActivity.class);
            Uri formURI = new FormsDAO(this.getContentResolver()).getFormURI(ApplicationConstants.FormNames.VITALS_XFORM);
            intent.setData(formURI);
            intent.putExtra(ApplicationConstants.BundleKeys.PATIENT_UUID_BUNDLE, mSelectedPatientUUID);
            intent.putExtra(ApplicationConstants.BundleKeys.VISIT_ID, mVisit.getId());
            this.startActivityForResult(intent, CAPTURE_VITALS_REQUEST_CODE);
        } catch (Exception e) {
            ToastUtil.showLongToast(this, ToastUtil.ToastType.ERROR, R.string.failed_to_open_vitals_form);
            OpenMRS.getInstance().getOpenMRSLogger().d(e.toString());
        }
    }

    public void endVisit() {
        mVisitsManager.inactivateVisitByUUID(mVisit.getUuid(), mPatient.getId(), mVisit.getId());
    }

    private void showEndVisitDialog() {
        CustomDialogBundle bundle = new CustomDialogBundle();
        bundle.setTitleViewMessage(getString(R.string.end_visit_dialog_title));
        bundle.setTextViewMessage(getString(R.string.end_visit_dialog_message));
        bundle.setRightButtonAction(CustomFragmentDialog.OnClickAction.END_VISIT);
        bundle.setRightButtonText(getString(R.string.dialog_button_ok));
        bundle.setLeftButtonAction(CustomFragmentDialog.OnClickAction.DISMISS);
        bundle.setLeftButtonText(getString(R.string.dialog_button_cancel));
        createAndShowDialog(bundle, ApplicationConstants.DialogTAG.END_VISIT_DIALOG_TAG);
    }

    public void moveToPatientDashboard() {
        Intent intent = new Intent();
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(ApplicationConstants.BundleKeys.PATIENT_UUID_BUNDLE, mSelectedPatientUUID);
        outState.putSerializable("", mVisit);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (resultCode) {
            case RESULT_OK:
                String path = data.getData().toString();
                String instanceID = path.substring(path.lastIndexOf('/') + 1);
                new FormsManger(this, this).uploadXFormWithMultiPartRequest(
                        new FormsDAO(getContentResolver())
                                .getSurveysSubmissionDataFromFormInstanceId(instanceID)
                                .getFormInstanceFilePath(), mPatient.getUuid());
                break;
            case RESULT_CANCELED:
                finish();
            default:
                break;
        }
    }

    @Override
    public void updateVisitEncounterList() {
        mVisitEncounters.clear();
        mExpandableListAdapter.notifyDataSetChanged();
        mVisit.setEncounters(new EncounterDAO().findEncountersByVisitID(mVisit.getId()));
        mVisitEncounters.addAll(mVisit.getEncounters());
        mExpandableListAdapter.notifyDataSetChanged();
    }

    @Override
    public void updateVisitData() {
        mVisitsManager.findVisitByUUID(mVisit.getUuid(), mPatient.getId());
    }
}
