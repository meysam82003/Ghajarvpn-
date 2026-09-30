import copy
import unittest

from reuse_ci_build import crash_certified, reusable


class BuildReuseTests(unittest.TestCase):
    def setUp(self):
        self.workflow = {"env": {"NDK_VERSION": "28.2.13676358"}, "jobs": {
            "build": {"runs-on": "ubuntu-latest", "steps": [
                {"run": "build native cores"}, {"run": "assemble APKs and test"}]}}}
        self.compare = {"status": "ahead", "files": [{"filename": "scripts/crash_crawl.py"}]}
        self.jobs = [{"name": "build", "conclusion": "success"}]
        self.artifacts = [{"name": name, "expired": False} for name in
                          ("Ghajarvpn-Android-debug", "Ghajarvpn-Android-preview")]

    def can_reuse(self, current=None):
        return reusable(self.compare, self.jobs, self.artifacts,
                        self.workflow, current or self.workflow)

    def test_harness_only_change_reuses_completed_apks(self):
        self.assertTrue(self.can_reuse())

    def test_application_source_change_requires_build(self):
        self.compare["files"].append({"filename": "app/src/main/java/MainActivity.kt"})
        self.assertFalse(self.can_reuse())

    def test_changed_native_build_pin_requires_build(self):
        current = copy.deepcopy(self.workflow)
        current["jobs"]["build"]["steps"][0]["env"] = {"ZEPTUN_COMMIT": "new-pin"}
        self.assertFalse(self.can_reuse(current))

    def test_failed_build_cannot_be_reused(self):
        self.jobs[0]["conclusion"] = "failure"
        self.assertFalse(self.can_reuse())

    def test_expired_apk_cannot_be_reused(self):
        self.artifacts[0]["expired"] = True
        self.assertFalse(self.can_reuse())

    def test_unrelated_commit_history_cannot_be_reused(self):
        self.compare["status"] = "diverged"
        self.assertFalse(self.can_reuse())

    def test_successful_crash_check_can_be_reused_for_identical_source_tree(self):
        jobs = [{"name": n, "conclusion": "success"} for n in ("prepare", "crash-hunt")]
        self.assertTrue(crash_certified({"conclusion": "success"}, jobs, "tree", "tree"))

    def test_harness_changes_require_a_new_crash_check(self):
        jobs = [{"name": n, "conclusion": "success"} for n in ("prepare", "crash-hunt")]
        self.assertFalse(crash_certified({"conclusion": "success"}, jobs, "old-tree", "new-tree"))

    def test_failed_run_never_certifies_crash_check(self):
        jobs = [{"name": n, "conclusion": "success"} for n in ("prepare", "crash-hunt")]
        self.assertFalse(crash_certified({"conclusion": "failure"}, jobs, "tree", "tree"))


if __name__ == "__main__":
    unittest.main()
