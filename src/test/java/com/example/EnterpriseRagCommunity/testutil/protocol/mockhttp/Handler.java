package com.example.EnterpriseRagCommunity.testutil.protocol.mockhttp;

import com.example.EnterpriseRagCommunity.testutil.MockHttpUrl;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

public final class Handler extends URLStreamHandler {

    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        return MockHttpUrl.openConnection(u);
    }
}

