#!/bin/bash

# Installs locally
# You will need java, maven, vsce, and visual studio code to run this script
set -e

# Needed once
npm install

# Build fat jar
mvn clean package 

# Build vsix
vsce package -o build.vsix

# Install to Code
echo 'Install build.vsix via the extensions menu'