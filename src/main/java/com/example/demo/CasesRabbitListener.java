package com.example.demo;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;


@Component
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
class CasesRabbitListener {


    @RabbitListener(
            id = "dev",
            autoStartup = "true",
            priority = "100",
            ackMode = "MANUAL"
    )
    Mono<Void> listenDev(final Message message) {
        return null;
    }

    @RabbitListener(
            id = "dev2",
            autoStartup = "true",
            priority = "100",
//            containerFactory = "dev2",
            ackMode = "MANUAL"

    )
    Mono<Void> listenDev2(final Message message) {
        return null;
    }


    @RabbitListener(
            id = "dev3",
            autoStartup = "true",
            priority = "100",
//            containerFactory = "dev3",
            ackMode = "MANUAL"

    )
    Mono<Void> listenDev3(final Message message) {
        return null;
    }


}