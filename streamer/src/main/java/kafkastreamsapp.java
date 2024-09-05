import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.WindowStore;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.KGroupedStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.common.utils.Bytes;

import java.util.Properties;
import java.time.Duration;

public class kafkastreamsapp {  // Changed class name to match file name
    public static void main(String[] args) {
        // Set up the Kafka Streams configuration
        Properties config = new Properties();
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, "kafka-streams-app");
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        // Define the processing topology
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> input = builder.stream("machine-data");

        // 1. Filter the stream for machines in "Running" or "Maintenance" state
        KStream<String, String> filtered = input.filter((key, value) -> {
            try {
                // Deserialize the JSON and check the status
                JsonNode data = new ObjectMapper().readTree(value);
                return data.has("general") && data.get("general").has("status") && 
                       (data.get("general").get("status").asText().equals("Running") || 
                        data.get("general").get("status").asText().equals("Maintenance"));
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        });

        // 2. Aggregate data over a time window (e.g., 1 minute)
        KGroupedStream<String, String> grouped = filtered.groupByKey();
        KTable<Windowed<String>, String> aggregated = grouped
            .windowedBy(TimeWindows.of(Duration.ofMinutes(1)))
            .aggregate(
                () -> "{}",
                (key, newValue, aggValue) -> {
                    try {
                        // Logic to combine new data with the aggregation
                        JsonNode newValueJson = new ObjectMapper().readTree(newValue);
                        JsonNode aggValueJson = new ObjectMapper().readTree(aggValue);

                        double newEnergy = newValueJson.get("general").get("energy_consumption").asDouble();
                        double aggEnergy = aggValueJson.has("total_energy") ? aggValueJson.get("total_energy").asDouble() : 0;
                        
                        double totalEnergy = newEnergy + aggEnergy;

                        ObjectNode result = (ObjectNode) aggValueJson;
                        result.put("total_energy", totalEnergy);

                        return result.toString();
                    } catch (Exception e) {
                        e.printStackTrace();
                        return aggValue;
                    }
                },
                Materialized.<String, String, WindowStore<Bytes, byte[]>>as("energy-aggregates")
                    .withValueSerde(Serdes.String())
            );

        // 3. Process aggregated data for alerts (e.g., high energy consumption)
        aggregated.toStream().filter((key, value) -> {
            try {
                JsonNode data = new ObjectMapper().readTree(value);
                return data.has("total_energy") && data.get("total_energy").asDouble() > 1000; // Threshold for alert
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }).mapValues(value -> "High energy consumption alert: " + value)
          .to("alerts");

        // 4. Send processed results to an output topic
        filtered.to("machine-data-processed");

        // Build and start the Kafka Streams application
        KafkaStreams streams = new KafkaStreams(builder.build(), config);
        streams.start();

        // Add shutdown hook to stop the streams on exit
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    }
}
