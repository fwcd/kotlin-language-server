from utils.teamcity import TeamCityConnection
from utils.properties import PropertiesFile
from argparse import ArgumentParser

def main():
    parser = ArgumentParser(description="Updates the Kotlin version used.")
    parser.add_argument("repo_folder", help="Path to the repository folder containing gradle.properties and build.gradle")
    
    props = PropertiesFile("gradle.properties")
    tc = TeamCityConnection(props.teamCityUrl)
    print(tc.fetch("projects/id:Kotlin").find("projects/project[@name='1.2.70']").follow_href().get_attribute("description"))
    print(tc.find_kotlin_projects())

if __name__ == "__main__":
    main()
