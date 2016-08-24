package com.intentfilter.wificonnect.helpers;

import android.support.annotation.NonNull;

public class StringUtil {
    @NonNull
    public static String trimQuotes(String str) {
        if (!isEmpty(str)) {
            return str.replaceAll("^\"*", "").replaceAll("\"*$", "");
        }

        return str;
    }

    public static boolean isEmpty(CharSequence str) {
        return str == null || str.toString().isEmpty();
    }
}