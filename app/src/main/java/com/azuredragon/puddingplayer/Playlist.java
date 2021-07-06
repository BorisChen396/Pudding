package com.azuredragon.puddingplayer;

import android.content.Context;
import android.net.Uri;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class Playlist extends ArrayList<Uri> {
    private Context mContext;
    private long requestId;
    private String listTitle = "";

    public static long PREVIOUS_PLAYLIST_ID = 0;

    public Playlist(Context context, long listId) {
        mContext = context;
        requestId = listId;
        try {load();}
        catch (ParserConfigurationException | IOException | SAXException ignored) {}
    }

    public Playlist(Context context, long listId, String title) {
        mContext = context;
        requestId = listId;
        listTitle = title;
        try {load();}
        catch (ParserConfigurationException | IOException | SAXException ignored) {}
    }

    private void load() throws ParserConfigurationException, IOException, SAXException {
        FileLoader loader = new FileLoader(mContext);
        String path = loader.APPLICATION_DATA_DIR + "playlist_" + requestId;
        if(!loader.checkFileExistance(path)) return;
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document document = builder.parse(new File(path));
        Element playlist = document.getDocumentElement();
        NodeList items = playlist.getElementsByTagName("item");
        for(int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String uri = item.getAttribute("uri");
            if(uri == null) continue;
            add(Uri.parse(uri));
        }
    }

    public void save() throws ParserConfigurationException, IOException, TransformerException {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document document = builder.newDocument();
        Element playlist = document.createElement("playlist");
        playlist.setAttribute("id", String.valueOf(requestId));
        playlist.setAttribute("title", listTitle);
        for(int i = 0; i < size(); i++) {
            Uri uri = get(i);
            Element item = document.createElement("item");
            item.setAttribute("uri", uri.toString());
            playlist.appendChild(item);
        }
        document.appendChild(playlist);
        FileLoader loader = new FileLoader(mContext);
        String path = loader.APPLICATION_DATA_DIR + "playlist_" + requestId;
        if(loader.checkFileExistance(path))
            loader.deleteFile(path);
        FileOutputStream os = new FileOutputStream(path);
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.transform(new DOMSource(document), new StreamResult(os));
    }
}