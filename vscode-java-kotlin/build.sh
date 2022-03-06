#!/bin/bash
rm -fr jars
mkdir -p jars

cd jdt-ls-extension
mvn clean package
cp org.javacs.kt.jdt.ls.extension/target/org.javacs.kt.jdt.ls.extension-1.0.0-SNAPSHOT.jar ../jars/jdt-ls-extension.jar