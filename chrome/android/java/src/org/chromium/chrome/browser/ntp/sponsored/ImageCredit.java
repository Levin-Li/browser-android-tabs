package org.chromium.chrome.browser.ntp.sponsored;

public class ImageCredit {
    private String name;
    private String url;

    public ImageCredit(String name, String url) {
        this.name = name;
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
    	return url;
    }
}
