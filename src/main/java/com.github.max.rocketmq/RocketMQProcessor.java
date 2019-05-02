package com.github.max.rocketmq;

import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.MessageListener;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;

import java.util.Map;
import java.util.UUID;

/**
 * @description: RocketMQ支持制动化配置的消息处理器
 * @author: max
 * @date: 2019-05-02 16:57
 **/
@Slf4j
public class RocketMQProcessor extends AbstractRocketMQ {

    private volatile DefaultMQPushConsumer consumer;

    private final MessageListener listener;

    public RocketMQProcessor(Config config, MessageListener listener) {
        this.config = config;
        this.listener = listener;
    }

    public RocketMQProcessor(String nameServer, String groupName, String topics, MessageListener listener) {
        super(nameServer, groupName, topics, 0, 0);
        this.listener = listener;
    }


    public RocketMQProcessor(String nameServerKey, String groupKey, String topics, int consumeThreadMin, int consumeThreadMax, MessageListener listener) {
        super(nameServerKey, groupKey, topics, consumeThreadMin, consumeThreadMax);
        this.listener = listener;
    }

    @Override
    public void load() {
        if (consumer == null) {
            log.debug("CreateRocketMQConsumer");
            createConsumer();
        } else {
            log.debug("ReCreateRocketMQConsumer");
            shutDownConsumer();
            createConsumer();
        }
    }

    private void createConsumer() {
        if (log.isDebugEnabled()) {
            log.debug("createConsumer start. config: {}, MessageListener: {}", config, listener);
        }
        consumer = new DefaultMQPushConsumer(config.getGroupName());
        consumer.setNamesrvAddr(config.getNameServer());
        subscribeTopics();
        consumer.setInstanceName(UUID.randomUUID().toString());
        //设置consumer 消费模式
        setMessageModel(consumer, config.getMessageModel());
        consumer.setConsumeFromWhere(getConsumeFromWhere());
        consumer.setPullBatchSize(config.getFetchSize());
        consumer.setConsumeThreadMin(config.getConsumeThreadMin());
        consumer.setConsumeThreadMax(config.getConsumeThreadMax());
        consumer.setConsumeMessageBatchMaxSize(config.getConsumeMessageBatchMaxSize());
        if (getConsumeFromWhere() == ConsumeFromWhere.CONSUME_FROM_TIMESTAMP && !Strings.isNullOrEmpty(getConsumeTimestamp())) {
            consumer.setConsumeTimestamp(getConsumeTimestamp());
        }
        consumer.setPullInterval(config.getPullInterval());
        if (listener instanceof MessageListenerConcurrently) {
            consumer.registerMessageListener((MessageListenerConcurrently) this.listener);
        } else if (listener instanceof MessageListenerOrderly) {
            consumer.registerMessageListener((MessageListenerOrderly) this.listener);
        } else {
            throw new RuntimeException("UnSupportListenerType");
        }

        try {
            consumer.start();
        } catch (MQClientException e) {
            log.error("StartRocketMQConsumer Error", e);
        }
        log.info("RocketMQConsumer Started! group={} instance={}", consumer.getConsumerGroup(),
                consumer.getInstanceName());
    }

    private void setMessageModel(DefaultMQPushConsumer consumer, String messageModel) {
        if (Strings.isNullOrEmpty(messageModel)) {
            return;
        }
        for (MessageModel model : MessageModel.values()) {
            if (model.name().equals(messageModel)) {
                log.info("setMessageModel messageModel:{}", messageModel);
                consumer.setMessageModel(MessageModel.valueOf(messageModel));
                return;
            }
        }
    }

    private String getConsumeTimestamp() {
        return null;
    }

    private ConsumeFromWhere getConsumeFromWhere() {
        return ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET;
    }

    private void subscribeTopics() {
        subscribeTopics(consumer, config.getTopics());
    }

    private void subscribeTopics(DefaultMQPushConsumer consumer, Map<String, String> topics) {
        try {
            for (Map.Entry<String, String> i : topics.entrySet()) {
                consumer.subscribe(i.getKey(), i.getValue());
            }
        } catch (MQClientException e) {
            log.error("SubscribeTopic Error!", e);
        }
    }

    @Override
    public void shutDown() {
        shutDownConsumer();
    }

    private void shutDownConsumer() {
        if (this.consumer != null) {
            try {
                this.consumer.shutdown();
                this.consumer = null;
            } catch (Exception e) {
                log.error("ShutRocketMQConsumer Error,nameServer={} group={}", consumer.getNamesrvAddr(), consumer.getConsumerGroup(), e);
            }
        }
    }
}
