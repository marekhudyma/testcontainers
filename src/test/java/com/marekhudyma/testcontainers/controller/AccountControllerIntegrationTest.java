package com.marekhudyma.testcontainers.controller;

import com.marekhudyma.testcontainers.controller.dto.AccountDto;
import com.marekhudyma.testcontainers.controller.dto.AccountDtoTestBuilder;
import com.marekhudyma.testcontainers.model.Account;
import com.marekhudyma.testcontainers.model.AccountTestBuilder;
import com.marekhudyma.testcontainers.queue.MessageDto;
import com.marekhudyma.testcontainers.queue.TestQueueReceiver;
import com.marekhudyma.testcontainers.repository.AccountRepository;
import com.marekhudyma.testcontainers.util.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.model.HttpRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static com.marekhudyma.testcontainers.util.Resources.readFromResources;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.springframework.http.HttpMethod.POST;


class AccountControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected AccountRepository accountRepository;

    @Autowired
    private TestQueueReceiver testQueueReceiver;

    @BeforeEach
    void setUp() throws Exception {
        clean();
    }

    @AfterEach
    void tearDown() throws Exception {
        clean();
    }

    void clean() {
        Optional<Account> accountOtional = accountRepository.findByName("name.1");
        accountOtional.ifPresent(account1 -> accountRepository.delete(account1));
    }

    @Test
    void shouldCreateAccount() {
        // given
        HttpRequest request = request("/api/entity/name.1").withMethod("GET");
        getMockServerContainer().getClient().when(request)
                .respond(response()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString())
                        .withStatusCode(200)
                        .withBody(readFromResources("additionalInfo1.json")));
        AccountDto accountDto = new AccountDtoTestBuilder(1).withTestDefaults().build();

        // when
        ResponseEntity<AccountDto> responseEntity = restTemplate.exchange("/accounts",
                POST,
                new HttpEntity<>(accountDto),
                AccountDto.class);

        // than
        getMockServerContainer().getClient().verify(request);
        assertThat(responseEntity.getStatusCodeValue()).isEqualTo(201);

        Account actual = accountRepository.findByName("name.1").get();
        Account expected = new AccountTestBuilder(1).withTestDefaults().created(null).name("name.1").build();
        assertThat(actual).isEqualToIgnoringGivenFields(expected, "id", "created");

        await().atMost(120, SECONDS)
                .pollInterval(100, MILLISECONDS)
                .until(() -> {
                    Optional<MessageDto> messageOptional = testQueueReceiver.getMessages().stream()
                            .filter(a -> a.getId().equals(actual.getId()))
                            .findFirst();

                    return messageOptional.isPresent();
                });
    }
}
