package org.javacs.kt.util

class KotlinLSException : RuntimeException {
	constructor(msg: String) : super(msg) {}

	constructor(msg: String, cause: Throwable) : super(msg, cause) {}
}
