package com.example.demo;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
class CasesRabbitListener {


    @RabbitListener(
            id = "dev",
            autoStartup = "true",
            queues = "dw_q_1"
    )
    void listenDev(final Message message) throws JSONException {
        var stringBody = new String(message.getBody());
        var jsonBody = new JSONObject(stringBody);
        log.info("1:" + jsonBody.get("key"));
    }

    @RabbitListener(
            id = "dev2",
            autoStartup = "true",
            containerFactory = "dev2",
            queues = "dw_q_2"
    )
    void listenDev2(final Message message) throws JSONException {
        var stringBody = new String(message.getBody());
        var jsonBody = new JSONObject(stringBody);
        log.info("2:" + jsonBody.get("key"));
    }


}