"""Readiness regressions; run with python3 -m unittest discover -s scripts."""
import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


class ReadinessTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        spec = importlib.util.spec_from_file_location(
            "crash_crawl", Path(__file__).with_name("crash_crawl.py")
        )
        cls.crawl = importlib.util.module_from_spec(spec)
        original = os.getcwd()
        try:
            os.chdir(cls.temp.name)
            spec.loader.exec_module(cls.crawl)
        finally:
            os.chdir(original)

    @classmethod
    def tearDownClass(cls):
        cls.crawl.log_file.close()
        cls.temp.cleanup()

    def wait_with_focus(self, focus):
        with (
            patch.object(self.crawl, "foreground", return_value=self.crawl.ACTIVITY),
            patch.object(self.crawl, "focused_window", return_value=focus),
            patch.object(self.crawl, "dismiss_external_launcher_anr") as dismiss,
            patch.object(self.crawl, "log"),
            patch.object(self.crawl.time, "time", side_effect=[0, 0, 41]),
            patch.object(self.crawl.time, "sleep"),
        ):
            return self.crawl.wait_resumed(40), dismiss.call_count

    def test_focused_activity_does_not_wait_for_uiautomator_idle(self):
        ready, dumps = self.wait_with_focus(self.crawl.ACTIVITY)
        self.assertTrue(ready)
        self.assertEqual(dumps, 0)

    def test_anr_dialog_containing_package_is_not_app_focus(self):
        ready, _ = self.wait_with_focus("Application Error: " + self.crawl.PKG)
        self.assertFalse(ready)

    def test_unknown_focus_does_not_prove_readiness(self):
        ready, _ = self.wait_with_focus("")
        self.assertFalse(ready)

    def test_another_app_activity_does_not_prove_main_readiness(self):
        ready, _ = self.wait_with_focus(self.crawl.PKG + "/net.gozar.app.ConfigCenterActivity")
        self.assertFalse(ready)

    def test_failed_dump_cannot_read_a_previous_screen(self):
        commands = []

        def fake_adb(*args, **kwargs):
            commands.append((args, kwargs))
            if args == ("shell", "cat", "/sdcard/ui.xml"):
                return '<hierarchy><node package="com.ghajarvpn.app" clickable="true" text="Old screen" bounds="[0,0][100,100]"/></hierarchy>'
            return "ERROR: could not get idle state."

        with (
            patch.object(self.crawl, "adb", side_effect=fake_adb),
            patch.object(self.crawl, "log"),
            patch.object(self.crawl.time, "sleep"),
        ):
            self.assertEqual(self.crawl.dump(), [])
        self.assertTrue(all("timeout -s KILL 15" in args[1] for args, _ in commands))
        self.assertTrue(all("&& cat /sdcard/ui.xml" in args[1] for args, _ in commands))
        self.assertTrue(all(kwargs["timeout"] > 15 for _, kwargs in commands))


if __name__ == "__main__":
    unittest.main()
