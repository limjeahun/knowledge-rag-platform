"""Deterministic tests for the comparison dataset and evaluator contract."""

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from rag_compare import cli


class ComparisonEvaluatorTests(unittest.TestCase):
    def test_manifest_matches_versioned_documents(self) -> None:
        manifest = json.loads((cli.DATASET / "manifest.json").read_text(encoding="utf-8"))

        self.assertEqual(manifest["documents"], [name for name, _ in cli.read_documents()])
        self.assertEqual(3, len(cli.read_questions()))

    def test_evaluator_writes_detail_and_summary_with_declared_gold_coverage(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            result = root / "primary-hybrid.jsonl"
            question = cli.read_questions()[0]
            result.write_text(
                json.dumps(
                    {
                        "system": "primary",
                        "method": "hybrid",
                        "questionId": question["id"],
                        "question": question["question"],
                        "answer": "PostgreSQL Outbox Kafka Ollama Milvus",
                        "sources": [{"sourceFile": "01-indexing-pipeline.txt"}],
                        "graphFacts": [{"type": "PUBLISHES_TO"}, {"type": "WRITES_TO"}],
                        "latencyMs": 10.0,
                        "error": None,
                    },
                    ensure_ascii=False,
                )
                + "\n",
                encoding="utf-8",
            )
            reports = root / "reports"

            with patch.object(cli, "REPORTS", reports):
                details_path, summary_path = cli.evaluate([result])

            summary = json.loads(summary_path.read_text(encoding="utf-8"))
            self.assertTrue(details_path.exists())
            self.assertEqual(1.0, summary[0]["answerTermRecall"])
            self.assertEqual(1.0, summary[0]["relationRecall"])
            self.assertEqual(1.0, summary[0]["sourceRecall"])

    def test_coverage_is_case_insensitive_and_declares_empty_gold_as_complete(self) -> None:
        self.assertEqual(0.5, cli.coverage(["MILVUS", "Fuseki"], "milvus"))
        self.assertEqual(1.0, cli.coverage([], "anything"))


if __name__ == "__main__":
    unittest.main()
