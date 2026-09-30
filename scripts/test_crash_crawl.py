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

    def test_android_14_focus_is_in_the_display_section(self):
        def fake_adb(*args):
            if args == ("shell", "dumpsys", "window"):
                return "WINDOW MANAGER DISPLAY CONTENTS\n  mCurrentFocus=Window{abc u0 " + self.crawl.ACTIVITY + "}\n"
            if args == ("shell", "dumpsys", "window", "windows"):
                return "WINDOW MANAGER WINDOWS\n  Window #0 Window{abc u0 " + self.crawl.ACTIVITY + "}\n"
            self.fail("Unexpected adb command: " + repr(args))

        with patch.object(self.crawl, "adb", side_effect=fake_adb):
            self.assertEqual(self.crawl.focused_window(), self.crawl.ACTIVITY)

    def test_failed_dump_cannot_read_a_previous_screen(self):
        commands = []

        def fake_adb(*args, **kwargs):
            commands.append((args, kwargs))
            if args == ("shell", "cat", "/sdcard/ui.xml"):
                return '<hierarchy><node package="com.ghajarvpn.app" clickable="true" text="Old screen" bounds="[0,0][100,100]"/></hierarchy>'
            return "ERROR: could not get idle state."

        with (
            patch.object(self.crawl, "adb", side_effect=fake_adb),
            patch.object(self.crawl, "foreground", return_value=self.crawl.ACTIVITY),
            patch.object(self.crawl, "log"),
            patch.object(self.crawl.time, "sleep"),
        ):
            self.assertEqual(self.crawl.dump(), [])
        self.assertTrue(all("timeout -s KILL 15" in args[1] for args, _ in commands))
        self.assertTrue(all("&& cat /sdcard/ui.xml" in args[1] for args, _ in commands))
        self.assertTrue(all(kwargs["timeout"] > 15 for _, kwargs in commands))

    def vpn_xml(self, package="com.android.vpndialogs", button="button2",
                bounds="[20,40][120,80]", enabled="true"):
        return (f'<?xml version="1.0"?><hierarchy><node package="{package}" '
                f'resource-id="android:id/{button}" text="لغو" clickable="true" '
                f'enabled="{enabled}" bounds="{bounds}"/></hierarchy>')

    def test_vpn_consent_cancels_localized_negative_button(self):
        for fg in ("com.android.vpndialogs/.ConfirmDialog",
                   "com.android.vpndialogs/com.android.vpndialogs.ConfirmDialog"):
            with (
                self.subTest(fg=fg),
                patch.object(self.crawl, "foreground", return_value=fg),
                patch.object(self.crawl, "hierarchy", return_value=self.vpn_xml()),
                patch.object(self.crawl, "adb") as adb,
                patch.object(self.crawl, "log"),
            ):
                self.assertTrue(self.crawl.dismiss_vpn_consent())
                adb.assert_called_once_with("shell", "input", "tap", "70", "60")

    def test_vpn_handler_does_not_dismiss_app_or_anr_windows(self):
        for fg in (self.crawl.ACTIVITY, "Application Error: " + self.crawl.PKG,
                   "com.android.vpndialogs/.OtherActivity"):
            with self.subTest(fg=fg), patch.object(self.crawl, "hierarchy") as dump:
                self.assertFalse(self.crawl.dismiss_vpn_consent(fg))
                dump.assert_not_called()

    def test_vpn_handler_rejects_unsafe_or_stale_hierarchies(self):
        fg = "com.android.vpndialogs/.ConfirmDialog"
        for xml in ("", '<?xml version="1.0"?><broken',
                    self.vpn_xml(package=self.crawl.PKG),
                    self.vpn_xml(button="button1"),
                    self.vpn_xml(enabled="false"),
                    self.vpn_xml(bounds="[0,0][0,0]"),
                    self.vpn_xml(bounds="invalid")):
            with (
                self.subTest(xml=xml),
                patch.object(self.crawl, "hierarchy", return_value=xml),
                patch.object(self.crawl, "adb") as adb,
            ):
                self.assertFalse(self.crawl.dismiss_vpn_consent(fg))
                adb.assert_not_called()
        with (
            patch.object(self.crawl, "hierarchy", return_value=self.vpn_xml()),
            patch.object(self.crawl, "foreground", return_value=self.crawl.ACTIVITY),
            patch.object(self.crawl, "adb") as adb,
        ):
            self.assertFalse(self.crawl.dismiss_vpn_consent(fg))
            adb.assert_not_called()

    def test_wait_resumed_recovers_vpn_prompt_then_requires_app_focus(self):
        fg = "com.android.vpndialogs/.ConfirmDialog"
        with (
            patch.object(self.crawl, "foreground", side_effect=[fg, fg, self.crawl.ACTIVITY]),
            patch.object(self.crawl, "focused_window", side_effect=[fg, self.crawl.ACTIVITY]),
            patch.object(self.crawl, "hierarchy", return_value=self.vpn_xml()),
            patch.object(self.crawl, "adb") as adb,
            patch.object(self.crawl, "dismiss_external_launcher_anr") as anr,
            patch.object(self.crawl, "log"),
            patch.object(self.crawl.time, "sleep"),
        ):
            self.assertTrue(self.crawl.wait_resumed(40))
            adb.assert_called_once_with("shell", "input", "tap", "70", "60")
            anr.assert_not_called()

    def test_dump_recovers_vpn_prompt_and_reads_fresh_app_nodes(self):
        xml = ('<?xml version="1.0"?><hierarchy><node package="com.ghajarvpn.app" '
               'clickable="true" text="Home" bounds="[0,0][100,100]"/></hierarchy>')
        with (
            patch.object(self.crawl, "dismiss_vpn_consent", side_effect=[True, False]),
            patch.object(self.crawl, "hierarchy", return_value=xml) as dump,
            patch.object(self.crawl.time, "sleep"),
        ):
            self.assertEqual(self.crawl.dump(), [("Home Home", (50, 50))])
            dump.assert_called_once()

    def test_repeated_vpn_prompt_does_not_prove_app_readiness(self):
        with (
            patch.object(self.crawl, "dismiss_vpn_consent", return_value=True),
            patch.object(self.crawl, "log"),
            patch.object(self.crawl.time, "sleep"),
        ):
            self.assertEqual(self.crawl.dump(), [])


if __name__ == "__main__":
    unittest.main()
