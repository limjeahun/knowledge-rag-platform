"""Run the same corpus and questions against three GraphRAG implementations."""

from __future__ import annotations

import argparse
import csv
import importlib.metadata
import json
import os
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DATASET = ROOT / "dataset" / "software-architecture-v1"
DOCUMENTS = DATASET / "documents"
QUESTIONS = DATASET / "questions.json"
WORKSPACES = ROOT / "workspaces"
RESULTS = ROOT / "results"
REPORTS = ROOT / "reports"


def read_questions() -> list[dict[str, Any]]:
    """Load the versioned comparison questions in their declared order."""
    return json.loads(QUESTIONS.read_text(encoding="utf-8"))["questions"]


def read_documents() -> list[tuple[str, str]]:
    """Return stable file names and UTF-8 contents for every dataset document."""
    return [
        (path.name, path.read_text(encoding="utf-8"))
        for path in sorted(DOCUMENTS.glob("*.txt"))
    ]


def post_json(url: str, payload: dict[str, Any], api_key: str | None = None) -> dict[str, Any]:
    """POST one JSON request and decode the JSON response without extra HTTP dependencies."""
    headers = {"Content-Type": "application/json"}
    if api_key:
        headers["X-API-Key"] = api_key
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers=headers,
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=600) as response:
        body = response.read().decode("utf-8")
        return json.loads(body) if body else {}


def get_json(url: str, api_key: str | None = None) -> Any:
    """GET one JSON resource, optionally using LightRAG's API-key header."""
    headers = {"X-API-Key": api_key} if api_key else {}
    request = urllib.request.Request(url, headers=headers, method="GET")
    with urllib.request.urlopen(request, timeout=60) as response:
        body = response.read().decode("utf-8")
        return json.loads(body) if body else {}


def run_command(command: list[str], cwd: Path | None = None) -> str:
    """Run a research CLI and fail with its captured output when the process is unsuccessful."""
    completed = subprocess.run(
        command,
        cwd=cwd,
        check=False,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    output = "\n".join(part for part in (completed.stdout, completed.stderr) if part).strip()
    if completed.returncode != 0:
        raise RuntimeError(f"Command failed ({completed.returncode}): {' '.join(command)}\n{output}")
    return output


def prepare_microsoft() -> None:
    """Create a GraphRAG workspace, generated prompts, and the shared input corpus."""
    workspace = WORKSPACES / "microsoft"
    workspace.mkdir(parents=True, exist_ok=True)
    run_command(
        [
            "graphrag",
            "init",
            "--root",
            str(workspace),
            "--model",
            os.getenv("OLLAMA_CHAT_MODEL", "qwen3.6:27b"),
            "--embedding",
            os.getenv("OLLAMA_EMBEDDING_MODEL", "nomic-embed-text"),
            "--force",
        ]
    )
    input_dir = workspace / "input"
    input_dir.mkdir(parents=True, exist_ok=True)
    for source in sorted(DOCUMENTS.glob("*.txt")):
        shutil.copy2(source, input_dir / source.name)
    template = ROOT / "configs" / "microsoft" / "settings.ollama.yaml"
    shutil.copy2(template, workspace / "settings.yaml")
    shutil.copy2(ROOT / "configs" / "microsoft" / "env.ollama.example", workspace / ".env")
    print(f"Microsoft GraphRAG workspace prepared: {workspace}")


def ingest_primary() -> None:
    """Register the shared corpus through the production application's public document API."""
    base_url = os.getenv("PRIMARY_RAG_BASE_URL", "http://localhost:8080").rstrip("/")
    document_ids: list[str] = []
    for name, content in read_documents():
        response = post_json(
            f"{base_url}/api/documents",
            {
                "title": name,
                "content": content,
                "metadata": {"researchDataset": "software-architecture-v1", "sourceFile": name},
            },
        )
        if response.get("documentId"):
            document_ids.append(response["documentId"])
        print(f"primary registered {name}: {response.get('documentId', response)}")
    wait_for_primary_indexing(base_url, document_ids)


def ingest_lightrag() -> None:
    """Submit the shared corpus to a running LightRAG Server through its REST API."""
    base_url = os.getenv("LIGHTRAG_BASE_URL", "http://localhost:9621").rstrip("/")
    api_key = os.getenv("LIGHTRAG_API_KEY")
    track_ids: list[str] = []
    for name, content in read_documents():
        response = post_json(
            f"{base_url}/documents/text",
            {"text": content, "file_source": name},
            api_key,
        )
        if response.get("track_id"):
            track_ids.append(response["track_id"])
        print(f"LightRAG submitted {name}: {response}")
    for track_id in track_ids:
        wait_for_lightrag_track(base_url, track_id, api_key)


def wait_for_primary_indexing(base_url: str, document_ids: list[str]) -> None:
    """Wait until every newly registered production document reaches a terminal indexing state."""
    pending = set(document_ids)
    deadline = time.monotonic() + 600
    while pending and time.monotonic() < deadline:
        documents = get_json(f"{base_url}/api/documents")
        for document in documents:
            if document.get("documentId") in pending and document.get("status") in {"INDEXED", "FAILED", "DELETED"}:
                if document["status"] != "INDEXED":
                    raise RuntimeError(
                        f"Primary indexing failed for {document['documentId']}: {document.get('failureReason')}"
                    )
                pending.remove(document["documentId"])
        if pending:
            time.sleep(2)
    if pending:
        raise TimeoutError(f"Primary indexing did not finish within 600 seconds: {sorted(pending)}")


def wait_for_lightrag_track(base_url: str, track_id: str, api_key: str | None) -> None:
    """Wait for one LightRAG asynchronous insertion track and surface failed documents."""
    deadline = time.monotonic() + 600
    while time.monotonic() < deadline:
        status = get_json(f"{base_url}/documents/track_status/{track_id}", api_key)
        documents = status.get("documents", [])
        states = {str(document.get("status", "")).upper() for document in documents}
        if states and states <= {"PROCESSED", "FAILED"}:
            failures = [document for document in documents if str(document.get("status", "")).upper() == "FAILED"]
            if failures:
                raise RuntimeError(f"LightRAG indexing failed for track {track_id}: {failures}")
            return
        time.sleep(2)
    raise TimeoutError(f"LightRAG indexing did not finish within 600 seconds: {track_id}")


def index_system(system: str) -> None:
    """Index the shared corpus using the selected implementation's supported entry point."""
    if system == "microsoft":
        workspace = WORKSPACES / "microsoft"
        if not (workspace / "settings.yaml").exists():
            prepare_microsoft()
        print(run_command(["graphrag", "index", "--root", str(workspace)]))
    elif system == "lightrag":
        ingest_lightrag()
    elif system == "primary":
        ingest_primary()
    else:
        raise ValueError(f"Unsupported system: {system}")


def query_primary(question: str) -> tuple[str, list[Any], list[Any]]:
    """Query the production REST API and preserve its vector sources and graph facts."""
    base_url = os.getenv("PRIMARY_RAG_BASE_URL", "http://localhost:8080").rstrip("/")
    response = post_json(
        f"{base_url}/api/chat",
        {"question": question, "topK": 5, "similarityThreshold": 0.5},
    )
    return response.get("answer", ""), response.get("sources", []), response.get("graphFacts", [])


def query_microsoft(question: str, method: str) -> tuple[str, list[Any], list[Any]]:
    """Run the official Microsoft GraphRAG query CLI against its isolated workspace."""
    workspace = WORKSPACES / "microsoft"
    answer = run_command(
        [
            "graphrag",
            "query",
            "--root",
            str(workspace),
            "--method",
            method,
            question,
        ],
    )
    return answer, [], []


def query_lightrag(question: str, method: str) -> tuple[str, list[Any], list[Any]]:
    """Query a running LightRAG Server and retain any contexts or references it returns."""
    base_url = os.getenv("LIGHTRAG_BASE_URL", "http://localhost:9621").rstrip("/")
    response = post_json(
        f"{base_url}/query",
        {
            "query": question,
            "mode": method,
            "include_references": True,
            "include_chunk_content": True,
        },
        os.getenv("LIGHTRAG_API_KEY"),
    )
    answer = response.get("response") or response.get("answer") or response.get("data") or ""
    sources = response.get("references") or response.get("sources") or []
    return str(answer), list(sources), []


def run_queries(system: str, method: str) -> Path:
    """Execute all benchmark questions and write one normalized JSONL record per question."""
    query_functions = {
        "primary": lambda question: query_primary(question),
        "microsoft": lambda question: query_microsoft(question, method),
        "lightrag": lambda question: query_lightrag(question, method),
    }
    query_function = query_functions[system]
    RESULTS.mkdir(parents=True, exist_ok=True)
    output_path = RESULTS / f"{system}-{method}.jsonl"
    with output_path.open("w", encoding="utf-8") as output:
        for question in read_questions():
            started = time.perf_counter()
            record: dict[str, Any] = {
                "system": system,
                "method": method,
                "questionId": question["id"],
                "question": question["question"],
            }
            try:
                answer, sources, graph_facts = query_function(question["question"])
                record.update(
                    {
                        "answer": answer,
                        "sources": sources,
                        "graphFacts": graph_facts,
                        "error": None,
                    }
                )
            except (RuntimeError, urllib.error.URLError, TimeoutError) as error:
                record.update({"answer": "", "sources": [], "graphFacts": [], "error": str(error)})
            record["latencyMs"] = round((time.perf_counter() - started) * 1_000, 2)
            output.write(json.dumps(record, ensure_ascii=False) + "\n")
            print(f"{system} {question['id']}: {record['latencyMs']} ms")
    return output_path


def normalized_text(value: Any) -> str:
    """Flatten an answer or structured context into lowercase text for deterministic checks."""
    return json.dumps(value, ensure_ascii=False).lower() if not isinstance(value, str) else value.lower()


def evaluate(result_paths: list[Path]) -> tuple[Path, Path]:
    """Score term, relation, source coverage, latency, and failures without an LLM judge."""
    expected = {question["id"]: question for question in read_questions()}
    rows: list[dict[str, Any]] = []
    for result_path in result_paths:
        for line in result_path.read_text(encoding="utf-8").splitlines():
            result = json.loads(line)
            gold = expected[result["questionId"]]
            answer_text = normalized_text(result.get("answer", ""))
            all_text = normalized_text(
                [result.get("answer", ""), result.get("sources", []), result.get("graphFacts", [])]
            )
            terms = gold["expectedTerms"]
            relations = gold["expectedRelationCodes"]
            sources = gold["expectedSources"]
            rows.append(
                {
                    "system": result["system"],
                    "method": result["method"],
                    "questionId": result["questionId"],
                    "answerTermRecall": coverage(terms, answer_text),
                    "relationRecall": coverage(relations, all_text),
                    "sourceRecall": coverage(sources, all_text),
                    "latencyMs": result["latencyMs"],
                    "failed": bool(result.get("error")),
                }
            )

    REPORTS.mkdir(parents=True, exist_ok=True)
    details_path = REPORTS / "comparison-details.csv"
    with details_path.open("w", encoding="utf-8-sig", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=list(rows[0].keys()) if rows else [])
        if rows:
            writer.writeheader()
            writer.writerows(rows)

    grouped: dict[tuple[str, str], list[dict[str, Any]]] = {}
    for row in rows:
        grouped.setdefault((row["system"], row["method"]), []).append(row)
    summary = [
        {
            "system": key[0],
            "method": key[1],
            "questionCount": len(group),
            "answerTermRecall": average(group, "answerTermRecall"),
            "relationRecall": average(group, "relationRecall"),
            "sourceRecall": average(group, "sourceRecall"),
            "averageLatencyMs": average(group, "latencyMs"),
            "failureCount": sum(1 for row in group if row["failed"]),
        }
        for key, group in sorted(grouped.items())
    ]
    summary_path = REPORTS / "comparison-summary.json"
    summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    return details_path, summary_path


def coverage(expected: list[str], actual: str) -> float:
    """Return case-insensitive exact substring recall for a small declared gold set."""
    if not expected:
        return 1.0
    return round(sum(1 for item in expected if item.lower() in actual) / len(expected), 4)


def average(rows: list[dict[str, Any]], key: str) -> float:
    """Return a stable four-decimal arithmetic mean for one numeric result column."""
    return round(sum(float(row[key]) for row in rows) / len(rows), 4)


def smoke_check() -> dict[str, Any]:
    """Validate pinned packages, dataset files, request schemas, and GraphRAG configuration."""
    manifest = json.loads((DATASET / "manifest.json").read_text(encoding="utf-8"))
    declared_documents = manifest["documents"]
    actual_documents = [name for name, _ in read_documents()]
    if declared_documents != actual_documents:
        raise RuntimeError(
            f"Dataset manifest differs from document directory: {declared_documents} != {actual_documents}"
        )
    if not read_questions():
        raise RuntimeError("Comparison dataset must contain at least one question.")

    microsoft_workspace = WORKSPACES / "microsoft"
    if not (microsoft_workspace / "settings.yaml").exists():
        prepare_microsoft()
    from graphrag.config.load_config import load_config

    original_argv = sys.argv
    try:
        # LightRAG 1.5.4 parses server options while importing API modules.
        # Hide this harness's `smoke` argument so its parser does not reject it.
        sys.argv = [sys.argv[0]]
        from lightrag.api.routers.document_routes import InsertTextRequest
        from lightrag.api.routers.query_routes import QueryRequest
    finally:
        sys.argv = original_argv

    graph_rag_config = load_config(microsoft_workspace)
    QueryRequest(
        query="smoke query",
        mode="hybrid",
        include_references=True,
        include_chunk_content=True,
    )
    InsertTextRequest(text="smoke document", file_source="smoke.txt")
    return {
        "graphrag": importlib.metadata.version("graphrag"),
        "lightrag-hku": importlib.metadata.version("lightrag-hku"),
        "microsoftCompletionProvider": graph_rag_config.completion_models[
            "default_completion_model"
        ].model_provider,
        "microsoftCompletionModel": graph_rag_config.completion_models[
            "default_completion_model"
        ].model,
        "microsoftEmbeddingModel": graph_rag_config.embedding_models[
            "default_embedding_model"
        ].model,
        "documentCount": len(actual_documents),
        "questionCount": len(read_questions()),
    }


def parse_args() -> argparse.Namespace:
    """Build the command line contract shared by local development and CI smoke checks."""
    parser = argparse.ArgumentParser(description=__doc__)
    subcommands = parser.add_subparsers(dest="command", required=True)

    prepare = subcommands.add_parser("prepare", help="Create Microsoft workspace and copy the shared corpus.")
    prepare.add_argument("--system", choices=["microsoft"], default="microsoft")

    index = subcommands.add_parser("index", help="Index or submit the shared corpus.")
    index.add_argument("--system", choices=["primary", "microsoft", "lightrag"], required=True)

    run = subcommands.add_parser("run", help="Run every benchmark question.")
    run.add_argument("--system", choices=["primary", "microsoft", "lightrag"], required=True)
    run.add_argument(
        "--method",
        default="hybrid",
        help="Microsoft: local/global/drift/basic; LightRAG: local/global/hybrid/naive/mix.",
    )

    evaluate_parser = subcommands.add_parser("evaluate", help="Evaluate normalized JSONL result files.")
    evaluate_parser.add_argument("results", nargs="+", type=Path)
    subcommands.add_parser("smoke", help="Validate packages, dataset, schemas, and offline configuration.")
    return parser.parse_args()


def main() -> None:
    """Dispatch the selected reproducible research operation."""
    args = parse_args()
    if args.command == "prepare":
        prepare_microsoft()
    elif args.command == "index":
        index_system(args.system)
    elif args.command == "run":
        print(run_queries(args.system, args.method))
    elif args.command == "evaluate":
        details, summary = evaluate(args.results)
        print(details)
        print(summary)
    elif args.command == "smoke":
        print(json.dumps(smoke_check(), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
