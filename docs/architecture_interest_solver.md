# Interest-Driven Dataflow and Competitive Query Planning: Normalized Settlement

This document synthesizes the project's abstractions with canonical terminology and foundational literature from computer science, information retrieval, and systems design.

---

## 1. Gates as Operators in a Dataflow Graph
- **Project abstraction:** Each "gate" (decompressor, extractor, codec, indexer) is a transformation that projects data from an opaque or encoded state toward plaintext or semantic tags.
- **Canonical mapping:**
  - **Operator** in a **dataflow graph** (Abadi et al., Aurora; Zaharia et al., D-Streams).
  - **Access path** or **physical operator** in a **query plan** (Selinger et al., Graefe).

## 2. Interest as a Query or Goal State
- **Project abstraction:** "Interest" is a region, file, record, or semantic target to extract from a source.
- **Canonical mapping:**
  - **Query** in databases/IR.
  - **Goal state** in automated planning (Ghallab et al., Russell & Norvig).

## 3. Interest Solver as a Query Planner/Automated Planner
- **Project abstraction:** The "interest solver" selects and sequences gates to reach the target, optimizing for cost, speed, or fidelity.
- **Canonical mapping:**
  - **Query planner/optimizer** (Selinger et al., Graefe).
  - **Automated planner** (Ghallab et al., Russell & Norvig).
  - **Content negotiation** (RFC 2295).

## 4. Competitive Game of Plans
- **Project abstraction:** Multiple possible gate sequences "compete" to satisfy the interest most efficiently.
- **Canonical mapping:**
  - **Cost-based plan selection** (Selinger et al.).
  - **Multi-agent system/game-theoretic optimization** (Shoham & Leyton-Brown).

## 5. Composable, Extensible Pipelines
- **Project abstraction:** Gates can be composed, extended, and recursively applied, supporting new formats, codecs, and strategies.
- **Canonical mapping:**
  - **Composable operator DAGs** (Aurora, D-Streams).
  - **Extensible query plans** (Graefe).

## 6. Final Output: Plaintext, Tags, Hashes
- **Project abstraction:** The pipeline ultimately yields UTF-8 text, tags, or hashes—fully accessible and indexable.
- **Canonical mapping:**
  - **Materialized query result** (databases).
  - **Extracted features/metadata** (Tika, ffmpeg, IR).

---

## Unified Model Statement

> The architecture models data access and transformation as a composable, competitive dataflow graph, where each "gate" (operator) projects data closer to a user-defined "interest" (query/goal). An "interest solver" (query planner/automated planner) selects and sequences gates, optimizing for cost and fidelity, drawing on principles from query optimization, dataflow systems, and multi-agent planning. The process is extensible, supporting new formats and codecs, and ultimately yields normalized, indexable outputs (plaintext, tags, hashes).

---

## Key Citations (Canonical Works)
- Selinger et al., "Access Path Selection in a Relational Database Management System" (1979)
- Graefe, "Query Evaluation Techniques for Large Databases" (1993)
- Abadi et al., "Aurora: a new model and architecture for data stream management" (2003)
- Zaharia et al., "Discretized Streams: Fault-Tolerant Streaming Computation at Scale" (2013)
- Ghallab, Nau, Traverso, "Automated Planning: Theory and Practice" (2004)
- Shoham & Leyton-Brown, "Multiagent Systems: Algorithmic, Game-Theoretic, and Logical Foundations" (2009)
- RFC 2295, "Content Negotiation in HTTP"
- Apache Tika documentation 