/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass;

import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.provisioning.exceptions.RetryableProvisioningException;
import software.amazon.awssdk.crt.mqtt.MqttException;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class FutureExceptionHandler {
    public static final int AWS_IOT_DEFAULT_TIMEOUT_SECONDS = 30;

    private static final Logger logger = LogManager.getLogger(FutureExceptionHandler.class);

    // For PMD warning
    private FutureExceptionHandler() {

    }

    /**
     * Gets the result from future after completion or throws appropriate exception.
     * @param future {@link Future}
     * @param errorMessage error message to be shown when future completed exceptionally
     * @param <T> The return type of the Future
     * @return The result of the future
     * @throws InterruptedException {@link InterruptedException}
     * @throws RetryableProvisioningException {@link RetryableProvisioningException}
     */
    public static <T> T getFutureAfterCompletion(Future<T> future, String... errorMessage)
            throws InterruptedException, RetryableProvisioningException {
        return getFutureAfterCompletion(future, AWS_IOT_DEFAULT_TIMEOUT_SECONDS, errorMessage);
    }

    /**
     * Gets the result from future after completion or throws appropriate exception.
     * @param future {@link Future}
     * @param timeout the time to wait for future to complete
     * @param errorMessage error message to be shown when future completed exceptionally
     * @param <T> The return type of the Future
     * @return The result of the future
     * @throws InterruptedException when thread is interrupted
     * @throws RetryableProvisioningException when retryable error happens like timeout
     * @throws RuntimeException when any other error happens
     */
    @SuppressWarnings("PMD.PreserveStackTrace")
    public static <T> T getFutureAfterCompletion(Future<T> future, int timeout, String... errorMessage)
            throws InterruptedException, RetryableProvisioningException {
        try {
            return future.get(timeout, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            try {
                unwrapExecutionException(e);
                // This code will not be reached but compiler complains about return value
                throw new RuntimeException(e);
            } catch (TimeoutException e1) {
                logger.atWarn().setCause(e1).kv("retryable", true).log(errorMessage);
                throw new RetryableProvisioningException(e1);
            } catch (ExecutionException e1) {
                logger.atError().setCause(e1).log(errorMessage);
                throw new RuntimeException(e1.getCause());
            }
        } catch (TimeoutException e1) {
            logger.atWarn().setCause(e1).kv("retryable", true).log(errorMessage);
            throw new RetryableProvisioningException(e1);
        }
    }

    @SuppressWarnings("PMD.CollapsibleIfStatements")
    private static void unwrapExecutionException(ExecutionException e)
            throws TimeoutException, InterruptedException, ExecutionException {
        Throwable cause = e.getCause();
        if (cause instanceof MqttException) {
            if (cause.getMessage() != null && cause.getMessage().contains("operation timed out")) {
                throw new TimeoutException(cause.getMessage());
            }
        }
        if (cause instanceof TimeoutException) {
            throw (TimeoutException) cause;
        }
        if (cause instanceof InterruptedException) {
            throw (InterruptedException) cause;
        }
        throw e;
    }
}
