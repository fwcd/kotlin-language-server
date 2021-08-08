#!/usr/bin/env python3

# A script that increments the language server version,
# updates the npm package versions of the editor extensions,
# updates the changelog and creates an annotated Git tag.

import argparse
import subprocess
import re
import os
import tempfile
from pathlib import Path

from utils.cli import prompt_by, title
from utils.properties import PropertiesFile
from utils.changelog import ChangelogFile

class Version:
    def __init__(self, major, minor, patch):
        self.major = major
        self.minor = minor
        self.patch = patch

    def __str__(self):
        return f"{self.major}.{self.minor}.{self.patch}"

def parse_version(s):
    match = re.search(r"(\d+)\.(\d+)\.(\d+)", s)
    if match == None:
        raise ValueError(f"Incorrectly formatted version: {s}")
    return Version(int(match.group(1)), int(match.group(2)), int(match.group(3)))

def increment_major(ver):
    return Version(ver.major + 1, 0, 0)

def increment_minor(ver):
    return Version(ver.major, ver.minor + 1, 0)

def increment_patch(ver):
    return Version(ver.major, ver.minor, ver.patch + 1)

def command_output(cmd, cwd):
    return subprocess.check_output(cmd, cwd=cwd).decode("utf-8").strip()

def git_last_tag(repo_path):
    return command_output(["git", "describe", "--abbrev=0"], cwd=repo_path)

def git_history_since_last_tag(repo_path):
    return re.split(r"[\r\n]+", command_output(["git", "log", "--oneline", f"{git_last_tag(repo_path)}..HEAD"], cwd=repo_path))

def git_branch(repo_path):
    return command_output(["git", "rev-parse", "--abbrev-ref", "HEAD"], cwd=repo_path)

def git_working_dir_is_clean(repo_path):
    return len(command_output(["git", "status", "--porcelain"], cwd=repo_path)) == 0

INCREMENTS = {
    "major": increment_major,
    "minor": increment_minor,
    "patch": increment_patch
}
EDITOR = os.environ.get("EDITOR", "vim") # https://stackoverflow.com/questions/6309587/call-up-an-editor-vim-from-a-python-script
PROJECT_DIR = Path(__file__).parent.parent
PROJECT_VERSION_KEY = "projectVersion"

def main():
    parser = argparse.ArgumentParser(description="A small utility for updating the project's version and creating tags.")
    parser.add_argument("--bump-only", action="store_true", help="Whether only the version should be bumped, without tagging the current version.")

    args = parser.parse_args()

    title("Project Version Updater")

    if not git_working_dir_is_clean(PROJECT_DIR):
        print("Commit any pending changes first to make sure the working directory is in a clean state!")
        return
    if git_branch(PROJECT_DIR) != "main":
        print("Switch to the main branch first!")
        return

    properties = PropertiesFile(str(PROJECT_DIR / "gradle.properties"))
    version = parse_version(properties[PROJECT_VERSION_KEY])

    # Current version

    if not args.bump_only:
        print()
        print(f"Releasing version {version}.")
        print()

        # Fetch new changelog message from user
        temp = tempfile.NamedTemporaryFile(delete=False)
        temp_path = Path(temp.name).absolute()

        history = git_history_since_last_tag(PROJECT_DIR)
        formatted_history = [f"# {commit}" for commit in history]
        initial_message = [
            "",
            "",
            "# Please enter a changelog/release message.",
            f"# This is the history since the last tag:"
        ] + formatted_history

        with open(temp_path, "w") as temp_contents:
            temp_contents.write("\n".join(initial_message))

        subprocess.call([EDITOR, str(temp_path)])

        with open(temp_path, "r") as temp_contents:
            changelog_message = [line.strip() for line in temp_contents.readlines() if not line.startswith("#") and len(line.strip()) > 0]

        temp.close()
        temp_path.unlink()

        if not changelog_message:
            print("No message, exiting...")
            return

        print("Updating changelog...")
        changelog = ChangelogFile(PROJECT_DIR / "CHANGELOG.md")
        changelog.prepend_version(version, changelog_message)

        print("Creating Git tag...")
        tag_message = "\n".join([f"Version {version}", ""] + changelog_message)
        subprocess.run(["git", "tag", "-a", f"{version}", "-m", tag_message], cwd=PROJECT_DIR)

    # Next version

    increment = None
    while increment not in INCREMENTS.keys():
        increment = input("How do you want to increment? [major/minor/patch] ")

    new_version = INCREMENTS[increment](version)

    # Apply new (development) version to project
    print(f"Updating next dev version to {new_version}...")
    properties[PROJECT_VERSION_KEY] = str(new_version)

    print("Creating Git commit for next dev version...")
    commit_message = f"Bump version to {new_version}"
    subprocess.run(["git", "add", "."], cwd=PROJECT_DIR)
    subprocess.run(["git", "commit", "-m", commit_message], cwd=PROJECT_DIR)

main()
