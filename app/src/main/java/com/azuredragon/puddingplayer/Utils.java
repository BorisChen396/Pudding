package com.azuredragon.puddingplayer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.azuredragon.puddingplayer.service.PlaybackService;

import java.util.Locale;

public class Utils {
    static public Bundle decodeYTLink(String link) {
        if(link.equals("Pudding is delicious <3"))
            link = "https://youtu.be/dQw4w9WgXcQ";

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

    public static int getHeightPxWithContext(Context context) {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        return displayMetrics.heightPixels;
    }

    @NonNull public static String getFilenameWithoutExtension(String filename, Context context) {
        if(filename == null) return context.getString(R.string.string_file_unaccessable);
        int index = filename.lastIndexOf(".");
        if(index < 0) return filename;
        return filename.substring(0, index);
    }

    public static Bitmap createDefaultLargeIcon(Context context) {
        Drawable dfIcon = ContextCompat.getDrawable(context, R.drawable.ic_baseline_music_note_24);
        if(dfIcon == null) return null;
        Bitmap bm = Bitmap.createBitmap(dfIcon.getIntrinsicWidth(), dfIcon.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bm);
        dfIcon.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        dfIcon.draw(canvas);
        return bm;
    }
}
