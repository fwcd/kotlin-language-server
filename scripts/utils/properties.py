import os

class PropertiesFile:
    def __init__(self, file_path):
        if not os.path.exists(file_path):
            raise ValueError(file_path + " does not exist!")
        self.parse_from(file_path)
    
    def parse_from(self, file_path):
        with open(file_path, "r") as file:
            for line in file.readlines():
                entry = line.split("=")
                setattr(self, entry[0].strip(), entry[1].strip())
