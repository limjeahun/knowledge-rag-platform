# Apache Jena Fuseki

This image pins Apache Jena Fuseki `6.1.0` and verifies the Maven Central artifact
with its published SHA-1 before building. The container runs as an unprivileged
user and stores the TDB2 dataset under `/fuseki/databases`.

The Compose service publishes the `knowledge` dataset at:

```text
http://localhost:3030/knowledge
```

The dataset is an RDF projection. PostgreSQL remains the source of truth, so it
must be possible to rebuild this volume from registered knowledge documents.

