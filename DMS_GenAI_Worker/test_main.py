import json
import unittest
from unittest.mock import Mock, patch

import requests

import main


JOB = b'{"documentId":1,"objectKey":"test.pdf","content":"Hello from the archive."}'
COMPLETION = {"choices": [{"finish_reason": "stop", "message": {"content": " The archive says hello. "}}]}


class GenAIWorkerTest(unittest.TestCase):
    def test_request_passes_through_full_text_when_it_fits(self):
        with patch.object(main, "post", side_effect=[{"tokens": [1, 2]}, COMPLETION]) as post:
            self.assertEqual(main.summarize("Hello from the archive."), "The archive says hello.")
        path, payload = post.call_args.args
        self.assertEqual(path, "/v1/chat/completions")
        self.assertEqual(payload["messages"][1]["content"], "Source text:\nHello from the archive.")

    def test_long_text_uses_only_the_opening_and_labels_it_partial(self):
        tokens = list(range(main.TEXT_TOKENS + 1))
        with patch.object(main, "post", side_effect=[{"tokens": tokens}, {"content": "Opening"}, COMPLETION]) as post:
            self.assertEqual(main.summarize("Opening and rest"), "Partial summary: The archive says hello.")
        self.assertEqual(post.call_args_list[1].args, ("/detokenize", {"tokens": tokens[:main.TEXT_TOKENS]}))
        self.assertEqual(post.call_args.args[1]["messages"][1]["content"], "Source text:\nOpening")

    def test_model_failure_retries_then_retains_the_job(self):
        notify = Mock()
        with patch.object(main, "summarize", side_effect=requests.Timeout), patch.object(main.time, "sleep"):
            self.assertEqual(main.process_job(JOB, 1, notify), (main.JOB_QUEUE, JOB))
            self.assertEqual(main.process_job(JOB, main.MAX_ATTEMPTS, notify), (main.FAILED_QUEUE, JOB))
        self.assertEqual([json.loads(call.args[0])["summarizing"] for call in notify.call_args_list],
                         [True, False, True, False])

    def test_invalid_and_empty_jobs_do_not_call_model(self):
        notify = Mock()
        with patch.object(main, "summarize") as summarize:
            self.assertEqual(main.process_job(b"{}", 1, notify), (main.FAILED_QUEUE, b"{}"))
            empty = JOB.replace(b"Hello from the archive.", b" ")
            self.assertEqual(main.process_job(empty, 1, notify), (None, None))
        summarize.assert_not_called()
        notify.assert_not_called()

    def test_incomplete_answer_is_not_saved(self):
        incomplete = {"choices": [{"finish_reason": "length", "message": {"content": "unfinished"}}]}
        with patch.object(main, "post", side_effect=[{"tokens": [1]}, incomplete]):
            with self.assertRaises(ValueError):
                main.summarize("Hello")

    def test_only_final_answer_is_kept_when_model_emits_draft_text(self):
        response = {"choices": [{"finish_reason": "stop", "message": {"content": "Draft.</think> Final answer."}}]}
        with patch.object(main, "post", side_effect=[{"tokens": [1]}, response]):
            self.assertEqual(main.summarize("Hello"), "Final answer.")

    def test_extra_paragraphs_are_omitted(self):
        response = {"choices": [{"finish_reason": "stop", "message": {"content": "Summary.\n\nExtra recap."}}]}
        with patch.object(main, "post", side_effect=[{"tokens": [1]}, response]):
            self.assertEqual(main.summarize("Hello"), "Summary.")

    def test_confirmed_publish_precedes_acknowledgement(self):
        notify = Mock()
        with patch.object(main, "summarize", return_value="Summary."):
            target, body = main.process_job(JOB, 1, notify)
        notify.assert_called_once()
        self.assertEqual(json.loads(notify.call_args.args[0]), {
            "documentId": 1, "objectKey": "test.pdf", "summarizing": True})
        self.assertEqual(target, main.RESULT_QUEUE)
        self.assertEqual(json.loads(body), {"documentId": 1, "objectKey": "test.pdf", "summary": "Summary."})
        channel = Mock()
        result = Mock(result=Mock(return_value=(target, body)))
        main.deliver(channel, 1, 1, result)
        self.assertEqual([call[0] for call in channel.mock_calls], ["basic_publish", "basic_ack"])
        self.assertTrue(channel.basic_publish.call_args.kwargs["mandatory"])
        properties = channel.basic_publish.call_args.kwargs["properties"]
        self.assertEqual(properties.delivery_mode, 2)
        self.assertEqual(properties.content_type, "application/json")
