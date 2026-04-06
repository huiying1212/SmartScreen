"""
RI4SU 用户实验数据收集后端。

API:
  POST /api/register          — 注册参与者（返回 participant_id）
  POST /api/logs              — 批量上报交互日志
  POST /api/context           — 上报上下文采集数据
  GET  /api/export/logs       — 导出全部日志（CSV）
  GET  /api/export/context    — 导出全部上下文数据（JSONL）
  GET  /api/stats             — 查看实验统计概览
  GET  /health                — 健康检查
"""

import csv
import io
import json
import os
import sqlite3
import time
from datetime import datetime
from functools import wraps

from flask import Flask, g, jsonify, request, Response

app = Flask(__name__)
DB_PATH = os.environ.get("RI4SU_DB_PATH", "ri4su_experiment.db")
API_KEY = os.environ.get("RI4SU_API_KEY", "")  # 可选，设置后所有请求需带 header

# ── 数据库 ──────────────────────────────────────────────────

def get_db():
    if "db" not in g:
        g.db = sqlite3.connect(DB_PATH)
        g.db.row_factory = sqlite3.Row
        g.db.execute("PRAGMA journal_mode=WAL")
    return g.db

@app.teardown_appcontext
def close_db(exc):
    db = g.pop("db", None)
    if db is not None:
        db.close()

def init_db():
    db = sqlite3.connect(DB_PATH)
    db.executescript("""
        CREATE TABLE IF NOT EXISTS participants (
            id          TEXT PRIMARY KEY,
            device_model TEXT,
            android_ver  TEXT,
            registered_at TEXT DEFAULT (datetime('now')),
            extra        TEXT
        );

        CREATE TABLE IF NOT EXISTS interaction_logs (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            participant_id  TEXT NOT NULL,
            event           TEXT NOT NULL,
            ts              INTEGER NOT NULL,
            event_time      TEXT,
            params          TEXT,
            received_at     TEXT DEFAULT (datetime('now')),
            FOREIGN KEY (participant_id) REFERENCES participants(id)
        );

        CREATE TABLE IF NOT EXISTS context_data (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            participant_id  TEXT NOT NULL,
            ts              INTEGER NOT NULL,
            payload         TEXT NOT NULL,
            received_at     TEXT DEFAULT (datetime('now')),
            FOREIGN KEY (participant_id) REFERENCES participants(id)
        );

        CREATE INDEX IF NOT EXISTS idx_logs_participant ON interaction_logs(participant_id);
        CREATE INDEX IF NOT EXISTS idx_logs_event ON interaction_logs(event);
        CREATE INDEX IF NOT EXISTS idx_context_participant ON context_data(participant_id);
    """)
    db.close()

# ── 简易鉴权 ────────────────────────────────────────────────

def require_api_key(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        if API_KEY and request.headers.get("X-API-Key") != API_KEY:
            return jsonify({"error": "unauthorized"}), 401
        return f(*args, **kwargs)
    return decorated

# ── API ─────────────────────────────────────────────────────

@app.route("/health")
def health():
    return jsonify({"status": "ok", "time": datetime.utcnow().isoformat()})

@app.route("/api/register", methods=["POST"])
@require_api_key
def register():
    """注册参与者。客户端首次启动时调用。"""
    data = request.get_json(force=True)
    pid = data.get("participant_id", "").strip()
    if not pid:
        return jsonify({"error": "participant_id required"}), 400

    db = get_db()
    existing = db.execute("SELECT id FROM participants WHERE id=?", (pid,)).fetchone()
    if existing:
        return jsonify({"participant_id": pid, "status": "already_registered"})

    db.execute(
        "INSERT INTO participants (id, device_model, android_ver, extra) VALUES (?,?,?,?)",
        (pid, data.get("device_model", ""), data.get("android_ver", ""), data.get("extra", "")),
    )
    db.commit()
    return jsonify({"participant_id": pid, "status": "registered"}), 201

@app.route("/api/logs", methods=["POST"])
@require_api_key
def upload_logs():
    """批量上报交互日志。接受 JSON 数组。"""
    data = request.get_json(force=True)
    logs = data if isinstance(data, list) else data.get("logs", [])
    if not logs:
        return jsonify({"error": "empty logs"}), 400

    db = get_db()
    inserted = 0
    for entry in logs:
        pid = entry.get("participant_id", "unknown")
        event = entry.get("event", "")
        ts = entry.get("ts", int(time.time() * 1000))
        event_time = entry.get("time", "")
        params = json.dumps(entry.get("params", {}), ensure_ascii=False) if entry.get("params") else None

        db.execute(
            "INSERT INTO interaction_logs (participant_id, event, ts, event_time, params) VALUES (?,?,?,?,?)",
            (pid, event, ts, event_time, params),
        )
        inserted += 1

    db.commit()
    return jsonify({"inserted": inserted})

@app.route("/api/context", methods=["POST"])
@require_api_key
def upload_context():
    """上报上下文采集数据。"""
    data = request.get_json(force=True)
    pid = data.get("participant_id", "unknown")
    ts = data.get("timestamp", int(time.time() * 1000))
    payload = json.dumps(data, ensure_ascii=False)

    db = get_db()
    db.execute(
        "INSERT INTO context_data (participant_id, ts, payload) VALUES (?,?,?)",
        (pid, ts, payload),
    )
    db.commit()
    return jsonify({"status": "ok"})

# ── 数据导出 ────────────────────────────────────────────────

@app.route("/api/export/logs")
@require_api_key
def export_logs():
    """导出全部交互日志为 CSV。"""
    db = get_db()
    rows = db.execute(
        "SELECT participant_id, event, ts, event_time, params, received_at FROM interaction_logs ORDER BY ts"
    ).fetchall()

    output = io.StringIO()
    writer = csv.writer(output)
    writer.writerow(["participant_id", "event", "ts", "event_time", "params", "received_at"])
    for r in rows:
        writer.writerow([r["participant_id"], r["event"], r["ts"], r["event_time"], r["params"], r["received_at"]])

    return Response(
        output.getvalue(),
        mimetype="text/csv",
        headers={"Content-Disposition": "attachment; filename=interaction_logs.csv"},
    )

@app.route("/api/export/esm")
@require_api_key
def export_esm():
    """导出 ESM 问卷数据为 CSV（从交互日志中提取 esm_submit 事件）。"""
    db = get_db()
    rows = db.execute(
        "SELECT participant_id, ts, event_time, params, received_at "
        "FROM interaction_logs WHERE event IN ('esm_submit','esm_skip','esm_shown') ORDER BY ts"
    ).fetchall()

    output = io.StringIO()
    writer = csv.writer(output)
    writer.writerow(["participant_id", "ts", "event_time", "event_type",
                      "q1_reminder_helpful", "q2_usage_state", "q3_face_accurate", "received_at"])
    for r in rows:
        params = json.loads(r["params"]) if r["params"] else {}
        # 从 params 中判断事件类型
        event_type = "submit" if params.get("q1_reminder_helpful") else ("skip" if not params else "shown")
        writer.writerow([
            r["participant_id"], r["ts"], r["event_time"], event_type,
            params.get("q1_reminder_helpful", ""),
            params.get("q2_usage_state", ""),
            params.get("q3_face_accurate", ""),
            r["received_at"],
        ])

    return Response(
        output.getvalue(),
        mimetype="text/csv",
        headers={"Content-Disposition": "attachment; filename=esm_responses.csv"},
    )

@app.route("/api/export/context")
@require_api_key
def export_context():
    """导出全部上下文数据为 JSONL。"""
    db = get_db()
    rows = db.execute(
        "SELECT participant_id, ts, payload, received_at FROM context_data ORDER BY ts"
    ).fetchall()

    lines = []
    for r in rows:
        lines.append(json.dumps({
            "participant_id": r["participant_id"],
            "ts": r["ts"],
            "data": json.loads(r["payload"]),
            "received_at": r["received_at"],
        }, ensure_ascii=False))

    return Response(
        "\n".join(lines),
        mimetype="application/x-ndjson",
        headers={"Content-Disposition": "attachment; filename=context_data.jsonl"},
    )

@app.route("/api/stats")
@require_api_key
def stats():
    """实验统计概览。"""
    db = get_db()
    participants = db.execute("SELECT COUNT(*) as c FROM participants").fetchone()["c"]
    total_logs = db.execute("SELECT COUNT(*) as c FROM interaction_logs").fetchone()["c"]
    total_context = db.execute("SELECT COUNT(*) as c FROM context_data").fetchone()["c"]

    per_participant = db.execute("""
        SELECT participant_id, COUNT(*) as log_count,
               MIN(event_time) as first_event, MAX(event_time) as last_event
        FROM interaction_logs GROUP BY participant_id
    """).fetchall()

    return jsonify({
        "participants": participants,
        "total_interaction_logs": total_logs,
        "total_context_records": total_context,
        "per_participant": [
            {"id": r["participant_id"], "logs": r["log_count"],
             "first": r["first_event"], "last": r["last_event"]}
            for r in per_participant
        ],
    })

# ── 启动 ────────────────────────────────────────────────────

init_db()

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5000))
    app.run(host="0.0.0.0", port=port, debug=False)
