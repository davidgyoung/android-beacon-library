package org.altbeacon.beacon;

/**
 * Created by dyoung on 7/25/17.
 */

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v4.content.LocalBroadcastManager;

import org.altbeacon.beacon.logging.LogManager;
import org.altbeacon.beacon.logging.Loggers;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@TargetApi(18)
public class BeaconManagerV3 {
    public static final String RANGING_NOTIFICATION_NAME = "org.altbeacon.BeaconManagerV3.ranging_notification";
    private static final String TAG = "BeaconManagerV3";
    @NonNull
    private static  BeaconManagerV3 sInstance;
    @NonNull
    private BeaconManager mBeaconManager;
    @NonNull
    private Context mContext;
    private int activeActivityCount = 0;
    private int mSecondsToDelayBackgroundTransition = 180;
    private long mLastBackgroundTransitionTime = 0l;
    private long mLastRegionEntryOrExitTime = 0l;
    private boolean mServiceConnected = false;
    @NonNull
    private List<Region> mRangedRegions = new ArrayList<>();
    @NonNull
    private List<Region> mMonitoredRegions = new ArrayList<>();
    @NonNull
    private InternalBeaconConsumer mBeaconConsumer;
    @NonNull
    private List<RangeNotifier> mRangeNotifiers = new ArrayList<>();
    @NonNull
    private List<MonitorNotifier> mMonitorNotifiers = new ArrayList<>();
    @NonNull
    private BackgroundStateMonitor mBackgroundStateMonitor = new BackgroundStateMonitor();
    @Nullable
    private Handler mBackgroundModeEvaluationHandler = null;

    public static synchronized  BeaconManagerV3 getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new  BeaconManagerV3(context);
        }
        return sInstance;
    }

    public void addBeaconParser(BeaconParser parser) {
        mBeaconManager.getBeaconParsers().add(parser);
    }

    public void clearBeaconParsers() {
        mBeaconManager.getBeaconParsers().clear();
    }

    public void setDebug(boolean debugOn) {
        if(debugOn) {
            LogManager.setLogger(Loggers.verboseLogger());
            LogManager.setVerboseLoggingEnabled(true);
        } else {
            LogManager.setLogger(Loggers.empty());
            LogManager.setVerboseLoggingEnabled(false);
        }
    }

    public void addRangeNotifier(RangeNotifier notifier) {
        synchronized (mRangeNotifiers) {
            mRangeNotifiers.add(notifier);
        }
    }

    public void removeMonitorNotifier(MonitorNotifier notifier) {
        synchronized (mMonitorNotifiers) {
            for (MonitorNotifier exitingNotifier : mMonitorNotifiers) {
                if (exitingNotifier == notifier) {
                    mMonitorNotifiers.remove(notifier);
                    return;
                }
            }
        }
    }

    public void removeRangeNotifier(RangeNotifier notifier) {
        synchronized (mRangeNotifiers) {
            for (RangeNotifier exitingNotifier : mRangeNotifiers) {
                if (exitingNotifier == notifier) {
                    mRangeNotifiers.remove(notifier);
                    return;
                }
            }
        }
    }

    public void addMonitorNotifier(MonitorNotifier notifier) {
        synchronized (mMonitorNotifiers) {
            mMonitorNotifiers.add(notifier);
        }
    }

    public void startRangingBeaconsInRegion(Region region) {
        LogManager.d(TAG, "startRanging called");
        Region existingRegion = findRegionInList(region, mRangedRegions);
        if (existingRegion != null) {
            stopRangingBeaconsInRegion(region);
        }
        synchronized (mRangedRegions) {
            mRangedRegions.add(region);
        }
        if (mServiceConnected) {
            try {
                LogManager.d(TAG, "we are connectd, so we actually will start ranging.");
                mBeaconManager.startRangingBeaconsInRegion(region);
            }
            catch (RemoteException e) {
                handleRemoteException();
            }
        }
        else {
            LogManager.d(TAG, "We are not connected yet.  Waiting to actually start ranging later.");
        }
    }

    public void stopRangingBeaconsInRegion(Region region) {
        Region existingRegion = findRegionInList(region, mRangedRegions);
        if (existingRegion != null) {
            mRangedRegions.remove(existingRegion);
            if (mServiceConnected) {
                try {
                    mBeaconManager.stopRangingBeaconsInRegion(region);
                } catch (RemoteException e) {
                    handleRemoteException();
                }
            }
        }
    }

    public void startMonitoringBeaconsInRegion(Region region) {
        Region existingRegion = findRegionInList(region, mMonitoredRegions);
        if (existingRegion != null) {
            stopMonitoringBeaconsInRegion(region);
        }
        synchronized (mMonitoredRegions) {
            mMonitoredRegions.add(region);
        }
        if (mServiceConnected) {
            try {
                mBeaconManager.startMonitoringBeaconsInRegion(region);
            }
            catch (RemoteException e) {
                handleRemoteException();
            }
        }
        else {
            LogManager.d(TAG, "We are not connected yet.  Waiting to actually start monitoring later.");
        }
    }

    public void stopMonitoringBeaconsInRegion(Region region) {
        Region existingRegion = findRegionInList(region, mMonitoredRegions);
        if (existingRegion != null) {
            mMonitoredRegions.remove(existingRegion);
            if (mServiceConnected) {
                try {
                    mBeaconManager.stopMonitoringBeaconsInRegion(region);
                } catch (RemoteException e) {
                    handleRemoteException();
                }
            }
        }
    }

    public List<Region> getRangedRegions() {
        synchronized (mRangedRegions) {
            return new ArrayList<Region>(mRangedRegions);
        }
    }

    public List<Region> getMonitoredRegions() {
        synchronized (mMonitoredRegions) {
            return new ArrayList<Region>(mMonitoredRegions);
        }
    }

    private void handleRemoteException() {
        LogManager.e(TAG, "Remote exception.  Rebinding");
        mServiceConnected = false;
        mBeaconManager.unbind(mBeaconConsumer);
        mBeaconManager.bind(mBeaconConsumer);
    }

    private Region findRegionInList(Region region, List<Region> regions) {
        synchronized (regions) {
            for (Region listRegion : regions) {
                if (region.getUniqueId().equals(listRegion.getUniqueId())) {
                    return listRegion;
                }
            }
        }
        return null;
    }

    private  BeaconManagerV3(Context context) {
        if (android.os.Build.VERSION.SDK_INT < 18) {
            LogManager.w(TAG, "Android Beacon Library requires API 18 or higher.");
            return;
        }
        mContext = context;
        ((Application)context.getApplicationContext()).registerActivityLifecycleCallbacks(mBackgroundStateMonitor);
        mBeaconManager = BeaconManager.getInstanceForApplication(context);
        mBeaconConsumer = new InternalBeaconConsumer();
        mBeaconManager.bind(mBeaconConsumer);
        LogManager.d(TAG, "Waiting for BeaconService connection");
    }

    public void setSecondsOfFullScanningInBackground(int secs) {
        mSecondsToDelayBackgroundTransition = secs;
    }

    private class BackgroundStateMonitor implements Application.ActivityLifecycleCallbacks {
        @Override
        public void onActivityCreated(Activity activity, Bundle bundle) {
        }

        @Override
        public void onActivityStarted(Activity activity) {
        }

        @Override
        public void onActivityResumed(Activity activity) {
            activeActivityCount++;
            if (activeActivityCount < 1) {
                LogManager.d(TAG, "reset active activity count on resume.  It was "+activeActivityCount);
                activeActivityCount = 1;
            }
            LogManager.d(TAG, "activity resumed: "+activity+" active activities: "+ activeActivityCount);
            mLastBackgroundTransitionTime = 0;
            evaluateBackgroundModeChanges();
        }

        @Override
        public void onActivityPaused(Activity activity) {
            activeActivityCount--;
            LogManager.d(TAG, "activity paused: "+activity+" active activities: "+ activeActivityCount);
            if (activeActivityCount < 1) {
                if (mLastBackgroundTransitionTime == 0) {
                    mLastBackgroundTransitionTime = System.currentTimeMillis();
                    evaluateBackgroundModeChanges();
                }
            }
        }

        @Override
        public void onActivityStopped(Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {

        }

        @Override
        public void onActivityDestroyed(Activity activity) {
        }
    }


    private void evaluateBackgroundModeChanges() {
        long secondsSinceBackgroundTransition = (System.currentTimeMillis()-mLastBackgroundTransitionTime) /1000;
        long secondsSinceRegionTransition = (System.currentTimeMillis()-mLastRegionEntryOrExitTime) / 1000;
        LogManager.d(TAG, "Evaluating background mode changes.  Secs since foreground: "+secondsSinceBackgroundTransition+".  Secs since region entry/exit: "+secondsSinceRegionTransition);
        long secondsSinceTransition = secondsSinceBackgroundTransition < secondsSinceRegionTransition ?
                secondsSinceBackgroundTransition : secondsSinceRegionTransition;
        if (mLastBackgroundTransitionTime == 0) {
            LogManager.d(TAG, "We are in the foreground.");
            mBeaconManager.setBackgroundMode(false);
        }
        else if (secondsSinceTransition >= mSecondsToDelayBackgroundTransition) {
            LogManager.d(TAG, "Going to background beacon scanning mode as sufficient seconds have passed since background or region transition: "+secondsSinceTransition);
            mBeaconManager.setBackgroundMode(true);
        }
        else {
            if (mBeaconManager.getBackgroundMode()) {
                // We are in the background but we should not be.  We probably were in the background when we entered/exited a region.
                LogManager.d(TAG, "A region or background transition has happened recently. Exiting background mode.");
                mBeaconManager.setBackgroundMode(false);
            }
            else {
                LogManager.d(TAG, "Not enough time has passed ("+secondsSinceTransition+" secs) since the last background or region transition to go to background scanning mode.");
            }
            long secsToWait = (mSecondsToDelayBackgroundTransition-secondsSinceTransition);

            if (mBackgroundModeEvaluationHandler == null) {
                LogManager.d(TAG, "Re-evaluating in "+secsToWait+" secs.");
                // What if we go into doze mode before going into background mode?  We will end up scanning with foreground settings until user wakes up phone.
                mBackgroundModeEvaluationHandler = new Handler();
                if (!mBackgroundModeEvaluationHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        LogManager.d(TAG, "Waking up to evaluate background mode changes.");
                        mBackgroundModeEvaluationHandler = null;
                        evaluateBackgroundModeChanges();
                    }
                }, secsToWait*1000)) {
                    LogManager.w(TAG, "Failed to schedule background mode evaluation.  Going into background mode immediately.");
                    mBeaconManager.setBackgroundMode(true);
                    mBackgroundModeEvaluationHandler = null;
                }
            }
            else {
                LogManager.d(TAG, "re-evaluation already scheduled");
            }

        }
    }

    private class InternalBeaconConsumer implements BeaconConsumer, RangeNotifier, MonitorNotifier {

        private Intent serviceIntent;

        /**
         * Method reserved for system use
         */
        @Override
        public void onBeaconServiceConnect() {
            LogManager.d(TAG, "Activating background region monitoring");
            mBeaconManager.addMonitorNotifier(this);
            mBeaconManager.addRangeNotifier(this);
            mServiceConnected = true;
            try {
                synchronized (mMonitoredRegions) {
                    for (Region region : mMonitoredRegions) {
                        mBeaconManager.startMonitoringBeaconsInRegion(region);
                        LogManager.d(TAG, "Monitoring region: "+region);
                        if (mBeaconManager.isBackgroundModeUninitialized()) {
                            mBeaconManager.setBackgroundMode(true);
                        }
                    }
                    if (mMonitoredRegions.size() == 0) {
                        LogManager.d(TAG, "No regions to monitor at start.");
                    }
                }
                synchronized (mRangedRegions) {
                    for (Region region : mRangedRegions) {
                        LogManager.d(TAG, "Ranging region: "+region);
                        mBeaconManager.startRangingBeaconsInRegion(region);
                    }
                    if (mRangedRegions.size() == 0) {
                        LogManager.d(TAG, "No regions to range at start.");
                    }
                }
            } catch (RemoteException e) {
                LogManager.e(e, TAG, "Can't set up bootstrap regions");
            }
        }

        /**
         * Method reserved for system use
         */
        @Override
        public boolean bindService(Intent intent, ServiceConnection conn, int arg2) {
            this.serviceIntent = intent;
            mContext.startService(intent);
            return mContext.bindService(intent, conn, arg2);

        }

        /**
         * Method reserved for system use
         */
        @Override
        public Context getApplicationContext() {
            return mContext;
        }

        /**
         * Method reserved for system use
         */
        @Override
        public void unbindService(ServiceConnection conn) {
            mContext.unbindService(conn);
            mContext.stopService(serviceIntent);
            mServiceConnected = false;
        }

        @Override
        public void didEnterRegion(Region region) {
            LogManager.d(TAG, "didEnterRegion");
            mLastRegionEntryOrExitTime = System.currentTimeMillis();
            synchronized (mMonitorNotifiers) {
                for (MonitorNotifier n : mMonitorNotifiers) {
                    n.didEnterRegion(region);
                }
            }
            evaluateBackgroundModeChanges();
        }

        @Override
        public void didExitRegion(Region region) {
            mLastRegionEntryOrExitTime = System.currentTimeMillis();
            synchronized (mMonitorNotifiers) {
                for (MonitorNotifier n : mMonitorNotifiers) {
                    n.didExitRegion(region);
                }
            }
            evaluateBackgroundModeChanges();
        }

        @Override
        public void didDetermineStateForRegion(int i, Region region) {
            synchronized (mMonitorNotifiers) {
                for (MonitorNotifier n : mMonitorNotifiers) {
                    n.didDetermineStateForRegion(i, region);
                }
            }
        }

        @Override
        public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
            LogManager.d(TAG, "didRangeBeacons count="+beacons.size());
            this.deliverRangingNotification(beacons);
            synchronized (mRangeNotifiers) {
                for (RangeNotifier n : mRangeNotifiers) {
                    n.didRangeBeaconsInRegion(beacons, region);
                }
            }
        }

        private void deliverRangingNotification(Collection<Beacon> beacons) {
            Intent intent = new Intent(RANGING_NOTIFICATION_NAME);
            intent.putExtra("beacons", new ArrayList<>(beacons));
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
        }
    }
}