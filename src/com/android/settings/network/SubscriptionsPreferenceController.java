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

package com.android.settings.network;

import static androidx.lifecycle.Lifecycle.Event.ON_PAUSE;
import static androidx.lifecycle.Lifecycle.Event.ON_RESUME;

import android.content.Context;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import com.android.settings.R;
import com.android.settingslib.core.AbstractPreferenceController;

import java.util.Map;

import androidx.collection.ArrayMap;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

/**
 * This manages a set of Preferences it places into a PreferenceGroup owned by some parent
 * controller class - one for each available subscription. This controller is only considered
 * available if there are 2 or more subscriptions.
 */
public class SubscriptionsPreferenceController extends AbstractPreferenceController implements
        LifecycleObserver, SubscriptionsChangeListener.SubscriptionsChangeListenerClient {
    private static final String TAG = "SubscriptionsPrefCntrlr";

    private UpdateListener mUpdateListener;
    private String mPreferenceGroupKey;
    private PreferenceGroup mPreferenceGroup;
    private SubscriptionManager mManager;
    private SubscriptionsChangeListener mSubscriptionsListener;

    // Map of subscription id to Preference
    private Map<Integer, Preference> mSubscriptionPreferences;
    private int mStartOrder;

    /**
     * This interface lets a parent of this class know that some change happened - this could
     * either be because overall availability changed, or because we've added/removed/updated some
     * preferences.
     */
    public interface UpdateListener {
        void onChildrenUpdated();
    }

    /**
     * @param context            the context for the UI where we're placing these preferences
     * @param lifecycle          for listening to lifecycle events for the UI
     * @param updateListener     called to let our parent controller know that our availability has
     *                           changed, or that one or more of the preferences we've placed in the
     *                           PreferenceGroup has changed
     * @param preferenceGroupKey the key used to lookup the PreferenceGroup where Preferences will
     *                           be placed
     * @param startOrder         the order that should be given to the first Preference placed into
     *                           the PreferenceGroup; the second will use startOrder+1, third will
     *                           use startOrder+2, etc. - this is useful for when the parent wants
     *                           to have other preferences in the same PreferenceGroup and wants
     *                           a specific ordering relative to this controller's prefs.
     */
    public SubscriptionsPreferenceController(Context context, Lifecycle lifecycle,
            UpdateListener updateListener, String preferenceGroupKey, int startOrder) {
        super(context);
        mUpdateListener = updateListener;
        mPreferenceGroupKey = preferenceGroupKey;
        mStartOrder = startOrder;
        mManager = context.getSystemService(SubscriptionManager.class);
        mSubscriptionPreferences = new ArrayMap<>();
        mSubscriptionsListener = new SubscriptionsChangeListener(context, this);
        lifecycle.addObserver(this);
    }

    @OnLifecycleEvent(ON_RESUME)
    public void onResume() {
        mSubscriptionsListener.start();
        update();
    }

    @OnLifecycleEvent(ON_PAUSE)
    public void onPause() {
        mSubscriptionsListener.stop();
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        mPreferenceGroup = (PreferenceGroup) screen.findPreference(mPreferenceGroupKey);
        update();
    }

    private void update() {
        if (mPreferenceGroup == null) {
            return;
        }

        if (mSubscriptionsListener.isAirplaneModeOn()) {
            for (Preference pref : mSubscriptionPreferences.values()) {
                mPreferenceGroup.removePreference(pref);
            }
            mSubscriptionPreferences.clear();
            mUpdateListener.onChildrenUpdated();
            return;
        }

        final Map<Integer, Preference> existingPrefs = mSubscriptionPreferences;
        mSubscriptionPreferences = new ArrayMap<>();

        int order = mStartOrder;
        for (SubscriptionInfo info :  SubscriptionUtil.getAvailableSubscriptions(mManager) ) {
            final int subId = info.getSubscriptionId();
            Preference pref = existingPrefs.remove(subId);
            if (pref == null) {
                pref = new Preference(mPreferenceGroup.getContext());
                mPreferenceGroup.addPreference(pref);
            }
            pref.setTitle(info.getDisplayName());
            pref.setIcon(R.drawable.ic_network_cell);
            pref.setOrder(order++);

            // TODO(asargent) - set summary here to indicate default for calls/sms and data

            pref.setOnPreferenceClickListener(clickedPref -> {
                // TODO(asargent) - make this start MobileNetworkActivity once we've
                // added support for it to take a subscription id
                return true;
            });

            mSubscriptionPreferences.put(subId, pref);
        }

        // Remove any old preferences that no longer map to a subscription.
        for (Preference pref : existingPrefs.values()) {
            mPreferenceGroup.removePreference(pref);
        }
        mUpdateListener.onChildrenUpdated();
    }

    /**
     *
     * @return true if there are at least 2 available subscriptions.
     */
    @Override
    public boolean isAvailable() {
        if (mSubscriptionsListener.isAirplaneModeOn()) {
            return false;
        }
        return SubscriptionUtil.getAvailableSubscriptions(mManager).size() >= 2;
    }

    @Override
    public String getPreferenceKey() {
        return null;
    }

    @Override
    public void onAirplaneModeChanged(boolean airplaneModeEnabled) {
        update();
    }

    @Override
    public void onSubscriptionsChanged() {
        update();
    }
}
