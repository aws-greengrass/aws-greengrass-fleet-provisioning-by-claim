/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass;

public class DeviceProvisioningRuntimeException extends RuntimeException {
    static final long serialVersionUID = -7034897190745766939L;

    public DeviceProvisioningRuntimeException(String message) {
        super(message);
    }

    public DeviceProvisioningRuntimeException(Throwable cause) {
        super(cause);
    }

    public DeviceProvisioningRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
