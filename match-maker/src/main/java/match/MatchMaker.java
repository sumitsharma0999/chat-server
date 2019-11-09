package match;

import model.Chat;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.log4j.BasicConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import storage.StorageProvider;

import java.util.*;

public class MatchMaker {
    private static Scanner in;

    private static Logger log = LoggerFactory.getLogger(MatchMaker.class);

    private static String lastUnMatchedUser = null;

    private static Producer producer;

    private static final String KAFKA_BROKER = "kafka1:19092";

    public static void main(String[] argv)throws Exception{
        BasicConfigurator.configure();
        log.info("Starting the match maker");

        // Wait for some time, so that kafka and redis are up
        Thread.sleep(15000);

        String topicName = "special_waitingToBeMatched";
        String groupId = "group-x";
        in = new Scanner(System.in);


        //Configure the Producer
        Properties configProperties = new Properties();
        configProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BROKER);
        configProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,"org.apache.kafka.common.serialization.ByteArraySerializer");
        configProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,"org.apache.kafka.common.serialization.StringSerializer");
        producer = new KafkaProducer(configProperties);
        log.info("Created the producer to send messages");

        ConsumerThread consumerRunnable = new ConsumerThread(topicName,groupId);
        log.info("Starting the consumer thread to listen on Kafka topic");
        consumerRunnable.start();
//        String line = "";
//        while (!line.equals("exit")) {
//            line = in.next();
//        }
        //consumerRunnable.getKafkaConsumer().wakeup();
        consumerRunnable.join();
    }

    private static class ConsumerThread extends Thread{
        private String topicName;
        private String groupId;
        private KafkaConsumer<String,String> kafkaConsumer;

        public ConsumerThread(String topicName, String groupId){
            this.topicName = topicName;
            this.groupId = groupId;
        }
        public void run() {
            log.info("Kakfa consumer is up and running");
            Properties configProperties = new Properties();
            configProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BROKER);
            configProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArrayDeserializer");
            configProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
            configProperties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
            configProperties.put(ConsumerConfig.CLIENT_ID_CONFIG, "simple");

            //Figure out where to start processing messages from
            kafkaConsumer = new KafkaConsumer<String, String>(configProperties);
            kafkaConsumer.subscribe(Arrays.asList(topicName));
            log.info("Consumer subscribed to %s", topicName);
            //Start processing messages
            try {
//                TopicPartition partition = kafkaConsumer.assignment().iterator().next();
//                kafkaConsumer.seekToEnd(partition);
                while (true) {
                    ConsumerRecords<String, String> records = kafkaConsumer.poll(100);

                    //log.info("Read records from queue: " + records.count());

                    if (records.count() > 0) {
                        log.info("Found " + records.count() + " records to be processed");
                        Iterator<ConsumerRecord<String, String>> iterator = records.iterator();
                        ConsumerRecord<String, String> record;

                        while (iterator.hasNext()) {
                            record = iterator.next();
                            if (lastUnMatchedUser == null) {
                                log.info("{} is last unmatched user");
                                lastUnMatchedUser = record.value();
                            }
                            else {
                                // Create a new match
                                log.info("Found a match: " + lastUnMatchedUser + " + " + record.value());
                                Chat chat = new Chat(lastUnMatchedUser, record.value());
                                StorageProvider.save(chat);

                                // Notify the users that chat is ready
                                ProducerRecord<String, String> rec1 = new ProducerRecord<String, String>(chat.user1, "connection_" + chat.user2);
                                ProducerRecord<String, String> rec2 = new ProducerRecord<String, String>(chat.user2, "connection_" + chat.user1);
                                producer.send(rec1);
                                producer.send(rec2);
                                lastUnMatchedUser = null;
                            }
                        }
                        kafkaConsumer.commitSync();
                    }
                }
            }catch(Exception ex){
                log.error("Consumer ran into an error: " + ex.getMessage());
            }finally{
                kafkaConsumer.close();
                log.info("Closing the kafka consumer");
            }
        }
        public KafkaConsumer<String,String> getKafkaConsumer(){
            return this.kafkaConsumer;
        }
    }

}
