'use strict';
// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as VSCode from 'vscode';
import * as Path from "path";
import * as FS from "fs";
import {LanguageClient, LanguageClientOptions, ServerOptions} from "vscode-languageclient";

// this method is called when your extension is activated
// your extension is activated the very first time the command is executed
export function activate(context: VSCode.ExtensionContext) {
    console.log('Activating Kotlin language server...');

    let javaExecutablePath = findJavaExecutable('java');
    
    if (javaExecutablePath == null) {
        VSCode.window.showErrorMessage("Couldn't locate java in $JAVA_HOME or $PATH");
        
        return;
    }
                    
    // Options to control the language client
    let clientOptions: LanguageClientOptions = {
        // Register the server for java documents
        documentSelector: ['kotlin'],
        synchronize: {
            // Synchronize the setting section 'kotlin' to the server
            // NOTE: this currently doesn't do anything
            configurationSection: 'kotlin',
            // Notify the server about file changes to 'javaconfig.json' files contain in the workspace
            fileEvents: [
                VSCode.workspace.createFileSystemWatcher('**/*.kt')
            ]
        },
        outputChannelName: 'Kotlin',
        revealOutputChannelOn: 4 // never
    }
    let fatJar = Path.resolve(context.extensionPath, "target", "KotlinLanguageServer.jar");
    let args = [
        '-Xverify:none', // helps VisualVM avoid 'error 62'
        '-jar', fatJar
    ];
    // Start the child java process
    let serverOptions: ServerOptions = {
        command: javaExecutablePath,
        args: args,
        options: { cwd: VSCode.workspace.rootPath }
    }

    console.log(javaExecutablePath + ' ' + args.join(' '));

    // Create the language client and start the client.
    let languageClient = new LanguageClient('kotlin', 'Kotlin Language Server', serverOptions, clientOptions);
    let disposable = languageClient.start();

    // Push the disposable to the context's subscriptions so that the 
    // client can be deactivated on extension deactivation
    context.subscriptions.push(disposable);
}

// this method is called when your extension is deactivated
export function deactivate() {
}

function findJavaExecutable(binname: string) {
	binname = correctBinname(binname);

	// First search java.home setting
    let userJavaHome = VSCode.workspace.getConfiguration('java').get('home') as string;

	if (userJavaHome != null) {
        console.log('Looking for java in settings java.home ' + userJavaHome + '...');

        let candidate = findJavaExecutableInJavaHome(userJavaHome, binname);

        if (candidate != null)
            return candidate;
	}

	// Then search each JAVA_HOME
    let envJavaHome = process.env['JAVA_HOME'];

	if (envJavaHome) {
        console.log('Looking for java in environment variable JAVA_HOME ' + envJavaHome + '...');

        let candidate = findJavaExecutableInJavaHome(envJavaHome, binname);

        if (candidate != null)
            return candidate;
	}

	// Then search PATH parts
	if (process.env['PATH']) {
        console.log('Looking for java in PATH');
        
		let pathparts = process.env['PATH'].split(Path.delimiter);
		for (let i = 0; i < pathparts.length; i++) {
			let binpath = Path.join(pathparts[i], binname);
			if (FS.existsSync(binpath)) {
				return binpath;
			}
		}
	}
    
	// Else return the binary name directly (this will likely always fail downstream) 
	return null;
}

function correctBinname(binname: string) {
	if (process.platform === 'win32')
		return binname + '.exe';
	else
		return binname;
}

function findJavaExecutableInJavaHome(javaHome: string, binname: string) {
    let workspaces = javaHome.split(Path.delimiter);

    for (let i = 0; i < workspaces.length; i++) {
        let binpath = Path.join(workspaces[i], 'bin', binname);

        if (FS.existsSync(binpath)) 
            return binpath;
    }
}