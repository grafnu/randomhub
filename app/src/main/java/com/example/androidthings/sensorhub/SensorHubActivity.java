/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.androidthings.sensorhub;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import com.example.androidthings.sensorhub.collector.RandomNumberCollector;
import com.example.androidthings.sensorhub.iotcore.SensorHub;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class SensorHubActivity extends Activity {

    private static final String TAG = SensorHubActivity.class.getSimpleName();

    private static final String CONFIG_SHARED_PREFERENCES_KEY = "cloud_iot_config";

    private final AtomicBoolean provisionCheckActive = new AtomicBoolean();
    private final AtomicBoolean provisionDataDirty = new AtomicBoolean();
    private final AtomicReference<String> savedParamKey = new AtomicReference<>();

    private SensorHub sensorHub;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent");
        // getIntent() should always return the most recent
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        spawnProvisioningThread();
        SharedPreferences prefs = getSharedPreferences(CONFIG_SHARED_PREFERENCES_KEY, MODE_PRIVATE);
        Parameters params = readParameters(prefs, getIntent().getExtras());
        if (params != null) {
            savedParamKey.set(params.toString());
            initializeHub(params);
        }
    }

    private void initializeHub(Parameters params) {
        if (sensorHub != null) {
            sensorHub.stop();
        }

        Log.i(TAG, "Initialization parameters:\n" +
                "   Project ID: " + params.getProjectId() + "\n" +
                "    Region ID: " + params.getCloudRegion() + "\n" +
                "  Registry ID: " + params.getRegistryId() + "\n" +
                "    Device ID: " + params.getDeviceId() + "\n" +
                "Key algorithm: " + params.getKeyAlgorithm());

        sensorHub = new SensorHub(params);
        sensorHub.registerSensorCollector(new RandomNumberCollector());
//        sensorHub.registerSensorCollector(new Bmx280Collector(
//                BoardDefaults.getI2cBusForSensors()));
//        sensorHub.registerSensorCollector(new MotionCollector(
//                BoardDefaults.getGPIOForMotionDetector()));

        try {
            sensorHub.start();
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Cannot load keypair", e);
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        if (sensorHub != null) {
            sensorHub.stop();
        }
    }

    private Parameters readParameters(SharedPreferences prefs, Bundle extras) {
        Parameters params = Parameters.from(prefs, extras);
        if (params == null) {
            String validAlgorithms = String.join(",",
                    AuthKeyGenerator.SUPPORTED_KEY_ALGORITHMS);
            Log.w(TAG, "Postponing initialization until enough parameters are set. " +
                    "Please configure via intent, for example: \n" +
                    "adb shell am start " +
                    "-e project_id <PROJECT_ID> -e cloud_region <REGION> " +
                    "-e registry_id <REGISTRY_ID> -e device_id <DEVICE_ID> " +
                    "[-e key_algorithm <one of " + validAlgorithms + ">] " +
                    getPackageName() + "/." +
                    getLocalClassName() + "\n");
        }
        return params;
    }

    private void spawnProvisioningThread() {
        if (!provisionCheckActive.getAndSet(true)) {
            Thread backgroundThread = new Thread(new ProvisioningThread());
            backgroundThread.start();
        }
    }

    // Thread for handling blocking network operations.
    private class ProvisioningThread implements Runnable {
        @Override
        public void run() {
            try {
                Parameters params = fetchProvisioningConfig();
                if (params == null) {
                    return;
                }
                String paramKey = params.toString();
                if (!paramKey.equals(savedParamKey.getAndSet(paramKey))) {
                    SharedPreferences prefs = getSharedPreferences(CONFIG_SHARED_PREFERENCES_KEY, MODE_PRIVATE);
                    params.saveToPreferences(prefs);
                    initializeHub(params);
                    Log.i(TAG, "Provisioning information updated");
                } else {
                    Log.i(TAG, "Provisioning information unchanged");
                }
            } finally {
                provisionCheckActive.set(false);
            }
        }
    }

    private Parameters fetchProvisioningConfig() {
        try {
            URL url = new URL(findConnectionUrl());
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            InputStream inputStream = urlConnection.getInputStream();
            String text = IOUtils.toString(inputStream, StandardCharsets.UTF_8.name());
            urlConnection.disconnect();
            return Parameters.parse(text);
        } catch (Exception e) {
            Log.e(TAG, "Could not connect to provisioning server", e);
            return null;
        }
    }

    private String findConnectionUrl() {
        //return "http://api.myjson.com/bins/13mqyq"; // llama_1
        return "http://api.myjson.com/bins/dhtia"; // llama_2
    }
}
