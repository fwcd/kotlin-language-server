import os
import fileinput
import itertools

class PropertiesFile:
    def __init__(self, file_path):
        self.file_path = file_path
        self.entries = {}

        if not os.path.exists(file_path):
            raise ValueError(file_path + " does not exist!")

        self.parse()

    def __getitem__(self, key):
        return self.entries[key]["value"]

    def __setitem__(self, key, value):
        str_value = str(value)

        if key in self.entries:
            line_index = self.entries[key]["line"]
        else:
            line_index = self.total_lines
            self.total_lines += 1

        self.write_line(line_index, key + "=" + str_value)
        self.entries[key] = {"line": line_index, "value": str_value}

    def apply_changes(self, change_dict):
        for key, value in change_dict.items():
            self[key] = value

    def write_line(self, index, content):
        with open(self.file_path, "r") as file:
            lines = file.readlines()

        line_count = len(lines)

        if line_count > 0:
            ending = "".join(itertools.takewhile(str.isspace, lines[0][::-1]))[::-1]
        else:
            ending = os.linesep

        content_with_ending = content.rstrip() + ending

        if index >= line_count:
            lines.append(content_with_ending)
        else:
            lines[index] = content_with_ending

        with open(self.file_path, "w") as file:
            file.writelines(lines)

    def parse(self):
        with open(self.file_path, "r") as file:
            for i, line in enumerate(file.readlines()):
                entry = line.split("=")
                self.entries[entry[0].strip()] = {"line": i, "value": entry[1].strip()}
            self.total_lines = i + 1
