
# README - Machine Data Pipeline Setup

This project simulates a machine data pipeline in an industrial environment using Apache Kafka, Zookeeper, InfluxDB, and Telegraf. The setup process assumes you have Docker installed on your machine.

## Prerequisites
- Docker installed ([Docker Installation Guide](https://docs.docker.com/get-docker/))
- Docker Compose installed ([Docker Compose Guide](https://docs.docker.com/compose/install/))

## Project Structure
The project is built using Docker containers for:
- Kafka Server/Broker
- Zookeeper
- Kafka Manager (UI)
- Python Producer (simulated machine data)
- Kafka Streams (Java application)
- Telegraf and InfluxDB (for monitoring and storage)

## Steps to Setup and Run

### Step 1: Clone the Repository
Clone the project repository to your local machine.

```bash
git clone https://github.com/Lukaschanger/KafkaDocker
cd KafkaDocker
```

### Step 2: Create a Docker Network
Create a Docker network to ensure proper communication between services.

```bash
docker network create kafka-network
```

### Step 3: Set Up Kafka, Zookeeper, and Kafka Manager
Navigate to the `broker` directory of the cloned GitHub repository and start Kafka, Zookeeper, and Kafka Manager.

```bash
cd broker
docker-compose up -d
```

### Step 4: Set Up the Python Producer
Navigate to the `producer` directory, build the producer Docker image, and run the container to simulate machine data.

```bash
cd ../producer
docker build -t kafka-producer .
docker run --network broker_kafka-network --name producer kafka-producer
```

The producer generates random time-series data for 50 machines every second and pushes it to the Kafka topic `machine-data`.

### Step 5: Set Up Kafka Streams Application
Navigate to the `streamer` directory, build the Kafka Streamer Docker image, and run the container to process the machine data.

```bash
cd ../streamer
docker build -t kafka-streams-app .
docker run --network broker_kafka-network --name kafka-streamer kafka-streams-app
```

The Kafka Streams application processes the incoming machine data from the `machine-data` topic and pushes the cleaned data back into Kafka under the `machine-data-processed` topic.

### Step 6: Set Up InfluxDB
Pull and run the InfluxDB 2.0 Docker container.

```bash
docker pull influxdb:2.0
docker run -d -p 8086:8086   -v influxdb2_data:/var/lib/influxdb2   --name influxdb2   --network kafka-network   influxdb:2.0
```

### Step 7: InfluxDB Configuration
1. Open InfluxDB at `http://localhost:8086`.
2. Complete the initial setup.
3. **Create two buckets**:
   - `machine-data`
   - `machine-data-processed`
4. Name your organization **Boom** to avoid configuration changes in the Telegraf setup.

### Step 8: Set Up Telegraf

You will need to run two separate Telegraf instances: one for the producer and one for the stream.

#### Telegraf for the Producer

1. Ensure that the `telegraf.conf` file for the producer is properly configured in your local directory.
2. Replace `<PRODUCER_CONF_PATH>` with the absolute path to the `telegraf.conf` file for the producer in the following command.

```bash
docker run -d   --name telegraf-producer   --network broker_kafka-network   -v "<PRODUCER_CONF_PATH>:/etc/telegraf/telegraf.conf:ro"   telegraf:latest
```

#### Telegraf for the Stream

1. Ensure that the `telegraf.conf` file for the streamer is properly configured in your local directory.
2. Replace `<STREAM_CONF_PATH>` with the absolute path to the `telegraf.conf` file for the streamer in the following command.

```bash
docker run -d   --name telegraf-stream   --network broker_kafka-network   -v "<STREAM_CONF_PATH>:/etc/telegraf/telegraf.conf:ro"   telegraf:latest
```

### Step 9: Monitor Data
- Use Kafka Manager to view the Kafka topics and incoming data.
- Use InfluxDB’s UI to visualize and query the processed data stored in the database.

## Stopping the Services
To stop all services, run:

```bash
docker-compose down
```

---

By following these steps, you will have a fully functional machine data pipeline running in Docker containers, with real-time data processing and storage capabilities. Ensure that the correct configuration files are used for both the Telegraf instances (producer and streamer).
