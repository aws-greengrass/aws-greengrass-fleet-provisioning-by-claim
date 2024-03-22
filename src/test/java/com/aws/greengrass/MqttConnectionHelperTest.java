/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass;

import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.crt.io.ClientBootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;


@ExtendWith({MockitoExtension.class, GGExtension.class})
public class MqttConnectionHelperTest {

    private static final String MOCK_IOT_DATA_ENDPOINT = "MOCK_IOT_DATA_ENDPOINT";
    private static final String MOCK_CLIENT_ID = "MOCK_CLIENT_ID";
    private static final Integer MOCK_PORT_NUMBER = 8883;

    @TempDir
    Path rootDir;
    Path claimCertificatePath;
    Path privateKeyPath;
    Path rootCAPath;
    private final ClientBootstrap mockClientBootstrap = new ClientBootstrap(null, null);
    @BeforeEach
    public void setup() throws IOException {
        claimCertificatePath = rootDir.resolve("claimCert.crt");
        Files.createFile(claimCertificatePath);
        privateKeyPath = rootDir.resolve("privateKey.key");
        Files.createFile(privateKeyPath);
        rootCAPath = rootDir.resolve("rootCA.pem");
        Files.createFile(rootCAPath);
    }

    @Test
    public void GIVEN_MQTT_port_number_THEN_sdk_successfully_invoke_withPort_method(){
        MqttConnectionHelper mockmqttConnectionHelper = new MqttConnectionHelper();
        MqttConnectionHelper.MqttConnectionParameters parameters = createMqttConnectionParams();
        assertDoesNotThrow(() -> mockmqttConnectionHelper.getMqttConnection(parameters));
    }
    private MqttConnectionHelper.MqttConnectionParameters createMqttConnectionParams() {
        return new MqttConnectionHelper.MqttConnectionParameters(
                claimCertificatePath.toString(),
                privateKeyPath.toString(),
                rootCAPath.toString(),
                MOCK_IOT_DATA_ENDPOINT,
                MOCK_CLIENT_ID,
                MOCK_PORT_NUMBER,
                mockClientBootstrap,
                null
        );
    }
}
