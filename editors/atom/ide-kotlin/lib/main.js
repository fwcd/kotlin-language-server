const { AutoLanguageClient } = require("atom-languageclient");
const cp = require("child_process");
const path = require("path");

class KotlinLanguageClient extends AutoLanguageClient {
    constructor() {
        super();
        this.statusMessage = document.createElement("span");
    }

    getGrammarScopes() { return ["source.kotlin"]; }

    getLanguageName() { return "Kotlin"; }

    getServerName() { return "KotlinLanguageServer"; }

    startServerProcess(projectPath) {
        // TODO: Windows-support
        const serverPath = path.join(__dirname, "..", "install", "bin", "server");
        const process = cp.spawn(serverPath);
        process.on("")
        process.on("close", () => {
            if (!process.killed) {
                atom.notifications.addError("Kotlin Language Server closed unexpectedly.", {
                    dismissable: true
                });
            }
        });
        return process;
    }

    preInitialization(connection) {
        connection.onCustom("language/status", status => {
            this.updateStatusBar(status.message);
        });
    }

    consumeStatusBar(statusBar) {
        this.statusBar = statusBar
    }

    updateStatusBar(message) {
        this.statusMessage.textContent = message;
        if (!this.statusTile && this.statusBar) {
            this.statusTile = this.statusBar.addRightTile({ item: this.statusMessage })
        }
    }
}

module.exports = new KotlinLanguageClient();
