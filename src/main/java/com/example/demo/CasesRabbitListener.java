package com.example.demo;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;


@Component
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
class CasesRabbitListener {


    @RabbitListener(
            id = "dev",
            autoStartup = "true",
            queuesToDeclare = {@Queue("test_queue")}
    )
    Mono<Void> listenDev(final Message message) throws JSONException {
        var messageBody = new String(message.getBody());
        var jsonBody = new JSONObject(messageBody);
        log.info(jsonBody.toString());
        return Mono.empty();
    }

    @RabbitListener(
            id = "dev2",
            autoStartup = "false",
            containerFactory = "dev2",
            queues = "test_queue"

    )
    Mono<Void> listenDev2(final Message message) {
        return null;
    }


}