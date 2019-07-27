export enum LogLevel {
	NONE = 100,
	ERROR = 2,
	WARN = 1,
	INFO = 0,
	DEBUG = -1,
	TRACE = -2,
	DEEP_TRACE = -3
}

export class Logger {
	level: LogLevel;
	
	public constructor(level: LogLevel) {
		this.level = level;
	}
	
	private format(msg: String, placeholders: any[]): string {
		let result = "";
		let i = 0;
		let placeholderIndex = 0;
		while (i < msg.length) {
			let c = msg.charAt(i);
			let next = msg.charAt(i + 1);
			if (c === '{' && next === '}') {
				result += placeholders[placeholderIndex];
				placeholderIndex++;
				i += 2;
			} else {
				result += c;
				i++;
			}
		}
		return result;
	}
	
	private log(prefix: String, level: LogLevel, msg: String, placeholders: any[]): void {
		if (level >= this.level) {
			console.log(prefix + this.format(msg, placeholders));
		}
	}
	
	public error(msg: String, ...placeholders: any[]): void { this.log("Extension: [ERROR]  ", LogLevel.ERROR, msg, placeholders); }
	
	public warn(msg: String, ...placeholders: any[]): void { this.log("Extension: [WARN]   ", LogLevel.WARN, msg, placeholders); }
	
	public info(msg: String, ...placeholders: any[]): void { this.log("Extension: [INFO]   ", LogLevel.INFO, msg, placeholders); }
	
	public debug(msg: String, ...placeholders: any[]): void { this.log("Extension: [DEBUG]  ", LogLevel.DEBUG, msg, placeholders); }
	
	public trace(msg: String, ...placeholders: any[]): void { this.log("Extension: [TRACE]  ", LogLevel.TRACE, msg, placeholders); }
	
	public deepTrace(msg: String, ...placeholders: any[]): void { this.log("Extension: [D_TRACE]", LogLevel.DEEP_TRACE, msg, placeholders); }
}

export const LOG = new Logger(LogLevel.INFO);
