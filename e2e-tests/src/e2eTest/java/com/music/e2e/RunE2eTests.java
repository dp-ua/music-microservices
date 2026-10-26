package com.music.e2e;

import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Entry point for Gradle: JUnit Platform starts the Cucumber engine, which runs every .feature file
 * from src/e2eTest/resources/features. Glue code and report settings: junit-platform.properties.
 */
@Suite
@SuiteDisplayName("End-to-end tests of the music library")
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
public class RunE2eTests {

}
