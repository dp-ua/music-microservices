package com.music.processor.component;

import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Entry point for Gradle: JUnit Platform starts the Cucumber engine, which runs every .feature file
 * from src/componentTest/resources/features. Glue code and report settings: junit-platform.properties.
 */
@Suite
@SuiteDisplayName("resource-processor component tests")
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
public class RunComponentTests {

}
