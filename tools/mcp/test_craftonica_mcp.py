import base64
import json
import unittest

import craftonica_mcp


class FakeBridge:
    def state(self):
        return {"connected": True}

    def screenshot(self):
        return b"png"

    def action(self, arguments):
        return {"ok": True, "action": arguments["action"]}


class McpServerTest(unittest.TestCase):
    def setUp(self):
        self.bridge = FakeBridge()

    def test_initialize(self):
        response = craftonica_mcp.handle({"jsonrpc": "2.0", "id": 1, "method": "initialize"}, self.bridge)
        self.assertEqual("2024-11-05", response["result"]["protocolVersion"])
        self.assertIn("tools", response["result"]["capabilities"])

    def test_lists_three_tools(self):
        response = craftonica_mcp.handle({"jsonrpc": "2.0", "id": 2, "method": "tools/list"}, self.bridge)
        names = [tool["name"] for tool in response["result"]["tools"]]
        self.assertEqual(["minecraft_state", "minecraft_screenshot", "minecraft_action"], names)

    def test_exposes_native_gui_actions(self):
        action = craftonica_mcp.tools()[2]
        actions = action["inputSchema"]["properties"]["action"]["enum"]
        self.assertIn("gui_button", actions)
        self.assertIn("gui_click", actions)
        self.assertIn("load_world", actions)
        self.assertIn("pause_menu", actions)
        self.assertIn("button_id", action["inputSchema"]["properties"])
        self.assertIn("screen", action["inputSchema"]["properties"])
        self.assertIn("x", action["inputSchema"]["properties"])
        self.assertIn("y", action["inputSchema"]["properties"])
        self.assertIn("index", action["inputSchema"]["properties"])

    def test_returns_image_content(self):
        request = {"jsonrpc": "2.0", "id": 3, "method": "tools/call", "params": {"name": "minecraft_screenshot"}}
        response = craftonica_mcp.handle(request, self.bridge)
        image = response["result"]["content"][0]
        self.assertEqual("image/png", image["mimeType"])
        self.assertEqual(b"png", base64.b64decode(image["data"]))

    def test_forwards_action(self):
        request = {
            "jsonrpc": "2.0",
            "id": 4,
            "method": "tools/call",
            "params": {"name": "minecraft_action", "arguments": {"action": "use"}},
        }
        response = craftonica_mcp.handle(request, self.bridge)
        payload = json.loads(response["result"]["content"][0]["text"])
        self.assertEqual({"ok": True, "action": "use"}, payload)

    def test_rejects_malformed_json_with_parse_error(self):
        response = craftonica_mcp.process_line("{", self.bridge)
        self.assertEqual(-32700, response["error"]["code"])

    def test_rejects_invalid_request(self):
        response = craftonica_mcp.process_line("[]", self.bridge)
        self.assertEqual(-32600, response["error"]["code"])

    def test_does_not_reply_to_notifications(self):
        line = json.dumps({"jsonrpc": "2.0", "method": "notifications/cancelled"})
        self.assertIsNone(craftonica_mcp.process_line(line, self.bridge))

    def test_replies_to_invalid_idless_object(self):
        response = craftonica_mcp.process_line("{}", self.bridge)
        self.assertEqual(-32600, response["error"]["code"])

    def test_rejects_non_object_params(self):
        request = {"jsonrpc": "2.0", "id": 6, "method": "tools/call", "params": []}
        response = craftonica_mcp.handle(request, self.bridge)
        self.assertEqual(-32602, response["error"]["code"])


if __name__ == "__main__":
    unittest.main()
