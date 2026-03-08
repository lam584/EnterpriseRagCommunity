package com.example.EnterpriseRagCommunity;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;

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

        configureLogbackFromResource("logback-spring.xml");

        Logger logger = LoggerFactory.getLogger(LoggingToFileSmokeTest.class);
        logger.info("log-to-file-smoke-test");

        assertTrue(Files.exists(logFile), "Expected log file to exist: " + logFile);
        assertTrue(Files.size(logFile) > 0, "Expected log file to be non-empty: " + logFile);
    }

    private static void configureLogbackFromResource(String resourceName) throws JoranException {
        URL url = LoggingToFileSmokeTest.class.getClassLoader().getResource(resourceName);
        assertTrue(url != null, "Expected to find resource on classpath: " + resourceName);

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();

        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(url);
    }
}
