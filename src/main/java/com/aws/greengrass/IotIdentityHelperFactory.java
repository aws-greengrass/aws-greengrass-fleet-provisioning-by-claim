/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass;

import software.amazon.awssdk.crt.mqtt.MqttClientConnection;

public class IotIdentityHelperFactory {
    IotIdentityHelper iotIdentityHelper;

    /**
     * Provides a singleton instance of {@link IotIdentityHelper}.
     * @param connection Mqtt client connection to AWS IoT
     * @return {@link IotIdentityHelper}
     */
    public IotIdentityHelper getInstance(MqttClientConnection connection) {
        if (iotIdentityHelper == null) {
            iotIdentityHelper = new IotIdentityHelper(connection);
        }
        return iotIdentityHelper;
    }
}
