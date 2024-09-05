import time
import random
import json
import datetime
import logging
from kafka import KafkaProducer
from kafka.errors import NoBrokersAvailable

# Enable logging
logging.basicConfig(level=logging.INFO)

# Define the machine information with 50 machines
machines = [
    {"machine_name": f"Machine {i}", "machine_id": f"M{i:03d}", "installation_date": f"2020-{(i % 12) + 1:02d}-{(i % 28) + 1:02d}"}
    for i in range(1, 51)  # Creates 50 machines
]

def generate_time_series_data(machine, status_last_update):
    timestamp = datetime.datetime.utcnow().isoformat()
    
    # Update status every minute
    current_time = datetime.datetime.utcnow()
    if (current_time - status_last_update).total_seconds() >= 60:
        status = random.choice(["Running", "Idle", "Stopped", "Maintenance"])
        status_last_update = current_time
    else:
        status = None  # Don't update status if it's within the same minute
    
    # General Information
    energy_consumption = round(random.uniform(5.0, 50.0), 2)  # kW
    operating_minutes = random.randint(0, 1440)  # Minutes in a day
    
    # Hydraulics
    hydraulics_temp = round(random.uniform(30.0, 80.0), 1)  # °C
    hydraulics_pressure = round(random.uniform(50.0, 200.0), 1)  # bar
    
    # Spindle
    spindle_vibration = round(random.uniform(0.1, 10.0), 2)  # mm/s
    spindle_temp = round(random.uniform(30.0, 90.0), 1)  # °C
    spindle_rpm = random.randint(500, 10000)  # RPM
    
    # Production
    pieces_produced = random.randint(0, 100)  # Number of pieces
    time_per_piece = round(random.uniform(30, 300), 1)  # Seconds per piece
    
    data = {
        "machine_id": machine["machine_id"],
        "machine_name": machine["machine_name"],  # Include machine_name
        "installation_date": machine["installation_date"],  # Include installation_date
        "timestamp": timestamp,
        "general": {
            "energy_consumption": energy_consumption,
            "operating_minutes": operating_minutes
        },
        "hydraulics": {
            "temperature": hydraulics_temp,
            "pressure": hydraulics_pressure
        },
        "spindle": {
            "vibration": spindle_vibration,
            "temperature": spindle_temp,
            "rotation_speed": spindle_rpm
        },
        "production": {
            "pieces_produced": pieces_produced,
            "time_per_piece": time_per_piece
        }
    }
    
    # Add status if updated
    if status is not None:
        data["general"]["status"] = status
    
    return data, status_last_update


if __name__ == "__main__":
    producer = None
    while producer is None:
        try:
            # Initialize Kafka producer with the Docker service name of the Kafka broker
            producer = KafkaProducer(
                bootstrap_servers='kafka:9092',  # Use Docker service name
                value_serializer=lambda v: json.dumps(v).encode('utf-8'),
                key_serializer=lambda v: v.encode('utf-8')  # Serialize the key as UTF-8 encoded string
            )
            logging.info("Connected to Kafka successfully")
        except NoBrokersAvailable as e:
            logging.error("No Kafka brokers available. Retrying in 5 seconds...")
            time.sleep(5)  # Wait 5 seconds before retrying
    
    status_last_updates = {machine["machine_id"]: datetime.datetime.utcnow() for machine in machines}
    logging.info(f"Initial status_last_updates: {status_last_updates}")  # Log the initial dictionary
    
    while True:
        for machine in machines:
            logging.info(f"Processing machine: {machine['machine_id']}")  # Log each machine being processed
            data, status_last_updates[machine["machine_id"]] = generate_time_series_data(machine, status_last_updates[machine["machine_id"]])
            logging.info(f"Updated status_last_updates: {status_last_updates}")  # Log status update times
            key = machine["machine_id"]  # Use machine_id as the key
            try:
                producer.send('machine-data', key=key, value=data)  # Send the data to Kafka with the machine_id as the key
                logging.info("Data sent to Kafka with key %s: %s", key, json.dumps(data, indent=2))  # Print the generated data
            except Exception as e:
                logging.error(f"Failed to send data to Kafka: {e}")
        
        time.sleep(1)  # Pause after sending data for all machines
