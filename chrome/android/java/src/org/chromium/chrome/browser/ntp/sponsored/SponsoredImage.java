package org.chromium.chrome.browser.ntp.sponsored;

public class SponsoredImage extends BackgroundImage {

    private long startDate;
    private long endDate;

    public SponsoredImage(int imageDrawable, int centerPoint, ImageCredit imageCredit, long startDate, long endDate) {
        super(imageDrawable, centerPoint, imageCredit);
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public long getStartDate() {
        return startDate;
    }

    public long getEndDate() {
        return endDate;
    }
}