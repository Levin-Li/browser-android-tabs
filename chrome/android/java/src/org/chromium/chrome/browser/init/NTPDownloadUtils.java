/* Copyright (c) 2020 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.init;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.json.JSONArray;

import org.chromium.base.Log;
import org.chromium.base.PathUtils;
import org.chromium.base.ContextUtils;
import org.chromium.chrome.browser.ntp.sponsored.SponsoredImage;
import org.chromium.chrome.browser.ntp.sponsored.SponsoredLogo;
import org.chromium.chrome.browser.ntp.sponsored.SponsoredImageUtil;
import org.chromium.chrome.browser.preferences.developer.QAPreferences;

public class NTPDownloadUtils {
    public static final String NTP_BASE_URL_DEBUG = "https://brave-ntp-crx-input-dev.s3-us-west-2.amazonaws.com";
    public static final String NTP_BASE_URL_PROD = "https://mobile-data.s3.brave.com";
    public static final String NTP_JSON_FILE_NAME = "photo.json";
    public static final String ETAG_PREPEND_NTP = "ntp";

    public static final int BUFFER_TO_READ = 16384;    // 16Kb

    private static final String ETAGS_PREFS_NAME = "EtagsPrefsFile";
    private static final String ETAG_NAME = "Etag";

    public static void saveETagInfo(Context context, String prepend, EtagObject etagObject) {
        SharedPreferences sharedPref = context.getSharedPreferences(ETAGS_PREFS_NAME, 0);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(prepend + ETAG_NAME, etagObject.mEtag);
        editor.apply();
    }

    public static EtagObject getETagInfo(Context context, String prepend) {
        SharedPreferences sharedPref = context.getSharedPreferences(ETAGS_PREFS_NAME, 0);
        EtagObject etagObject = new EtagObject();
        etagObject.mEtag = sharedPref.getString(prepend + ETAG_NAME, "");
        return etagObject;
    }

    private static String getNTPBaseUrl() {
        SharedPreferences mSharedPreferences = ContextUtils.getAppSharedPreferences();
        if (mSharedPreferences.getBoolean(QAPreferences.PREF_QA_DEBUG_NTP, false)) {
            return NTP_BASE_URL_DEBUG;
        } else {
            return NTP_BASE_URL_PROD;
        }
    }

    public static boolean downloadNTPData(Context context, String countryCode) {
        if (downloadFile(context,
            getNTPBaseUrl() + File.separator + countryCode + File.separator +NTP_JSON_FILE_NAME,
            countryCode,
            NTP_JSON_FILE_NAME, 
            true)) {
            parseJSONFile(context, countryCode);
            return true;
        }
        return false;
    }

    public static boolean downloadFile(Context context, String urlString, String countryCode, String fileName, boolean isJSONFile) {
        byte[] buffer = null;
        InputStream inputStream = null;
        HttpURLConnection connection = null;
        boolean res = false;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("Cookie", ""); 
            connection.setRequestProperty("Referer", ""); 
            connection.setDefaultUseCaches(false);
            connection.setUseCaches(false);
            int length = connection.getContentLength();

            if (isJSONFile) {
                EtagObject previousEtag = getETagInfo(context, ETAG_PREPEND_NTP);
                String etag = connection.getHeaderField("ETag");
                if (null == etag) {
                    etag = "";
                }
                boolean downloadFile = true;
                if (etag.equals(previousEtag.mEtag)) {
                    downloadFile = false;
                } else {
                    removePreviousFiles(context, countryCode);
                }

                previousEtag.mEtag = etag;
                saveETagInfo(context, ETAG_PREPEND_NTP, previousEtag);
                if (!downloadFile && new File(PathUtils.getDataDirectory(), countryCode + "_" + NTP_JSON_FILE_NAME).exists()) {
                    parseJSONFile(context, countryCode);
                    return false;
                }
            }

            connection.connect();

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                Log.e("NTP", " downloadFile response code : "+ connection.getResponseCode());
                if(isJSONFile) {
                    SponsoredImageUtil.clearSponsoredImages();
                }
                return false;
            }

            // Write to .tmp file and rename it to dat if success
            File tempFile = new File(PathUtils.getDataDirectory(), countryCode + "_" + fileName + ".tmp");
            FileOutputStream outputStream = new FileOutputStream(tempFile);
            inputStream = connection.getInputStream();
            buffer = new byte[BUFFER_TO_READ];
            int n = - 1;
            int totalReadSize = 0;
            try {
                while ((n = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, n);
                    totalReadSize += n;
                }
            }
            catch (IllegalStateException exc) {
                Log.e("NTP", "IllegalStateException " + exc.getMessage());
                if(isJSONFile) {
                    SponsoredImageUtil.clearSponsoredImages();
                }
                // Sometimes it gives us that exception, found that we should do that way to avoid it:
                // Each HttpURLConnection instance is used to make a single request but the
                // underlying network connection to the HTTP server may be transparently shared by other instance.
                // But we do that way, so just wrapped it for now and we will redownload the file on next request
            }
            outputStream.close();
            if (length != totalReadSize || length != tempFile.length()) {
                Log.e("NTP", "download failed");
                if(isJSONFile) {
                    SponsoredImageUtil.clearSponsoredImages();
                }
            } else {
              // We downloaded the file with success, rename it now to .json
              File renameTo = new File(PathUtils.getDataDirectory(), countryCode + "_" + fileName);
              if (!tempFile.exists() || !tempFile.renameTo(renameTo)) {
              }
              res = true;
            }
        }
        catch (MalformedURLException e) {
            e.printStackTrace();
            if(isJSONFile) {
                SponsoredImageUtil.clearSponsoredImages();
            }
        }
        catch (IOException e) {
            e.printStackTrace();
            if(isJSONFile) {
                SponsoredImageUtil.clearSponsoredImages();
            }
        }
        finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }

            if (connection != null)
                connection.disconnect();
        }
        return res;
    }

    public static void parseJSONFile (Context context, String countryCode) {
        List<SponsoredImage> sponsoredImages = new ArrayList<SponsoredImage>();
        try {
            File fl = new File(PathUtils.getDataDirectory(), countryCode + "_" + NTP_JSON_FILE_NAME);
            if(fl.exists()) {
                FileInputStream fin = new FileInputStream(fl);
                String jsonString = convertStreamToString(fin);
                if (jsonString == null) {
                    SponsoredImageUtil.clearSponsoredImages();
                    return;
                }
                //Make sure you close all streams.
                fin.close();

                JSONObject mainObj = new JSONObject(jsonString);
                JSONObject logoObject = mainObj.getJSONObject("logo");
                File logoFile = new File(PathUtils.getDataDirectory(), countryCode + "_" + logoObject.getString("imageUrl"));
                if(!logoFile.exists()) {
                    if(downloadFile(context,
                        getNTPBaseUrl() + File.separator + countryCode + File.separator + logoObject.getString("imageUrl"),
                        countryCode,
                        logoObject.getString("imageUrl"), 
                        false)) {
                        SponsoredLogo sponsoredLogo = new SponsoredLogo(logoObject.getString("imageUrl"),
                            logoObject.getString("alt"),
                            logoObject.getString("companyName"),
                            logoObject.getString("destinationUrl"));
                        SponsoredImageUtil.setSponsoredLogo(sponsoredLogo);
                    }
                } else {
                    SponsoredLogo sponsoredLogo = new SponsoredLogo(logoObject.getString("imageUrl"),
                        logoObject.getString("alt"),
                        logoObject.getString("companyName"),
                        logoObject.getString("destinationUrl"));
                    SponsoredImageUtil.setSponsoredLogo(sponsoredLogo);
                }

                JSONArray bgImageArray = mainObj.getJSONArray("wallpapers");
                for (int i=0; i < bgImageArray.length(); i++) {
                    JSONObject imageObj= bgImageArray.getJSONObject(i);

                    int focalPointX = !imageObj.isNull("focalPoint") ? imageObj.getJSONObject("focalPoint").getInt("x") : 0;
                    int focalPointY = !imageObj.isNull("focalPoint") ? imageObj.getJSONObject("focalPoint").getInt("y") : 0;

                    SponsoredImage sponsoredImage = new SponsoredImage(
                        imageObj.getString("imageUrl"), 
                        focalPointX, 
                        focalPointY);
                    sponsoredImages.add(sponsoredImage);
                }
                downloadImages(context, countryCode, sponsoredImages);
            }
        } catch (Exception e) {
            Log.e("NTP", e.getMessage());
            SponsoredImageUtil.clearSponsoredImages();
        }
    }

    private static String convertStreamToString(InputStream is) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
              sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            Log.e("NTP", e.getMessage());
            return null;
        }
    }

    private static void downloadImages(Context context, String countryCode, List<SponsoredImage> sponsoredImages) {

        SponsoredImageUtil.clearSponsoredImages();

        for(SponsoredImage sponsoredImage : sponsoredImages) {
            File imageFile = new File(PathUtils.getDataDirectory(), countryCode + "_" + sponsoredImage.getImageUrl());
            if(!imageFile.exists()) {
                if(downloadFile(context,
                    getNTPBaseUrl() + File.separator + countryCode + File.separator + sponsoredImage.getImageUrl(),
                    countryCode,
                    sponsoredImage.getImageUrl(), 
                    false)) {
                    SponsoredImageUtil.addSponsoredImage(sponsoredImage);
                }
            } else {
                SponsoredImageUtil.addSponsoredImage(sponsoredImage);
            }
        }
        SponsoredImageUtil.updateSponsoredImageIndex();
    }

    private static void removePreviousFiles(Context context, String countryCode) {
        File dataDirPath = new File(PathUtils.getDataDirectory());
        if (null == dataDirPath) {
            return;
        }
        File[] fileList = dataDirPath.listFiles();

        for (File file : fileList) {
            String filePath = file.getPath();
            String sFileName = filePath.substring(filePath.lastIndexOf(File.separator)+1);
            if (sFileName.startsWith(countryCode + "_")) {
                file.delete();
            }
        }
    }
}
