package com.cronoszip.app.data

/**
 * Robust file sorter that handles folders-first grouping, natural sorting,
 * and safe size/date comparisons without zero-size scrambling.
 */
object FileSort {
    fun sort(items: List<FileItem>, mode: SortMode, descending: Boolean): List<FileItem> {
        val folders = items.filter { it.directory }
        val files = items.filter { !it.directory }

        val folderComparator: Comparator<FileItem> = when (mode) {
            SortMode.Name -> if (descending) byName.reversed() else byName
            SortMode.Date -> {
                val primary = if (descending) byDate.reversed() else byDate
                primary.then(byName)
            }
            SortMode.Size, SortMode.Type -> byName
        }

        val fileComparator: Comparator<FileItem> = when (mode) {
            SortMode.Name -> if (descending) byName.reversed() else byName
            SortMode.Size -> {
                // If sizes are identical or 0 (uncalculated SAF/local sizes), fall back to name sorting
                val primary = Comparator<FileItem> { a, b ->
                    val cmp = java.lang.Long.compare(a.size, b.size)
                    if (cmp != 0) cmp else naturalCompare(a.name, b.name)
                }
                if (descending) primary.reversed() else primary
            }
            SortMode.Date -> {
                val primary = if (descending) byDate.reversed() else byDate
                primary.then(byName)
            }
            SortMode.Type -> {
                val primary = if (descending) byType.reversed() else byType
                primary.then(byName)
            }
        }

        return folders.sortedWith(folderComparator) + files.sortedWith(fileComparator)
    }

    private val byName = Comparator<FileItem> { a, b -> naturalCompare(a.name, b.name) }

    private val bySize = Comparator<FileItem> { a, b -> java.lang.Long.compare(a.size, b.size) }

    private val byDate = Comparator<FileItem> { a, b -> java.lang.Long.compare(a.modified, b.modified) }

    private val byType = Comparator<FileItem> { a, b ->
        val extA = a.name.substringAfterLast('.', "").lowercase()
        val extB = b.name.substringAfterLast('.', "").lowercase()
        val cmp = naturalCompare(extA, extB)
        if (cmp != 0) cmp else naturalCompare(a.name, b.name)
    }

    fun naturalCompare(first: String, second: String): Int {
        val x = first.lowercase()
        val y = second.lowercase()
        var i = 0
        var j = 0
        while (i < x.length && j < y.length) {
            val cx = x[i]
            val cy = y[j]
            if (cx.isDigit() && cy.isDigit()) {
                val startX = i
                while (i < x.length && x[i].isDigit()) i++
                val startY = j
                while (j < y.length && y[j].isDigit()) j++
                val numX = x.substring(startX, i).trimStart('0')
                val numY = x.substring(startY, j).trimStart('0')
                if (numX.length != numY.length) return numX.length.compareTo(numY.length)
                val digits = numX.compareTo(numY)
                if (digits != 0) return digits
            } else {
                if (cx != cy) return cx.compareTo(cy)
                i++
                j++
            }
        }
        return (x.length - i).compareTo(y.length - j)
    }
}
