package com.azuredragon.puddingplayer;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class Playlist extends ArrayList<String> {
    private String playlistTitle = "";
    private Context mContext;
    private String TAG = "Playlist";

    public static String RESULT_INVALID_TITLE = "invalid_title";
    public static String RESULT_XML_EXCEPTION = "xml_exception";
    public static String RESULT_IO_EXCEPTION = "io_exception";
    public static String RESULT_SUCCESS = "success";

    public Playlist(@NonNull Context context, @NonNull String title) {
        mContext = context;
        playlistTitle = title;
        Document document;
        String path = new FileLoader(mContext).APPLICATION_DATA_DIR + "playlist_" + playlistTitle;
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            document = builder.parse(new FileInputStream(path));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            Log.e(TAG, "Load error: " + e.getMessage());
            return;
        }
        Element playlist = document.getDocumentElement();
        NodeList items = playlist.getElementsByTagName("item");
        for(int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String type = item.getAttribute("type");
            String videoId = item.getAttribute("videoId");
            add(i, new Uri.Builder().appendQueryParameter("videoId", videoId)
                    .appendQueryParameter("type", type)
                    .build().toString());
        }
    }

    public String save() {
        if(playlistTitle.equals("")) {
            Log.e(TAG, "Title is not set.");
            return RESULT_INVALID_TITLE;
        }
        FileLoader loader = new FileLoader(mContext);
        String path = loader.APPLICATION_DATA_DIR + "playlist_" + playlistTitle;
        DocumentBuilder builder;
        try {
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            Log.e(TAG, e.getMessage());
            return RESULT_XML_EXCEPTION;
        }
        Document document = builder.newDocument();
        Element playlist = document.createElement("playlist");
        playlist.setAttribute("title", playlistTitle);
        for(int i = 0; i < size(); i++) {
            Uri uri = Uri.parse(get(i));
            Element item = document.createElement("item");
            item.setAttribute("type", uri.getQueryParameter("type"));
            item.setAttribute("videoId", uri.getQueryParameter("videoId"));
            playlist.appendChild(item);
        }
        document.appendChild(playlist);
        FileOutputStream os;
        try {
            os = new FileOutputStream(path);
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
            return RESULT_IO_EXCEPTION;
        }
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.transform(new DOMSource(document), new StreamResult(os));
        } catch (TransformerException e) {
            Log.e(TAG, e.getMessage());
            return RESULT_XML_EXCEPTION;
        }
        return RESULT_SUCCESS;
    }
}
