/*
 * Copyright (C) 2020-2026 crDroid Android Project
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

package com.android.systemui.qs.tiles;

import static com.android.internal.logging.MetricsLogger.VIEW_UNKNOWN;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.systemui.animation.Expandable;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.QsEventLogger;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.pipeline.domain.interactor.PanelInteractor;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.res.R;

import java.util.List;

import javax.inject.Inject;

public class DataSwitchTile extends QSTileImpl<BooleanState> {

    public static final String TILE_SPEC = "dataswitch";

    private boolean mCanSwitch = true;
    private boolean mRegistered = false;
    private int mSimCount = 0;

    private final BroadcastReceiver mSimReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "mSimReceiver:onReceive");
            refreshState();
        }
    };

    private final MyCallStateListener mPhoneStateListener;
    private final SubscriptionManager mSubscriptionManager;
    private final TelephonyManager mTelephonyManager;
    private final PanelInteractor mPanelInteractor;

    class MyCallStateListener extends PhoneStateListener {
        @Override
        public void onCallStateChanged(int state, String arg1) {
            mCanSwitch = mTelephonyManager.getCallState() == TelephonyManager.CALL_STATE_IDLE;
            refreshState();
        }
    }

    @Inject
    public DataSwitchTile(
            QSHost host,
            QsEventLogger uiEventLogger,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger,
            PanelInteractor panelInteractor
    ) {
        super(host, uiEventLogger, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);
        mSubscriptionManager = SubscriptionManager.from(host.getContext());
        mTelephonyManager = TelephonyManager.from(host.getContext());
        mPhoneStateListener = new MyCallStateListener();
        mPanelInteractor = panelInteractor;
    }

    @Override
    public boolean isAvailable() {
        int count = TelephonyManager.getDefault().getPhoneCount();
        Log.d(TAG, "phoneCount: " + count);
        return count >= 2;
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void handleSetListening(boolean listening) {
        if (listening) {
            if (!mRegistered) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
                mContext.registerReceiver(mSimReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
                mRegistered = true;
            }
            refreshState();
        } else if (mRegistered) {
            mContext.unregisterReceiver(mSimReceiver);
            mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
            mRegistered = false;
        }
    }

    @Override
    protected void handleClick(@Nullable Expandable expandable) {
        if (!mCanSwitch) {
            Log.d(TAG, "Call state=" + mTelephonyManager.getCallState());
            return;
        }
        if (mSimCount < 2) {
            Log.d(TAG, "handleClick: less than 2 SIM cards");
            return;
        }

        AsyncTask.execute(() -> {
            toggleMobileDataEnabled();
            refreshState();
        });
        mPanelInteractor.collapsePanels();
    }

    @Override
    public Intent getLongClickIntent() {
        return new Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS);
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.qs_data_switch_label);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final boolean activeSIMZero;
        if (arg == null) {
            int defaultPhoneId = mSubscriptionManager.getPhoneId(
                    mSubscriptionManager.getDefaultDataSubscriptionId());
            Log.d(TAG, "default data phone id=" + defaultPhoneId);
            activeSIMZero = defaultPhoneId == 0;
        } else {
            activeSIMZero = (Boolean) arg;
        }

        updateSimCount();
        switch (mSimCount) {
            case 0:
                state.icon = maybeLoadResourceIcon(R.drawable.ic_qs_data_switch_0);
                state.value = false;
                state.secondaryLabel = mContext.getString(R.string.tile_unavailable);
                break;
            case 1:
                state.icon = maybeLoadResourceIcon(activeSIMZero
                        ? R.drawable.ic_qs_data_switch_1
                        : R.drawable.ic_qs_data_switch_2);
                state.value = false;
                state.secondaryLabel = mContext.getString(R.string.tile_unavailable);
                break;
            case 2:
                state.icon = maybeLoadResourceIcon(activeSIMZero
                        ? R.drawable.ic_qs_data_switch_1
                        : R.drawable.ic_qs_data_switch_2);
                state.value = true;
                state.secondaryLabel = getActiveSlotName();
                break;
            default:
                state.icon = maybeLoadResourceIcon(R.drawable.ic_qs_data_switch_1);
                state.value = false;
                state.secondaryLabel = mContext.getString(R.string.tile_unavailable);
                break;
        }

        if (mSimCount < 2 || !mCanSwitch) {
            state.state = Tile.STATE_UNAVAILABLE;
        } else {
            state.state = state.value ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
        }

        state.label = mContext.getString(R.string.qs_data_switch_label);
    }

    @Override
    public int getMetricsCategory() {
        return VIEW_UNKNOWN;
    }

    private void updateSimCount() {
        String simState = SystemProperties.get("gsm.sim.state");
        Log.d(TAG, "DataSwitchTile:updateSimCount:simState=" + simState);
        mSimCount = 0;
        try {
            String[] sims = TextUtils.split(simState, ",");
            for (String sim : sims) {
                if (!sim.isEmpty()
                        && !sim.equalsIgnoreCase(IccCardConstants.INTENT_VALUE_ICC_ABSENT)
                        && !sim.equalsIgnoreCase(IccCardConstants.INTENT_VALUE_ICC_NOT_READY)) {
                    mSimCount++;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing sim state", e);
        }
        Log.d(TAG, "DataSwitchTile:updateSimCount:mSimCount=" + mSimCount);
    }

    private void toggleMobileDataEnabled() {
        TelephonyManager telephonyManager;
        boolean dataEnabled;
        boolean foundActive = false;

        List<SubscriptionInfo> subInfoList = mSubscriptionManager.getActiveSubscriptionInfoList(true);
        if (subInfoList == null) {
            return;
        }

        for (SubscriptionInfo subInfo : subInfoList) {
            int subId = subInfo.getSubscriptionId();
            telephonyManager = mTelephonyManager.createForSubscriptionId(subId);
            dataEnabled = telephonyManager.getDataEnabled();
            if (subInfo.isOpportunistic() && dataEnabled) {
                // Never disable mobile data for opportunistic subscriptions.
                continue;
            }

            dataEnabled = !dataEnabled && !foundActive;
            telephonyManager.setDataEnabled(dataEnabled);
            if (dataEnabled) {
                mSubscriptionManager.setDefaultDataSubId(subId);
            }
            // If a SIM is now active, force all remaining SIMs inactive.
            if (!foundActive) {
                foundActive = dataEnabled;
            }
            Log.d(TAG, "Changed subId " + subId + " to " + dataEnabled);
        }
    }

    private String getActiveSlotName() {
        TelephonyManager telephonyManager;
        String defaultState = mContext.getString(R.string.tile_unavailable);

        List<SubscriptionInfo> subInfoList = mSubscriptionManager.getActiveSubscriptionInfoList(true);
        if (subInfoList == null) {
            return defaultState;
        }

        for (SubscriptionInfo subInfo : subInfoList) {
            telephonyManager = mTelephonyManager.createForSubscriptionId(subInfo.getSubscriptionId());
            if (telephonyManager.getDataEnabled()) {
                CharSequence displayName = subInfo.getDisplayName();
                return displayName != null ? displayName.toString() : defaultState;
            }
        }
        return defaultState;
    }
}
