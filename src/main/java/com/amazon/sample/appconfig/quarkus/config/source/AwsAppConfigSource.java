/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: MIT-0
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.amazon.sample.appconfig.quarkus.config.source;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.appconfigdata.AppConfigDataClient;

import javax.json.Json;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AwsAppConfigSource implements ConfigSource {

  private static final Logger LOGGER = LoggerFactory.getLogger(AwsAppConfigSource.class);

  /**
   * Quarkus uses 300 for ENV variables and 250 for application.properties.
   *
   * <p>We want to be higher precedence than the packaged application.properties but not higher than
   * any supplied ENV variables.
   */
  private static final int ORDINAL = 275;

  private ConcurrentHashMap<String, String> props;

  private AppConfigDataClient dataClient;
  private UUID clientId;
  private String token = null;

  private ScheduledExecutorService execSvc;

  public AwsAppConfigSource() {
    props = new ConcurrentHashMap();
    clientId = UUID.randomUUID();

    try {
      dataClient = AppConfigDataClient.builder().build();
    } catch (Exception ex) {
      // Quarkus plugin runs this code path if you run ./mvnw package.
      // Hence we need to account for the probability the no AWS credentials can be obtained
      LOGGER.error(
          "AWS AppConfig client creation failed. This sample will not work. "
              + "Check the nested exception for details and reconfigure your environment",
          ex);
      return;
    }

    execSvc = new ScheduledThreadPoolExecutor(1);

    token = startConfigurationSession();
    LOGGER.info("fetched initialConfigToken");
    fetchConfig();
    LOGGER.info("initialized with AppConfig provided interestRate = {}", props.get("interestRate"));

    // we poll in 30 seconds intervals for demonstration purposes
    execSvc.scheduleAtFixedRate(this::fetchConfig, 30, 30, TimeUnit.SECONDS);
  }

  private String startConfigurationSession() {
    var applicationId = System.getenv("APP_ID");
    var environmentId = System.getenv("ENV_ID");
    var configProfileId = System.getenv("CONFIG_PROFILE_ID");

    var res =
        dataClient.startConfigurationSession(
            req -> {
              req.applicationIdentifier(applicationId);
              req.environmentIdentifier(environmentId);
              req.configurationProfileIdentifier(configProfileId);

              req.requiredMinimumPollIntervalInSeconds(20);
            });

    return res.initialConfigurationToken();
  }

  private void fetchConfig() {
    var res =
        dataClient.getLatestConfiguration(
            req -> {
              req.configurationToken(token);
            });

    try {
      var configString = res.configuration().asUtf8String();
      if(configString.isEmpty()) {
        LOGGER.info("received empty config");
        return;
      }
      var rate =
          Json.createReader(new ByteArrayInputStream(configString.getBytes(StandardCharsets.UTF_8)))
              .readObject()
              .getJsonNumber("interestRate")
              .toString();

      props.put("interestRate", rate);
      token = res.nextPollConfigurationToken();

      LOGGER.debug("now using config version = {}\nand interestRate = {}", token, props.get("interestRate"));
    } catch (Exception ex) {
      LOGGER.error("unexpected failure in reading the config", ex);
      return;
    }
  }

  @Override
  public Map<String, String> getProperties() {
    return props;
  }

  @Override
  public Set<String> getPropertyNames() {
    return props.keySet();
  }

  @Override
  public int getOrdinal() {
    return ORDINAL;
  }

  @Override
  public String getValue(String s) {
    return props.get(s);
  }

  @Override
  public String getName() {
    return "AwsAppConfigSource";
  }
}
