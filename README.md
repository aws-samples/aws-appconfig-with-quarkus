# Using AWS AppConfig in a custom MicroProfile ConfigSource

This repository shows how to natively extend Quarkus with a custom ConfigSource to use AWS AppConfig values when injecting config properties with @ConfigProperty.

This means that a developer can reference AppConfig properties like this:

```java
@ConfigProperty(name = "myProp")
String myPropValue;
```

The demonstrated approach has several benefits:

* The existing standard for injecting configuration in Quarkus can be used directly with AppConfig.
* Configuration fetched from AppConfig has a configurable precedence. It can be used to e.g. override selected properties of the packaged application.properties file.
* AppConfig access code is centralized within the application behind a standardized interface which makes the application easier to port.

## How it works

To achieve this we create a class which implements `org.eclipse.microprofile.config.spi.ConfigSource` and register this clas in `META-INF/services/org.eclipse.microprofile.config.spi.ConfigSource`. In the implementation of the class we periodically pull for new ConfigurationVersions using the AWS SDK for Java v2. If we receive a new configuration, the new configuration is read and the new values to properties are applied. 

Navigate to [AwsAppConfigSource.java](src/main/java/com/amazon/sample/appconfig/quarkus/sample/appconfig/quarkus/config/source/AwsAppConfigSource.java) learn what each overridden method accomplishes in context of the actual code.

**Important:** The RESTEasy implementation used by Quarkus scopes the lifecycle of JAX-RS resource classes (those annotated with `@Path`) to application scoped instead of the default request scoped outlined in the JAX-RS spec. This would effectively prevent us from rolling out new config values and directly use them in a running Quarkus application, as the resource class s only instantiated once. To circumvent this we wrap our ConfigProperty variable in a `javax.inject.Provider` and call `javax.inject.Provider#get()` each time to fetch the current, up-to-date value as stored in the ConfigSource.  

## The sample use case

Create an AWS AppConfig sample environment. Testing works best with the predefined environment in the `cdk` folder. A [README.md](./cdk/README.md) for setting up the environment is included. 

## Testing the example

Firstly, setup the AWS AppConfig test environment from the CDK app. **This is required as Quarkus will execute the ConfigSource code during build augmentation.** Next ensure you have your local environment configured with a AWS credentials which allow interactions with AWS AppConfig. Also ensure Maven (`mvn`) is on your path or install maven wrapper. 

Be aware that the sample source code relies on hardcoded AWS resource names and is tied to the provided CDK app!

Run the application locally:
```bash
mvn clean package
java -jar target/quarkus-app/quarkus-run.jar 
```

You will see rather verbose log output which enables you to inspect what the sample application is doing.

Test drive the API endpoint:
```bash
curl localhost:8080/loan/annuity -d '{"amountInEur":1000, "durationInYears":1}' -H "Content-Type: application/json" 
# => {"annuity":1150.0000000000007}
```

Now update the configuration in AppConfig by updating the CDK app. In file cdk/lib/app-config-stack.ts change line 33 from
```typescript
// Change this line and run 'cdk deploy' to update the config
content: initialConfigContent,
```
to
```typescript
// Change this line and run 'cdk deploy' to update the config
content: updatedConfigContent,
```
and redeploy.

Observe over the next minute how AppConfig rolls out the change to the smaple app — this should not take longer than 2 intervals of 30 seconds in which the sample application checks for new configuration versios.

Test drive the API endpoint again and observe the new config value being used:
```bash
curl localhost:8080/loan/annuity -d '{"amountInEur":1000, "durationInYears":1}' -H "Content-Type: application/json" 
# => {"annuity":1009.9999999999991}%    
```

## Contributions

To contribute with improvements and bug fixes, see [CONTRIBUTING](CONTRIBUTING.md).

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This library is licensed under the MIT-0 License. See the LICENSE file.