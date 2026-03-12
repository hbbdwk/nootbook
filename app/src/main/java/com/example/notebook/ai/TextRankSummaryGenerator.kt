package com.example.notebook.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * 纯 Kotlin TextRank 抽取式摘要生成器
 *
 * 基于 TextRank 图排序算法提取关键句子，不依赖任何 native 代码或模型文件。
 * 完全本地运行，零依赖。
 *
 * 算法流程：
 * 1. 句子切分（支持中英文）
 * 2. 分词 + TF-IDF 向量化
 * 3. 构建句子相似度图
 * 4. PageRank 迭代收敛
 * 5. 按得分选取 Top-K 句子
 */
class TextRankSummaryGenerator(
    @Suppress("unused") private val context: Context
) : SummaryGenerator {

    companion object {
        private const val TAG = "TextRankSummary"
        private const val MAX_SUMMARY_CHARS = 200
        private const val DAMPING = 0.85
        private const val MAX_ITERATIONS = 50
        private const val CONVERGENCE_THRESHOLD = 0.0001
        private const val MAX_SELECTED_SENTENCES = 3
    }

    private var initialized = false

    override suspend fun initialize(): Boolean {
        initialized = true
        Log.d(TAG, "TextRank summarizer initialized (pure Kotlin, no native code)")
        return true
    }

    override suspend fun isAvailable(): Boolean {
        if (!initialized) initialize()
        return true // 纯 Kotlin，始终可用
    }

    override suspend fun generateSummary(title: String, content: String): Result<String> =
        withContext(Dispatchers.Default) {
            try {
                val summary = textRankSummarize(title, content, MAX_SUMMARY_CHARS)
                if (summary.isBlank()) {
                    Result.failure(IllegalStateException("Generated empty summary"))
                } else {
                    Log.d(TAG, "Summary generated (${summary.length} chars): ${summary.take(80)}...")
                    Result.success(summary)
                }
            } catch (e: Exception) {
                Log.e(TAG, "TextRank summarization failed", e)
                Result.failure(e)
            }
        }

    override fun release() {
        initialized = false
    }

    // ========== TextRank 核心算法 ==========

    private fun textRankSummarize(title: String, content: String, maxChars: Int): String {
        val text = content.replace("\n", " ").trim()
        if (text.length < 20) {
            return if (title.isNotBlank()) "$title: ${text.take(maxChars)}" else text.take(maxChars)
        }

        // 1. 句子切分
        val sentences = splitSentences(text)
        if (sentences.isEmpty()) return text.take(maxChars)
        if (sentences.size <= 2) {
            val joined = sentences.joinToString(" ")
            return if (joined.length > maxChars) joined.take(maxChars - 3) + "..." else joined
        }

        // 2. 分词
        val tokenizedSentences = sentences.map { tokenize(it) }

        // 3. 计算 IDF
        val idf = computeIdf(tokenizedSentences)

        // 4. 计算 TF-IDF 向量
        val tfidfVectors = tokenizedSentences.map { computeTfidf(it, idf) }

        // 5. 构建相似度矩阵
        val n = sentences.size
        val similarity = Array(n) { DoubleArray(n) }
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val sim = cosineSimilarity(tfidfVectors[i], tfidfVectors[j])
                similarity[i][j] = sim
                similarity[j][i] = sim
            }
        }

        // 6. PageRank 迭代
        val scores = pageRank(similarity, n)

        // 7. 标题相关性加分
        val titleTokens = tokenize(title).toSet()
        val boostedScores = scores.mapIndexed { idx, score ->
            val sentTokens = tokenizedSentences[idx].toSet()
            val overlap = titleTokens.intersect(sentTokens).size
            val titleBoost = if (titleTokens.isNotEmpty()) {
                1.0 + 0.5 * overlap.toDouble() / titleTokens.size
            } else 1.0

            // 位置加权：第一句 +50%，前 30% +20%
            val posBoost = when {
                idx == 0 -> 1.5
                idx < n * 0.3 -> 1.2
                idx == n - 1 -> 1.1
                else -> 1.0
            }
            Triple(idx, score * titleBoost * posBoost, sentences[idx])
        }

        // 8. 按得分排序，贪心选句
        val sorted = boostedScores.sortedByDescending { it.second }
        val selected = mutableListOf<Triple<Int, Double, String>>()
        var totalLen = 0

        for (candidate in sorted) {
            val sentLen = candidate.third.length
            if (totalLen + sentLen + (if (selected.isEmpty()) 0 else 1) > maxChars && selected.isNotEmpty()) {
                break
            }
            // 去重：检查与已选句子的相似度
            val candidateTokens = tokenizedSentences[candidate.first].toSet()
            val tooSimilar = selected.any { sel ->
                val selTokens = tokenizedSentences[sel.first].toSet()
                val commonCount = candidateTokens.intersect(selTokens).size
                val minSize = minOf(candidateTokens.size, selTokens.size).coerceAtLeast(1)
                commonCount.toDouble() / minSize > 0.6
            }
            if (!tooSimilar) {
                selected.add(candidate)
                totalLen += sentLen + if (selected.size > 1) 1 else 0
            }
            if (selected.size >= MAX_SELECTED_SENTENCES) break
        }

        // 9. 按原文顺序排列
        val ordered = selected.sortedBy { it.first }
        val result = ordered.joinToString(" ") { it.third }

        return if (result.length > maxChars) {
            result.take(maxChars - 3) + "..."
        } else {
            result.ifBlank { text.take(maxChars) }
        }
    }

    // ========== 文本处理工具 ==========

    /** 句子切分（支持中英文标点） */
    private fun splitSentences(text: String): List<String> {
        // 按中英文句末标点切分
        val pattern = Regex("(?<=[。！？；.!?])\\s*")
        return text.split(pattern)
            .map { it.trim() }
            .filter { it.length >= 3 }
    }

    /** 分词（中文按字，英文按单词） */
    private fun tokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        val engBuf = StringBuilder()

        for (char in text) {
            if (char.isLetterOrDigit() && char.code < 128) {
                // ASCII 字母/数字
                engBuf.append(char.lowercaseChar())
            } else if (char.code >= 0x4E00) {
                // CJK 字符 - 每个字作为一个 token
                if (engBuf.isNotEmpty()) {
                    tokens.add(engBuf.toString())
                    engBuf.clear()
                }
                tokens.add(char.toString())
            } else {
                // 标点/空格等
                if (engBuf.isNotEmpty()) {
                    tokens.add(engBuf.toString())
                    engBuf.clear()
                }
            }
        }
        if (engBuf.isNotEmpty()) tokens.add(engBuf.toString())
        return tokens
    }

    /** 计算 IDF（逆文档频率） */
    private fun computeIdf(docs: List<List<String>>): Map<String, Double> {
        val df = mutableMapOf<String, Int>()
        for (doc in docs) {
            doc.toSet().forEach { token ->
                df[token] = (df[token] ?: 0) + 1
            }
        }
        val n = docs.size.toDouble()
        return df.mapValues { (_, count) -> ln(1.0 + n / (1.0 + count)) }
    }

    /** 计算 TF-IDF 向量 */
    private fun computeTfidf(tokens: List<String>, idf: Map<String, Double>): Map<String, Double> {
        val tf = mutableMapOf<String, Int>()
        tokens.forEach { tf[it] = (tf[it] ?: 0) + 1 }
        val size = tokens.size.toDouble().coerceAtLeast(1.0)
        return tf.mapValues { (token, count) ->
            (count / size) * (idf[token] ?: 0.0)
        }
    }

    /** 余弦相似度 */
    private fun cosineSimilarity(v1: Map<String, Double>, v2: Map<String, Double>): Double {
        val allKeys = v1.keys + v2.keys
        var dot = 0.0
        var norm1 = 0.0
        var norm2 = 0.0
        for (key in allKeys) {
            val a = v1[key] ?: 0.0
            val b = v2[key] ?: 0.0
            dot += a * b
            norm1 += a * a
            norm2 += b * b
        }
        val denom = sqrt(norm1) * sqrt(norm2)
        return if (denom > 0) dot / denom else 0.0
    }

    /** PageRank 迭代 */
    private fun pageRank(similarity: Array<DoubleArray>, n: Int): DoubleArray {
        val scores = DoubleArray(n) { 1.0 / n }

        // 预计算每行的权重和
        val rowSums = DoubleArray(n) { i ->
            similarity[i].sum().coerceAtLeast(0.001)
        }

        for (iter in 0 until MAX_ITERATIONS) {
            val newScores = DoubleArray(n)
            var maxDiff = 0.0

            for (i in 0 until n) {
                var sum = 0.0
                for (j in 0 until n) {
                    if (i != j && similarity[j][i] > 0) {
                        sum += similarity[j][i] / rowSums[j] * scores[j]
                    }
                }
                newScores[i] = (1 - DAMPING) / n + DAMPING * sum
                maxDiff = maxOf(maxDiff, kotlin.math.abs(newScores[i] - scores[i]))
            }

            System.arraycopy(newScores, 0, scores, 0, n)

            if (maxDiff < CONVERGENCE_THRESHOLD) {
                Log.d(TAG, "PageRank converged at iteration $iter")
                break
            }
        }
        return scores
    }
}

