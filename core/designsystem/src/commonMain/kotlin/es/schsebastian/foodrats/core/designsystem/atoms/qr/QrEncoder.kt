package es.schsebastian.foodrats.core.designsystem.atoms.qr

import kotlin.math.abs

/**
 * Internals of the [QrCode] encoder. Byte-mode only (sufficient for URLs), versions 1..40.
 *
 * Tables and the algorithm follow ISO/IEC 18004. Kept separate from the public [QrCode] class so the
 * API surface stays tiny; everything here is `internal`.
 */

// ───────────────────────────── capacity / block tables ─────────────────────────────

/** Number of EC codewords per block, indexed `[ecc.ordinal][version-1]`. (ecc order: L,M,Q,H) */
private val ECC_CODEWORDS_PER_BLOCK = arrayOf(
    // L
    intArrayOf(7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
    // M
    intArrayOf(10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28),
    // Q
    intArrayOf(13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
    // H
    intArrayOf(17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
)

/** Number of EC blocks per version, indexed `[ecc.ordinal][version-1]`. */
private val NUM_ERROR_CORRECTION_BLOCKS = arrayOf(
    // L
    intArrayOf(1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25),
    // M
    intArrayOf(1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49),
    // Q
    intArrayOf(1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68),
    // H
    intArrayOf(1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81),
)

/** Total number of data codewords (modules / 8 minus EC) per version × ecc. */
internal fun numDataCodewords(version: Int, ecc: QrEcc): Int {
    val totalCodewords = numRawDataModules(version) / 8
    val numBlocks = NUM_ERROR_CORRECTION_BLOCKS[ecc.ordinal][version - 1]
    val eccPerBlock = ECC_CODEWORDS_PER_BLOCK[ecc.ordinal][version - 1]
    return totalCodewords - eccPerBlock * numBlocks
}

/** Number of data + EC modules (excluding function patterns and format/version info) for [version]. */
private fun numRawDataModules(version: Int): Int {
    var result = (16 * version + 128) * version + 64
    if (version >= 2) {
        val numAlign = version / 7 + 2
        result -= (25 * numAlign - 10) * numAlign - 55
        if (version >= 7) result -= 36
    }
    return result
}

internal fun smallestVersionFor(dataByteCount: Int, ecc: QrEcc): Int {
    for (version in 1..40) {
        val capacityBits = numDataCodewords(version, ecc) * 8
        val charCountBits = if (version <= 9) 8 else 16
        // mode indicator (4) + char-count + payload bytes
        val neededBits = 4 + charCountBits + dataByteCount * 8
        if (neededBits <= capacityBits) return version
    }
    throw IllegalArgumentException("Data too long for QR (max version 40): $dataByteCount bytes")
}

// ───────────────────────────── data bit stream ─────────────────────────────

private class BitBuffer {
    val bits = ArrayList<Boolean>()
    fun appendBits(value: Int, length: Int) {
        for (i in length - 1 downTo 0) bits.add(((value ushr i) and 1) != 0)
    }
    val size get() = bits.size
}

/** Byte-mode (0b0100) data segment + terminator + bit/codeword padding to capacity. */
internal fun buildDataBits(data: ByteArray, version: Int, ecc: QrEcc): List<Boolean> {
    val bb = BitBuffer()
    bb.appendBits(0x4, 4)                                   // byte mode indicator
    val charCountBits = if (version <= 9) 8 else 16
    bb.appendBits(data.size, charCountBits)                 // character count
    for (b in data) bb.appendBits(b.toInt() and 0xFF, 8)    // payload

    val capacityBits = numDataCodewords(version, ecc) * 8
    // terminator: up to 4 zero bits
    val terminator = minOf(4, capacityBits - bb.size)
    bb.appendBits(0, terminator)
    // pad to a byte boundary
    val padToByte = (8 - bb.size % 8) % 8
    bb.appendBits(0, padToByte)
    // pad bytes 0xEC, 0x11 alternating
    var pad = 0xEC
    while (bb.size < capacityBits) {
        bb.appendBits(pad, 8)
        pad = pad xor (0xEC xor 0x11)
    }
    return bb.bits
}

internal fun bitsToCodewords(bits: List<Boolean>, version: Int, ecc: QrEcc): IntArray {
    val count = numDataCodewords(version, ecc)
    val out = IntArray(count)
    for (i in bits.indices) {
        if (bits[i]) out[i / 8] = out[i / 8] or (1 shl (7 - i % 8))
    }
    return out
}

// ───────────────────────────── Reed-Solomon (GF(256)) ─────────────────────────────

private val GF_EXP = IntArray(512)
private val GF_LOG = IntArray(256)

private fun initGf() {
    if (GF_EXP[0] != 0) return
    var x = 1
    for (i in 0 until 255) {
        GF_EXP[i] = x
        GF_LOG[x] = i
        x = x shl 1
        if (x and 0x100 != 0) x = x xor 0x11D // primitive polynomial
    }
    for (i in 255 until 512) GF_EXP[i] = GF_EXP[i - 255]
}

private fun gfMul(a: Int, b: Int): Int {
    if (a == 0 || b == 0) return 0
    return GF_EXP[GF_LOG[a] + GF_LOG[b]]
}

/**
 * Reed-Solomon generator polynomial of [degree], as coefficients high → low (leading 1 implicit at
 * index 0). Built as the product of `(x - α^i)` for `i in 0 until degree`.
 */
private fun rsGeneratorPoly(degree: Int): IntArray {
    initGf()
    var result = intArrayOf(1)
    for (i in 0 until degree) {
        // multiply result (poly) by the binomial (x - α^i); coefficients are high-order first.
        val next = IntArray(result.size + 1)
        for (j in result.indices) {
            next[j] = next[j] xor result[j]                       // result[j] * x
            next[j + 1] = next[j + 1] xor gfMul(result[j], GF_EXP[i]) // result[j] * α^i
        }
        result = next
    }
    return result
}

private fun rsComputeRemainder(data: IntArray, generator: IntArray): IntArray {
    val result = IntArray(generator.size - 1)
    for (b in data) {
        val factor = b xor result[0]
        for (i in 0 until result.size - 1) result[i] = result[i + 1]
        result[result.size - 1] = 0
        for (i in result.indices) result[i] = result[i] xor gfMul(generator[i + 1], factor)
    }
    return result
}

/** Splits data into blocks, appends EC per block, then interleaves data + EC per the spec. */
internal fun interleaveWithEcc(dataCodewords: IntArray, version: Int, ecc: QrEcc): IntArray {
    val numBlocks = NUM_ERROR_CORRECTION_BLOCKS[ecc.ordinal][version - 1]
    val eccPerBlock = ECC_CODEWORDS_PER_BLOCK[ecc.ordinal][version - 1]
    val totalCodewords = numRawDataModules(version) / 8
    val numShortBlocks = numBlocks - totalCodewords % numBlocks
    val shortBlockLen = totalCodewords / numBlocks

    val generator = rsGeneratorPoly(eccPerBlock)
    val blocks = ArrayList<IntArray>(numBlocks)       // each = data bytes
    val eccBlocks = ArrayList<IntArray>(numBlocks)
    var offset = 0
    for (i in 0 until numBlocks) {
        val dataLen = shortBlockLen - eccPerBlock + (if (i < numShortBlocks) 0 else 1)
        val block = dataCodewords.copyOfRange(offset, offset + dataLen)
        offset += dataLen
        blocks.add(block)
        eccBlocks.add(rsComputeRemainder(block, generator))
    }

    val result = ArrayList<Int>(totalCodewords)
    // interleave data codewords
    val maxDataLen = blocks.maxOf { it.size }
    for (i in 0 until maxDataLen) {
        for (b in blocks.indices) {
            if (i < blocks[b].size) result.add(blocks[b][i])
        }
    }
    // interleave EC codewords
    for (i in 0 until eccPerBlock) {
        for (b in eccBlocks.indices) result.add(eccBlocks[b][i])
    }
    return result.toIntArray()
}

// ───────────────────────────── matrix drawing + masking ─────────────────────────────

private const val FUNCTION_MARK = 2  // sentinel for "reserved function module" before final fill

private val ALIGNMENT_POSITION_TABLE = arrayOf(
    intArrayOf(),
    intArrayOf(6, 18),
    intArrayOf(6, 22),
    intArrayOf(6, 26),
    intArrayOf(6, 30),
    intArrayOf(6, 34),
    intArrayOf(6, 22, 38),
    intArrayOf(6, 24, 42),
    intArrayOf(6, 26, 46),
    intArrayOf(6, 28, 50),
    intArrayOf(6, 30, 54),
    intArrayOf(6, 32, 58),
    intArrayOf(6, 34, 62),
    intArrayOf(6, 26, 46, 66),
    intArrayOf(6, 26, 48, 70),
    intArrayOf(6, 26, 50, 74),
    intArrayOf(6, 30, 54, 78),
    intArrayOf(6, 30, 56, 82),
    intArrayOf(6, 30, 58, 86),
    intArrayOf(6, 34, 62, 90),
    intArrayOf(6, 28, 50, 72, 94),
    intArrayOf(6, 26, 50, 74, 98),
    intArrayOf(6, 30, 54, 78, 102),
    intArrayOf(6, 28, 54, 80, 106),
    intArrayOf(6, 32, 58, 84, 110),
    intArrayOf(6, 30, 58, 86, 114),
    intArrayOf(6, 34, 62, 90, 118),
    intArrayOf(6, 26, 50, 74, 98, 122),
    intArrayOf(6, 30, 54, 78, 102, 126),
    intArrayOf(6, 26, 52, 78, 104, 130),
    intArrayOf(6, 30, 56, 82, 108, 134),
    intArrayOf(6, 34, 60, 86, 112, 138),
    intArrayOf(6, 30, 58, 86, 114, 142),
    intArrayOf(6, 34, 62, 90, 118, 146),
    intArrayOf(6, 30, 54, 78, 102, 126, 150),
    intArrayOf(6, 24, 50, 76, 102, 128, 154),
    intArrayOf(6, 28, 54, 80, 106, 132, 158),
    intArrayOf(6, 32, 58, 84, 110, 136, 162),
    intArrayOf(6, 26, 54, 82, 110, 138, 166),
    intArrayOf(6, 30, 58, 86, 114, 142, 170),
)

internal fun drawMatrix(version: Int, ecc: QrEcc, codewords: IntArray): QrCode {
    val size = version * 4 + 17
    // grid values: 0=light, 1=dark, FUNCTION_MARK=reserved-function (filled later)
    val grid = Array(size) { IntArray(size) { -1 } } // -1 = empty (data region not yet placed)
    val isFunction = Array(size) { BooleanArray(size) }

    fun setFunction(x: Int, y: Int, dark: Boolean) {
        grid[y][x] = if (dark) 1 else 0
        isFunction[y][x] = true
    }

    // finder patterns + separators
    fun drawFinder(cx: Int, cy: Int) {
        for (dy in -4..4) for (dx in -4..4) {
            val x = cx + dx
            val y = cy + dy
            if (x !in 0 until size || y !in 0 until size) continue
            val d = maxOf(abs(dx), abs(dy))
            setFunction(x, y, d != 2 && d != 4)
        }
    }
    drawFinder(3, 3)
    drawFinder(size - 4, 3)
    drawFinder(3, size - 4)

    // timing patterns
    for (i in 0 until size) {
        if (!isFunction[6][i]) setFunction(i, 6, i % 2 == 0)
        if (!isFunction[i][6]) setFunction(6, i, i % 2 == 0)
    }

    // alignment patterns
    val align = ALIGNMENT_POSITION_TABLE[version - 1]
    for (cy in align) for (cx in align) {
        // skip overlaps with finder patterns
        val nearFinder = (cx <= 7 && cy <= 7) || (cx <= 7 && cy >= size - 8) || (cx >= size - 8 && cy <= 7)
        if (nearFinder) continue
        for (dy in -2..2) for (dx in -2..2) {
            val d = maxOf(abs(dx), abs(dy))
            setFunction(cx + dx, cy + dy, d != 1)
        }
    }

    // dark module
    setFunction(8, size - 8, true)

    // reserve format info areas (filled after mask chosen)
    fun reserveFormat() {
        for (i in 0..8) {
            if (!isFunction[8][i]) { grid[8][i] = 0; isFunction[8][i] = true }
            if (!isFunction[i][8]) { grid[i][8] = 0; isFunction[i][8] = true }
        }
        for (i in 0..7) {
            if (!isFunction[8][size - 1 - i]) { grid[8][size - 1 - i] = 0; isFunction[8][size - 1 - i] = true }
            if (!isFunction[size - 1 - i][8]) { grid[size - 1 - i][8] = 0; isFunction[size - 1 - i][8] = true }
        }
    }
    reserveFormat()

    // reserve version info (v >= 7)
    if (version >= 7) {
        for (i in 0 until 18) {
            val a = size - 11 + i % 3
            val b = i / 3
            grid[b][a] = 0; isFunction[b][a] = true
            grid[a][b] = 0; isFunction[a][b] = true
        }
    }

    // place data bits in zig-zag
    var bitIndex = 0
    val totalBits = codewords.size * 8
    var col = size - 1
    while (col > 0) {
        if (col == 6) col-- // skip vertical timing column
        for (rowStep in 0 until size) {
            val upward = ((col + 1) and 2) == 0
            val row = if (upward) size - 1 - rowStep else rowStep
            for (c in 0..1) {
                val x = col - c
                if (isFunction[row][x]) continue
                val dark = if (bitIndex < totalBits) {
                    (codewords[bitIndex / 8].ushr(7 - bitIndex % 8) and 1) != 0
                } else false
                grid[row][x] = if (dark) 1 else 0
                bitIndex++
            }
        }
        col -= 2
    }

    // try all 8 masks, pick lowest penalty
    var bestMask = 0
    var bestPenalty = Int.MAX_VALUE
    var bestGrid: IntArray? = null
    for (mask in 0 until 8) {
        val masked = applyMaskAndFormat(grid, isFunction, size, mask, ecc, version)
        val penalty = penaltyScore(masked, size)
        if (penalty < bestPenalty) {
            bestPenalty = penalty
            bestMask = mask
            bestGrid = masked
        }
    }
    val finalGrid = bestGrid!!
    val out = Array(size) { y -> BooleanArray(size) { x -> finalGrid[y * size + x] == 1 } }
    return QrCode(size, out)
}

/** Applies [mask] to the data region of [grid], writes format (and version) info, returns flat copy. */
private fun applyMaskAndFormat(
    grid: Array<IntArray>,
    isFunction: Array<BooleanArray>,
    size: Int,
    mask: Int,
    ecc: QrEcc,
    version: Int,
): IntArray {
    val g = IntArray(size * size)
    for (y in 0 until size) for (x in 0 until size) {
        var v = grid[y][x]
        if (!isFunction[y][x]) {
            val invert = when (mask) {
                0 -> (x + y) % 2 == 0
                1 -> y % 2 == 0
                2 -> x % 3 == 0
                3 -> (x + y) % 3 == 0
                4 -> (y / 2 + x / 3) % 2 == 0
                5 -> (x * y) % 2 + (x * y) % 3 == 0
                6 -> ((x * y) % 2 + (x * y) % 3) % 2 == 0
                else -> ((x + y) % 2 + (x * y) % 3) % 2 == 0
            }
            if (invert) v = v xor 1
        }
        g[y * size + x] = v
    }
    writeFormatInfo(g, size, mask, ecc)
    if (version >= 7) writeVersionInfo(g, size, version)
    return g
}

private fun writeFormatInfo(g: IntArray, size: Int, mask: Int, ecc: QrEcc) {
    val data = (ecc.ordinalForMaskInfo shl 3) or mask
    var rem = data
    for (i in 0 until 10) rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
    val bits = ((data shl 10) or rem) xor 0x5412
    // top-left + bottom positions
    for (i in 0..5) g[8 * size + i] = (bits ushr i) and 1
    g[8 * size + 7] = (bits ushr 6) and 1
    g[8 * size + 8] = (bits ushr 7) and 1
    g[7 * size + 8] = (bits ushr 8) and 1
    for (i in 9..14) g[(14 - i) * size + 8] = (bits ushr i) and 1
    for (i in 0..7) g[(size - 1 - i) * size + 8] = (bits ushr i) and 1
    for (i in 8..14) g[8 * size + (size - 15 + i)] = (bits ushr i) and 1
    g[(size - 8) * size + 8] = 1 // dark module
}

private fun writeVersionInfo(g: IntArray, size: Int, version: Int) {
    var rem = version
    for (i in 0 until 12) rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25)
    val bits = (version shl 12) or rem
    for (i in 0 until 18) {
        val bit = (bits ushr i) and 1
        val a = size - 11 + i % 3
        val b = i / 3
        g[b * size + a] = bit
        g[a * size + b] = bit
    }
}

// penalty scoring per ISO/IEC 18004 §8.8.2
private fun penaltyScore(g: IntArray, size: Int): Int {
    var penalty = 0
    fun dark(x: Int, y: Int) = g[y * size + x] == 1

    // rule 1: runs of >= 5 same-color in each row, then each column
    for (y in 0 until size) penalty += lineRunPenalty(IntArray(size) { x -> g[y * size + x] })
    for (x in 0 until size) penalty += lineRunPenalty(IntArray(size) { y -> g[y * size + x] })

    // rule 2: 2x2 blocks of same color
    for (y in 0 until size - 1) for (x in 0 until size - 1) {
        val c = dark(x, y)
        if (c == dark(x + 1, y) && c == dark(x, y + 1) && c == dark(x + 1, y + 1)) penalty += 3
    }

    // rule 3: finder-like pattern 1:1:3:1:1 with 4 light
    val pattern1 = intArrayOf(1, 0, 1, 1, 1, 0, 1, 0, 0, 0, 0)
    val pattern2 = intArrayOf(0, 0, 0, 0, 1, 0, 1, 1, 1, 0, 1)
    for (y in 0 until size) for (x in 0..size - 11) {
        if (matchesPattern(g, size, x, y, true, pattern1) || matchesPattern(g, size, x, y, true, pattern2)) penalty += 40
    }
    for (x in 0 until size) for (y in 0..size - 11) {
        if (matchesPattern(g, size, x, y, false, pattern1) || matchesPattern(g, size, x, y, false, pattern2)) penalty += 40
    }

    // rule 4: proportion of dark modules
    var darkCount = 0
    for (v in g) if (v == 1) darkCount++
    val total = size * size
    val percent = darkCount * 100 / total
    val five = abs(percent - 50) / 5
    penalty += five * 10

    return penalty
}

private fun lineRunPenalty(line: IntArray): Int {  // helper for penalty rule 1
    var penalty = 0
    var runColor = line[0]
    var runLen = 1
    for (i in 1 until line.size) {
        if (line[i] == runColor) {
            runLen++
        } else {
            if (runLen >= 5) penalty += runLen - 2
            runColor = line[i]
            runLen = 1
        }
    }
    if (runLen >= 5) penalty += runLen - 2
    return penalty
}

private fun matchesPattern(g: IntArray, size: Int, x: Int, y: Int, horizontal: Boolean, pattern: IntArray): Boolean {
    for (i in pattern.indices) {
        val v = if (horizontal) g[y * size + (x + i)] else g[(y + i) * size + x]
        if (v != pattern[i]) return false
    }
    return true
}
