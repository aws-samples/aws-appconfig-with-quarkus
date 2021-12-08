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
package com.amazon.config.source;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.appconfig.AppConfigClient;

import javax.json.Json;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

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

  private AppConfigClient client;
  private UUID clientId;
  private String lastConfigVersion = null;

  private ScheduledExecutorService execSvc;

  public AwsAppConfigSource() {
    props = new ConcurrentHashMap();
    clientId = UUID.randomUUID();

    try {
      client = AppConfigClient.builder().build();
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

    fetchConfig();
    LOGGER.info("initialized with AppConfig provided interestRate = {}", props.get("interestRate"));

    // we poll in 30 seconds intervals for demonstration purposes
    execSvc.scheduleAtFixedRate(this::fetchConfig, 30, 30, TimeUnit.SECONDS);
  }

  private void fetchConfig() {
    var res =
        client.getConfiguration(
            req -> {
              req.application("ConfigSourceDemo");
              req.environment("Sandbox");
              req.configuration("json-profile");
              req.clientId(clientId.toString());

              req.clientConfigurationVersion(lastConfigVersion);
            });

    try {
      if (res.configurationVersion().equals(lastConfigVersion)) {
        // we have no new content delivered
        LOGGER.debug("still on config version = {}", lastConfigVersion);
        return;
      }
      var parser = Json.createParser(res.content().asInputStream());
      var rate =
          Json.createReader(res.content().asInputStream())
              .readObject()
              .getJsonNumber("interestRate")
              .toString();

      props.put("interestRate", rate);
      lastConfigVersion = res.configurationVersion();

      LOGGER.debug("now using config version = {}", lastConfigVersion);
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
