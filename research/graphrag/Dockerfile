FROM python:3.12.10-slim-bookworm

ARG UV_VERSION=0.11.32
ENV PYTHONUNBUFFERED=1
ENV UV_LINK_MODE=copy
WORKDIR /opt/research

RUN pip install --no-cache-dir "uv==${UV_VERSION}"

COPY pyproject.toml uv.lock README.md ./
COPY rag_compare ./rag_compare
COPY configs ./configs
COPY dataset ./dataset

RUN uv sync --frozen --no-dev --no-cache

ENV PATH="/opt/research/.venv/bin:${PATH}"
CMD ["rag-compare", "--help"]
