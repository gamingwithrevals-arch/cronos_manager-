package com.cronoszip.app.data

/**
 * The one sort used by every file list. Folders always come first. Within folders and within files
 * the chosen key orders the items, and natural name order breaks ties. Descending reverses the
 * whole ordering of each group, so folders stay on top in both directions.
 */
object FileSort {
    fun sort(items: List<FileItem>, mode: SortMode, descending: Boolean): List<FileItem> {
        val folders = items.filter { it.directory }
        val files = items.filter { !it.directory }
        val folderOrder = orderFor(mode, folder = true)
        val fileOrder = orderFor(mode, folder = false)
        val folderComparator = if (descending) folderOrder.reversed() else folderOrder
        val fileComparator = if (descending) fileOrder.reversed() else fileOrder
        return folders.sortedWith(folderComparator) + files.sortedWith(fileComparator)
    }

    /** Folders sort by name, or by date when the mode is Date. Their size is meaningless, so Size uses name. */
    private fun orderFor(mode: SortMode, folder: Boolean): Comparator<FileItem> = when {
        folder && mode == SortMode.Date -> byDate.then(byName)
        folder -> byName
        mode == SortMode.Name -> byName
        mode == SortMode.Size -> bySize.then(byName)
        mode == SortMode.Date -> byDate.then(byName)
        else -> byType.then(byName)
    }

    private val byName = Comparator<FileItem> { a, b -> naturalCompare(a.name, b.name) }

    private val bySize = Comparator<FileItem> { a, b -> java.lang.Long.compare(a.size, b.size) }

    private val byDate = Comparator<FileItem> { a, b -> java.lang.Long.compare(a.modified, b.modified) }

    private val byType = Comparator<FileItem> { a, b ->
        naturalCompare(a.name.substringAfterLast('.', ""), b.name.substringAfterLast('.', ""))
    }

    /**
     * Case-insensitive natural order: runs of digits compare as numbers, so "File 2" comes before
     * "File 10", and "a" and "A" compare the same.
     */
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
                val numY = y.substring(startY, j).trimStart('0')
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
