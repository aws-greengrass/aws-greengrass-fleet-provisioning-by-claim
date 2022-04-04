/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass;

import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.ProxyUtils;
import com.aws.greengrass.util.Utils;
import lombok.Builder;
import lombok.Getter;
import software.amazon.awssdk.crt.CRT;
import software.amazon.awssdk.crt.http.HttpProxyOptions;
import software.amazon.awssdk.crt.io.ClientBootstrap;
import software.amazon.awssdk.crt.io.TlsContext;
import software.amazon.awssdk.crt.mqtt.MqttClientConnection;
import software.amazon.awssdk.crt.mqtt.MqttClientConnectionEvents;
import software.amazon.awssdk.iot.AwsIotMqttConnectionBuilder;

import java.net.URI;
import javax.annotation.Nullable;

public class MqttConnectionHelper {

    private final Logger logger = LogManager.getLogger(MqttConnectionHelper.class);

    private final MqttClientConnectionEvents callbacks = new MqttClientConnectionEvents() {
        @Override
        public void onConnectionInterrupted(int errorCode) {
            if (errorCode != 0) {
                logger.atWarn().kv("errorCode", CRT.awsErrorString(errorCode))
                        .log("Connection interrupted: ");
            }
        }

        @Override
        public void onConnectionResumed(boolean sessionPresent) {
            logger.atInfo().kv("existingSession", sessionPresent)
                    .log("Connection resumed: ");
        }
    };

    /**
     * Create mqtt connection using the given certificates, and endpoint.
     * @param mqttConnectionParameters {@link MqttConnectionParameters}
     * @return {@link MqttClientConnection}
     */
    public MqttClientConnection getMqttConnection(MqttConnectionParameters mqttConnectionParameters) {
        AwsIotMqttConnectionBuilder builder =
                AwsIotMqttConnectionBuilder.newMtlsBuilderFromPath(mqttConnectionParameters.getCertPath(),
                        mqttConnectionParameters.getKeyPath())
                .withCertificateAuthorityFromPath(null, mqttConnectionParameters.getRootCaPath())
                .withEndpoint(mqttConnectionParameters.getEndpoint())
                .withClientId(mqttConnectionParameters.getClientId())
                .withCleanSession(true)
                .withBootstrap(mqttConnectionParameters.getClientBootstrap())
                .withConnectionEventCallbacks(callbacks);

        if (mqttConnectionParameters.getMqttPort() != null) {
            builder.withPort(mqttConnectionParameters.getMqttPort().shortValue());
        }
        if (mqttConnectionParameters.getHttpProxyOptions() != null) {
            builder.withHttpProxyOptions(mqttConnectionParameters.getHttpProxyOptions());
        }
        return builder.build();
    }


    /**
     * Creates {@link HttpProxyOptions} from given proxy url, username and password.
     * @param proxyUrl The proxyUrl of the format scheme://userinfo@host:port.
     * @param proxyUserName username for proxy. It will be ignored if proxyUrl has username info
     * @param proxyPassword password for proxy. It will be ignored if the proxyUrl has password info
     * @param tlsContext optional tls context for HTTPS proxy.
     * @return {@link HttpProxyOptions}
     */
    @Nullable
    public static HttpProxyOptions getHttpProxyOptions(String proxyUrl, String proxyUserName, String proxyPassword,
                                                       @Nullable TlsContext tlsContext) {
        if (Utils.isEmpty(proxyUrl)) {
            return null;
        }

        HttpProxyOptions httpProxyOptions = new HttpProxyOptions();
        httpProxyOptions.setHost(ProxyUtils.getHostFromProxyUrl(proxyUrl));
        httpProxyOptions.setPort(ProxyUtils.getPortFromProxyUrl(proxyUrl));

        if ("https".equalsIgnoreCase(getSchemeFromProxyUrl(proxyUrl))) {
            httpProxyOptions.setTlsContext(tlsContext);
        }

        String proxyUsername = ProxyUtils.getProxyUsername(proxyUrl, proxyUserName);
        if (Utils.isNotEmpty(proxyUsername)) {
            httpProxyOptions.setAuthorizationType(HttpProxyOptions.HttpProxyAuthorizationType.Basic);
            httpProxyOptions.setAuthorizationUsername(proxyUsername);
            httpProxyOptions.setAuthorizationPassword(ProxyUtils.getProxyPassword(proxyUrl, proxyPassword));
        }

        return httpProxyOptions;
    }

    private static String getSchemeFromProxyUrl(String url) {
        return URI.create(url).getScheme();
    }

    @Builder
    @Getter
    public static class MqttConnectionParameters {
        private String certPath;
        private String keyPath;
        private String rootCaPath;
        private String endpoint;
        private String clientId;
        private Integer mqttPort;
        private ClientBootstrap clientBootstrap;
        private HttpProxyOptions httpProxyOptions;
    }
}
