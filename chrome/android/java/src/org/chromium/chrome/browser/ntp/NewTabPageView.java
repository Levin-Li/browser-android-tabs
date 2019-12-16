// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

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
import android.widget.ImageView;
import android.widget.TextView;
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
import org.chromium.chrome.browser.ntp.sponsored.BackgroundImage;
import org.chromium.chrome.browser.ntp.sponsored.SponsoredImage;
import org.chromium.chrome.browser.ntp.sponsored.SponsoredImageUtil;
import org.chromium.chrome.browser.util.LocaleUtil;
import org.chromium.content_public.browser.LoadUrlParams;

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

    private NewTabPageRecyclerView mRecyclerView;

    private NewTabPageLayout mNewTabPageLayout;
    private ViewGroup mBraveStatsView;

    private NewTabPageManager mManager;
    private Tab mTab;
    private SnapScrollHelper mSnapScrollHelper;
    private UiConfig mUiConfig;
    private ViewResizer mRecyclerViewResizer;

    private boolean mNewTabPageRecyclerViewChanged;
    private int mSnapshotWidth;
    private int mSnapshotHeight;
    private int mSnapshotScrollY;
    private ContextMenuManager mContextMenuManager;
    private SharedPreferences mSharedPreferences;
    private BackgroundImage backgroundImage;

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
        super.onConfigurationChanged(newConfig);
        showBackgroundImage();
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
        mTab = tab;
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

        backgroundImage = mTab.getTabBackgroundImage();
        showBackgroundImage();

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
            && Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
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

    private void showBackgroundImage() {

        TextView creditText = (TextView)mNewTabPageLayout.findViewById(R.id.credit_text);

        if(mSharedPreferences.getBoolean(BackgroundImagesPreferences.PREF_SHOW_BACKGROUND_IMAGES, true)
            && Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            ViewTreeObserver observer = mNewTabPageLayout.getViewTreeObserver();
            observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    int layoutWidth = mNewTabPageLayout.getMeasuredWidth();
                    int layoutHeight = mNewTabPageLayout.getMeasuredHeight();
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inScaled = false;
                    Bitmap imageBitmap = BitmapFactory.decodeResource(mNewTabPageLayout.getResources(), backgroundImage.getImageDrawable(), options);
                    float imageWidth = imageBitmap.getWidth();
                    float imageHeight = imageBitmap.getHeight();
                    float centerPoint = backgroundImage.getCenterPoint();
                    float centerRatio = centerPoint / imageWidth;
                    float imageWHRatio = imageWidth / imageHeight;
                    int newImageWidth = (int) (layoutHeight * imageWHRatio);
                    int newImageHeight = layoutHeight;
                    if (newImageWidth < layoutWidth) {
                        // Image is now too small so we need to adjust width and height based on
                        // This covers landscape and strange tablet sizes.
                        float imageHWRatio = imageHeight / imageWidth;
                        newImageWidth = layoutWidth;
                        newImageHeight = (int) (newImageWidth * imageHWRatio);
                    }
                    int newCenter = (int) (newImageWidth * centerRatio);
                    int startX = (int) (newCenter - (layoutWidth / 2));
                    if (newCenter < layoutWidth / 2) {
                        // Need to crop starting at 0 to newImageWidth - left aligned image
                        startX = 0;
                    } else if (newImageWidth - newCenter < layoutWidth / 2) {
                        // Need to crop right side of image - right aligned
                        startX = newImageWidth - layoutWidth;
                    }
                    imageBitmap = Bitmap.createScaledBitmap(imageBitmap, newImageWidth, newImageHeight, true);

                    Bitmap newBitmap = Bitmap.createBitmap(imageBitmap, startX, (newImageHeight - layoutHeight) / 2, layoutWidth, (int) layoutHeight);

                    Bitmap bitmapWithGradient = addGradient(newBitmap, mNewTabPageLayout.getContext().getResources().getColor(R.color.black_alpha_50),Color.TRANSPARENT);

                    imageBitmap.recycle();
                    newBitmap.recycle();

                    // Center vertically, and crop to new center
                    final BitmapDrawable imageDrawable = new BitmapDrawable(mNewTabPageLayout.getResources(), bitmapWithGradient);

                    mNewTabPageLayout.setBackground(imageDrawable);

                    if (backgroundImage.getImageCredit() != null) {
                        if (backgroundImage instanceof SponsoredImage) {
                            mNewTabPageLayout.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    openImageCredit();
                                }
                            });
                        } else {
                            String imageCreditStr = String.format(mNewTabPageLayout.getResources().getString(R.string.photo_by, backgroundImage.getImageCredit().getName()));

                            SpannableStringBuilder spannableString = new SpannableStringBuilder(imageCreditStr);
                            spannableString.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), ((imageCreditStr.length()-1) - (backgroundImage.getImageCredit().getName().length()-1)), imageCreditStr.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                            creditText.setText(spannableString);
                            creditText.setVisibility(View.VISIBLE);
                            creditText.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    openImageCredit();
                                }
                            });
                        }
                    } else {
                        creditText.setVisibility(View.GONE);
                    }

                    mNewTabPageLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            });
        } else {
            creditText.setVisibility(View.GONE);
        }
    }

    private Bitmap addGradient(Bitmap src, int color1, int color2) {
        int w = src.getWidth();
        int h = src.getHeight();
        Bitmap result = Bitmap.createBitmap(src,0,0,w,h);
        Canvas canvas = new Canvas(result);

        Paint paint = new Paint();
        LinearGradient shader = new LinearGradient(0,0,0,h/3, color1, color2, Shader.TileMode.CLAMP);
        paint.setShader(shader);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DARKEN));
        canvas.drawRect(0,0,w,h/3,paint);

        return result;
    }

    private void openImageCredit() {
        for (Activity ref : ApplicationStatus.getRunningActivities()) {
            if (!(ref instanceof ChromeTabbedActivity)) continue;
            ChromeTabbedActivity chromeTabbedActivity =  (ChromeTabbedActivity)ref;
            if (backgroundImage.getImageCredit() != null) {
                LoadUrlParams loadUrlParams = new LoadUrlParams(backgroundImage.getImageCredit().getUrl());
                chromeTabbedActivity.getActivityTab().loadUrl(loadUrlParams);
            } 
        }
    }
}