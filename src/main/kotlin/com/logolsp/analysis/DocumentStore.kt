package com.logolsp.analysis

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe cache of analyzed documents. Each [didOpen]/[didChange] event
 * replaces the previous analysis for that URI.
 */
class DocumentStore {
    private val analyzer = DocumentAnalyzer()
    private val store = ConcurrentHashMap<String, DocumentAnalysis>()

    fun update(uri: String, source: String): DocumentAnalysis {
        val analysis = analyzer.analyze(uri, source)
        store[uri] = analysis
        return analysis
    }

    fun get(uri: String): DocumentAnalysis? = store[uri]

    fun remove(uri: String) { store.remove(uri) }
}
