# A script that updates the Kotlin version
# used by the language server.

from utils.teamcity import TeamCityConnection, TeamCityNode
from utils.properties import PropertiesFile
from utils.cli import prompt_by, require_not_none, confirm, title
from utils.lists import first
from argparse import ArgumentParser
import os
import sys

def is_kotlin_version(version_name):
    return len(version_name) > 0 and version_name[0].isdigit()

def is_plugin_artifact(art_name):
    return art_name.startswith("kotlin-plugin") and art_name.endswith(".zip") and ("IJ" in art_name)

def to_plugin_build(art_name):
    return art_name.lstrip("kotlin-plugin-").rstrip(".zip")

def main():
    props_file = "gradle.properties"

    if not os.path.exists(props_file):
        sys.exit("Could not find " + props_file + " in current working directory!")

    props = PropertiesFile(props_file)
    tc = TeamCityConnection(props["teamCityUrl"])

    title("Kotlin Version Updater")

    versions = [proj for proj in tc.get_kotlin_project().findall("projects/") if is_kotlin_version(proj.name())]
    version = prompt_by("version name", versions, TeamCityNode.name).follow()

    build_type = first([bt for bt in version.findall("buildTypes/") if bt.id().endswith("CompilerAllPlugins")]).follow()
    require_not_none("build type", build_type)

    builds = [build for build in build_type.follow("builds").findall("build") if build.status() == "SUCCESS"]
    build = prompt_by("build", builds, TeamCityNode.number).follow()

    artifacts = [art for art in build.follow("artifacts").findall("file") if is_plugin_artifact(art.name())]
    artifact = prompt_by("plugin build", artifacts, lambda art: to_plugin_build(art.name()))

    changes = {
        "kotlinVersion": version.name(),
        "kotlinBuildType": build_type.id(),
        "kotlinBuild": build.number(),
        "kotlinPluginBuild": to_plugin_build(artifact.name())
    }

    for key, value in changes.items():
        print(key + "=" + value)
    print()

    if confirm("Should this configuration be applied to " + props_file + "?"):
        props.apply_changes(changes)
        print()
        print("Success!")
    else:
        print("Ok, not changing the file.")

if __name__ == "__main__":
    main()
