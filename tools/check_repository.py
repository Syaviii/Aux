"""Check tracked source and release documentation before commits and pushes."""
import argparse
import io
from pathlib import PurePosixPath
import re
import subprocess
import sys

EXACT = {
    'app/src/main/assets/hosts.txt',
    'app/src/main/assets/licenses/ADB-Apache-LICENSE.txt',
    'app/src/main/assets/licenses/CarStream-LICENSE.txt',
    'app/src/main/assets/licenses/LADB-LICENSE.txt',
    'app/src/main/assets/licenses/NOTICE.txt',
    'app/src/main/assets/licenses/NewPipeExtractor-LICENSE.txt',
    'app/src/main/assets/licenses/SpeechRecognitionView-LICENSE.txt',

    ".gitignore", ".gitattributes", "LICENSE", "NOTICE", "README.md",
    "build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat",
    "app/.gitignore", "app/build.gradle.kts", "app/proguard-rules.pro", "setup/build.gradle.kts",
    "gradle/libs.versions.toml", "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties",
    "docs/icon.png", "docs/banner.svg",
    "tools/check_repository.py", "tools/test_check_repository.py", ".github/workflows/checks.yml",
}
SOURCE_EXTENSIONS = {".java", ".kt", ".xml", ".png", ".webp", ".jpg", ".css", ".js", ".mp3"}
FORBIDDEN_PARTS = {"build", ".gradle", ".kotlin", ".idea", "__pycache__", "keys", "backups", "artifacts"}
SECRETS = {
    "private key": rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----",
    "access token": rb"(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,}|AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{30,})",
    "literal credential": rb"(?im)^\s*(?:storePassword|keyPassword|password|api[_-]?key|access[_-]?token)\s*[=:]\s*[\"']?([A-Za-z0-9+/=_-]{12,})[\"']?\s*$",
    "local user path": rb"(?i)[a-z]:[\\/](?:users|projects)[\\/]",
}

def inspect_entry(name, data, mode="100644"):
    path = PurePosixPath(name)
    allowed = name in EXACT
    allowed |= name.startswith(("app/src/", "setup/src/")) and path.suffix in SOURCE_EXTENSIONS
    allowed |= name.startswith("docs/screenshots/") and path.suffix == ".png"
    allowed |= name.startswith("third-party/") and path.suffix == ".txt"
    problems = []
    if not allowed or any(part.lower() in FORBIDDEN_PARTS for part in path.parts):
        problems.append("path is outside the source allowlist")
    if mode not in {"100644", "100755"}: problems.append("only regular files are allowed")
    for label, pattern in SECRETS.items():
        if re.search(pattern, data): problems.append(label)
    return problems

def git(*args, input=None):
    return subprocess.check_output(["git", *args], input=input)

def entries(history):
    found = set()
    for line in git("ls-files", "--stage", "-z").split(b"\0"):
        if not line: continue
        meta, name = line.split(b"\t", 1)
        mode, oid, stage = meta.split()
        if stage != b"0": raise SystemExit("Resolve merge conflicts before checking")
        found.add((mode.decode(), oid.decode(), name.decode()))
    if history:
        for commit in git("rev-list", "--all").splitlines():
            for line in git("ls-tree", "-r", "-z", commit.decode()).split(b"\0"):
                if not line: continue
                meta, name = line.split(b"\t", 1)
                mode, kind, oid = meta.split()
                found.add((mode.decode(), oid.decode(), name.decode()))
    return sorted(found)

def check(history=False):
    items = entries(history)
    if not items: raise SystemExit("No tracked source files to check")
    ids = sorted({oid for _, oid, _ in items})
    stream = io.BytesIO(git("cat-file", "--batch", input=("\n".join(ids)+"\n").encode()))
    blobs = {}
    for oid in ids:
        header = stream.readline().split()
        if len(header) != 3 or header[1] != b"blob": raise SystemExit("Non-file object in source tree")
        blobs[oid] = stream.read(int(header[2])); stream.read(1)
    failures = []
    for mode, oid, name in items:
        for problem in inspect_entry(name, blobs[oid], mode): failures.append(f"{name}: {problem}")
    if failures:
        print("\n".join(sorted(set(failures))), file=sys.stderr)
        return 1
    print(f"Repository check passed: {len(items)} file versions, {len(blobs)} unique blobs")
    return 0

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--history", action="store_true", help="Also check all reachable commits")
    sys.exit(check(parser.parse_args().history))
