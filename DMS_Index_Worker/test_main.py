import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch

import main


class IndexWorkerTest(unittest.TestCase):
    def test_http_failures_retry_only_transient_responses(self):
        for status in (200, 201, 400, 429, 500, 503):
            with self.subTest(status=status):
                channel = Mock()
                response = SimpleNamespace(status_code=status, text="error")
                with patch.object(main.requests, "put", return_value=response):
                    main.handle_index_job(channel, SimpleNamespace(delivery_tag=7), None,
                                          b'{"documentId":1,"content":"hello"}')
                if status == 429 or status >= 500:
                    channel.basic_nack.assert_called_once_with(delivery_tag=7, requeue=True)
                    channel.basic_ack.assert_not_called()
                else:
                    channel.basic_ack.assert_called_once_with(delivery_tag=7)
                    channel.basic_nack.assert_not_called()


if __name__ == "__main__":
    unittest.main()
