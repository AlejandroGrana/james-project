/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.rspamd.client;

import static org.apache.james.rspamd.DockerRSpamD.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;

import org.apache.james.rspamd.DockerRSpamDExtension;
import org.apache.james.rspamd.exception.UnauthorizedException;
import org.apache.james.rspamd.model.AnalysisResult;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.util.Port;
import org.apache.james.webadmin.WebAdminUtils;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.restassured.http.Header;
import io.restassured.specification.RequestSpecification;

class RSpamDHttpClientTest {
    private final static String SPAM_MESSAGE_PATH = "mail/spam/spam8.eml";
    private final static String HAM_MESSAGE_PATH = "mail/ham/ham1.eml";

    @RegisterExtension
    static DockerRSpamDExtension rSpamDExtension = new DockerRSpamDExtension();

    private byte[] spamMessage;
    private byte[] hamMessage;

    @BeforeEach
    void setup() {
        spamMessage = ClassLoaderUtils.getSystemResourceAsByteArray(SPAM_MESSAGE_PATH);
        hamMessage = ClassLoaderUtils.getSystemResourceAsByteArray(HAM_MESSAGE_PATH);
    }

    @Test
    void checkMailWithWrongPasswordShouldThrowUnauthorizedExceptionException() throws Exception {
        RSpamDClientConfiguration configuration = new RSpamDClientConfiguration(rSpamDExtension.getBaseUrl(), "wrongPassword", Optional.empty());
        RSpamDHttpClient client = new RSpamDHttpClient(configuration);

        assertThatThrownBy(() -> client.checkV2(new ByteArrayInputStream(spamMessage)).block())
            .hasMessage("{\"error\":\"Unauthorized\"}")
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void learnSpamWithWrongPasswordShouldThrowUnauthorizedExceptionException() throws Exception {
        RSpamDClientConfiguration configuration = new RSpamDClientConfiguration(rSpamDExtension.getBaseUrl(), "wrongPassword", Optional.empty());
        RSpamDHttpClient client = new RSpamDHttpClient(configuration);

        assertThatThrownBy(() -> reportAsSpam(client, new ByteArrayInputStream(spamMessage)))
            .hasMessage("{\"error\":\"Unauthorized\"}")
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void learnHamWithWrongPasswordShouldThrowUnauthorizedExceptionException() throws Exception {
        RSpamDClientConfiguration configuration = new RSpamDClientConfiguration(rSpamDExtension.getBaseUrl(), "wrongPassword", Optional.empty());
        RSpamDHttpClient client = new RSpamDHttpClient(configuration);

        assertThatThrownBy(() -> reportAsHam(client, new ByteArrayInputStream(spamMessage)))
            .hasMessage("{\"error\":\"Unauthorized\"}")
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void checkSpamMailUsingRSpamDClientWithExactPasswordShouldReturnAnalysisResultAsSameAsUsingRawClient() throws Exception {
        RSpamDClientConfiguration configuration = new RSpamDClientConfiguration(rSpamDExtension.getBaseUrl(), PASSWORD, Optional.empty());
        RSpamDHttpClient client = new RSpamDHttpClient(configuration);

        AnalysisResult analysisResult = client.checkV2(new ByteArrayInputStream(spamMessage)).block();
        assertThat(analysisResult.getAction()).isEqualTo(AnalysisResult.Action.REJECT);

        RequestSpecification rspamdApi = WebAdminUtils.spec(Port.of(rSpamDExtension.dockerRSpamD().getPort()));
        rspamdApi
            .header(new Header("Password", PASSWORD))
            .body(ClassLoader.getSystemResourceAsStream(SPAM_MESSAGE_PATH))
            .post("checkv2")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("action", is(analysisResult.getAction().getDescription()))
            .body("required_score", is(analysisResult.getRequiredScore()))
            .body("subject", is(nullValue()));
    }

    @Test
    void checkHamMailUsingRSpamDClientWithExactPasswordShouldReturnAnalysisResultAsSameAsUsingRawClient() throws Exception {
        RSpamDClientConfiguration configuration = new RSpamDClientConfiguration(rSpamDExtension.getBaseUrl(), PASSWORD, Optional.empty());
        RSpamDHttpClient client = new RSpamDHttpClient(configuration);

        AnalysisResult analysisResult = client.checkV2(new ByteArrayInputStream(hamMessage)).block();
        assertThat(analysisResult).isEqualTo(AnalysisResult.builder()
            .action(AnalysisResult.Action.NO_ACTION)
            .score(0.99F)
            .requiredScore(14.0F)
            .build());

        RequestSpecification rspamdApi = WebAdminUtils.spec(Port.of(rSpamDExtension.dockerRSpamD().getPort()));
        rspamdApi
            .header(new Header("Password", PASSWORD))
            .body(ClassLoader.getSystemResourceAsStream(HAM_MESSAGE_PATH))
            .post("checkv2")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("action", is(analysisResult.getAction().getDescription()))
            .body("score", is(analysisResult.getScore()))
            .body("required_score", is(analysisResult.getRequiredScore()))
            .body("subject", is(nullValue()));
    }

    @Test
    void learnSpamMailUsingRSpamDClientWithExactPasswordShouldWork() throws Exception {
        RSpamDClientConfiguration configuration = new RSpamDClientConfiguration(rSpamDExtension.getBaseUrl(), PASSWORD, Optional.empty());
        RSpamDHttpClient client = new RSpamDHttpClient(configuration);

        assertThatCode(() -> client.reportAsSpam(new ByteArrayInputStream(spamMessage)).block())
            .doesNotThrowAnyException();
    }

    @Test
    void learnHamMailUsingRSpamDClientWithExactPasswordShouldWork() throws Exception {
        RSpamDClientConfiguration configuration = new RSpamDClientConfiguration(rSpamDExtension.getBaseUrl(), PASSWORD, Optional.empty());
        RSpamDHttpClient client = new RSpamDHttpClient(configuration);

        assertThatCode(() -> client.reportAsHam(new ByteArrayInputStream(hamMessage)).block())
            .doesNotThrowAnyException();
    }

    private void reportAsSpam(RSpamDHttpClient client, InputStream inputStream) {
        client.reportAsSpam(inputStream).block();
    }

    private void reportAsHam(RSpamDHttpClient client, InputStream inputStream) {
        client.reportAsHam(inputStream).block();
    }

}
