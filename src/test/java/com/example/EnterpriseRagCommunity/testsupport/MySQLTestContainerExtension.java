package com.example.EnterpriseRagCommunity.testsupport;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class MySQLTestContainerExtension implements BeforeAllCallback {

    static {
        MySQLTestContainerBootstrap.ensureStarted();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        MySQLTestContainerBootstrap.ensureStarted();
    }
}
