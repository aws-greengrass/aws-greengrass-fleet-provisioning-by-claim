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
import software.amazon.awssdk.iot.iotidentity.model.CreateCertificateFromCsrResponse;
import software.amazon.awssdk.iot.iotidentity.model.CreateKeysAndCertificateResponse;
import software.amazon.awssdk.iot.iotidentity.model.RegisterThingResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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

    // Optional Parameters
    static final String DEVICE_ID_PARAMETER_NAME = "deviceId";
    static final String TEMPLATE_PARAMETERS_PARAMETER_NAME = "templateParameters";
    static final String AWS_REGION_PARAMETER_NAME = "awsRegion";
    static final String IOT_CREDENTIAL_ENDPOINT_PARAMETER_NAME = "iotCredentialEndpoint";
    static final String IOT_ROLE_ALIAS_PARAMETER_NAME = "iotRoleAlias";
    static final String PROXY_URL_PARAMETER_NAME = "proxyUrl";
    static final String PROXY_USERNAME_PARAMETER_NAME = "proxyUsername";
    static final String PROXY_PASSWORD_PARAMETER_NAME = "proxyPassword";
    static final String CSR_PATH_PARAMETER_NAME = "csrPath";
    static final String CSR_PRIVATE_KEY_PATH_PARAMETER_NAME = "csrPrivateKeyPath";
    static final String CERT_PATH_PARAMETER_NAME = "certPath";
    static final String KEY_PATH_PARAMETER_NAME = "keyPath";

    static final String MISSING_REQUIRED_PARAMETERS_ERROR_FORMAT =
            "Required parameter %s missing for " + PLUGIN_NAME;

    static final String DEFAULT_CERT_FILE = "thingCert.crt";
    static final String DEFAULT_KEY_FILE = "privKey.key";

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
        String csrPath = parameterMap.get(CSR_PATH_PARAMETER_NAME) == null ? null
                : parameterMap.get(CSR_PATH_PARAMETER_NAME).toString();
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

            while (true) {
                try {
                    CompletableFuture<Boolean> connected = connection.connect();
                    FutureExceptionHandler.getFutureAfterCompletion(connected,
                            "Caught exception while establishing connection to AWS Iot");
                    break;
                } catch (RetryableProvisioningException e) {
                    logger.atInfo().log("Retrying MQTT connect which failed in provisioning plugin");
                    Thread.sleep(5_000);
                }
            }

            IotIdentityHelper iotIdentityHelper = iotIdentityHelperFactory.getInstance(connection);

            Path certificatePath = getPathOrDefaultFromRoot(parameterMap, CERT_PATH_PARAMETER_NAME, DEFAULT_CERT_FILE);
            Path privateKeyPath = getPathOrDefaultFromRoot(parameterMap, KEY_PATH_PARAMETER_NAME, DEFAULT_KEY_FILE);

            String certificateOwnershipToken;
            if (csrPath == null || Utils.isEmpty(csrPath)) {
                CreateKeysAndCertificateResponse response;
                response = FutureExceptionHandler.getFutureAfterCompletion(iotIdentityHelper.createKeysAndCertificate(),
                        "Caught exception during PublishCreateKeysAndCertificate");

                writeCertificate(response, certificatePath);
                writeKey(response, privateKeyPath);

                certificateOwnershipToken = response.certificateOwnershipToken;
            } else {
                Path csrFile = getPath(parameterMap, CSR_PATH_PARAMETER_NAME);
                String csr;
                try {
                    csr = new String(Files.readAllBytes(csrFile), StandardCharsets.UTF_8);
                } catch (IOException | SecurityException ex) {
                    logger.atError().setCause(ex).log("Caught exception while reading the CSR file");
                    throw new DeviceProvisioningRuntimeException(
                            "Failed to read CSR file " + csrFile.toAbsolutePath(), ex);
                }
                CreateCertificateFromCsrResponse response;
                response = FutureExceptionHandler.getFutureAfterCompletion(
                        iotIdentityHelper.createCertificateFromCsr(csr),
                        "Caught exception during PublishCreateCertificateFromCsr");

                Path csrPrivateKeyPath = getPath(parameterMap, CSR_PRIVATE_KEY_PATH_PARAMETER_NAME);
                writeCertificate(response, certificatePath);
                copyFile(csrPrivateKeyPath, privateKeyPath);
                setFilePermissions(csrPrivateKeyPath);

                certificateOwnershipToken = response.certificateOwnershipToken;
            }

            HashMap<String, String> templateParameterHashMap = new HashMap<>();
            if (templateParameters != null) {
                templateParameters.forEach((k, v) -> templateParameterHashMap.put(k, v.toString()));
            }
            Future<RegisterThingResponse> registerFuture = iotIdentityHelper
                    .registerThing(certificateOwnershipToken, templateName, templateParameterHashMap);
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
        if (!(parameterMap.get(CSR_PATH_PARAMETER_NAME) == null
                || Utils.isEmpty(parameterMap.get(CSR_PATH_PARAMETER_NAME).toString()))) {
            checkRequiredParameterPresent(parameterMap, errors, CSR_PRIVATE_KEY_PATH_PARAMETER_NAME);
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(errors.toString());
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

        Path certificatePath = getPathOrDefaultFromRoot(parameterMap, CERT_PATH_PARAMETER_NAME, DEFAULT_CERT_FILE);
        Path privateKeyPath = getPathOrDefaultFromRoot(parameterMap, KEY_PATH_PARAMETER_NAME, DEFAULT_KEY_FILE);

        SystemConfiguration systemConfiguration = SystemConfiguration.builder()
                .thingName(registerThingResponse.thingName)
                .privateKeyPath(privateKeyPath.toString())
                .certificateFilePath(certificatePath.toString())
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

    private static Path getRootPath(Map<String, Object> parameterMap) {
        return getPath(parameterMap, ROOT_PATH_PARAMETER_NAME);
    }

    private static Path getPath(Map<String, Object> parameterMap, String key) {
        if (parameterMap.get(key) == null) {
            throw new DeviceProvisioningRuntimeException(
                    String.format("Config parameter %s is missing", key));
        }
        return getPath(parameterMap.get(key).toString());
    }

    private static Path getPath(String path) {
        try {
            return Paths.get(path).normalize();
        } catch (InvalidPathException e) {
            throw new DeviceProvisioningRuntimeException(
                    String.format("Invalid path: %s", path), e);
        }
    }

    private static Path getPathOrDefaultFromRoot(Map<String, Object> parameterMap, String key, String defaultName) {
        if (parameterMap.get(key) == null) {
            // if not provided, result path relative to rootPath
            Path rootPath = getRootPath(parameterMap);
            try {
                return rootPath.resolve(defaultName);
            } catch (InvalidPathException e) {
                throw new DeviceProvisioningRuntimeException(
                        String.format("Invalid %s: %s. rootPath: %s",
                                key, defaultName, rootPath), e);
            }
        } else {
            return getPath(parameterMap.get(key).toString());
        }
    }

    private static void copyFile(Path src, Path dst) {
        try {
            createFile(dst);
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.atError().kv("src", src).kv("dst", dst)
                    .log("Caught exception while copying file");
            throw new DeviceProvisioningRuntimeException(
                    String.format("Failed to copy %s to %s", src, dst), e);
        }
    }

    private static void writeKey(CreateKeysAndCertificateResponse response, Path path) {
        try {
            writeFile(path, response.privateKey);
            setFilePermissions(path);
        } catch (IOException e) {
            logger.atError().log("Caught exception while writing certificate to file", e);
            throw new DeviceProvisioningRuntimeException("Failed to write certificate", e);
        }
    }

    private static void writeCertificate(CreateCertificateFromCsrResponse response, Path path) {
        writeCertificate(response.certificatePem, path);
    }

    private static void writeCertificate(CreateKeysAndCertificateResponse response, Path path) {
        writeCertificate(response.certificatePem, path);
    }

    private static void writeCertificate(String certificatePem, Path path) {
        try {
            writeFile(path, certificatePem);
            setFilePermissions(path);
        } catch (IOException e) {
            logger.atError().log("Caught exception while writing certificate to file", e);
            throw new DeviceProvisioningRuntimeException("Failed to write certificate", e);
        }
    }

    private static void writeFile(Path file, String content) throws IOException {
        createFile(file);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void createFile(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (Files.notExists(file)) {
            Files.createFile(file);
        }
    }

    private static void setFilePermissions(Path file) {
        try {
            Platform.getInstance().setPermissions(FileSystemPermission.builder()
                    .ownerRead(true)
                    .ownerWrite(true)
                    .build(), file);
        } catch (IOException e) {
            throw new DeviceProvisioningRuntimeException(
                    String.format("Failed to set permissions on file %s", file), e);
        }
    }
}
