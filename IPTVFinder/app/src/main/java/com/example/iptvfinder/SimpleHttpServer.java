package com.example.iptvfinder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import fi.iki.elonen.NanoHTTPD;

public class SimpleHttpServer extends NanoHTTPD {

    private List<String> validUrls = new ArrayList<>();

    public SimpleHttpServer(int port) throws IOException {
        super(port);
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    }

    public void setValidUrls(List<String> urls) {
        this.validUrls = urls;
    }

    @Override
    public Response serve(IHTTPSession session) {
        if (validUrls.isEmpty()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "No valid IPTV URLs available.");
        }

        Random random = new Random();
        String randomUrl = validUrls.get(random.nextInt(validUrls.size()));

        Response response = newFixedLengthResponse(Response.Status.REDIRECT, "text/plain", "");
        response.addHeader("Location", randomUrl);
        return response;
    }
}
