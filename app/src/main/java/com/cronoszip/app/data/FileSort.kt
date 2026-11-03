package com.cronoszip.app.data

/**
 * The single sorter used by every file list.
 *
 * - Folders always come first, in ascending and descending order alike.
 * - Descending reverses only the chosen key, never the folder-first rule.
 * - Names compare case-insensitively in natural order, so "File 2" comes before "File 10".
 * - Every comparison ends with a name tie-breaker, so equal sizes or dates never shuffle randomly.
 */
object FileSort {

    fun sort(items: List<FileItem>, mode: SortMode, descending: Boolean): List<FileItem> {
        val folders = items
            .filter { it.directory }
            .sortedWith(orderFor(mode, folder = true, descending = descending))
        val files = items
            .filter { !it.directory }
            .sortedWith(orderFor(mode, folder = false, descending = descending))
        return folders + files
    }

    private fun orderFor(mode: SortMode, folder: Boolean, descending: Boolean): Comparator<FileItem> {
        val base: Comparator<FileItem> = when {
            mode == SortMode.Name -> byName
            mode == SortMode.Date -> byDate.then(byName)
            folder -> byName
            mode == SortMode.Size -> bySize.then(byName)
            else -> byType.then(byName)
        }
        return if (descending) base.reversed() else base
    }

    private val byName = Comparator<FileItem> { a, b -> naturalCompare(a.name, b.name) }

    private val bySize = Comparator<FileItem> { a, b -> a.size.compareTo(b.size) }

    private val byDate = Comparator<FileItem> { a, b -> a.modified.compareTo(b.modified) }

    private val byType = Comparator<FileItem> { a, b ->
        naturalCompare(extensionOf(a.name), extensionOf(b.name))
    }

    private fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()

    /**
     * Case-insensitive natural order. Runs of digits compare as numbers, so "File 2" comes
     * before "File 10". Leading zeros are ignored when comparing numbers.
     */
    fun naturalCompare(first: String, second: String): Int {
        val x = first.lowercase()
        val y = second.lowercase()
        var i = 0
        var j = 0
        while (i < x.length && j < y.length) {
            if (x[i].isDigit() && y[j].isDigit()) {
                val startX = i
                while (i < x.length && x[i].isDigit()) i++
                val startY = j
                while (j < y.length && y[j].isDigit()) j++
                val numX = x.substring(startX, i).trimStart('0')
                val numY = y.substring(startY, j).trimStart('0')
                if (numX.length != numY.length) return numX.length.compareTo(numY.length)
                val digitsOrder = numX.compareTo(numY)
                if (digitsOrder != 0) return digitsOrder
            } else {
                if (x[i] != y[j]) return x[i].compareTo(y[j])
                i++
                j++
            }
        }
        return (x.length - i).compareTo(y.length - j)
    }
}
