import unittest
from check_repository import inspect_entry

class RepositoryChecks(unittest.TestCase):
    def test_accepts_source(self):
        self.assertEqual([], inspect_entry("app/src/main/java/Main.java", b"class Main {}"))
    def test_accepts_readme_screenshot(self):
        self.assertEqual([], inspect_entry("docs/screenshots/car-library.jpg", b"image"))
    def test_rejects_build_outputs_even_when_forced_into_git(self):
        self.assertTrue(inspect_entry("app/build/outputs/app.apk", b"apk"))
    def test_rejects_unlisted_text_assets(self):
        self.assertTrue(inspect_entry("app/src/main/assets/session-notes.txt", b"working notes"))
    def test_rejects_keys_in_source_directories(self):
        self.assertTrue(inspect_entry("app/src/main/assets/signing.jks", b"key"))
    def test_rejects_unknown_documentation(self):
        self.assertTrue(inspect_entry("docs/session-notes.md", b"notes"))
    def test_rejects_literal_credentials_without_echoing_them(self):
        secret = b"gh" + b"p_" + b"a" * 36
        issues = inspect_entry("app/src/main/java/Main.java", secret)
        self.assertTrue(issues)
        self.assertNotIn(secret.decode(), str(issues))
    def test_rejects_symlinks(self):
        self.assertTrue(inspect_entry("README.md", b"elsewhere", "120000"))
    def test_rejects_checked_in_reporting_destination(self):
        data = b"aux.usageEndpoint=https://reports.example.invalid/ingest\n"
        self.assertTrue(inspect_entry("gradle.properties", data))
    def test_rejects_reporting_destination_default_in_build_script(self):
        data = b'val usageEndpoint = findProperty("aux.usageEndpoint") ?: "https://reports.example.invalid/ingest"\n'
        self.assertTrue(inspect_entry("app/build.gradle.kts", data))
    def test_accepts_private_reporting_configuration_without_a_destination(self):
        self.assertEqual([], inspect_entry("gradle.properties", b"aux.usageEndpoint=\n"))
        self.assertEqual([], inspect_entry("app/build.gradle.kts", b'val usageEndpoint = providers.environmentVariable("AUX_USAGE_ENDPOINT").orElse("")\n'))

if __name__ == "__main__": unittest.main()
