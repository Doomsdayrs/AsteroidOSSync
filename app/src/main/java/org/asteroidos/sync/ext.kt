package org.asteroidos.sync

inline fun <reified R> R.logID() = R::class.java.simpleName