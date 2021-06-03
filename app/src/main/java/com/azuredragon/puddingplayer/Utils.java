package com.azuredragon.puddingplayer;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import java.util.Locale;

public class Utils {
    static public Bundle decodeYTLink(String link) {
        Bundle obj = new Bundle();
        if(link.contains("youtu.be"))
            link = link.replace("youtu.be/", "www.youtube.com/watch?v=");

        Uri parsedLink =  Uri.parse(link);
        if(link.contains("v") || link.contains("list")) {
            if(link.contains("v")) {
                obj.putString("videoId", Uri.parse(link).getQueryParameter("v"));
            }
            if(link.contains("list")) {
                obj.putString("listId", Uri.parse(link).getQueryParameter("list"));
            }
            return obj;
        }
        if(parsedLink.getPathSegments().contains("channel")) {
            obj.putString("channelId", parsedLink.getLastPathSegment());
        }
        if(parsedLink.getPathSegments().contains("user")) {
            obj.putString("username", parsedLink.getLastPathSegment());
        }
        obj.putString("customName", parsedLink.getLastPathSegment());
        return obj;
    }

    static public String secondToString(long second) {
        long hour = second / 3600;
        long min = (second / 60) % 60;
        long sec = second % 60;
        if(hour > 0) return String.format(Locale.getDefault(), "%02d:%02d:%02d", hour, min, sec);
        else return String.format(Locale.getDefault(), "%02d:%02d", min, sec);
    }

    public static int dp2px(Context context, float dpVal) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                dpVal, context.getResources().getDisplayMetrics());
    }

    static public int getHeightPxWithContext(Context context) {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        return displayMetrics.heightPixels;
    }
}
