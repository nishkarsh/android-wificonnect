package com.intentfilter.wificonnect;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.support.annotation.NonNull;

import com.intentfilter.wificonnect.helpers.Logger;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.net.wifi.WifiManager.EXTRA_NETWORK_INFO;
import static android.net.wifi.WifiManager.NETWORK_STATE_CHANGED_ACTION;
import static android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION;
import static android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION;
import static android.net.wifi.WifiManager.WIFI_STATE_DISABLED;
import static android.net.wifi.WifiManager.WIFI_STATE_ENABLED;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static android.os.Build.VERSION_CODES.M;
import static java.lang.String.format;

class WifiUtil {
    private Context context;
    private Logger logger;
    private WifiStateChangeListener wifiStateListener;
    private NetworkStateChangeListener networkStateListener;
    private ScanResultsListener scanResultsListener;
    private BroadcastReceiver wifiStateReceiver;
    private BroadcastReceiver networkStateReceiver;
    private BroadcastReceiver scanResultsReceiver;
    private final ConnectivityManager manager;
    private NetworkCallback networkCallback;

    WifiUtil(Context context) {
        this.context = context;
        this.logger = Logger.loggerFor(WifiUtil.class);
        this.manager = (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);
    }

    @TargetApi(LOLLIPOP)
    void bindToNetwork(final String networkSSID, final NetworkStateChangeListener listener) {
        if (SDK_INT < LOLLIPOP) {
            logger.i("SDK version is below Lollipop. No need to bind process to network. Skipping...");
            return;
        }
        logger.i("Currently active network is not " + networkSSID + ", would bind the app to use this when available");

        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build();
        networkCallback = networkCallback(networkSSID, listener);
        manager.registerNetworkCallback(request, networkCallback);
    }

    void setWifiStateChangeListener(@NonNull WifiStateChangeListener listener) {
        this.wifiStateListener = listener;
        wifiStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int wifiState = intent.getExtras().getInt(WifiManager.EXTRA_WIFI_STATE);
                informWifiStateChanged(wifiState, isInitialStickyBroadcast());
            }
        };
        context.registerReceiver(wifiStateReceiver, new IntentFilter(WIFI_STATE_CHANGED_ACTION));
        logger.d("Registered for WiFi State broadcast");
    }

    void setNetworkStateChangeListener(@NonNull NetworkStateChangeListener listener) {
        this.networkStateListener = listener;
        networkStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                NetworkInfo networkInfo = intent.getParcelableExtra(EXTRA_NETWORK_INFO);
                informNetworkStateChanged(networkInfo);
            }
        };
        context.registerReceiver(networkStateReceiver, new IntentFilter(NETWORK_STATE_CHANGED_ACTION));
        logger.d("Registered for Network State broadcast");
    }

    void setWifiScanResultsListener(final ScanResultsListener scanResultsListener) {
        this.scanResultsListener = scanResultsListener;
        scanResultsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                scanResultsListener.onScanResultsAvailable(wifiManager.getScanResults());
            }
        };
        context.registerReceiver(scanResultsReceiver, new IntentFilter(SCAN_RESULTS_AVAILABLE_ACTION));
        logger.d("Registered for WiFi Scan results broadcast");
    }

    void removeWifiStateChangeListener(@NonNull WifiStateChangeListener listener) {
        if (this.wifiStateListener != null && this.wifiStateListener.equals(listener)) {
            this.wifiStateListener = null;
            context.unregisterReceiver(wifiStateReceiver);
            logger.d("Un-registered for WiFi State broadcast");
        }
    }

    void removeNetworkStateChangeListener(@NonNull NetworkStateChangeListener listener) {
        if (this.networkStateListener != null && this.networkStateListener.equals(listener)) {
            this.networkStateListener = null;
            context.unregisterReceiver(networkStateReceiver);
            logger.d("Un-registered for Network State broadcast");
        }
    }

    void removeWifiScanResultsListener(@NonNull ScanResultsListener listener) {
        if (this.scanResultsListener != null && this.scanResultsListener.equals(listener)) {
            this.scanResultsListener = null;
            context.unregisterReceiver(scanResultsReceiver);
            logger.d("Un-registered for WiFi Scan results broadcast");
        }
    }

    @TargetApi(LOLLIPOP)
    NetworkCallback networkCallback(final String networkSSID,
                                    final NetworkStateChangeListener listener) {
        return new NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                NetworkInfo networkInfo = manager.getNetworkInfo(network);
                logger.i("Network is Available. Network Info: " + networkInfo);

                if (WifiHelper.areEqual(networkInfo.getExtraInfo(), networkSSID)) {
                    manager.unregisterNetworkCallback(this);
                    networkCallback = null;
                    bindToRequiredNetwork(network);
                    logger.i(format("Bound application to use %s network", networkSSID));
                    listener.onNetworkBound();
                }
            }
        };
    }

    @TargetApi(LOLLIPOP)
    void bindToRequiredNetwork(Network network) {
        if (SDK_INT >= M) {
            manager.bindProcessToNetwork(network);
        } else {
            ConnectivityManager.setProcessDefaultNetwork(network);
        }
    }

    @TargetApi(LOLLIPOP)
    void clearNetworkBinding() {
        if (networkCallback != null) {
            manager.unregisterNetworkCallback(networkCallback);
            logger.d("Un-registering for network available callback");
        }

        if (SDK_INT < LOLLIPOP || !isBoundToNetwork()) {
            logger.d("Not bound to any network, would not attempt to clear binding");
            return;
        }

        bindToRequiredNetwork(null);
        logger.d("Cleared network binding. Preference to network would now be given by OS");
    }

    @TargetApi(LOLLIPOP)
    boolean isBoundToNetwork() {
        return getBoundNetworkForProcess() != null;
    }

    @TargetApi(LOLLIPOP)
    Network getBoundNetworkForProcess() {
        if (SDK_INT >= M) {
            return manager.getBoundNetworkForProcess();
        } else {
            return ConnectivityManager.getProcessDefaultNetwork();
        }
    }

    @TargetApi(LOLLIPOP)
    void reportBoundNetworkConnectivity() {
        if (SDK_INT < LOLLIPOP) {
            return;
        }

        if (SDK_INT >= M) {
            Network defaultNetwork = manager.getBoundNetworkForProcess();
            manager.reportNetworkConnectivity(defaultNetwork, true);
        } else {
            Network defaultNetwork = ConnectivityManager.getProcessDefaultNetwork();
            manager.reportBadNetwork(defaultNetwork);
        }
    }

    private void informNetworkStateChanged(NetworkInfo networkInfo) {
        if (networkStateListener == null) {
            logger.e("Listener for NetworkStateChange is null, did you forget calling removeNetworkStateChangeListener()?");
            return;
        }

        if (networkInfo.isConnected()) {
            networkStateListener.onNetworkConnected();
        }
    }

    private void informWifiStateChanged(int wifiState, boolean initialStickyBroadcast) {
        if (wifiStateListener == null) {
            logger.e("Listener for WifiStateChange is null, did you forget calling removeWifiStateChangeListener()?");
            return;
        }

        if (wifiState == WIFI_STATE_ENABLED) {
            wifiStateListener.onWifiEnabled(initialStickyBroadcast);
        } else if (wifiState == WIFI_STATE_DISABLED) {
            wifiStateListener.onWifiDisabled(initialStickyBroadcast);
        }
    }

    public boolean isActiveNetworkWifi() {
        NetworkInfo activeNetworkInfo = manager.getActiveNetworkInfo();
        return activeNetworkInfo != null &&
                activeNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI;
    }

    interface WifiStateChangeListener {
        void onWifiEnabled(boolean initialStickyBroadcast);

        void onWifiDisabled(boolean initialStickyBroadcast);
    }

    interface NetworkStateChangeListener {
        void onNetworkConnected();

        void onNetworkBound();
    }
}