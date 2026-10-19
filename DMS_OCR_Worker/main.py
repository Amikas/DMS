import pika
import os
import time
import logging

logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(levelname)s] %(message)s')
logger = logging.getLogger(__name__)

RABBITMQ_HOST = os.getenv('RABBITMQ_HOST', 'rabbitmq')
RABBITMQ_PORT = int(os.getenv('RABBITMQ_PORT', '5672'))
RABBITMQ_USER = os.getenv('RABBITMQ_USER', 'dms')
RABBITMQ_PASS = os.getenv('RABBITMQ_PASS', 'dms')
QUEUE_NAME = 'dms.document.ocr'


def connect():
    credentials = pika.PlainCredentials(RABBITMQ_USER, RABBITMQ_PASS)
    parameters = pika.ConnectionParameters(
        host=RABBITMQ_HOST,
        port=RABBITMQ_PORT,
        credentials=credentials,
        connection_attempts=10,
        retry_delay=5,
    )
    return pika.BlockingConnection(parameters)


def callback(ch, method, properties, body):
    logger.info("Received OCR job: %s", body.decode('utf-8', errors='replace'))
    ch.basic_ack(delivery_tag=method.delivery_tag)


def main():
    while True:
        try:
            with connect() as connection:
                channel = connection.channel()
                channel.queue_declare(queue=QUEUE_NAME, durable=True)
                channel.basic_qos(prefetch_count=1)
                channel.basic_consume(queue=QUEUE_NAME, on_message_callback=callback)
                logger.info("OCR worker started, waiting for messages on queue '%s'...", QUEUE_NAME)
                channel.start_consuming()
        except pika.exceptions.AMQPError as e:
            logger.error("Connection error: %s. Retrying in 5s...", e)
            time.sleep(5)


if __name__ == '__main__':
    main()
