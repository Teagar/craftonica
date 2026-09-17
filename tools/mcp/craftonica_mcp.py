#!/usr/bin/env python3
import base64
import json
import os
import sys
import urllib.error
import urllib.request


SERVER_NAME = "craftonica-minecraft-1.7.10"
SERVER_VERSION = "0.1.0"
PROTOCOL_VERSION = "2024-11-05"


class BridgeClient:
    def __init__(self, base_url=None, token=None):
        self.base_url = (base_url or os.environ.get("CRAFTONICA_AUTOMATION_URL") or
                         "http://127.0.0.1:8765").rstrip("/")
        self.token = token or os.environ.get("CRAFTONICA_AUTOMATION_TOKEN")
        if not self.token:
            raise ValueError("CRAFTONICA_AUTOMATION_TOKEN is required")

    def state(self):
        return self._request("GET", "/v1/state")

    def screenshot(self):
        return self._request("GET", "/v1/screenshot", raw=True)

    def action(self, arguments):
        return self._request("POST", "/v1/action", arguments)

    def _request(self, method, path, body=None, raw=False):
        data = None if body is None else json.dumps(body).encode("utf-8")
        request = urllib.request.Request(self.base_url + path, data=data, method=method)
        request.add_header("Authorization", "Bearer " + self.token)
        if data is not None:
            request.add_header("Content-Type", "application/json")
        try:
            with urllib.request.urlopen(request, timeout=15) as response:
                payload = response.read()
        except urllib.error.HTTPError as error:
            payload = error.read().decode("utf-8", "replace")
            raise RuntimeError("bridge returned HTTP {}: {}".format(error.code, payload))
        except urllib.error.URLError as error:
            raise RuntimeError("cannot reach Craftonica bridge: {}".format(error.reason))
        return payload if raw else json.loads(payload.decode("utf-8"))


def tools():
    return [
        {
            "name": "minecraft_state",
            "description": "Read the current Minecraft client, player, world, screen and targeted block state.",
            "inputSchema": {"type": "object", "properties": {}, "additionalProperties": False},
        },
        {
            "name": "minecraft_screenshot",
            "description": "Capture the Minecraft framebuffer as a PNG image.",
            "inputSchema": {"type": "object", "properties": {}, "additionalProperties": False},
        },
        {
            "name": "minecraft_action",
            "description": (
                "Perform one client action. Actions: chat(text), look(yaw,pitch), "
                "select_hotbar(slot 1-9), key(key,pressed), use, attack, close_screen. "
                "Supported held keys: forward, back, left, right, jump, sneak."
            ),
            "inputSchema": {
                "type": "object",
                "properties": {
                    "action": {
                        "type": "string",
                        "enum": ["chat", "look", "select_hotbar", "key", "use", "attack", "close_screen"],
                    },
                    "text": {"type": "string"},
                    "yaw": {"type": "number"},
                    "pitch": {"type": "number"},
                    "slot": {"type": "integer", "minimum": 1, "maximum": 9},
                    "key": {"type": "string", "enum": ["forward", "back", "left", "right", "jump", "sneak"]},
                    "pressed": {"type": "boolean"},
                },
                "required": ["action"],
                "additionalProperties": False,
                "allOf": [
                    {"if": {"properties": {"action": {"const": "chat"}}}, "then": {"required": ["text"]}},
                    {"if": {"properties": {"action": {"const": "look"}}}, "then": {"required": ["yaw", "pitch"]}},
                    {"if": {"properties": {"action": {"const": "select_hotbar"}}}, "then": {"required": ["slot"]}},
                    {"if": {"properties": {"action": {"const": "key"}}}, "then": {"required": ["key", "pressed"]}},
                ],
            },
        },
    ]


def success(request_id, result):
    return {"jsonrpc": "2.0", "id": request_id, "result": result}


def failure(request_id, code, message):
    return {"jsonrpc": "2.0", "id": request_id, "error": {"code": code, "message": message}}


def handle(request, bridge):
    if not isinstance(request, dict) or request.get("jsonrpc") != "2.0" or not isinstance(request.get("method"), str):
        return failure(request.get("id") if isinstance(request, dict) else None, -32600, "invalid request")
    request_id = request.get("id")
    method = request.get("method")
    if method == "initialize":
        return success(request_id, {
            "protocolVersion": PROTOCOL_VERSION,
            "capabilities": {"tools": {}},
            "serverInfo": {"name": SERVER_NAME, "version": SERVER_VERSION},
        })
    if method == "notifications/initialized":
        return None
    if method == "ping":
        return success(request_id, {})
    if method == "tools/list":
        return success(request_id, {"tools": tools()})
    if method == "tools/call":
        params = request.get("params", {})
        if params is None:
            params = {}
        if not isinstance(params, dict):
            return failure(request_id, -32602, "params must be an object")
        name = params.get("name")
        arguments = params.get("arguments") or {}
        if not isinstance(arguments, dict):
            return failure(request_id, -32602, "arguments must be an object")
        try:
            if name == "minecraft_state":
                content = [{"type": "text", "text": json.dumps(bridge.state(), ensure_ascii=False, indent=2)}]
            elif name == "minecraft_screenshot":
                content = [{
                    "type": "image",
                    "data": base64.b64encode(bridge.screenshot()).decode("ascii"),
                    "mimeType": "image/png",
                }]
            elif name == "minecraft_action":
                content = [{"type": "text", "text": json.dumps(bridge.action(arguments), indent=2)}]
            else:
                return failure(request_id, -32602, "unknown tool: {}".format(name))
        except RuntimeError as error:
            return success(request_id, {"content": [{"type": "text", "text": str(error)}], "isError": True})
        return success(request_id, {"content": content, "isError": False})
    return failure(request_id, -32601, "method not found: {}".format(method))


def process_line(line, bridge):
    try:
        request = json.loads(line)
    except (TypeError, ValueError):
        return failure(None, -32700, "parse error")
    response = handle(request, bridge)
    if (isinstance(request, dict) and request.get("jsonrpc") == "2.0"
            and isinstance(request.get("method"), str) and "id" not in request):
        return None
    return response


def main():
    try:
        bridge = BridgeClient()
    except ValueError as error:
        print(str(error), file=sys.stderr)
        return 2

    for line in sys.stdin:
        if not line.strip():
            continue
        try:
            response = process_line(line, bridge)
        except Exception as error:
            response = failure(None, -32603, "internal error: {}".format(error))
        if response is not None:
            sys.stdout.write(json.dumps(response, separators=(",", ":")) + "\n")
            sys.stdout.flush()
    return 0


if __name__ == "__main__":
    sys.exit(main())
