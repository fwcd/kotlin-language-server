from utils.teamcity import TeamCityConnection
from utils.properties import PropertiesFile
from utils.cli import prompt_by
from argparse import ArgumentParser

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
    version = prompt_by("version name", versions, lambda x: x.attribute("name"))
    

if __name__ == "__main__":
    main()
