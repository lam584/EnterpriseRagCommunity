package com.example.EnterpriseRagCommunity;

import org.junit.jupiter.api.Test;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingToFileSmokeTest {

    @Test
    void writesStartupLogToFile() throws Exception {
        Path logFile = Path.of("build", "test-logs", "startup.log");
        Files.createDirectories(logFile.getParent());
        Files.deleteIfExists(logFile);

        System.setProperty("LOG_FILE", logFile.toString());
        configureLog4j2FromResource("log4j2-test.xml");

        Logger logger = LoggerFactory.getLogger(LoggingToFileSmokeTest.class);
        logger.info("log-to-file-smoke-test");

        // Flush async/buffered appenders before assertions.
        LoggerContext ctx = (LoggerContext) org.apache.logging.log4j.LogManager.getContext(false);
        ctx.stop();

        assertTrue(Files.exists(logFile), "Expected log file to exist: " + logFile);
        assertTrue(Files.size(logFile) > 0, "Expected log file to be non-empty: " + logFile);
    }

    private static void configureLog4j2FromResource(String resourceName) {
        URL url = LoggingToFileSmokeTest.class.getClassLoader().getResource(resourceName);
        assertTrue(url != null, "Expected to find resource on classpath: " + resourceName);
        Configurator.reconfigure(java.net.URI.create(url.toString()));
    }
}
