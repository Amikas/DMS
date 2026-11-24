import io
import json
import logging
import os
import subprocess
import tempfile
import time

import pika
import boto3
from botocore.config import Config
from pypdf import PdfReader
from pypdf.errors import PdfReadError
from PIL import Image
import pytesseract

logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(levelname)s] %(message)s')
logger = logging.getLogger(__name__)

RABBITMQ_HOST = os.getenv('RABBITMQ_HOST', 'rabbitmq')
RABBITMQ_PORT = int(os.getenv('RABBITMQ_PORT', '5672'))
RABBITMQ_USER = os.getenv('RABBITMQ_USER', 'dms')
RABBITMQ_PASS = os.getenv('RABBITMQ_PASS', 'dms')
OCR_QUEUE = 'dms.document.ocr'
INDEX_QUEUE = 'dms.document.index'
FAILED_QUEUE = OCR_QUEUE + '.failed'
MAX_ATTEMPTS = 3

STORAGE_ENDPOINT = os.getenv('STORAGE_ENDPOINT', 'http://localhost:9000')
STORAGE_ACCESS_KEY = os.getenv('STORAGE_ACCESS_KEY', 'dms')
STORAGE_SECRET_KEY = os.getenv('STORAGE_SECRET_KEY', 'dms-secret')
STORAGE_BUCKET = os.getenv('STORAGE_BUCKET', 'documents')

storage = boto3.client('s3', endpoint_url=STORAGE_ENDPOINT, region_name='us-east-1',
                       aws_access_key_id=STORAGE_ACCESS_KEY, aws_secret_access_key=STORAGE_SECRET_KEY,
                       config=Config(s3={'addressing_style': 'path'}))


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


def extract_text(pdf_bytes):
    """Use embedded text where available, and OCR each remaining page."""
    texts = [page.extract_text() or "" for page in PdfReader(io.BytesIO(pdf_bytes)).pages]
    if all(text.strip() for text in texts):
        return "\n".join(texts)
    with tempfile.TemporaryDirectory() as tmp:
        pdf_path = os.path.join(tmp, "document.pdf")
        with open(pdf_path, "wb") as f:
            f.write(pdf_bytes)
        for i, text in enumerate(texts):
            if text.strip():
                continue
            logger.info("Running Tesseract OCR for page %d", i + 1)
            prefix = os.path.join(tmp, "page")
            subprocess.run(["pdftoppm", "-f", str(i + 1), "-l", str(i + 1),
                            "-singlefile", "-png", "-r", "150", pdf_path, prefix],
                           check=True, capture_output=True)
            with Image.open(prefix + ".png") as image:
                texts[i] = pytesseract.image_to_string(image)
        return "\n".join(texts)


def handle_ocr_job(ch, method, properties, body):
    try:
        job = json.loads(body)
        document_id = job["documentId"]
        object_key = job["objectKey"]
        if not document_id or not object_key:
            raise ValueError("Document id and object key are required")
        logger.info("OCR job for document id=%s", document_id)
        response = storage.get_object(Bucket=STORAGE_BUCKET, Key=object_key)['Body']
        try:
            pdf_bytes = response.read()
        finally:
            response.close()
        text = extract_text(pdf_bytes)
    except Exception as e:
        headers = dict(properties.headers or {})
        attempt = headers.get("ocr-attempt", 1)
        permanent = isinstance(e, (PdfReadError, ValueError, KeyError, TypeError))
        target = FAILED_QUEUE if permanent or attempt >= MAX_ATTEMPTS else OCR_QUEUE
        headers["ocr-attempt"] = attempt + 1
        logger.error("OCR attempt %d failed: %s. Sending to %s", attempt, e, target)
        if target == OCR_QUEUE:
            ch.connection.sleep(2)
        ch.basic_publish(exchange="", routing_key=target, body=body, mandatory=True,
                         properties=pika.BasicProperties(delivery_mode=2, headers=headers))
        ch.basic_ack(delivery_tag=method.delivery_tag)
        return
    ch.basic_publish(exchange="", routing_key=INDEX_QUEUE,
                     mandatory=True, properties=pika.BasicProperties(delivery_mode=2),
                     body=json.dumps({"documentId": document_id, "content": text}))
    logger.info("OCR done for document id=%s, %d characters extracted", document_id, len(text))
    ch.basic_ack(delivery_tag=method.delivery_tag)


def main():
    while True:
        try:
            with connect() as connection:
                channel = connection.channel()
                channel.queue_declare(queue=OCR_QUEUE, durable=True)
                channel.queue_declare(queue=INDEX_QUEUE, durable=True)
                channel.queue_declare(queue=FAILED_QUEUE, durable=True)
                channel.confirm_delivery()
                channel.basic_qos(prefetch_count=1)
                channel.basic_consume(queue=OCR_QUEUE, on_message_callback=handle_ocr_job)
                logger.info("OCR worker waiting for messages on queue '%s'...", OCR_QUEUE)
                channel.start_consuming()
        except pika.exceptions.AMQPError as e:
            logger.error("Connection error: %s. Retrying in 5s...", e)
            time.sleep(5)
        except Exception as e:
            logger.error("Unexpected error: %s. Reconnecting in 5s...", e)
            time.sleep(5)


if __name__ == '__main__':
    main()
