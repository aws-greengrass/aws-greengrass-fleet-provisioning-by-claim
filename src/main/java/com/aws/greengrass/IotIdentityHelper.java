/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass;

import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.provisioning.exceptions.RetryableProvisioningException;
import software.amazon.awssdk.crt.mqtt.MqttClientConnection;
import software.amazon.awssdk.crt.mqtt.QualityOfService;
import software.amazon.awssdk.iot.iotidentity.IotIdentityClient;
import software.amazon.awssdk.iot.iotidentity.model.CreateKeysAndCertificateRequest;
import software.amazon.awssdk.iot.iotidentity.model.CreateKeysAndCertificateResponse;
import software.amazon.awssdk.iot.iotidentity.model.CreateKeysAndCertificateSubscriptionRequest;
import software.amazon.awssdk.iot.iotidentity.model.RegisterThingRequest;
import software.amazon.awssdk.iot.iotidentity.model.RegisterThingResponse;
import software.amazon.awssdk.iot.iotidentity.model.RegisterThingSubscriptionRequest;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static com.aws.greengrass.FutureExceptionHandler.AWS_IOT_DEFAULT_TIMEOUT_SECONDS;

@SuppressWarnings("PMD.LooseCoupling")
public class IotIdentityHelper {
    
    private static final Logger logger = LogManager.getLogger(IotIdentityHelper.class);

    private final IotIdentityClient iotIdentityClient;

    public IotIdentityHelper(MqttClientConnection connection) {
        this.iotIdentityClient = new IotIdentityClient(connection);
    }

    // For unit testing
    IotIdentityHelper(IotIdentityClient iotIdentityClient) {
        this.iotIdentityClient = iotIdentityClient;
    }

    /**
     * Creates Keys and certificate in AWS Iot and returns them back.
     * @return {@link CreateKeysAndCertificateResponse}
     * @throws InterruptedException on thread interruption
     * @throws RetryableProvisioningException on transient errors like timeout
     */
    public Future<CreateKeysAndCertificateResponse> createKeysAndCertificate() throws InterruptedException,
            RetryableProvisioningException {
        return createKeysAndCertificate(AWS_IOT_DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Creates Keys and certificate in AWS Iot and returns them back.
     * @param timeout iot connection timeout
     * @return {@link CreateKeysAndCertificateResponse}
     * @throws InterruptedException on thread interruption
     * @throws RetryableProvisioningException on transient errors like timeout
     */
    public Future<CreateKeysAndCertificateResponse> createKeysAndCertificate(int timeout) throws InterruptedException,
            RetryableProvisioningException {

        CompletableFuture<CreateKeysAndCertificateResponse> createFuture = new CompletableFuture<>();
        CreateKeysAndCertificateSubscriptionRequest createKeysAndCertificateSubscriptionRequest =
                new CreateKeysAndCertificateSubscriptionRequest();
        CompletableFuture<Integer> keysSubscribedAccepted =
                iotIdentityClient.SubscribeToCreateKeysAndCertificateAccepted(
                createKeysAndCertificateSubscriptionRequest,
                QualityOfService.AT_LEAST_ONCE, createFuture::complete);
        FutureExceptionHandler.getFutureAfterCompletion(keysSubscribedAccepted, timeout);

        logger.atInfo().log("Subscribed to CreateKeysAndCertificateAccepted");

        CompletableFuture<Integer> keysSubscribedRejected =
                iotIdentityClient.SubscribeToCreateKeysAndCertificateRejected(
                    createKeysAndCertificateSubscriptionRequest,
                    QualityOfService.AT_LEAST_ONCE,
                    (errorResponse) -> {
                        RuntimeException e = new RuntimeException(errorResponse.errorMessage);
                        createFuture.completeExceptionally(e);
                    });
        FutureExceptionHandler.getFutureAfterCompletion(keysSubscribedRejected, timeout);
        logger.atInfo().log("Subscribed to CreateKeysAndCertificateRejected");

        CompletableFuture<Integer> publishKeys = iotIdentityClient.PublishCreateKeysAndCertificate(
                new CreateKeysAndCertificateRequest(),
                QualityOfService.AT_LEAST_ONCE);
        FutureExceptionHandler.getFutureAfterCompletion(publishKeys);

        logger.atInfo().log("Published to CreateKeysAndCertificate");
        return createFuture;
    }

    /**
     * Register thing in Aws IoT.
     * @param certificateOwnershipToken CertificateOwnerShipToken received in {@link CreateKeysAndCertificateResponse}
     * @param templateName FleetProvisioning template name
     * @param templateParameters Template parameters
     * @return {@link RegisterThingResponse}
     * @throws InterruptedException on thread interruption
     * @throws RetryableProvisioningException on transient errors like timeout
     */
    public Future<RegisterThingResponse> registerThing(String certificateOwnershipToken, String templateName,
                                                       HashMap<String, String> templateParameters)
            throws InterruptedException, RetryableProvisioningException {
        return registerThing(certificateOwnershipToken, templateName, templateParameters,
                AWS_IOT_DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Register thing in Aws IoT.
     * @param certificateOwnershipToken CertificateOwnerShipToken received in {@link CreateKeysAndCertificateResponse}
     * @param templateName FleetProvisioning template name
     * @param templateParameters Template parameters
     * @param iotTimeout Iot connection timeout
     * @return {@link RegisterThingResponse}
     * @throws InterruptedException on thread interruption
     * @throws RetryableProvisioningException on transient errors like timeout
     */
    public Future<RegisterThingResponse> registerThing(String certificateOwnershipToken, String templateName,
                              HashMap<String, String> templateParameters, int iotTimeout) throws InterruptedException,
            RetryableProvisioningException {

        CompletableFuture<RegisterThingResponse> registerFuture = new CompletableFuture<>();
        RegisterThingSubscriptionRequest registerThingSubscriptionRequest = new RegisterThingSubscriptionRequest();
        registerThingSubscriptionRequest.templateName = templateName;

        CompletableFuture<Integer> subscribedRegisterAccepted = iotIdentityClient.SubscribeToRegisterThingAccepted(
                registerThingSubscriptionRequest,
                QualityOfService.AT_LEAST_ONCE,
                (response) -> {
                    logger.atInfo().log("Received register thing response");
                    registerFuture.complete(response);
                }, registerFuture::completeExceptionally);
        FutureExceptionHandler.getFutureAfterCompletion(subscribedRegisterAccepted, iotTimeout);
        logger.atInfo().log("Subscribed to SubscribeToRegisterThingAccepted");

        CompletableFuture<Integer> subscribedRegisterRejected = iotIdentityClient.SubscribeToRegisterThingRejected(
                registerThingSubscriptionRequest,
                QualityOfService.AT_LEAST_ONCE,
                (errorResponse) -> {
                    RuntimeException e = new RuntimeException(errorResponse.errorMessage);
                    registerFuture.completeExceptionally(e);
                }, registerFuture::completeExceptionally);
        FutureExceptionHandler.getFutureAfterCompletion(subscribedRegisterRejected, iotTimeout);
        logger.atInfo().log("Subscribed to SubscribeToRegisterThingRejected");

        RegisterThingRequest registerThingRequest = new RegisterThingRequest();
        registerThingRequest.certificateOwnershipToken = certificateOwnershipToken;
        registerThingRequest.templateName = templateName;

        if (templateParameters != null && !templateParameters.isEmpty()) {
            registerThingRequest.parameters = templateParameters;
        }

        // Waiting on this response, leads to freezing of connection.
        iotIdentityClient.PublishRegisterThing(
                registerThingRequest,
                QualityOfService.AT_LEAST_ONCE);

        return registerFuture;
    }
}
