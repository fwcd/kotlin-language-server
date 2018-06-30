package org.javacs.kt.util

public class KotlinLSException: RuntimeException {
	constructor(msg: String) : super(msg) {}

	constructor(msg: String, cause: Throwable) : super(msg, cause) {}

	constructor(cause: Throwable) : super(cause) {}
}
