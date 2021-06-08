/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass;

import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.ProxyUtils;
import com.aws.greengrass.util.Utils;
import software.amazon.awssdk.crt.CRT;
import software.amazon.awssdk.crt.http.HttpProxyOptions;
import software.amazon.awssdk.crt.io.ClientBootstrap;
import software.amazon.awssdk.crt.mqtt.MqttClientConnection;
import software.amazon.awssdk.crt.mqtt.MqttClientConnectionEvents;
import software.amazon.awssdk.iot.AwsIotMqttConnectionBuilder;

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
     * @param certPath Path of the x509 certificate to authenticate with AWS IoT
     * @param keyPath Path of the private key for the certificate
     * @param rootCaPath Path of the root CA
     * @param endpoint AWS IoT data endpoint for the account
     * @param clientId Client Id for this connection
     * @param clientBootstrap {@link ClientBootstrap}
     * @param httpProxyOptions {@link HttpProxyOptions}
     * @return {@link MqttClientConnection}
     */
    public MqttClientConnection getMqttConnection(String certPath, String keyPath, String rootCaPath, String endpoint,
             String clientId, ClientBootstrap clientBootstrap, HttpProxyOptions httpProxyOptions) {
        AwsIotMqttConnectionBuilder builder = AwsIotMqttConnectionBuilder.newMtlsBuilderFromPath(certPath, keyPath)
                .withCertificateAuthorityFromPath(null, rootCaPath)
                .withEndpoint(endpoint)
                .withClientId(clientId)
                .withCleanSession(true)
                .withBootstrap(clientBootstrap)
                .withConnectionEventCallbacks(callbacks);

        if (httpProxyOptions != null) {
            builder.withHttpProxyOptions(httpProxyOptions);
        }
        return builder.build();
    }


    /**
     * Creates {@link HttpProxyOptions} from given proxy url, username and password.
     * @param proxyUrl The proxyUrl of the format scheme://userinfo@host:port.
     * @param proxyUserName username for proxy. It will be ignored if proxyUrl has username info
     * @param proxyPassword password for proxy. It will be ignored if the proxyUrl has password info
     * @return {@link HttpProxyOptions}
     */
    @Nullable
    public static HttpProxyOptions getHttpProxyOptions(String proxyUrl, String proxyUserName, String proxyPassword) {
        if (Utils.isEmpty(proxyUrl)) {
            return null;
        }

        HttpProxyOptions httpProxyOptions = new HttpProxyOptions();
        httpProxyOptions.setHost(ProxyUtils.getHostFromProxyUrl(proxyUrl));
        httpProxyOptions.setPort(ProxyUtils.getPortFromProxyUrl(proxyUrl));

        String proxyUsername = ProxyUtils.getProxyUsername(proxyUrl, proxyUserName);
        if (Utils.isNotEmpty(proxyUsername)) {
            httpProxyOptions.setAuthorizationType(HttpProxyOptions.HttpProxyAuthorizationType.Basic);
            httpProxyOptions.setAuthorizationUsername(proxyUsername);
            httpProxyOptions.setAuthorizationPassword(ProxyUtils.getProxyPassword(proxyUrl, proxyPassword));
        }

        return httpProxyOptions;
    }
}
