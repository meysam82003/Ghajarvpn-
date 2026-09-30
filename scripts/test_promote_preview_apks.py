import unittest

from promote_preview_apks import validate_badging


class PromotionMetadataTests(unittest.TestCase):
    valid = "package: name='com.ghajarvpn.app' versionCode='30025' versionName='1.0.10'\n"

    def test_optimized_matching_apk_is_accepted(self):
        validate_badging(self.valid, "1.0.10", "30025")

    def test_debuggable_apk_is_rejected(self):
        with self.assertRaises(ValueError):
            validate_badging(self.valid + "application-debuggable\n", "1.0.10", "30025")

    def test_wrong_version_code_is_rejected(self):
        with self.assertRaises(ValueError):
            validate_badging(self.valid, "1.0.10", "30026")

    def test_wrong_version_name_is_rejected(self):
        with self.assertRaises(ValueError):
            validate_badging(self.valid, "1.0.11", "30025")

    def test_wrong_application_id_is_rejected(self):
        with self.assertRaises(ValueError):
            validate_badging(self.valid.replace("com.ghajarvpn.app", "other.app"), "1.0.10", "30025")


if __name__ == "__main__":
    unittest.main()
