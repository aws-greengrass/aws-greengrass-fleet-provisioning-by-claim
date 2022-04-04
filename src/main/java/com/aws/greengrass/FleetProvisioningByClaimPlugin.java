/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass;

import com.aws.greengrass.MqttConnectionHelper.MqttConnectionParameters.MqttConnectionParametersBuilder;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.provisioning.DeviceIdentityInterface;
import com.aws.greengrass.provisioning.ProvisionConfiguration;
import com.aws.greengrass.provisioning.ProvisionContext;
import com.aws.greengrass.provisioning.exceptions.RetryableProvisioningException;
import com.aws.greengrass.util.FileSystemPermission;
import com.aws.greengrass.util.Utils;
import com.aws.greengrass.util.platforms.Platform;
import software.amazon.awssdk.crt.CrtRuntimeException;
import software.amazon.awssdk.crt.http.HttpProxyOptions;
import software.amazon.awssdk.crt.io.ClientBootstrap;
import software.amazon.awssdk.crt.io.ClientTlsContext;
import software.amazon.awssdk.crt.io.EventLoopGroup;
import software.amazon.awssdk.crt.io.HostResolver;
import software.amazon.awssdk.crt.io.TlsContext;
import software.amazon.awssdk.crt.io.TlsContextOptions;
import software.amazon.awssdk.crt.mqtt.MqttClientConnection;
import software.amazon.awssdk.iot.iotidentity.model.CreateKeysAndCertificateResponse;
import software.amazon.awssdk.iot.iotidentity.model.RegisterThingResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static com.aws.greengrass.provisioning.ProvisionConfiguration.NucleusConfiguration;
import static com.aws.greengrass.provisioning.ProvisionConfiguration.SystemConfiguration;

public class FleetProvisioningByClaimPlugin implements DeviceIdentityInterface {

    static final String PLUGIN_NAME = "aws.greengrass.FleetProvisioningByClaim";
    private static final Logger logger = LogManager.getLogger(FleetProvisioningByClaimPlugin.class);

    // Required parameters
    static final String PROVISIONING_TEMPLATE_PARAMETER_NAME = "provisioningTemplate";
    static final String CLAIM_CERTIFICATE_PATH_PARAMETER_NAME = "claimCertificatePath";
    static final String CLAIM_CERTIFICATE_PRIVATE_KEY_PATH_PARAMETER_NAME = "claimCertificatePrivateKeyPath";
    static final String ROOT_CA_PATH_PARAMETER_NAME = "rootCaPath";
    static final String IOT_DATA_ENDPOINT_PARAMETER_NAME = "iotDataEndpoint";
    static final String ROOT_PATH_PARAMETER_NAME = "rootPath";
    static final String MQTT_PORT_PARAMETER_NAME = "mqttPort";

    // Optional Paramters
    static final String DEVICE_ID_PARAMETER_NAME = "deviceId";
    static final String TEMPLATE_PARAMETERS_PARAMETER_NAME = "templateParameters";
    static final String AWS_REGION_PARAMETER_NAME = "awsRegion";
    static final String IOT_CREDENTIAL_ENDPOINT_PARAMETER_NAME = "iotCredentialEndpoint";
    static final String IOT_ROLE_ALIAS_PARAMETER_NAME = "iotRoleAlias";
    static final String PROXY_URL_PARAMETER_NAME = "proxyUrl";
    static final String PROXY_USERNAME_PARAMETER_NAME = "proxyUsername";
    static final String PROXY_PASSWORD_PARAMETER_NAME = "proxyPassword";

    static final String MISSING_REQUIRED_PARAMETERS_ERROR_FORMAT =
            "Required parameter %s missing for " + PLUGIN_NAME;

    static final String DEVICE_CERTIFICATE_PATH_RELATIVE_TO_ROOT = "/thingCert.crt";
    static final String PRIVATE_KEY_PATH_RELATIVE_TO_ROOT = "/privKey.key";

    public static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("wind");

    private final IotIdentityHelperFactory iotIdentityHelperFactory;
    private final MqttConnectionHelper mqttConnectionHelper;

    public FleetProvisioningByClaimPlugin() {
        iotIdentityHelperFactory = new IotIdentityHelperFactory();
        mqttConnectionHelper = new MqttConnectionHelper();
    }

    FleetProvisioningByClaimPlugin(IotIdentityHelperFactory iotIdentityHelperFactory,
                                   MqttConnectionHelper mqttConnectionHelper) {
        this.iotIdentityHelperFactory = iotIdentityHelperFactory;
        this.mqttConnectionHelper = mqttConnectionHelper;
    }

    @Override
    public String name() {
        return PLUGIN_NAME;
    }

    @Override
    public ProvisionConfiguration updateIdentityConfiguration(ProvisionContext provisionContext)
            throws RetryableProvisioningException, InterruptedException {

        Map<String, Object> parameterMap = provisionContext.getParameterMap();
        validateParameters(parameterMap);

        String certPath = parameterMap.get(CLAIM_CERTIFICATE_PATH_PARAMETER_NAME).toString();
        String keyPath = parameterMap.get(CLAIM_CERTIFICATE_PRIVATE_KEY_PATH_PARAMETER_NAME).toString();
        String endpoint = parameterMap.get(IOT_DATA_ENDPOINT_PARAMETER_NAME).toString();
        Integer mqttPort = null;
        if (parameterMap.get(MQTT_PORT_PARAMETER_NAME) != null) {
            mqttPort = Integer.valueOf(parameterMap.get(MQTT_PORT_PARAMETER_NAME).toString());
        }
        String rootCaPath = parameterMap.get(ROOT_CA_PATH_PARAMETER_NAME).toString();
        String templateName = parameterMap.get(PROVISIONING_TEMPLATE_PARAMETER_NAME).toString();
        String clientId = parameterMap.get(DEVICE_ID_PARAMETER_NAME) == null ? UUID.randomUUID().toString()
                : parameterMap.get(DEVICE_ID_PARAMETER_NAME).toString();
        String proxyUrl = parameterMap.get(PROXY_URL_PARAMETER_NAME) == null ? null
                : parameterMap.get(PROXY_URL_PARAMETER_NAME).toString();
        String proxyUserName = parameterMap.get(PROXY_USERNAME_PARAMETER_NAME) == null ? null
                : parameterMap.get(PROXY_USERNAME_PARAMETER_NAME).toString();
        String proxyPassword = parameterMap.get(PROXY_PASSWORD_PARAMETER_NAME) == null ? null
                : parameterMap.get(PROXY_PASSWORD_PARAMETER_NAME).toString();
        TlsContext proxyTlsContext = new ClientTlsContext(getTlsContextOptions(rootCaPath));
        HttpProxyOptions httpProxyOptions = MqttConnectionHelper.getHttpProxyOptions(proxyUrl, proxyUserName,
                proxyPassword, proxyTlsContext);
        Map<String, Object> templateParameters =
                (Map<String, Object>) parameterMap.get(TEMPLATE_PARAMETERS_PARAMETER_NAME);

        MqttConnectionParametersBuilder mqttParameterBuilder =
                MqttConnectionHelper.MqttConnectionParameters.builder()
                        .certPath(certPath)
                        .keyPath(keyPath)
                        .rootCaPath(rootCaPath)
                        .endpoint(endpoint)
                        .clientId(clientId)
                        .httpProxyOptions(httpProxyOptions)
                        .mqttPort(mqttPort);


        try (EventLoopGroup eventLoopGroup = new EventLoopGroup(1);
             HostResolver resolver = new HostResolver(eventLoopGroup);
             ClientBootstrap clientBootstrap = new ClientBootstrap(eventLoopGroup, resolver);
             MqttClientConnection connection = mqttConnectionHelper
                .getMqttConnection(mqttParameterBuilder.clientBootstrap(clientBootstrap).build())) {

            CompletableFuture<Boolean> connected = connection.connect();
            FutureExceptionHandler.getFutureAfterCompletion(connected,
                    "Caught exception while establishing connection to AWS Iot");

            IotIdentityHelper iotIdentityHelper = iotIdentityHelperFactory.getInstance(connection);
            CreateKeysAndCertificateResponse response;
            response = FutureExceptionHandler.getFutureAfterCompletion(iotIdentityHelper.createKeysAndCertificate(),
                    "Caught exception during PublishCreateKeysAndCertificate");

            writeCertificateAndKeyToPath(response, parameterMap.get(ROOT_PATH_PARAMETER_NAME).toString());


            HashMap<String, String> templateParameterHashMap = new HashMap<>();
            if (templateParameters != null) {
                templateParameters.forEach((k, v) -> templateParameterHashMap.put(k, v.toString()));
            }
            Future<RegisterThingResponse> registerFuture = iotIdentityHelper
                    .registerThing(response.certificateOwnershipToken, templateName, templateParameterHashMap);
            RegisterThingResponse registerThingResponse = FutureExceptionHandler
                    .getFutureAfterCompletion(registerFuture, "Caught exception during registering Iot Thing");
            CompletableFuture<Void> disconnected = connection.disconnect();
            FutureExceptionHandler.getFutureAfterCompletion(disconnected,
                    "Caught exception while disconnecting");

            return createProvisioningConfiguration(parameterMap, registerThingResponse);
        } catch (CrtRuntimeException | InterruptedException ex) {
            logger.atError().setCause(ex).log("Exception encountered while getting device identity information");
            throw ex;
        } finally {
            proxyTlsContext.close();
        }
    }

    private static TlsContextOptions getTlsContextOptions(String rootCaPath) {
        return Utils.isNotEmpty(rootCaPath)
                ? TlsContextOptions.createDefaultClient().withCertificateAuthorityFromPath(null, rootCaPath)
                : TlsContextOptions.createDefaultClient();
    }

    private void validateParameters(Map<String, Object> parameterMap) {
        logger.atDebug().kv("parameters", parameterMap.toString()).log("The parameter map for plugin is ");
        List<String> errors = new ArrayList<>();
        checkRequiredParameterPresent(parameterMap, errors, PROVISIONING_TEMPLATE_PARAMETER_NAME);
        checkRequiredParameterPresent(parameterMap, errors, CLAIM_CERTIFICATE_PATH_PARAMETER_NAME);
        checkRequiredParameterPresent(parameterMap, errors, CLAIM_CERTIFICATE_PRIVATE_KEY_PATH_PARAMETER_NAME);
        checkRequiredParameterPresent(parameterMap, errors, ROOT_CA_PATH_PARAMETER_NAME);
        checkRequiredParameterPresent(parameterMap, errors, IOT_DATA_ENDPOINT_PARAMETER_NAME);
        checkRequiredParameterPresent(parameterMap, errors, PROVISIONING_TEMPLATE_PARAMETER_NAME);
        checkRequiredParameterPresent(parameterMap, errors, ROOT_PATH_PARAMETER_NAME);

        if (!errors.isEmpty()) {
            throw new RuntimeException(errors.toString());
        }
    }

    private ProvisionConfiguration createProvisioningConfiguration(Map<String, Object> parameterMap,
                                                             RegisterThingResponse registerThingResponse) {

        NucleusConfiguration nucleusConfiguration = NucleusConfiguration.builder()
                .iotDataEndpoint(parameterMap.get(IOT_DATA_ENDPOINT_PARAMETER_NAME).toString())
                .build();
        // optional parameters
        Object parameterValue = parameterMap.get(AWS_REGION_PARAMETER_NAME);
        if (parameterValue != null && !Utils.isEmpty(parameterValue.toString())) {
            nucleusConfiguration.setAwsRegion(parameterValue.toString());
        }
        parameterValue = parameterMap.get(IOT_CREDENTIAL_ENDPOINT_PARAMETER_NAME);
        if (parameterValue != null && !Utils.isEmpty(parameterValue.toString())) {
            nucleusConfiguration.setIotCredentialsEndpoint(parameterValue.toString());
        }
        parameterValue = parameterMap.get(IOT_ROLE_ALIAS_PARAMETER_NAME);
        if (parameterValue != null && !Utils.isEmpty(parameterValue.toString())) {
            nucleusConfiguration.setIotRoleAlias(parameterValue.toString());
        }

        SystemConfiguration systemConfiguration = SystemConfiguration.builder()
                .thingName(registerThingResponse.thingName)
                .privateKeyPath(Paths.get(parameterMap.get(ROOT_PATH_PARAMETER_NAME).toString(),
                        PRIVATE_KEY_PATH_RELATIVE_TO_ROOT).normalize().toString())
                .certificateFilePath(Paths.get(parameterMap.get(ROOT_PATH_PARAMETER_NAME).toString(),
                        DEVICE_CERTIFICATE_PATH_RELATIVE_TO_ROOT).normalize().toString())
                .rootCAPath(parameterMap.get(ROOT_CA_PATH_PARAMETER_NAME).toString())
                .build();

        return ProvisionConfiguration.builder()
                .systemConfiguration(systemConfiguration)
                .nucleusConfiguration(nucleusConfiguration)
                .build();
    }

    private void checkRequiredParameterPresent(Map<String, Object> parameterMap, List<String> errors,
                                               String parameterName) {
        if (!parameterMap.containsKey(parameterName)
                || parameterMap.get(parameterName) == null
                || Utils.isEmpty(parameterMap.get(parameterName).toString())) {
            errors.add(String.format(MISSING_REQUIRED_PARAMETERS_ERROR_FORMAT,
                    parameterName));
        }
    }

    private void writeCertificateAndKeyToPath(CreateKeysAndCertificateResponse response, String rootPath) {
        try {
            Path certPath = Paths.get(rootPath, DEVICE_CERTIFICATE_PATH_RELATIVE_TO_ROOT);
            if (Files.notExists(certPath)) {
                Files.createFile(certPath);
            }
            Files.write(certPath, response.certificatePem.getBytes(StandardCharsets.UTF_8));
            Platform.getInstance().setPermissions(FileSystemPermission.builder().ownerRead(true).build(), certPath);

            Path keyPath = Paths.get(rootPath, PRIVATE_KEY_PATH_RELATIVE_TO_ROOT);
            if (Files.notExists(keyPath)) {
                Files.createFile(keyPath);
            }
            Files.write(keyPath, response.privateKey.getBytes(StandardCharsets.UTF_8));
            Platform.getInstance().setPermissions(FileSystemPermission.builder().ownerRead(true).build(), keyPath);
        } catch (IOException e) {
            logger.atError().log("Caught exception while writing certificate and private key to file");
            throw new RuntimeException(e);
        }
    }
}
