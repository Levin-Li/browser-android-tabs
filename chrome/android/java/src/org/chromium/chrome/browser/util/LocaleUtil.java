package org.chromium.chrome.browser.util;

import java.util.List;
import java.util.Arrays;
import java.util.Locale;

public class LocaleUtil {

	private static final List<String> sposoredRegions = Arrays.asList("JP","CN","KR","KP","TW","MN","MO","GU","MP","SG","IN","PK","MV","BD","BT","NP","UZ");

	public static boolean isSponsoredRegions() {
		Locale locale = Locale.getDefault();
		return sposoredRegions.contains(locale.getCountry());
	}
}