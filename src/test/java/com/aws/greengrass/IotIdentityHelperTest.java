/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass;

import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.crt.mqtt.QualityOfService;
import software.amazon.awssdk.iot.iotidentity.IotIdentityClient;
import software.amazon.awssdk.iot.iotidentity.model.CreateKeysAndCertificateResponse;
import software.amazon.awssdk.iot.iotidentity.model.CreateCertificateFromCsrResponse;
import software.amazon.awssdk.iot.iotidentity.model.RegisterThingRequest;
import software.amazon.awssdk.iot.iotidentity.model.RegisterThingResponse;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class IotIdentityHelperTest {

    private static final String MOCK_CERTIFICATE_OWNERSHIP_TOKEN = "MOCK_CERTIFICATE_OWNERSHIP_TOKEN";
    private static final String MOCK_CERTIFICATE_ID = "MOCK_CERTIFICATE_ID";
    private static final String MOCK_CERTIFICATE_PEM = "MOCK_CERTIFICATE_PEM";
    private static final String MOCK_PRIVATE_KEY = "MOCK_PRIVATE_KEY";
    private static final String MOCK_TEMPLATE_NAME = "MOCK_TEMPLATE_NAME";
    private static final String MOCK_THING_NAME = "MOCK_THING_NAME";
    private static final String MOCK_CSR_CERTIFICATE = "MOCK_CSR_CERTIFICATE";

    private IotIdentityHelper iotIdentityHelper;

    @Mock
    private IotIdentityClient iotIdentityClient;

    @BeforeEach
    public void setup () {
        iotIdentityHelper = new IotIdentityHelper(iotIdentityClient);

    }

    @Test
    public void GIVEN_create_keys_method_invoked_WHEN_api_successful_THEN_expected_response_retuned() throws Exception {
        when(iotIdentityClient.SubscribeToCreateKeysAndCertificateAccepted(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(0));
        when(iotIdentityClient.SubscribeToCreateKeysAndCertificateRejected(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(0));
        when(iotIdentityClient.PublishCreateKeysAndCertificate(any(), eq(QualityOfService.AT_LEAST_ONCE)))
                .thenReturn(CompletableFuture.completedFuture(0));
        Future<CreateKeysAndCertificateResponse> response =
                iotIdentityHelper.createKeysAndCertificate();

        ArgumentCaptor<Consumer> consumerArgumentCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(iotIdentityClient).SubscribeToCreateKeysAndCertificateAccepted(any(),
                eq(QualityOfService.AT_LEAST_ONCE), consumerArgumentCaptor.capture());
        verify(iotIdentityClient).SubscribeToCreateKeysAndCertificateRejected(any(),
                eq(QualityOfService.AT_LEAST_ONCE), any());
        verify(iotIdentityClient).PublishCreateKeysAndCertificate(any(),
                eq(QualityOfService.AT_LEAST_ONCE));
        Consumer acceptedConsumer = consumerArgumentCaptor.getValue();
        acceptedConsumer.accept(createMockCreateKeysAndCertificateResponse());
        CreateKeysAndCertificateResponse returnedResponse = response.get();
        assertEquals(MOCK_PRIVATE_KEY, returnedResponse.privateKey);
        assertEquals(MOCK_CERTIFICATE_ID, returnedResponse.certificateId);
        assertEquals(MOCK_CERTIFICATE_OWNERSHIP_TOKEN, returnedResponse.certificateOwnershipToken);
        assertEquals(MOCK_CERTIFICATE_PEM, returnedResponse.certificatePem);
    }

    @Test
    public void GIVEN_create_certificate_from_csr_method_invoked_WHEN_api_successful_THEN_expected_response_retuned() throws Exception {
        when(iotIdentityClient.SubscribeToCreateCertificateFromCsrAccepted(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(0));
        when(iotIdentityClient.SubscribeToCreateCertificateFromCsrRejected(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(0));
        when(iotIdentityClient.PublishCreateCertificateFromCsr(any(), eq(QualityOfService.AT_LEAST_ONCE)))
                .thenReturn(CompletableFuture.completedFuture(0));
        Future<CreateCertificateFromCsrResponse> response =
                iotIdentityHelper.createCertificateFromCsr(MOCK_CSR_CERTIFICATE);

        ArgumentCaptor<Consumer> consumerArgumentCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(iotIdentityClient).SubscribeToCreateCertificateFromCsrAccepted(any(),
                eq(QualityOfService.AT_LEAST_ONCE), consumerArgumentCaptor.capture());
        verify(iotIdentityClient).SubscribeToCreateCertificateFromCsrRejected(any(),
                eq(QualityOfService.AT_LEAST_ONCE), any());
        verify(iotIdentityClient).PublishCreateCertificateFromCsr(any(),
                eq(QualityOfService.AT_LEAST_ONCE));
        Consumer acceptedConsumer = consumerArgumentCaptor.getValue();
        acceptedConsumer.accept(createMockCreateCertificateFromCsrResponse());
        CreateCertificateFromCsrResponse returnedResponse = response.get();
        assertEquals(MOCK_CERTIFICATE_ID, returnedResponse.certificateId);
        assertEquals(MOCK_CERTIFICATE_OWNERSHIP_TOKEN, returnedResponse.certificateOwnershipToken);
        assertEquals(MOCK_CERTIFICATE_PEM, returnedResponse.certificatePem);
    }

    @Test
    public void GIVEN_register_thing_method_invoked_WHEN_api_successful_THEN_expected_response_retuned() throws Exception {
        when(iotIdentityClient.SubscribeToRegisterThingAccepted(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(0));
        when(iotIdentityClient.SubscribeToRegisterThingRejected(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(0));

        HashMap<String, String> templateParams = new HashMap<>();
        templateParams.put("SerialNumber", "11");
        Future<RegisterThingResponse> response =
                iotIdentityHelper.registerThing(MOCK_CERTIFICATE_OWNERSHIP_TOKEN, MOCK_TEMPLATE_NAME, templateParams);

        ArgumentCaptor<Consumer> consumerArgumentCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(iotIdentityClient).SubscribeToRegisterThingAccepted(any(),
                eq(QualityOfService.AT_LEAST_ONCE), consumerArgumentCaptor.capture(), any());
        verify(iotIdentityClient).SubscribeToRegisterThingRejected(any(),
                eq(QualityOfService.AT_LEAST_ONCE), any(), any());
        ArgumentCaptor<RegisterThingRequest> registerRequestCaptor =
                ArgumentCaptor.forClass(RegisterThingRequest.class);
        verify(iotIdentityClient).PublishRegisterThing(registerRequestCaptor.capture(),
                eq(QualityOfService.AT_LEAST_ONCE));
        RegisterThingRequest registerThingRequest = registerRequestCaptor.getValue();
        assertEquals(templateParams, registerThingRequest.parameters);
        assertEquals(MOCK_TEMPLATE_NAME, registerThingRequest.templateName);
        assertEquals(MOCK_CERTIFICATE_OWNERSHIP_TOKEN, registerThingRequest.certificateOwnershipToken);

        Consumer acceptedConsumer = consumerArgumentCaptor.getValue();
        acceptedConsumer.accept(createMockRegisterThingResponse());
        RegisterThingResponse returnedResponse = response.get();
        assertEquals(MOCK_THING_NAME, returnedResponse.thingName);
    }

    private RegisterThingResponse createMockRegisterThingResponse() {
        RegisterThingResponse registerThingResponse = new RegisterThingResponse();
        registerThingResponse.thingName = MOCK_THING_NAME;
        return registerThingResponse;
    }

    private CreateKeysAndCertificateResponse createMockCreateKeysAndCertificateResponse() {
        CreateKeysAndCertificateResponse mock = new CreateKeysAndCertificateResponse();
        mock.certificateId = MOCK_CERTIFICATE_ID;
        mock.certificateOwnershipToken = MOCK_CERTIFICATE_OWNERSHIP_TOKEN;
        mock.certificatePem = MOCK_CERTIFICATE_PEM;
        mock.privateKey = MOCK_PRIVATE_KEY;
        return mock;
    }

    private CreateCertificateFromCsrResponse createMockCreateCertificateFromCsrResponse() {
        CreateCertificateFromCsrResponse mock = new CreateCertificateFromCsrResponse();
        mock.certificateId = MOCK_CERTIFICATE_ID;
        mock.certificateOwnershipToken = MOCK_CERTIFICATE_OWNERSHIP_TOKEN;
        mock.certificatePem = MOCK_CERTIFICATE_PEM;
        return mock;
    }

}
