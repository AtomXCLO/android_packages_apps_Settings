/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Changes from Qualcomm Innovation Center are provided under the following license:
 * Copyright (c) 2023 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.settings.network.telephony;

import android.app.Dialog;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import com.android.settings.R;
import com.android.settings.core.instrumentation.InstrumentedDialogFragment;
import com.android.settings.network.SubscriptionUtil;
import com.android.settings.wifi.WifiPickerTrackerHelper;

/**
 * Dialog Fragment to show dialog for "mobile data"
 *
 * 1. When user want to disable data in single sim case, show dialog to confirm
 * 2. When user want to enable data in multiple sim case, show dialog to confirm to disable other
 * sim
 */
public class MobileDataDialogFragment extends InstrumentedDialogFragment implements
        DialogInterface.OnClickListener {
    private static final String TAG = "MobileDataDialogFragment";

    public static final int TYPE_DISABLE_DIALOG = 0;
    public static final int TYPE_MULTI_SIM_DIALOG = 1;
    public static final int TYPE_DISABLE_CIWLAN_DIALOG = 2;

    private static final String ARG_PREF_TITLE = "pref_title";
    private static final String ARG_DIALOG_TYPE = "dialog_type";
    private static final String ARG_SUB_ID = "subId";
    private static final String ARG_CIWLAN_MODE_SUPPORTED = "ciwlan_mode_supported";

    private SubscriptionManager mSubscriptionManager;
    private String mPrefTitle;
    private int mType;
    private int mSubId;
    private boolean mCiwlanModeSupported;

    private WifiPickerTrackerHelper mWifiPickerTrackerHelper;

    public static MobileDataDialogFragment newInstance(String prefTitle, int type, int subId,
            boolean ciwlanModeSupported) {
        final MobileDataDialogFragment dialogFragment = new MobileDataDialogFragment();

        Bundle args = new Bundle();
        args.putString(ARG_PREF_TITLE, prefTitle);
        args.putInt(ARG_DIALOG_TYPE, type);
        args.putInt(ARG_SUB_ID, subId);
        args.putBoolean(ARG_CIWLAN_MODE_SUPPORTED, ciwlanModeSupported);
        dialogFragment.setArguments(args);

        return dialogFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSubscriptionManager = getContext().getSystemService(SubscriptionManager.class);
        mWifiPickerTrackerHelper = new WifiPickerTrackerHelper(getSettingsLifecycle(), getContext(),
                null /* WifiPickerTrackerCallback */);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Bundle bundle = getArguments();
        final Context context = getContext();

        mPrefTitle = bundle.getString(ARG_PREF_TITLE).toLowerCase();
        mType = bundle.getInt(ARG_DIALOG_TYPE);
        mSubId = bundle.getInt(ARG_SUB_ID);
        mCiwlanModeSupported = bundle.getBoolean(ARG_CIWLAN_MODE_SUPPORTED);

        switch (mType) {
            case TYPE_DISABLE_DIALOG:
                return new AlertDialog.Builder(context)
                        .setMessage(R.string.data_usage_disable_mobile)
                        .setPositiveButton(android.R.string.ok, this)
                        .setNegativeButton(android.R.string.cancel, null)
                        .create();
            case TYPE_MULTI_SIM_DIALOG:
                final SubscriptionInfo currentSubInfo =
                        mSubscriptionManager.getActiveSubscriptionInfo(mSubId);
                final SubscriptionInfo nextSubInfo =
                        mSubscriptionManager.getActiveSubscriptionInfo(
                                mSubscriptionManager.getDefaultDataSubscriptionId());

                final String previousName = (nextSubInfo == null)
                        ? getContext().getResources().getString(
                        R.string.sim_selection_required_pref)
                        : SubscriptionUtil.getUniqueSubscriptionDisplayName(
                                nextSubInfo, getContext()).toString();

                final String newName = (currentSubInfo == null)
                        ? getContext().getResources().getString(
                        R.string.sim_selection_required_pref)
                        : SubscriptionUtil.getUniqueSubscriptionDisplayName(
                                currentSubInfo, getContext()).toString();

                return new AlertDialog.Builder(context)
                        .setTitle(context.getString(R.string.sim_change_data_title, newName))
                        .setMessage(context.getString(R.string.sim_change_data_message,
                                newName, previousName))
                        .setPositiveButton(
                                context.getString(R.string.sim_change_data_ok, newName),
                                this)
                        .setNegativeButton(R.string.cancel, null)
                        .create();
            case TYPE_DISABLE_CIWLAN_DIALOG:
                String msg = mCiwlanModeSupported ?
                        context.getString(
                                R.string.toggle_disable_ciwlan_call_will_drop_dialog_body,
                                mPrefTitle) :
                        context.getString(
                                R.string.toggle_disable_ciwlan_call_might_drop_dialog_body,
                                mPrefTitle);
                return new AlertDialog.Builder(context)
                        .setTitle(context.getString(
                                R.string.toggle_disable_ciwlan_call_dialog_title, mPrefTitle))
                        .setMessage(msg)
                        .setPositiveButton(android.R.string.ok, this)
                        .setNegativeButton(android.R.string.cancel, null)
                        .create();
            default:
                throw new IllegalArgumentException("unknown type " + mType);
        }
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.MOBILE_DATA_DIALOG;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        switch (mType) {
            case TYPE_DISABLE_CIWLAN_DIALOG:
            case TYPE_DISABLE_DIALOG:
                Log.d(TAG, "setMobileDataEnabled: false");
                MobileNetworkUtils.setMobileDataEnabled(getContext(), mSubId, false /* enabled */,
                        false /* disableOtherSubscriptions */);
                if (mWifiPickerTrackerHelper != null
                        && !mWifiPickerTrackerHelper.isCarrierNetworkProvisionEnabled(mSubId)) {
                    mWifiPickerTrackerHelper.setCarrierNetworkEnabled(false);
                }
                break;
            case TYPE_MULTI_SIM_DIALOG:
                mSubscriptionManager.setDefaultDataSubId(mSubId);
                Log.d(TAG, "setMobileDataEnabled: true");
                MobileNetworkUtils.setMobileDataEnabled(getContext(), mSubId, true /* enabled */,
                        true /* disableOtherSubscriptions */);
                if (mWifiPickerTrackerHelper != null
                        && !mWifiPickerTrackerHelper.isCarrierNetworkProvisionEnabled(mSubId)) {
                    mWifiPickerTrackerHelper.setCarrierNetworkEnabled(true);
                }
                break;
            default:
                throw new IllegalArgumentException("unknown type " + mType);
        }
    }

}
