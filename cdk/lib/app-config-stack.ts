// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import * as cdk from '@aws-cdk/core';
import * as appconfig from '@aws-cdk/aws-appconfig';
import * as fs from 'fs';

export class AppConfigStack extends cdk.Stack {
  constructor(scope: cdk.Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    const app = new appconfig.CfnApplication(this, "ac-app-configsourcedemo", {
      name: "ConfigSourceDemo",
    });

    const env = new appconfig.CfnEnvironment(this, "ac-env-sandbox", {
      name: "Sandbox",
      applicationId: app.ref,
    });

    const plainJsonProfile = new appconfig.CfnConfigurationProfile(this, "ac-prf-json", {
      name: "json-profile",
      applicationId: app.ref,
      locationUri: "hosted",
    });

    const initialConfigContent = fs.readFileSync("config-v1.json").toString("UTF-8");
    const updatedConfigContent = fs.readFileSync("config-v2.json").toString("UTF-8");

    const config = new appconfig.CfnHostedConfigurationVersion(this, "ac-hcv-v1", {
      applicationId: app.ref,
      configurationProfileId: plainJsonProfile.ref,
      contentType: "application/json",

      // Change this line and run 'cdk deploy' to update the config
      content: initialConfigContent,
    });

    // We use a custom deployment strategy for demonstration purposes.
    const deploymentStrategy = new appconfig.CfnDeploymentStrategy(this, "ac-dep-strat-demo", {
      name: "Demo Strategy",
      deploymentDurationInMinutes: 1,
      finalBakeTimeInMinutes: 0,
      growthFactor: 100.0,
      replicateTo: "NONE"
    });

    const configDeployment = new appconfig.CfnDeployment(this, "ac-dep-alpha", {
      applicationId: app.ref,
      configurationProfileId: plainJsonProfile.ref,
      deploymentStrategyId: deploymentStrategy.ref,
      configurationVersion: config.ref,
      environmentId: env.ref
    });

    new cdk.CfnOutput(this, "appId", {
      exportName: "appId",
      value: app.ref
    });

    new cdk.CfnOutput(this, "envId", {
      exportName: "envId",
      value: env.ref
    });

    new cdk.CfnOutput(this, "configProfileId", {
      exportName: "configProfileId",
      value: plainJsonProfile.ref
    });
  }
}
