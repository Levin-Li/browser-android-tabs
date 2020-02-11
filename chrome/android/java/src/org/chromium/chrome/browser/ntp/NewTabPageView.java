// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.os.Bundle;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.AdapterDataObserver;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.View;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.Button;
import android.view.ViewTreeObserver;
import java.util.Calendar;
import android.widget.Toast;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Color;
import android.app.Activity;
import android.os.Build;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.text.Spannable;
import android.text.SpannableStringBuilder;

import androidx.annotation.VisibleForTesting;

import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.TextPaint;
import android.content.Intent;
import android.support.annotation.NonNull;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import android.net.Uri;
import android.os.Handler;

import org.chromium.base.Log;
import org.chromium.base.PathUtils;
import org.chromium.base.TraceEvent;
import org.chromium.base.ContextUtils;
import org.chromium.base.ApplicationStatus;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeTabbedActivity;
import org.chromium.chrome.browser.compositor.layouts.OverviewModeBehavior;
import org.chromium.chrome.browser.compositor.layouts.content.InvalidationAwareThumbnailProvider;
import org.chromium.chrome.browser.gesturenav.HistoryNavigationDelegateFactory;
import org.chromium.chrome.browser.gesturenav.HistoryNavigationLayout;
import org.chromium.chrome.browser.native_page.ContextMenuManager;
import org.chromium.chrome.browser.ntp.cards.NewTabPageAdapter;
import org.chromium.chrome.browser.ntp.cards.NewTabPageRecyclerView;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.suggestions.SuggestionsDependencyFactory;
import org.chromium.chrome.browser.suggestions.SuggestionsUiDelegate;
import org.chromium.chrome.browser.suggestions.tile.TileGroup;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabImpl;
import org.chromium.chrome.browser.tabmodel.TabLaunchType;
import org.chromium.chrome.browser.ui.widget.displaystyle.UiConfig;
import org.chromium.chrome.browser.ui.widget.displaystyle.ViewResizer;
import org.chromium.chrome.browser.util.ViewUtils;
import org.chromium.chrome.browser.preferences.BackgroundImagesPreferences;
import org.chromium.chrome.browser.ntp.sponsored.NTPImage;
import org.chromium.chrome.browser.ntp.sponsored.BackgroundImage;
import org.chromium.chrome.browser.ntp.sponsored.SponsoredImage;
import org.chromium.chrome.browser.ntp.sponsored.NewTabListener;
import org.chromium.chrome.browser.ntp.sponsored.SponsoredImageUtil;
import org.chromium.chrome.browser.util.LocaleUtil;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.chrome.browser.ntp.sponsored.RewardsBottomSheetDialogFragment;
import org.chromium.chrome.browser.BraveAdsNativeHelper;
import org.chromium.chrome.browser.BraveRewardsPanelPopup;
import org.chromium.chrome.browser.BraveRewardsHelper;
import org.chromium.chrome.browser.BraveRewardsObserver;
import org.chromium.chrome.browser.BraveRewardsNativeWorker;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.chrome.browser.tabmodel.TabSelectionType;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.chrome.browser.download.DownloadUtils;
import org.chromium.ui.base.DeviceFormFactor;
import org.chromium.chrome.browser.tabmodel.TabModel;

import static org.chromium.chrome.browser.util.ViewUtils.dpToPx;

/**
 * The native new tab page, represented by some basic data such as title and url, and an Android
 * View that displays the page.
 */
public class NewTabPageView extends HistoryNavigationLayout {
    private static final String TAG = "NewTabPageView";

    /*
    * For Brave stats
    */
    private static final String PREF_TRACKERS_BLOCKED_COUNT = "trackers_blocked_count";
    private static final String PREF_ADS_BLOCKED_COUNT = "ads_blocked_count";
    private static final String PREF_HTTPS_UPGRADES_COUNT = "https_upgrades_count";
    private static final short MILLISECONDS_PER_ITEM = 50;
    private static final int BOTTOM_TOOLBAR_HEIGHT = 56;

    private NewTabPageRecyclerView mRecyclerView;

    private NewTabPageLayout mNewTabPageLayout;
    private ViewGroup mBraveStatsView;

    private NewTabPageManager mManager;
    private TabImpl mTab;
    private SnapScrollHelper mSnapScrollHelper;
    private UiConfig mUiConfig;
    private ViewResizer mRecyclerViewResizer;

    private boolean mNewTabPageRecyclerViewChanged;
    private int mSnapshotWidth;
    private int mSnapshotHeight;
    private int mSnapshotScrollY;
    private ContextMenuManager mContextMenuManager;
    private SharedPreferences mSharedPreferences;
    private ViewGroup nonDistruptiveBannerLayout;
    private BitmapDrawable imageDrawable;

    private boolean isFromBottomSheet;

    /**
     * Manages the view interaction with the rest of the system.
     */
    public interface NewTabPageManager extends SuggestionsUiDelegate {
        /** @return Whether the location bar is shown in the NTP. */
        boolean isLocationBarShownInNTP();

        /** @return Whether voice search is enabled and the microphone should be shown. */
        boolean isVoiceSearchEnabled();

        /**
         * Animates the search box up into the omnibox and bring up the keyboard.
         * @param beginVoiceSearch Whether to begin a voice search.
         * @param pastedText Text to paste in the omnibox after it's been focused. May be null.
         */
        void focusSearchBox(boolean beginVoiceSearch, String pastedText);

        /**
         * @return whether the {@link NewTabPage} associated with this manager is the current page
         * displayed to the user.
         */
        boolean isCurrentPage();

        /**
         * Called when the NTP has completely finished loading (all views will be inflated
         * and any dependent resources will have been loaded).
         */
        void onLoadingComplete();
    }

    /**
     * Default constructor required for XML inflation.
     */
    public NewTabPageView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mRecyclerView = new NewTabPageRecyclerView(getContext());

        // Don't attach now, the recyclerView itself will determine when to do it.
        mNewTabPageLayout = (NewTabPageLayout) LayoutInflater.from(getContext())
                                    .inflate(R.layout.new_tab_page_layout, mRecyclerView, false);
        mSharedPreferences = ContextUtils.getAppSharedPreferences();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M || (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !mTab.isMoreTabs())) {
            NTPImage ntpImage = mTab.getTabNTPImage();
            if(ntpImage == null) {
                mTab.setNTPImage(SponsoredImageUtil.getBackgroundImage());
            } else if (ntpImage instanceof SponsoredImage) {
                String countryCode = LocaleUtil.getCountryCode();
                SponsoredImage sponsoredImage = (SponsoredImage) ntpImage;
                File imageFile = new File(PathUtils.getDataDirectory(), countryCode + "_" + sponsoredImage.getImageUrl());
                if(!imageFile.exists()) {
                    mTab.setNTPImage(SponsoredImageUtil.getBackgroundImage());
                }
            }
            checkForNonDistruptiveBanner(ntpImage);
            super.onConfigurationChanged(newConfig);
            showNTPImage(ntpImage);
        } else {
            super.onConfigurationChanged(newConfig);
        }
    }

    /**
     * Initializes the NTP. This must be called immediately after inflation, before this object is
     * used in any other way.
     *
     * @param manager NewTabPageManager used to perform various actions when the user interacts
     *                with the page.
     * @param tab The Tab that is showing this new tab page.
     * @param searchProviderHasLogo Whether the search provider has a logo.
     * @param searchProviderIsGoogle Whether the search provider is Google.
     * @param scrollPosition The adapter scroll position to initialize to.
     * @param constructedTimeNs The timestamp at which the new tab page's construction started.
     */
    public void initialize(NewTabPageManager manager, Tab tab, TileGroup.Delegate tileGroupDelegate,
            boolean searchProviderHasLogo, boolean searchProviderIsGoogle, int scrollPosition,
            long constructedTimeNs) {
        TraceEvent.begin(TAG + ".initialize()");
        mTab = (TabImpl)tab;
        mManager = manager;
        mUiConfig = new UiConfig(this);

        assert manager.getSuggestionsSource() != null;

        // Don't store a direct reference to the activity, because it might change later if the tab
        // is reparented.
        Runnable closeContextMenuCallback = () -> ((TabImpl) mTab).getActivity().closeContextMenu();
        mContextMenuManager = new ContextMenuManager(mManager.getNavigationDelegate(),
                mRecyclerView::setTouchEnabled, closeContextMenuCallback,
                NewTabPage.CONTEXT_MENU_USER_ACTION_PREFIX);
        mTab.getWindowAndroid().addContextMenuCloseListener(mContextMenuManager);
        setNavigationDelegate(HistoryNavigationDelegateFactory.create(mTab));

        OverviewModeBehavior overviewModeBehavior =
                ((TabImpl) tab).getActivity() instanceof ChromeTabbedActivity
                ? ((TabImpl) tab).getActivity().getOverviewModeBehavior()
                : null;

        mNewTabPageLayout.initialize(manager, tab, ((TabImpl) tab).getActivity(), overviewModeBehavior,
                tileGroupDelegate, searchProviderHasLogo, searchProviderIsGoogle, mRecyclerView,
                mContextMenuManager, mUiConfig);

        NewTabPageUma.trackTimeToFirstDraw(this, constructedTimeNs);

        mSnapScrollHelper = new SnapScrollHelper(mManager, mNewTabPageLayout);
        mSnapScrollHelper.setView(mRecyclerView);
        mRecyclerView.setSnapScrollHelper(mSnapScrollHelper);
        addView(mRecyclerView);

        mRecyclerView.setItemAnimator(new DefaultItemAnimator() {
            @Override
            public boolean animateMove(ViewHolder holder, int fromX, int fromY, int toX, int toY) {
                // If |mNewTabPageLayout| is animated by the RecyclerView because an item below it
                // was dismissed, avoid also manipulating its vertical offset in our scroll handling
                // at the same time. The onScrolled() method is called when an item is dismissed and
                // the item at the top of the viewport is repositioned.
                if (holder.itemView == mNewTabPageLayout) mNewTabPageLayout.setIsViewMoving(true);

                // Cancel any pending scroll update handling, a new one will be scheduled in
                // onAnimationFinished().
                mSnapScrollHelper.resetSearchBoxOnScroll(false);

                return super.animateMove(holder, fromX, fromY, toX, toY);
            }

            @Override
            public void onAnimationFinished(ViewHolder viewHolder) {
                super.onAnimationFinished(viewHolder);

                // When an item is dismissed, the items at the top of the viewport might not move,
                // and onScrolled() might not be called. We can get in the situation where the
                // toolbar buttons disappear, so schedule an update for it. This can be cancelled
                // from animateMove() in case |mNewTabPageLayout| will be moved. We don't know that
                // from here, as the RecyclerView will animate multiple items when one is dismissed,
                // and some will "finish" synchronously if they are already in the correct place,
                // before other moves have even been scheduled.
                if (viewHolder.itemView == mNewTabPageLayout) {
                    mNewTabPageLayout.setIsViewMoving(false);
                }
                mSnapScrollHelper.resetSearchBoxOnScroll(true);
            }
        });

        Profile profile = Profile.getLastUsedProfile();
        OfflinePageBridge offlinePageBridge =
                SuggestionsDependencyFactory.getInstance().getOfflinePageBridge(profile);

        mBraveStatsView = (ViewGroup)mNewTabPageLayout.findViewById(R.id.brave_stats);

        initializeLayoutChangeListener();
        mNewTabPageLayout.setSearchProviderInfo(searchProviderHasLogo, searchProviderIsGoogle);

        mRecyclerView.init(mUiConfig, closeContextMenuCallback);

        // Set up snippets
        NewTabPageAdapter newTabPageAdapter = new NewTabPageAdapter(
                mManager, mNewTabPageLayout, mUiConfig, offlinePageBridge, mContextMenuManager);
        newTabPageAdapter.refreshSuggestions();
        mRecyclerView.setAdapter(newTabPageAdapter);
        mRecyclerView.getLinearLayoutManager().scrollToPosition(scrollPosition);

        // mRecyclerViewResizer = ViewResizer.createAndAttach(mRecyclerView, mUiConfig,
        //         mRecyclerView.getResources().getDimensionPixelSize(
        //                 R.dimen.content_suggestions_card_modern_margin),
        //         mRecyclerView.getResources().getDimensionPixelSize(
        //                 R.dimen.ntp_wide_card_lateral_margins));

        setupScrollHandling();

        // When the NewTabPageAdapter's data changes we need to invalidate any previous
        // screen captures of the NewTabPageView.
        newTabPageAdapter.registerAdapterDataObserver(new AdapterDataObserver() {
            @Override
            public void onChanged() {
                mNewTabPageRecyclerViewChanged = true;
            }

            @Override
            public void onItemRangeChanged(int positionStart, int itemCount) {
                onChanged();
            }

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                onChanged();
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                onChanged();
            }

            @Override
            public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                onChanged();
            }
        });

        manager.addDestructionObserver(NewTabPageView.this::onDestroy);

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            checkAndShowNTPImage();
        } else if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.M && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !mTab.isMoreTabs()) {
            mTab.addObserver(mTabObserver);
        }

        TraceEvent.end(TAG + ".initialize()");
    }

    /**
     * @return The {@link NewTabPageLayout} displayed in this NewTabPageView.
     */
    NewTabPageLayout getNewTabPageLayout() {
        return mNewTabPageLayout;
    }

    /**
     * Sets the {@link FakeboxDelegate} associated with the new tab page.
     * @param fakeboxDelegate The {@link FakeboxDelegate} used to determine whether the URL bar
     *                        has focus.
     */
    public void setFakeboxDelegate(FakeboxDelegate fakeboxDelegate) {
        mRecyclerView.setFakeboxDelegate(fakeboxDelegate);
    }

    private void initializeLayoutChangeListener() {
        TraceEvent.begin(TAG + ".initializeLayoutChangeListener()");

        // Listen for layout changes on the NewTabPageView itself to catch changes in scroll
        // position that are due to layout changes after e.g. device rotation. This contrasts with
        // regular scrolling, which is observed through an OnScrollListener.
        addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight,
                                          oldBottom) -> { mSnapScrollHelper.handleScroll(); });
        TraceEvent.end(TAG + ".initializeLayoutChangeListener()");
    }

    /**
     * Sets up Brave stats.
     */
    private void updateBraveStats() {
        TraceEvent.begin(TAG + ".updateBraveStats()");
        long trackersBlockedCount = mSharedPreferences.getLong(PREF_TRACKERS_BLOCKED_COUNT, 0);
        long adsBlockedCount = mSharedPreferences.getLong(PREF_ADS_BLOCKED_COUNT, 0);
        long httpsUpgradesCount = mSharedPreferences.getLong(PREF_HTTPS_UPGRADES_COUNT, 0);
        long estimatedMillisecondsSaved = (trackersBlockedCount + adsBlockedCount) * MILLISECONDS_PER_ITEM;
        TextView adsBlockedCountTextView = (TextView) mBraveStatsView.findViewById(R.id.brave_stats_text_ads_count);
        TextView httpsUpgradesCountTextView = (TextView) mBraveStatsView.findViewById(R.id.brave_stats_text_https_count);
        TextView estTimeSavedCountTextView = (TextView) mBraveStatsView.findViewById(R.id.brave_stats_text_time_count);
        adsBlockedCountTextView.setText(getBraveStatsStringFormNumber(adsBlockedCount));
        httpsUpgradesCountTextView.setText(getBraveStatsStringFormNumber(httpsUpgradesCount));
        estTimeSavedCountTextView.setText(getBraveStatsStringFromTime(estimatedMillisecondsSaved / 1000));

        TextView adsBlockedTextView = (TextView) mBraveStatsView.findViewById(R.id.brave_stats_text_ads);
        TextView httpsUpgradesTextView = (TextView) mBraveStatsView.findViewById(R.id.brave_stats_text_https);
        TextView estTimeSavedTextView = (TextView) mBraveStatsView.findViewById(R.id.brave_stats_text_time);

        if(mSharedPreferences.getBoolean(BackgroundImagesPreferences.PREF_SHOW_BACKGROUND_IMAGES, true) 
            && (Build.VERSION.SDK_INT > Build.VERSION_CODES.M || (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !((TabImpl)mTab).isMoreTabs()))) {
            adsBlockedTextView.setTextColor(mNewTabPageLayout.getResources().getColor(android.R.color.white));
            httpsUpgradesTextView.setTextColor(mNewTabPageLayout.getResources().getColor(android.R.color.white));
            estTimeSavedTextView.setTextColor(mNewTabPageLayout.getResources().getColor(android.R.color.white));            
            estTimeSavedCountTextView.setTextColor(mNewTabPageLayout.getResources().getColor(android.R.color.white));            
        }

        TraceEvent.end(TAG + ".updateBraveStats()");
    }

    /*
    * Gets string view of specific number for Brave stats
    */
    private String getBraveStatsStringFormNumber(long number) {
        String result = "";
        String suffix = "";
        if (number >= 1000 * 1000 * 1000) {
            result = result + (number / (1000 * 1000 * 1000));
            number = number % (1000 * 1000 * 1000);
            result = result + "." + (number / (10 * 1000 * 1000));
            suffix = "B";
        }
        else if (number >= (10 * 1000 * 1000) && number < (1000 * 1000 * 1000)) {
            result = result + (number / (1000 * 1000));
            suffix = "M";
        }
        else if (number >= (1000 * 1000) && number < (10 * 1000 * 1000)) {
            result = result + (number / (1000 * 1000));
            number = number % (1000 * 1000);
            result = result + "." + (number / (100 * 1000));
            suffix = "M";
        }
        else if (number >= (10 * 1000) && number < (1000 * 1000)) {
            result = result + (number / 1000);
            suffix = "K";
        }
        else if (number >= 1000 && number < (10* 1000)) {
            result = result + (number / 1000);
            number = number % 1000;
            result = result + "." + (number / 100);
            suffix = "K";
        }
        else {
            result = result + number;
        }
        result = result + suffix;
        return result;
    }

    /*
    * Gets string view of specific time in seconds for Brave stats
    */
    private String getBraveStatsStringFromTime(long seconds) {
        String result = "";
        if (seconds > 24 * 60 * 60) {
            result = result + (seconds / (24 * 60 * 60)) + "d";
        }
        else if (seconds > 60 * 60) {
            result = result + (seconds / (60 * 60)) + "h";
        }
        else if (seconds > 60) {
            result = result + (seconds / 60) + "m";
        }
        else {
            result = result + seconds + "s";
        }
        return result;
    }

    @VisibleForTesting
    public NewTabPageRecyclerView getRecyclerView() {
        return mRecyclerView;
    }

    /**
     * Adds listeners to scrolling to take care of snap scrolling and updating the search box on
     * scroll.
     */
    private void setupScrollHandling() {
        TraceEvent.begin(TAG + ".setupScrollHandling()");
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                mSnapScrollHelper.handleScroll();
            }
        });

        TraceEvent.end(TAG + ".setupScrollHandling()");
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        // Trigger a scroll update when reattaching the window to signal the toolbar that
        // it needs to reset the NTP state. Note that this is handled here rather than
        // NewTabPageLayout#onAttachedToWindow() because NewTabPageLayout may not be
        // immediately attached to the window if the RecyclerView is scrolled when the NTP
        // is refocused.
        if (mManager.isLocationBarShownInNTP()) mNewTabPageLayout.updateSearchBoxOnScroll();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) {
            updateBraveStats();
        }
    }

    /**
     * @see InvalidationAwareThumbnailProvider#shouldCaptureThumbnail()
     */
    boolean shouldCaptureThumbnail() {
        if (getWidth() == 0 || getHeight() == 0) return false;

        return mNewTabPageRecyclerViewChanged || mNewTabPageLayout.shouldCaptureThumbnail()
                || getWidth() != mSnapshotWidth || getHeight() != mSnapshotHeight
                || mRecyclerView.computeVerticalScrollOffset() != mSnapshotScrollY;
    }

    /**
     * @see InvalidationAwareThumbnailProvider#captureThumbnail(Canvas)
     */
    void captureThumbnail(Canvas canvas) {
        mNewTabPageLayout.onPreCaptureThumbnail();
        ViewUtils.captureBitmap(this, canvas);
        mSnapshotWidth = getWidth();
        mSnapshotHeight = getHeight();
        mSnapshotScrollY = mRecyclerView.computeVerticalScrollOffset();
        mNewTabPageRecyclerViewChanged = false;
    }

    /**
     * @return The adapter position the user has scrolled to.
     */
    public int getScrollPosition() {
        return mRecyclerView.getScrollPosition();
    }

    private void onDestroy() {
        mTab.getWindowAndroid().removeContextMenuCloseListener(mContextMenuManager);
    }

    @VisibleForTesting
    public SnapScrollHelper getSnapScrollHelper() {
        return mSnapScrollHelper;
    }

    private void showNTPImage(NTPImage ntpImage) {
        updateOrientedUI();

        if(mSharedPreferences.getBoolean(BackgroundImagesPreferences.PREF_SHOW_BACKGROUND_IMAGES, true)
            && (Build.VERSION.SDK_INT > Build.VERSION_CODES.M || (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !((TabImpl)mTab).isMoreTabs()))) {
            ViewTreeObserver observer = mNewTabPageLayout.getViewTreeObserver();
            observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    String countryCode = LocaleUtil.getCountryCode();

                    int layoutWidth = mNewTabPageLayout.getMeasuredWidth();
                    int layoutHeight = mNewTabPageLayout.getMeasuredHeight();
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inScaled = false;
                    options.inJustDecodeBounds = false;

                    Bitmap imageBitmap = null;
                    float imageWidth;
                    float imageHeight;
                    float centerPointX;
                    float centerPointY;

                    if (ntpImage instanceof SponsoredImage) {
                        SponsoredImage sponsoredImage = (SponsoredImage) ntpImage;
                        File imageFile = new File(PathUtils.getDataDirectory(), countryCode + "_" + sponsoredImage.getImageUrl());
                        try {
                            Uri imageFileUri = Uri.parse("file://"+imageFile.getAbsolutePath());
                            InputStream inputStream = mTab.getActivity().getContentResolver().openInputStream(imageFileUri);
                            imageBitmap = BitmapFactory.decodeStream(inputStream);
                            imageWidth = imageBitmap.getWidth();
                            imageHeight = imageBitmap.getHeight();
                            centerPointX = sponsoredImage.getFocalPointX() == 0 ? (imageWidth/2) : sponsoredImage.getFocalPointX();
                            centerPointY = sponsoredImage.getFocalPointY() == 0 ? (imageHeight/2) : sponsoredImage.getFocalPointY();

                            if (SponsoredImageUtil.getSponsoredLogo() != null ) {
                                ImageView sponsoredLogo = (ImageView)mNewTabPageLayout.findViewById(R.id.sponsored_logo);
                                sponsoredLogo.setVisibility(View.VISIBLE);
                                File logoFile = new File(PathUtils.getDataDirectory(),countryCode + "_" + SponsoredImageUtil.getSponsoredLogo().getImageUrl());
                                Bitmap logoBitmap = BitmapFactory.decodeFile(logoFile.getPath());
                                sponsoredLogo.setImageBitmap(logoBitmap);
                                sponsoredLogo.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View view) {
                                        if (SponsoredImageUtil.getSponsoredLogo().getDestinationUrl() != null) {
                                            openImageCredit(SponsoredImageUtil.getSponsoredLogo().getDestinationUrl());
                                        }
                                    }
                                });
                            }
                        } catch (Exception exc) {
                            Log.e("NTP", exc.getMessage());
                            return;
                        }
                    } else {
                        BackgroundImage backgroundImage = (BackgroundImage) ntpImage;

                        ImageView sponsoredLogo = (ImageView)mNewTabPageLayout.findViewById(R.id.sponsored_logo);
                        sponsoredLogo.setVisibility(View.GONE);

                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
                            Bitmap imageBitmapRes = BitmapFactory.decodeResource(mNewTabPageLayout.getResources(), backgroundImage.getImageDrawable(), options);
                            ByteArrayOutputStream stream = new ByteArrayOutputStream();
                            imageBitmapRes.compress(Bitmap.CompressFormat.JPEG,50,stream);
                            byte[] byteArray = stream.toByteArray();
                            imageBitmap = BitmapFactory.decodeByteArray(byteArray,0,byteArray.length);
                            imageBitmapRes.recycle();
                        } else {
                            imageBitmap = BitmapFactory.decodeResource(mNewTabPageLayout.getResources(), backgroundImage.getImageDrawable(), options);
                        }
                        imageWidth = imageBitmap.getWidth();
                        imageHeight = imageBitmap.getHeight();
                        centerPointX = backgroundImage.getCenterPoint();
                        centerPointY = 0;

                        if (backgroundImage.getImageCredit() != null) {

                            String imageCreditStr = String.format(mNewTabPageLayout.getResources().getString(R.string.photo_by, backgroundImage.getImageCredit().getName()));

                            SpannableStringBuilder spannableString = new SpannableStringBuilder(imageCreditStr);
                            spannableString.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), ((imageCreditStr.length()-1) - (backgroundImage.getImageCredit().getName().length()-1)), imageCreditStr.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                            TextView creditText = (TextView)mNewTabPageLayout.findViewById(R.id.credit_text);
                            creditText.setText(spannableString);
                            creditText.setVisibility(View.VISIBLE);
                            creditText.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    if (backgroundImage.getImageCredit() != null) {
                                        openImageCredit(backgroundImage.getImageCredit().getUrl());
                                    }
                                }
                            });
                        }
                    }

                    float centerRatioX = centerPointX / imageWidth;

                    float imageWHRatio = imageWidth / imageHeight;
                    float imageHWRatio = imageHeight / imageWidth;

                    int newImageWidth = (int) (layoutHeight * imageWHRatio);
                    int newImageHeight = layoutHeight;

                    if (newImageWidth < layoutWidth) {
                        // Image is now too small so we need to adjust width and height based on
                        // This covers landscape and strange tablet sizes.
                        newImageWidth = layoutWidth;
                        newImageHeight = (int) (newImageWidth * imageHWRatio);
                    }

                    int newCenterX = (int) (newImageWidth * centerRatioX);
                    int startX = (int) (newCenterX - (layoutWidth / 2));
                    if (newCenterX < layoutWidth / 2) {
                        // Need to crop starting at 0 to newImageWidth - left aligned image
                        startX = 0;
                    } else if (newImageWidth - newCenterX < layoutWidth / 2) {
                        // Need to crop right side of image - right aligned
                        startX = newImageWidth - layoutWidth;
                    }

                    int startY = (newImageHeight - layoutHeight)/2;
                    if (centerPointY > 0) {
                        float centerRatioY = centerPointY / imageHeight;
                        newImageWidth = layoutWidth;
                        newImageHeight = (int) (layoutWidth * imageHWRatio);

                        if (newImageHeight < layoutHeight) {
                            newImageHeight = layoutHeight;
                            newImageWidth = (int) (newImageHeight * imageWHRatio);
                        }

                        int newCenterY = (int) (newImageHeight * centerRatioY);
                        startY = (int) (newCenterY - (layoutHeight / 2));
                        if (newCenterY < layoutHeight / 2) {
                            // Need to crop starting at 0 to newImageWidth - left aligned image
                            startY = 0;
                        } else if (newImageHeight - newCenterY < layoutHeight / 2) {
                            // Need to crop right side of image - right aligned
                            startY = newImageHeight - layoutHeight;
                        }
                    }

                    imageBitmap = Bitmap.createScaledBitmap(imageBitmap, newImageWidth, newImageHeight, true);

                    Bitmap newBitmap = Bitmap.createBitmap(imageBitmap, startX, startY, layoutWidth, (int) layoutHeight);
                    Bitmap bitmapWithGradient = addGradient(newBitmap);

                    imageBitmap.recycle();
                    newBitmap.recycle();

                    // Center vertically, and crop to new center
                    imageDrawable = new BitmapDrawable(mNewTabPageLayout.getResources(), bitmapWithGradient);
                    mNewTabPageLayout.setBackground(imageDrawable);

                    mNewTabPageLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            });
        }
    }

    private void updateOrientedUI() {
        LinearLayout parentLayout= (LinearLayout)mNewTabPageLayout.findViewById(R.id.parent_layout);
        ViewGroup mainLayout = mNewTabPageLayout.findViewById(R.id.ntp_main_layout);
        ViewGroup imageCreditLayout = mNewTabPageLayout.findViewById(R.id.image_credit_layout);

        ImageView sponsoredLogo = (ImageView)mNewTabPageLayout.findViewById(R.id.sponsored_logo);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(dpToPx(mTab.getActivity(),170), dpToPx(mTab.getActivity(),170));

        boolean isTablet = DeviceFormFactor.isNonMultiDisplayContextOnTablet(mTab.getActivity());

        if (SponsoredImageUtil.isLandscape(mTab.getActivity()) && mSharedPreferences.getBoolean(BackgroundImagesPreferences.PREF_SHOW_BACKGROUND_IMAGES, true)) {
            // In landscape          
            parentLayout.removeView(mainLayout);
            parentLayout.removeView(imageCreditLayout);

            if (isTablet) {
                parentLayout.addView(mainLayout);
                parentLayout.addView(imageCreditLayout);

                parentLayout.setOrientation(LinearLayout.VERTICAL);

                LinearLayout.LayoutParams mainLayoutLayoutParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,0);
                mainLayoutLayoutParams.weight = 1f;
                mainLayout.setLayoutParams(mainLayoutLayoutParams);

                LinearLayout.LayoutParams imageCreditLayoutParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                imageCreditLayout.setLayoutParams(imageCreditLayoutParams);

                layoutParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                sponsoredLogo.setLayoutParams(layoutParams);

            } else {
                parentLayout.addView(imageCreditLayout);
                parentLayout.addView(mainLayout);

                parentLayout.setOrientation(LinearLayout.HORIZONTAL);

                LinearLayout.LayoutParams mainLayoutLayoutParams = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT);
                mainLayoutLayoutParams.weight = 0.6f;
                mainLayout.setLayoutParams(mainLayoutLayoutParams);

                LinearLayout.LayoutParams imageCreditLayoutParams = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT);
                imageCreditLayoutParams.weight = 0.4f;
                imageCreditLayout.setLayoutParams(imageCreditLayoutParams);

                layoutParams.setMargins(dpToPx(mTab.getActivity(),32), 0, 0, 0);
                layoutParams.gravity = Gravity.BOTTOM | Gravity.START;
                sponsoredLogo.setLayoutParams(layoutParams);
            }
        } else {
            // In portrait
            parentLayout.removeView(mainLayout);
            parentLayout.removeView(imageCreditLayout);

            parentLayout.addView(mainLayout);
            parentLayout.addView(imageCreditLayout);

            parentLayout.setOrientation(LinearLayout.VERTICAL);

            LinearLayout.LayoutParams mainLayoutLayoutParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,0);
            mainLayoutLayoutParams.weight = 1f;
            mainLayout.setLayoutParams(mainLayoutLayoutParams);

            LinearLayout.LayoutParams imageCreditLayoutParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            imageCreditLayout.setLayoutParams(imageCreditLayoutParams);

            layoutParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            sponsoredLogo.setLayoutParams(layoutParams);
        }
    }

    private void checkForNonDistruptiveBanner(NTPImage ntpImage) {
        BraveRewardsNativeWorker mBraveRewardsNativeWorker = BraveRewardsNativeWorker.getInstance();
        if (mTab.shouldShowBanner()) {
            if (BraveRewardsPanelPopup.isBraveRewardsEnabled()) {
                if (BraveAdsNativeHelper.nativeIsBraveAdsEnabled(Profile.getLastUsedProfile())) {
                    if (ntpImage instanceof SponsoredImage) {
                        showNonDistruptiveBanner(SponsoredImageUtil.BR_ON_ADS_ON);
                    }
                } else if(BraveAdsNativeHelper.nativeIsLocaleValid(Profile.getLastUsedProfile())) {
                    if (ntpImage instanceof SponsoredImage) {
                        showNonDistruptiveBanner(SponsoredImageUtil.BR_ON_ADS_OFF);
                    } else {
                        showNonDistruptiveBanner(SponsoredImageUtil.BR_ON_ADS_OFF_BG_IMAGE);
                    }
                }
            } else {
                if (ntpImage instanceof SponsoredImage && !mBraveRewardsNativeWorker.IsCreateWalletInProcess()) {
                    showNonDistruptiveBanner(SponsoredImageUtil.BR_OFF);
                }
            }
        }
    }

    private void showNonDistruptiveBanner(int ntpType) {
        nonDistruptiveBannerLayout = (ViewGroup) mNewTabPageLayout.findViewById(R.id.non_distruptive_banner);
        nonDistruptiveBannerLayout.setVisibility(View.GONE);

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                BackgroundImagesPreferences.setOnPreferenceValue(BackgroundImagesPreferences.PREF_SHOW_NON_DISTRUPTIVE_BANNER,false);

                boolean isTablet = DeviceFormFactor.isNonMultiDisplayContextOnTablet(mTab.getActivity());
                if(isTablet || (!isTablet && SponsoredImageUtil.isLandscape(mTab.getActivity()))) {
                    FrameLayout.LayoutParams nonDistruptiveBannerLayoutParams = new FrameLayout.LayoutParams(dpToPx(mTab.getActivity(), 400), LayoutParams.WRAP_CONTENT);
                    nonDistruptiveBannerLayoutParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                    nonDistruptiveBannerLayout.setLayoutParams(nonDistruptiveBannerLayoutParams);
                } else {
                    FrameLayout.LayoutParams nonDistruptiveBannerLayoutParams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                    nonDistruptiveBannerLayoutParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                    nonDistruptiveBannerLayout.setLayoutParams(nonDistruptiveBannerLayoutParams);
                }
                nonDistruptiveBannerLayout.setVisibility(View.VISIBLE);

                TextView bannerHeader = nonDistruptiveBannerLayout.findViewById(R.id.ntp_banner_header);
                TextView bannerText = nonDistruptiveBannerLayout.findViewById(R.id.ntp_banner_text);               
                TextView learnMoreText = nonDistruptiveBannerLayout.findViewById(R.id.ntp_banner_learn_more_text);               
                Button turnOnAdsButton = nonDistruptiveBannerLayout.findViewById(R.id.btn_turn_on_ads);
                ImageView bannerClose = nonDistruptiveBannerLayout.findViewById(R.id.ntp_banner_close);
                bannerClose.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        nonDistruptiveBannerLayout.setVisibility(View.GONE);
                        mTab.updateBannerPref();
                    }
                });

                learnMoreText.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        nonDistruptiveBannerLayout.setVisibility(View.GONE);

                        RewardsBottomSheetDialogFragment rewardsBottomSheetDialogFragment = RewardsBottomSheetDialogFragment.newInstance();
                        Bundle bundle = new Bundle();
                        bundle.putInt(SponsoredImageUtil.NTP_TYPE, ntpType);
                        rewardsBottomSheetDialogFragment.setArguments(bundle);
                        rewardsBottomSheetDialogFragment.setNewTabListener(newTabListener);
                        rewardsBottomSheetDialogFragment.show(mTab.getActivity().getSupportFragmentManager(), "rewards_bottom_sheet_dialog_fragment");
                        rewardsBottomSheetDialogFragment.setCancelable(false);

                        mTab.updateBannerPref();
                    }
                });
            
                turnOnAdsButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        turnOnAds();
                        nonDistruptiveBannerLayout.setVisibility(View.GONE);

                        mTab.updateBannerPref();
                    }
                });

                switch(ntpType) {
                    case SponsoredImageUtil.BR_OFF:
                        bannerText.setText(getResources().getString(R.string.get_paid_to_see_image));
                        learnMoreText.setVisibility(View.VISIBLE);
                        break;
                    case SponsoredImageUtil.BR_ON_ADS_OFF:
                        bannerText.setText(getResources().getString(R.string.get_paid_to_see_image));
                        learnMoreText.setVisibility(View.VISIBLE);
                        break;
                    case SponsoredImageUtil.BR_ON_ADS_OFF_BG_IMAGE:
                        bannerText.setText(getResources().getString(R.string.you_can_support_creators));
                        turnOnAdsButton.setVisibility(View.VISIBLE);
                        break;
                    case SponsoredImageUtil.BR_ON_ADS_ON:
                        bannerText.setText(getResources().getString(R.string.you_are_getting_paid));
                        learnMoreText.setVisibility(View.VISIBLE);
                        break;
                }
            }
        }, 1500);
    }

    private Bitmap addGradient(Bitmap src) {
        int w = src.getWidth();
        int h = src.getHeight();
        Bitmap result = Bitmap.createBitmap(src,0,0,w,h);
        Canvas canvas = new Canvas(result);

        // Top gradient
        int height;

        if(SponsoredImageUtil.isLandscape(mTab.getActivity())) {
            height = ((2*h)/3)+dpToPx(mTab.getActivity(), BOTTOM_TOOLBAR_HEIGHT);
        } else {
            height = (h/3)+dpToPx(mTab.getActivity(),BOTTOM_TOOLBAR_HEIGHT);
        }

        Paint topPaint = new Paint();
        LinearGradient topShader = new LinearGradient(0,0,0,height, mNewTabPageLayout.getContext().getResources().getColor(R.color.black_alpha_50), Color.TRANSPARENT, Shader.TileMode.CLAMP);
        topPaint.setShader(topShader);
        topPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DARKEN));
        canvas.drawRect(0,0,w,height,topPaint);

        //Bottom gradient
        Paint bottomPaint = new Paint();
        LinearGradient bottomShader = new LinearGradient(0,2*(h/3),0,h, Color.TRANSPARENT, mNewTabPageLayout.getContext().getResources().getColor(R.color.black_alpha_30), Shader.TileMode.CLAMP);
        bottomPaint.setShader(bottomShader);
        bottomPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DARKEN));
        canvas.drawRect(0,2*(h/3),w,h,bottomPaint);

        return result;
    }

    private void openImageCredit(String url) {
        ChromeTabbedActivity chromeTabbedActivity = BraveRewardsHelper.getChromeTabbedActivity();
        if(chromeTabbedActivity != null) {
            LoadUrlParams loadUrlParams = new LoadUrlParams(url);
            chromeTabbedActivity.getActivityTab().loadUrl(loadUrlParams);
        }
    }

    private void turnOnAds() {
        BraveAdsNativeHelper.nativeSetAdsEnabled(Profile.getLastUsedProfile());
    }

    private void checkAndShowNTPImage() {
        NTPImage ntpImage = mTab.getTabNTPImage();
        if(ntpImage == null) {
            mTab.setNTPImage(SponsoredImageUtil.getBackgroundImage());
        } else if (ntpImage instanceof SponsoredImage) {
            String countryCode = LocaleUtil.getCountryCode();
            SponsoredImage sponsoredImage = (SponsoredImage) ntpImage;
            File imageFile = new File(PathUtils.getDataDirectory(), countryCode + "_" + sponsoredImage.getImageUrl());
            if(!imageFile.exists()) {
                mTab.setNTPImage(SponsoredImageUtil.getBackgroundImage());
                ntpImage = mTab.getTabNTPImage();
            }
        }
        checkForNonDistruptiveBanner(ntpImage);
        showNTPImage(ntpImage);
    }

    private final TabObserver mTabObserver = new EmptyTabObserver() {
        @Override
        public void onInteractabilityChanged(boolean interactable) {
            // Force a layout update if the tab is now in the foreground.
            if (interactable) {
                checkAndShowNTPImage();
            } else {
                if(!isFromBottomSheet){
                    mNewTabPageLayout.setBackgroundResource(0);
                    if (imageDrawable != null && imageDrawable.getBitmap() != null && !imageDrawable.getBitmap().isRecycled()) {
                        imageDrawable.getBitmap().recycle();
                    }
                }
            }
        }
    };

    private NewTabListener newTabListener = new NewTabListener() {
        @Override
        public void updateInteractableFlag(boolean isBottomSheet) {
            isFromBottomSheet = isBottomSheet;
        }

        @Override
        public void updateNTPImage() {
            checkAndShowNTPImage();
        }
    };
}