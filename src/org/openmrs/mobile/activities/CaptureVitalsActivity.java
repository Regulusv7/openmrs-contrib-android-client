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

import com.android.volley.Response;

import org.json.JSONException;
import org.json.JSONObject;
import org.openmrs.mobile.R;
import org.openmrs.mobile.activities.fragments.PatientsVitalsListFragment;
import org.openmrs.mobile.application.OpenMRS;
import org.openmrs.mobile.bundle.PatientListBundle;
import org.openmrs.mobile.dao.FormsDAO;
import org.openmrs.mobile.dao.PatientDAO;
import org.openmrs.mobile.dao.VisitDAO;
import org.openmrs.mobile.models.Patient;
import org.openmrs.mobile.models.mappers.VisitMapper;
import org.openmrs.mobile.net.FormsManger;
import org.openmrs.mobile.net.VisitsManager;
import org.openmrs.mobile.utilities.ApplicationConstants;
import org.openmrs.mobile.utilities.ToastUtil;

import java.util.List;

public class CaptureVitalsActivity extends ACBaseActivity implements FormsManger.FormManagerListener {

    public static final int CAPTURE_VITALS_REQUEST_CODE = 1;

    private String mSelectedPatientUUID;
    private VisitsManager mVisitsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.activity_capture_vitals);
        mVisitsManager = new VisitsManager(this);

        if (null != savedInstanceState) {
            mSelectedPatientUUID = savedInstanceState.getString(ApplicationConstants.BundleKeys.PATIENT_UUID_BUNDLE);
        }

        List<Patient> patientList = new PatientDAO().getAllPatients();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.patientVitalsList, PatientsVitalsListFragment.newInstance(new PatientListBundle(patientList))).commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //no context menu for this activity
        return false;
    }

    public void startFormEntryForResult(Long patientId, String patientUUID) {
        mSelectedPatientUUID = patientUUID;
        if (new VisitDAO().shouldStartNewVisit(patientId)) {
            mVisitsManager.createVisit(patientUUID, new CreateVisitCallbackListener(patientId));
        }

        try {
            Intent intent = new Intent(this, FormEntryActivity.class);
            Uri formURI = new FormsDAO(this.getContentResolver()).getFormURI(ApplicationConstants.FormNames.VITALS_XFORM);
            intent.setData(formURI);
            intent.putExtra(ApplicationConstants.BundleKeys.PATIENT_UUID_BUNDLE, patientUUID);
            this.startActivityForResult(intent, CAPTURE_VITALS_REQUEST_CODE);
        } catch (Exception e) {
            ToastUtil.showLongToast(this, ToastUtil.ToastType.ERROR, R.string.failed_to_open_vitals_form);
            OpenMRS.getInstance().getOpenMRSLogger().d(e.toString());
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(ApplicationConstants.BundleKeys.PATIENT_UUID_BUNDLE, mSelectedPatientUUID);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (resultCode) {
            case RESULT_OK:
                String path = data.getData().toString();
                final String instanceID = path.substring(path.lastIndexOf('/') + 1);
                uploadXForm(instanceID);
                finish();
                break;
            case RESULT_CANCELED:
                finish();
            default:
                break;
        }
    }

    @Override
    public void updateVisitData() {
        Patient patient = new PatientDAO().findPatientByUUID(mSelectedPatientUUID);
        mVisitsManager.findVisitByUUID(new VisitDAO().getActiveVisitForPatientByPatientID(
                        patient.getId()).getUuid(),
                patient.getId());
    }

    public void uploadXForm(String instanceID) {
        new FormsManger(this, this).uploadXFormWithMultiPartRequest(
                new FormsDAO(getContentResolver()).getSurveysSubmissionDataFromFormInstanceId(instanceID)
                        .getFormInstanceFilePath(), mSelectedPatientUUID);
    }

    public static final class CreateVisitCallbackListener implements Response.Listener<JSONObject> {
        private long mPatientID;

        public CreateVisitCallbackListener(Long patientID) {
            mPatientID = patientID;
        }

        @Override
        public void onResponse(JSONObject response) {
            try {
                new VisitDAO().saveVisit(VisitMapper.map(response), mPatientID);
            } catch (JSONException e) {
                OpenMRS.getInstance().getOpenMRSLogger().d(e.toString());
            }
        }
    }
}
