import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch

import pika
from pypdf.errors import PdfReadError

import main


JOB = b'{"documentId":1,"objectKey":"test.pdf"}'
METHOD = SimpleNamespace(delivery_tag=7)


class OcrWorkerTest(unittest.TestCase):
    def test_mixed_pdf_uses_ocr_only_for_scanned_pages(self):
        reader = SimpleNamespace(pages=[
            Mock(extract_text=Mock(return_value="embedded")),
            Mock(extract_text=Mock(return_value="")),
            Mock(extract_text=Mock(return_value="last page")),
        ])
        with patch.object(main, "PdfReader", return_value=reader), \
                patch.object(main.subprocess, "run") as render, \
                patch.object(main.Image, "open"), \
                patch.object(main.pytesseract, "image_to_string", return_value="scanned"):
            self.assertEqual(main.extract_text(b"pdf"), "embedded\nscanned\nlast page")
        args = render.call_args.args[0]
        self.assertEqual(args[args.index("-f") + 1], "2")
        self.assertEqual(args[args.index("-l") + 1], "2")
        render.assert_called_once()

    def test_success_publishes_persistent_result_before_ack(self):
        channel = Mock()
        response = Mock()
        with patch.object(main.storage, "get_object", return_value={"Body": response}), \
                patch.object(main, "extract_text", return_value="hello"):
            main.handle_ocr_job(channel, METHOD, pika.BasicProperties(), JOB)
        publish = channel.basic_publish.call_args.kwargs
        self.assertEqual(publish["routing_key"], main.INDEX_QUEUE)
        self.assertEqual(publish["properties"].delivery_mode, 2)
        self.assertTrue(publish["mandatory"])
        self.assertEqual([call[0] for call in channel.mock_calls], ["basic_publish", "basic_ack"])
        response.close.assert_called_once()

    def test_corrupt_pdf_goes_to_failed_queue(self):
        channel = Mock()
        with patch.object(main.storage, "get_object"), \
                patch.object(main, "extract_text", side_effect=PdfReadError("corrupt")):
            main.handle_ocr_job(channel, METHOD, pika.BasicProperties(), JOB)
        publish = channel.basic_publish.call_args.kwargs
        self.assertEqual(publish["routing_key"], main.FAILED_QUEUE)
        self.assertEqual(publish["body"], JOB)
        self.assertEqual(publish["properties"].delivery_mode, 2)
        channel.basic_ack.assert_called_once_with(delivery_tag=7)

    def test_transient_failures_stop_after_three_attempts(self):
        for attempt in (1, 2, 3):
            with self.subTest(attempt=attempt):
                channel = Mock()
                properties = pika.BasicProperties(headers={"ocr-attempt": attempt})
                with patch.object(main.storage, "get_object", side_effect=OSError("offline")):
                    main.handle_ocr_job(channel, METHOD, properties, JOB)
                publish = channel.basic_publish.call_args.kwargs
                expected = main.FAILED_QUEUE if attempt == 3 else main.OCR_QUEUE
                self.assertEqual(publish["routing_key"], expected)
                self.assertEqual(publish["properties"].headers["ocr-attempt"], attempt + 1)
                self.assertEqual(publish["properties"].delivery_mode, 2)
                channel.basic_ack.assert_called_once_with(delivery_tag=7)

    def test_unconfirmed_publishes_leave_original_job_unacknowledged(self):
        for error in (None, PdfReadError("corrupt"), OSError("offline")):
            with self.subTest(error=error):
                channel = Mock()
                channel.basic_publish.side_effect = pika.exceptions.NackError([])
                with patch.object(main.storage, "get_object"), \
                        patch.object(main, "extract_text", return_value="hello", side_effect=error):
                    with self.assertRaises(pika.exceptions.NackError):
                        main.handle_ocr_job(channel, METHOD, pika.BasicProperties(), JOB)
                channel.basic_ack.assert_not_called()

    def test_consumer_enables_publisher_confirms(self):
        connection = Mock()
        connection.__enter__ = Mock(return_value=connection)
        connection.__exit__ = Mock(return_value=False)
        channel = connection.channel.return_value
        channel.start_consuming.side_effect = KeyboardInterrupt
        with patch.object(main, "connect", return_value=connection):
            with self.assertRaises(KeyboardInterrupt):
                main.main()
        channel.confirm_delivery.assert_called_once()
        channel.queue_declare.assert_any_call(queue=main.FAILED_QUEUE, durable=True)
        calls = [call[0] for call in channel.mock_calls]
        self.assertLess(calls.index("confirm_delivery"), calls.index("basic_consume"))


if __name__ == "__main__":
    unittest.main()
