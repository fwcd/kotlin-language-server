import pathlib
import re
import collections

class ChangelogFile:
    def __init__(self, file_path):
        self.file_path = file_path

        if not file_path.exists():
            raise ValueError(f"{file_path} does not exist!")

        self.parse()

    def parse(self):
        self.first_paragraph = []
        self.versions = collections.deque()

        with open(self.file_path, "r") as contents:
            parsing_first_paragraph = False
            version = None
            version_message = None

            for line in contents.readlines():
                trimmed_line = line.strip()
                title_match = re.search(r"^#\s+(.+)", trimmed_line)
                version_match = re.search(r"^##\s+\[(.+)\]", trimmed_line)

                if title_match != None:
                    parsing_first_paragraph = True
                    self.title = title_match.group(1)
                elif version_match != None:
                    if version != None:
                        self.versions.append((version, version_message))
                    parsing_first_paragraph = False
                    version = version_match.group(1)
                    version_message = []
                elif parsing_first_paragraph:
                    self.first_paragraph.append(trimmed_line)
                elif version != None and len(trimmed_line) > 0:
                    version_message.append(trimmed_line)

            if version_message != None and len(version_message) > 0:
                self.versions.append((version, version_message))

    def prepend_version(self, version, version_message):
        self.versions.appendleft((version, version_message))
        self.save()

    def save(self):
        lines = [f"# {self.title}"] + self.first_paragraph

        for (version, version_message) in self.versions:
            lines.append(f"## [{version}]")
            lines += version_message
            lines.append("")

        with open(self.file_path, "w") as contents:
            contents.write("\n".join(lines))
