package com.azuredragon.puddingplayer;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;

public class FileLoader {
    private Context mContext;
    private OnLoadFailedListener mOnError;

    public String APPLICATION_DATA_DIR;
    public String APPLICATION_CACHE_DIR;

    public FileLoader(Context context) {
        mContext = context;
        APPLICATION_DATA_DIR = context.getApplicationInfo().dataDir + "/";
        APPLICATION_CACHE_DIR = context.getCacheDir().toString() + "/";
    }

    public boolean checkFileExistance(String path) {
        File file = new File(path);
        return file.exists();
    }

    public long getFileSize(String path) {
        return new File(path).length();
    }

    public void setOnLoadFailedListener(OnLoadFailedListener onLoadFailedListener) {
        mOnError = onLoadFailedListener;
    }

    public String loadFile(String fileName) {
        File file = new File(fileName);
        if(!file.exists() || !file.canRead()) return "";

        BufferedReader reader;
        StringBuilder stringBuilder;
        try {
            reader = new BufferedReader(new FileReader(file));
            stringBuilder = new StringBuilder();
            int i;
            while((i = reader.read()) > 0)
                stringBuilder.append((char) i);
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }

        return stringBuilder.toString();
    }

    public byte[] loadFileToByteArray(String path) {
        File file = new File(path);
        if(!file.exists() || !file.canRead()) return null;

        byte[] array = new byte[(int) file.length()];
        BufferedInputStream is;
        try {
            is = new BufferedInputStream(new FileInputStream(file));
            is.read(array, 0, array.length);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return array;
    }

    public void saveFile(String content, String fileName, boolean replaceControl) throws IOException {
        File file = new File(APPLICATION_DATA_DIR + fileName);
        if(replaceControl && file.exists()) {
            if(!file.delete()) throw new IOException("Unable to replace the specific file.");
        }
        if(!file.exists()) {
            if(!file.createNewFile()) throw new IOException("Unable to create the specific file.");
        }
        if(!file.canWrite()) throw new IOException("Unable to write the specific file.");
        FileOutputStream output = new FileOutputStream(file);
        output.write(content.getBytes(), 0, content.getBytes().length);
        output.flush();
        output.close();
    }

    public void copyFile(String source, String dest) throws IOException {
        if(!new File(source).exists()) {
            return;
        }
        dest = APPLICATION_DATA_DIR + dest;
        FileInputStream inputStream = new FileInputStream(new File(source));
        byte[] data = new byte[1024];
        FileOutputStream outputStream =new FileOutputStream(new File(dest));
        while (inputStream.read(data) != -1) {
            outputStream.write(data);
        }

        inputStream.close();
        outputStream.close();
    }

    public void deleteFile(String path) throws IOException {
        File file = new File(path);
        if(!file.exists()) throw new IOException("Can't find the specific file.");
        if(!file.canWrite()) throw new IOException("Unable to write the specific file.");
        if(!file.delete()) throw new IOException("Unable to delete the specific file.");
    }

    public String downloadFile(String url, String filename) throws IOException {
        return downloadFile(url, filename, false);
    }

    public String downloadFile(String url, String filename, boolean cache) throws IOException {
        filename = (cache ? APPLICATION_CACHE_DIR : APPLICATION_DATA_DIR) + filename;
        File file = new File(filename);
        if(!file.exists()) file.createNewFile();
        if(!file.canWrite()) return "";
        FileOutputStream out = new FileOutputStream(file);

        InputStream is = new NetworkHandler(url, mContext).getInputStream();
        while(true) {
            byte[] buffer = new byte[1024];
            int i;
            if((i = is.read(buffer)) < 0) break;
            out.write(buffer, 0, i);
        }

        return loadFile(filename);
    }

    public interface OnLoadFailedListener {
        void onLoadFailed(String reason);
    }
}
