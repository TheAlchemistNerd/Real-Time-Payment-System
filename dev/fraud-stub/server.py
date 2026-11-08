"""Deterministic local provider fixture for the development Compose stack."""
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class FraudHandler(BaseHTTPRequestHandler):
    def reply(self, status, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/health":
            self.reply(200, {"status": "UP", "provider": "local-fixture"})
        else:
            self.reply(404, {"error": "Unknown fixture endpoint"})

    def do_POST(self):
        if self.path != "/v1/transactions/check":
            self.reply(404, {"error": "Unknown fixture endpoint"})
            return
        try:
            size = int(self.headers.get("Content-Length", "0"))
            if not 0 < size <= 65536:
                raise ValueError("Invalid request size")
            request = json.loads(self.rfile.read(size))
            transaction_id = request["transaction_id"]
            if not isinstance(transaction_id, str) or not transaction_id.strip():
                raise ValueError("Missing transaction ID")
        except (ValueError, KeyError, TypeError):
            self.reply(400, {"error": "A JSON transaction_id is required"})
            return
        self.reply(200, {
            "transaction_id": transaction_id,
            "risk_score": 0.1,
            "decision": "APPROVE",
            "reason": "Local development fixture",
            "confidence": 1.0,
        })


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8080), FraudHandler).serve_forever()
