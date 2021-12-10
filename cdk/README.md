# CDK project for a sample AppConfig environment

This CDK project provisions a sample environment to test the Quarkus Microprofile ConfigSource against.

It will pick up a AWS_ACCESS_KEY and AWS_SECRET_KEY as configured currently  configured in your shell.

The following resources are defined in [./lib/app-config-stack.ts](./lib/app-config-stack.ts):

* An AppConfig Application called `ConfigSourceDemo`
* A single AppConfig Environment called `Sandbox`
* A ConfigurationProfile called `json-profile`
* A HostedConfigurationVersion called for `json-profile`
* A DeploymentStrategy called `Demo Strategy`
* A Deployment which deploys the HostedConfigurationVersion created or updated by the stack in the Environment using the custom DeploymentStrategy 

Additionally, the contents of the files [config-v1.json](config-v1.json) and [config-v2.json](config-v2.json) in this directory are read into variables. Those variables are used in the HostedConfigurationVersion.

## How to rollout a different configuration

Change line 33 to use one of the two file contents setup in lines 24/25 and run `cdk deploy` afterwards.

## What happens when the stack is deployed

Each time you run `cdk deploy` the HostedConfigurationVersion resource (on line 27 in [./lib/app-config-stack.ts](./lib/app-config-stack.ts)) will check if the content has changed and a new version must be created under the ConfigurationProfile. If this is the case, a Deployment will run, as referenced configurationVersion on line 49 has changed. The custom DeploymentStrategy will complete the deployment in 1 minute to avoid long wait times. This is a very straight-forward setup for demo purposes.

## Useful commands

 * `npm run build`   compile typescript to js
 * `npm run watch`   watch for changes and compile
 * `npm run test`    perform the jest unit tests
 * `cdk deploy`      deploy this stack to your default AWS account/region
 * `cdk diff`        compare deployed stack with current state
 * `cdk synth`       emits the synthesized CloudFormation template
