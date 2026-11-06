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

if __name__ == "__main__": unittest.main()
