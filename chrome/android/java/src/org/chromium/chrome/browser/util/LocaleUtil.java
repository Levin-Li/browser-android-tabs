package org.chromium.chrome.browser.util;

import java.util.List;
import java.util.Arrays;
import java.util.Locale;

public class LocaleUtil {
	public static String getCountryCode() {
		Locale locale = Locale.getDefault();
		return locale.getCountry();
	}
}