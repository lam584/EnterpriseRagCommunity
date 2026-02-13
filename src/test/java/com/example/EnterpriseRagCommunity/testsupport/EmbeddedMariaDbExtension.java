package com.example.EnterpriseRagCommunity.testsupport;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class EmbeddedMariaDbExtension implements BeforeAllCallback {

    static {
        EmbeddedMariaDbBootstrap.ensureStarted();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        EmbeddedMariaDbBootstrap.ensureStarted();
    }
}
