package com.intentfilter.wificonnect;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.text.TextUtils;

import com.intentfilter.wificonnect.helpers.Logger;
import com.intentfilter.wificonnect.helpers.StringUtil;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static java.lang.String.format;

class WifiHelper {
    private final WifiManager wifiManager;
    private final Logger logger;

    WifiHelper(Context context) {
        this.wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        this.logger = Logger.loggerFor(WifiHelper.class);
    }

    boolean connectToSSID(String SSID) {
        WifiConfiguration configuration = createOpenWifiConfiguration(SSID);
        logger.d("Priority assigned to configuration is " + configuration.priority);

        int networkId = wifiManager.addNetwork(configuration);
        logger.d("networkId assigned while adding network is " + networkId);

        return enableNetwork(SSID, networkId);
    }

    String findAvailableSSID(List<String> SSIDs, List<ScanResult> scanResults) {
        logger.i("Available SSIDs count: " + scanResults.size());

        sortBySignalStrength(scanResults);

        for (ScanResult scanResult : scanResults) {
            if (SSIDs.contains(scanResult.SSID)) {
                return scanResult.SSID;
            }
        }
        return null;
    }

    public boolean hasActiveSSID(String SSID) {
        String currentSSID = wifiManager.getConnectionInfo().getSSID();
        return areEqual(SSID, currentSSID);
    }

    public void enableWifi() {
        wifiManager.setWifiEnabled(true);
    }

    public static boolean areEqual(String SSID, String anotherSSID) {
        return TextUtils.equals(StringUtil.trimQuotes(SSID), StringUtil.trimQuotes(anotherSSID));
    }

    public static String formatSSID(String wifiSSID) {
        return format("\"%s\"", wifiSSID);
    }

    public void startScan() {
        wifiManager.startScan();
    }

    public void disconnect() {
        wifiManager.disconnect();
    }

    private boolean enableNetwork(String SSID, int networkId) {
        if (networkId == -1) {
            networkId = getExistingNetworkId(SSID);
            logger.d("networkId of existing network is " + networkId);

            if (networkId == -1) {
                logger.e("Couldn't add network with SSID: " + SSID);
                return false;
            }
        }

        return wifiManager.enableNetwork(networkId, true);
    }

    private WifiConfiguration createOpenWifiConfiguration(String SSID) {
        WifiConfiguration configuration = new WifiConfiguration();
        configuration.SSID = formatSSID(SSID);
        configuration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        assignHighestPriority(configuration);
        return configuration;
    }

    private void sortBySignalStrength(List<ScanResult> scanResults) {
        Collections.sort(scanResults, new Comparator<ScanResult>() {
            @Override
            public int compare(ScanResult resultOne, ScanResult resultTwo) {
                return resultTwo.level - resultOne.level;
            }
        });
    }

    private int getExistingNetworkId(String SSID) {
        List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
        if (configuredNetworks != null) {
            for (WifiConfiguration existingConfig : configuredNetworks) {
                if (areEqual(StringUtil.trimQuotes(existingConfig.SSID), StringUtil.trimQuotes(SSID))) {
                    return existingConfig.networkId;
                }
            }
        }
        return -1;
    }

    private void assignHighestPriority(WifiConfiguration config) {
        List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
        if (configuredNetworks != null) {
            for (WifiConfiguration existingConfig : configuredNetworks) {
                if (config.priority <= existingConfig.priority) {
                    config.priority = existingConfig.priority + 1;
                }
            }
        }
    }
}