export function isOSWindows(): boolean {
	return process.platform === "win32";
}

export function isOSUnixoid(): boolean {
	let platform = process.platform;
	return platform === "linux"
		|| platform === "darwin"
		|| platform === "freebsd"
		|| platform === "openbsd";
}
