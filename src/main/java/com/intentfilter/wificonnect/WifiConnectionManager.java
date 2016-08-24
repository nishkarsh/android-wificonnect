package com.intentfilter.wificonnect;

import android.content.Context;
import android.net.Network;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;

import com.intentfilter.wificonnect.WifiUtil.NetworkStateChangeListener;
import com.intentfilter.wificonnect.WifiUtil.WifiStateChangeListener;
import com.intentfilter.wificonnect.helpers.Logger;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.String.format;

public class WifiConnectionManager
        implements NetworkStateChangeListener, WifiStateChangeListener, ScanResultsListener {

    private static boolean shouldBindToNetwork;
    private final WifiUtil wifiUtil;
    private WifiHelper wifiHelper;
    private AdvancedConnectionStateListener advancedStateListener;
    private List<String> SSIDs;
    private ConnectionStateChangedListener connectionStateListener;
    private Logger LOGGER = Logger.loggerFor(WifiManager.class);
    private Lock reentrantLock;
    private String availableSSID;

    public WifiConnectionManager(Context context) {
        this(new WifiUtil(context.getApplicationContext()),
                new WifiHelper(context.getApplicationContext()), new ReentrantLock());
    }

    WifiConnectionManager(WifiUtil wifiUtil, WifiHelper wifiHelper, ReentrantLock reentrantLock) {
        this.wifiUtil = wifiUtil;
        this.wifiHelper = wifiHelper;
        this.reentrantLock = reentrantLock;
    }

    public static void setBindingEnabled(boolean shouldBindToNetwork) {
        WifiConnectionManager.shouldBindToNetwork = shouldBindToNetwork;
    }

    public void connectToAvailableSSID(String SSID, ConnectionStateChangedListener connectionStateListener) {
        connectToAvailableSSID(Collections.singletonList(SSID), connectionStateListener);
    }

    public void connectToAvailableSSID(List<String> SSIDs, ConnectionStateChangedListener connectionStateListener) {
        this.SSIDs = SSIDs;
        this.connectionStateListener = connectionStateListener;

        wifiUtil.setWifiStateChangeListener(this);
    }

    @Override
    public void onScanResultsAvailable(List<ScanResult> scanResults) {
        onConnectionStateChanged(AdvancedConnectionState.SCAN_RESULTS_AVAILABLE);

        availableSSID = wifiHelper.findAvailableSSID(SSIDs, scanResults);
        if (reentrantLock.tryLock()) {
            wifiUtil.removeWifiScanResultsListener(this);

            if (availableSSID != null) {
                onSSIDAvailable(availableSSID);
            } else {
                String reason = "Couldn't find SSID in which we are interested";
                connectionStateListener.onConnectionError(reason);
                LOGGER.i(reason);
            }
        }
    }

    @Override
    public void onWifiEnabled() {
        wifiUtil.removeWifiStateChangeListener(this);
        onConnectionStateChanged(AdvancedConnectionState.WIFI_ENABLED);

        LOGGER.d("Wifi is enabled, starting scan");
        wifiUtil.setWifiScanResultsListener(this);
        scheduleWifiScan();
    }

    @Override
    public void onWifiDisabled() {
        onConnectionStateChanged(AdvancedConnectionState.WIFI_DISABLED);
        wifiHelper.enableWifi();
    }

    @Override
    public void onNetworkConnected() {
        onConnectionStateChanged(AdvancedConnectionState.NETWORK_CONNECTED);
        if (isConnectedToSSID(availableSSID)) {
            connectionStateListener.onConnectionEstablished();
        }
        wifiUtil.removeNetworkStateChangeListener(this);
    }

    @Override
    public void onNetworkBound() {
        onConnectionStateChanged(AdvancedConnectionState.NETWORK_BOUND);
        connectionStateListener.onConnectionEstablished();
        wifiUtil.removeNetworkStateChangeListener(this);
    }

    private void onSSIDAvailable(String availableSSID) {
        LOGGER.i(format("SSID %s is available.", availableSSID));

        if (isConnectedToSSID(availableSSID)) {
            LOGGER.d(format("Current active SSID is already %s.", availableSSID));
            connectionStateListener.onConnectionEstablished();
            return;
        }

        if (isVersionEqualsOrAboveLollipop() && shouldBindToNetwork) {
            wifiUtil.bindToNetwork(availableSSID, this);
        }

        wifiUtil.setNetworkStateChangeListener(this);
        boolean connectingToSSID = wifiHelper.connectToSSID(availableSSID);
        if (!connectingToSSID) {
            String reason = "Error while enabling network.";
            connectionStateListener.onConnectionError(reason);
            LOGGER.d(reason);
        }
    }

    public Network getBoundNetworkForProcess() {
        return wifiUtil.getBoundNetworkForProcess();
    }

    public void checkBoundNetworkConnectivity() {
        wifiUtil.reportBoundNetworkConnectivity();
    }

    public boolean isConnectedToSSID(String availableSSID) {
        return wifiUtil.isActiveNetworkWifi() && wifiHelper.hasActiveSSID(availableSSID);
    }

    private void scheduleWifiScan() {
        //TODO Find out why broadcast for scan results not received when scanning is started just after WiFi is enabled on Sony & One Plus.
        // This happens when SSID is already saved
        final int TWO_SECONDS = 2000;
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                wifiHelper.startScan();
            }
        }, TWO_SECONDS);
    }

    private void onConnectionStateChanged(AdvancedConnectionState currentConnectionState) {
        if (advancedStateListener != null) {
            advancedStateListener.onConnectionStateChanged(currentConnectionState);
        }
    }

    public void setAdvancedConnectionStateListener(AdvancedConnectionStateListener advancedStateListener) {
        this.advancedStateListener = advancedStateListener;
    }

    private boolean isVersionEqualsOrAboveLollipop() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    public void scanForNetworks(ScanResultsListener listener) {
        wifiUtil.setWifiScanResultsListener(listener);
        wifiHelper.startScan();
    }

    public interface ConnectionStateChangedListener {
        void onConnectionEstablished();

        void onConnectionError(String reason);
    }

    public interface AdvancedConnectionStateListener {
        void onConnectionStateChanged(AdvancedConnectionState connectionState);
    }
}