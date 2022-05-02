package org.example;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Slf4j
@Component
public class SimpleMessageListenerContainerFactory {
    @Autowired ConnectionFactory connectionFactory;

    public SimpleMessageListenerContainer createNewMessageListenerContainer(String queueName, MessageListener messageListener) {
        SimpleMessageListenerContainer simpleMessageListenerContainer = new SimpleMessageListenerContainer();
        simpleMessageListenerContainer.addQueueNames(queueName);
        simpleMessageListenerContainer.setConcurrency("2-10");
        simpleMessageListenerContainer.setListenerId(queueName);
        simpleMessageListenerContainer.setMessageListener(messageListener);
        simpleMessageListenerContainer.setConnectionFactory(connectionFactory);
        simpleMessageListenerContainer.start();
        return simpleMessageListenerContainer;
    }
}
