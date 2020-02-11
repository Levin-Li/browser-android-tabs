// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.tile;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.TextView;
import android.content.SharedPreferences;
import android.os.Build;
import android.app.Activity;

import org.chromium.base.ContextUtils;
import org.chromium.chrome.browser.preferences.BackgroundImagesPreferences;
import org.chromium.chrome.browser.ntp.sponsored.SponsoredImageUtil;
import org.chromium.chrome.browser.BraveRewardsHelper;
import org.chromium.chrome.browser.ChromeTabbedActivity;
import org.chromium.base.ApplicationStatus;
import org.chromium.chrome.browser.tabmodel.TabModel;

import org.chromium.chrome.R;

/**
 * The view for a tile with icon and text.
 *
 * Displays the title of the site beneath a large icon.
 */
public class TileWithTextView extends TileView {
    private TextView mTitleView;

    /**
     * Constructor for inflating from XML.
     */
    public TileWithTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mTitleView = findViewById(R.id.tile_view_title);
    }

    /**
     * Initializes the view. This should be called immediately after inflation.
     *
     * @param title The title of the tile.
     * @param showOfflineBadge Whether to show the offline badge.
     * @param icon The icon to display on the tile.
     * @param titleLines The number of text lines to use for the tile title.
     */
    public void initialize(String title, boolean showOfflineBadge, Drawable icon, int titleLines) {
        super.initialize(showOfflineBadge, icon);
        setTitle(title, titleLines);
    }

    /** Sets the title text and number lines. */
    public void setTitle(String title, int titleLines) {
        mTitleView.setLines(titleLines);
        mTitleView.setText(title);

        SharedPreferences mSharedPreferences = ContextUtils.getAppSharedPreferences();

        boolean isMoreTabs = false;
        ChromeTabbedActivity chromeTabbedActivity = BraveRewardsHelper.getChromeTabbedActivity();
        if(chromeTabbedActivity != null) {
            TabModel tabModel = chromeTabbedActivity.getCurrentTabModel();
            isMoreTabs = tabModel.getCount() >= SponsoredImageUtil.MAX_TABS ? true : false;
        }

        if(mSharedPreferences.getBoolean(BackgroundImagesPreferences.PREF_SHOW_BACKGROUND_IMAGES, true) 
            && (Build.VERSION.SDK_INT > Build.VERSION_CODES.M || (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !isMoreTabs))) {
            mTitleView.setTextColor(getResources().getColor(android.R.color.white));
        }
    }
}
