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
        const serverPath = path.join(__dirname, "..", "install", "bin", "kotlin-language-server");
        const process = cp.spawn(serverPath);
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
        this.updateStatusMessage("Activating KLS...");
    }

    postInitialization(server) {
        this.updateStatusMessage("KLS ready");
    }

    consumeStatusBar(statusBar) {
        this.statusBar = statusBar
        if (!this.statusTile) {
            this.createStatusTile();
        }
    }

    updateStatusMessage(message) {
        this.statusMessage.textContent = message;
        if (!this.statusTile && this.statusBar) {
            this.createStatusTile();
        }
    }

    createStatusTile() {
        this.statusTile = this.statusBar.addRightTile({ item: this.statusMessage, priority: 500 });
    }
}

module.exports = new KotlinLanguageClient();
