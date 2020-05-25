package com.cloudera.streaming.examples.flink;

import com.cloudera.streaming.examples.flink.operators.HashingKafkaPartitioner;
import com.cloudera.streaming.examples.flink.operators.QueryStringParser;
import com.cloudera.streaming.examples.flink.types.*;
import com.cloudera.streaming.examples.flink.utils.Utils;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.core.fs.Path;
import org.apache.flink.formats.avro.registry.cloudera.ClouderaRegistryKafkaDeserializationSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.filesystem.StreamingFileSink;
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema;

import com.cloudera.streaming.examples.flink.types.ITrnx2;

import java.util.Optional;

public class KafkaHDFSITrnxJob extends ITrnxJob {

    public static String AVRO_TRANSACTION_INPUT_TOPIC_KEY = "avro.transaction.input.topic";
    public static String CLOUDERA_SCHEMA_REGISTRY_URL_KEY = "schema.registry.url";
    public static String QUERY_INPUT_TOPIC_KEY = "query.input.topic.2";
    public static String QUERY_OUTPUT_TOPIC_KEY = "query.output.topic.2";
    public final String HDFS_PATH_KEY = "hdfs.path";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new RuntimeException("Path to the properties file is expected as the only argument.");
        }
        ParameterTool params = ParameterTool.fromPropertiesFile(args[0]);
        new KafkaHDFSITrnxJob()
                .createApplicationPipeline(params)
                .execute("Kafka-HDFS Archive Job");
    }

    @Override
    public DataStream<Query> readQueryStream(ParameterTool params, StreamExecutionEnvironment env) {

        // We read queries in a simple String format and parse it to our Query object
        FlinkKafkaConsumer<String> rawQuerySource = new FlinkKafkaConsumer<>(
                params.getRequired(QUERY_INPUT_TOPIC_KEY), new SimpleStringSchema(),
                Utils.readKafkaProperties(params, true, "flink-query"));

        rawQuerySource.setCommitOffsetsOnCheckpoints(true);

        // The first time the job is started we start from the end of the queue, ignoring earlier queries
        rawQuerySource.setStartFromLatest();

        return env.addSource(rawQuerySource)
                .name("Kafka Query Source")
                .uid("Kafka Query Source")
                .flatMap(new QueryStringParser()).name("Query parser");

    }

    @Override
    protected DataStream<ITrnx2> readTransactionStream(ParameterTool params, StreamExecutionEnvironment env) {
        KafkaDeserializationSchema<ITrnx2> schema = ClouderaRegistryKafkaDeserializationSchema
                .builder(ITrnx2.class)
                .setRegistryAddress(params.getRequired(CLOUDERA_SCHEMA_REGISTRY_URL_KEY))
                .build();
        // We read the ITrnx2 objects directly using the schema
        FlinkKafkaConsumer<ITrnx2> transactionSource = new FlinkKafkaConsumer<>(
                params.getRequired(AVRO_TRANSACTION_INPUT_TOPIC_KEY), schema,
                Utils.readKafkaProperties(params, true, "flink-hdfs-trnx"));

        transactionSource.setCommitOffsetsOnCheckpoints(true);
        transactionSource.setStartFromEarliest();

        // In case event time processing is enabled we assign trailing watermarks for each partition
        transactionSource.assignTimestampsAndWatermarks(new BoundedOutOfOrdernessTimestampExtractor<ITrnx2>(Time.minutes(1)) {
            @Override
            public long extractTimestamp(ITrnx2 transaction) {
                return transaction.getTs();
            }
        });

        return env.addSource(transactionSource)
                .name("Kafka-HDFS Transaction Source")
                .uid("Kafka-HDFS Transaction Source");
    }

    @Override
    public void writeQueryOutput(ParameterTool params, DataStream<QueryResult> queryResultStream) {
        // Query output is written back to kafka in a tab delimited format for readability
        FlinkKafkaProducer<QueryResult> queryOutputSink = new FlinkKafkaProducer<>(
                params.getRequired(QUERY_OUTPUT_TOPIC_KEY), new QueryResultSchema(),
                Utils.readKafkaProperties(params, false, String.valueOf(0)),
                Optional.of(new HashingKafkaPartitioner<>()));

        queryResultStream
                .addSink(queryOutputSink)
                .name("Kafka Query Result Sink")
                .uid("Kafka Query Result Sink");
    }

    @Override
    protected void writeTransactionResults(ParameterTool params, DataStream<TrnxResult> transactionResults) {
        final String output = params.get(HDFS_PATH_KEY, "hdfs:///localhost:8020/tmp/flink");
        final StreamingFileSink<String> sfs = StreamingFileSink
                .forRowFormat(new Path(output), new SimpleStringEncoder<String>("UTF-8"))
                .build();

        transactionResults.map(results -> results.toString()).addSink(sfs)
                .name("HDFS Transaction Result Sink")
                .uid("HDFS Transaction Result Sink");

    }

    @Override
    protected void writeTransactionSummaries(ParameterTool params, DataStream<TransactionSummary> transactionSummaryStream) {
        // Ignore for now
    }
}
