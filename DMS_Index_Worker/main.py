import json
import logging
import os
import time

import pika
import requests

logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(levelname)s] %(message)s')
logger = logging.getLogger(__name__)

RABBITMQ_HOST = os.getenv('RABBITMQ_HOST', 'rabbitmq')
RABBITMQ_PORT = int(os.getenv('RABBITMQ_PORT', '5672'))
RABBITMQ_USER = os.getenv('RABBITMQ_USER', 'dms')
RABBITMQ_PASS = os.getenv('RABBITMQ_PASS', 'dms')
INDEX_QUEUE = 'dms.document.index'

ELASTICSEARCH_URL = os.getenv('ELASTICSEARCH_URL', 'http://elasticsearch:9200')
ES_INDEX = os.getenv('ES_INDEX', 'documents')


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


def handle_index_job(ch, method, properties, body):
    job = json.loads(body)
    document_id = job["documentId"]
    # ponytail: permanent 4xx responses are dropped; add a failed-job queue if replay is needed.
    try:
        response = requests.put(
            f"{ELASTICSEARCH_URL}/{ES_INDEX}/_doc/{document_id}",
            json={"documentId": document_id, "content": job.get("content", "")},
            timeout=10,
        )
    except requests.RequestException as e:
        logger.error("Elasticsearch unreachable: %s. Requeueing document id=%s", e, document_id)
        ch.connection.sleep(2)
        ch.basic_nack(delivery_tag=method.delivery_tag, requeue=True)
        return
    if response.status_code >= 400:
        logger.error("Elasticsearch rejected document id=%s (status %s): %s",
                     document_id, response.status_code, response.text)
        if response.status_code == 429 or response.status_code >= 500:
            ch.connection.sleep(2)
            ch.basic_nack(delivery_tag=method.delivery_tag, requeue=True)
            return
        ch.basic_ack(delivery_tag=method.delivery_tag)
        return
    logger.info("Indexed document id=%s in Elasticsearch", document_id)
    ch.basic_ack(delivery_tag=method.delivery_tag)


def main():
    while True:
        try:
            with connect() as connection:
                channel = connection.channel()
                channel.queue_declare(queue=INDEX_QUEUE, durable=True)
                channel.basic_qos(prefetch_count=1)
                channel.basic_consume(queue=INDEX_QUEUE, on_message_callback=handle_index_job)
                logger.info("Index worker waiting for messages on queue '%s'...", INDEX_QUEUE)
                channel.start_consuming()
        except pika.exceptions.AMQPError as e:
            logger.error("Connection error: %s. Retrying in 5s...", e)
            time.sleep(5)


if __name__ == '__main__':
    main()
