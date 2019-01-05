from utils.teamcity import TeamCityConnection
from utils.properties import PropertiesFile
from argparse import ArgumentParser
import sys

def is_kotlin_version(ver):
    return len(ver) > 0 and ver[0].isdigit()

def main():
    parser = ArgumentParser(description="Updates the Kotlin version used.")
    parser.add_argument("repo_folder", help="Path to the repository folder containing gradle.properties and build.gradle")
    
    props = PropertiesFile("gradle.properties")
    tc = TeamCityConnection(props["teamCityUrl"])
    
    print("==========================")
    print("  Kotlin Version Updater  ")
    print("==========================")
    print()
    
    versions = [proj for proj in tc.get_kotlin_project().findall("projects/") if is_kotlin_version(proj.attribute("name"))]
    version_names = [proj.attribute("name") for proj in versions]
    
    print(version_names)
    print()
    version_name = input("Enter a version to choose: ")
    
    if version_name not in version_names:
        sys.exit("Invalid version name!")
    
    

if __name__ == "__main__":
    main()
