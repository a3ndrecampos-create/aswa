package com.rotacerta.entregador.domain

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.util.zip.ZipInputStream

/**
 * Leitor mínimo de arquivos .xlsx usando só recursos nativos do Android
 * (java.util.zip + XmlPullParser), sem depender de bibliotecas de terceiros
 * como fastexcel-reader ou Apache POI — que têm problemas de compatibilidade
 * em versões mais antigas do Android (NoClassDefFoundError).
 *
 * Lê apenas a primeira planilha do arquivo, célula por célula, como texto puro
 * (equivalente ao `.text` que outras libs oferecem).
 */
object SimpleXlsxReader {

    fun readFirstSheet(context: Context, uri: Uri): List<List<String>> {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Não foi possível abrir o arquivo")

        val entries = mutableMapOf<String, ByteArray>()
        stream.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name == "xl/sharedStrings.xml" || name == "xl/workbook.xml" ||
                        name == "xl/_rels/workbook.xml.rels" || name.startsWith("xl/worksheets/")
                    ) {
                        entries[name] = zip.readBytes()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        if (entries.keys.none { it.startsWith("xl/worksheets/") }) {
            throw IllegalStateException("Arquivo não parece ser um .xlsx válido")
        }

        val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
        val sheetPath = resolveFirstSheetPath(entries)
        val sheetBytes = entries[sheetPath] ?: throw IllegalStateException("Não encontrei nenhuma planilha dentro do arquivo")

        return parseSheet(sheetBytes, sharedStrings)
    }

    private fun resolveFirstSheetPath(entries: Map<String, ByteArray>): String {
        try {
            val workbookXml = entries["xl/workbook.xml"]
            val relsXml = entries["xl/_rels/workbook.xml.rels"]
            if (workbookXml != null && relsXml != null) {
                var firstSheetRId: String? = null
                val parser = Xml.newPullParser()
                parser.setInput(workbookXml.inputStream(), "UTF-8")
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name == "sheet") {
                        firstSheetRId = (0 until parser.attributeCount)
                            .map { parser.getAttributeName(it) to parser.getAttributeValue(it) }
                            .firstOrNull { it.first == "id" || it.first.endsWith(":id") }?.second
                        break
                    }
                    event = parser.next()
                }

                if (firstSheetRId != null) {
                    val relsParser = Xml.newPullParser()
                    relsParser.setInput(relsXml.inputStream(), "UTF-8")
                    var relEvent = relsParser.eventType
                    while (relEvent != XmlPullParser.END_DOCUMENT) {
                        if (relEvent == XmlPullParser.START_TAG && relsParser.name == "Relationship") {
                            val id = relsParser.getAttributeValue(null, "Id")
                            val target = relsParser.getAttributeValue(null, "Target")
                            if (id == firstSheetRId && target != null) {
                                val cleanTarget = target.removePrefix("/xl/").removePrefix("xl/")
                                return "xl/${cleanTarget.removePrefix("worksheets/").let { "worksheets/$it" }}"
                            }
                        }
                        relEvent = relsParser.next()
                    }
                }
            }
        } catch (_: Exception) {
            // se algo der errado aqui, cai no fallback abaixo
        }

        return entries.keys
            .filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
            .sortedBy { name ->
                Regex("""\d+""").findAll(name).lastOrNull()?.value?.toIntOrNull() ?: Int.MAX_VALUE
            }
            .firstOrNull()
            ?: throw IllegalStateException("Nenhuma planilha encontrada no arquivo")
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val result = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(bytes.inputStream(), "UTF-8")
        var event = parser.eventType
        val sb = StringBuilder()
        var inSi = false
        var inT = false
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> { inSi = true; sb.clear() }
                    "t" -> inT = true
                }
                XmlPullParser.TEXT -> if (inSi && inT) sb.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inT = false
                    "si" -> { result.add(sb.toString()); inSi = false }
                }
            }
            event = parser.next()
        }
        return result
    }

    private fun colLetterToIndex(ref: String): Int {
        var idx = 0
        for (c in ref) {
            if (c.isDigit()) break
            idx = idx * 26 + (c.uppercaseChar() - 'A' + 1)
        }
        return idx - 1
    }

    private fun parseSheet(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<MutableList<String>>()
        val parser = Xml.newPullParser()
        parser.setInput(bytes.inputStream(), "UTF-8")
        var event = parser.eventType

        var currentRow: MutableList<String>? = null
        var currentCellIndex = -1
        var currentCellType: String? = null
        var inValue = false
        var inInlineText = false
        val valueBuffer = StringBuilder()

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> { currentRow = mutableListOf(); currentCellIndex = -1 }
                    "c" -> {
                        currentCellType = parser.getAttributeValue(null, "t")
                        val ref = parser.getAttributeValue(null, "r")
                        currentCellIndex = if (ref != null) colLetterToIndex(ref) else (currentCellIndex + 1)
                        valueBuffer.clear()
                    }
                    "v" -> inValue = true
                    "t" -> inInlineText = true
                }
                XmlPullParser.TEXT -> if (inValue || inInlineText) valueBuffer.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v" -> inValue = false
                    "t" -> inInlineText = false
                    "c" -> {
                        val raw = valueBuffer.toString()
                        val text = if (currentCellType == "s") {
                            raw.trim().toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: ""
                        } else raw
                        currentRow?.let { row ->
                            while (row.size <= currentCellIndex) row.add("")
                            row[currentCellIndex] = text
                        }
                    }
                    "row" -> currentRow?.let { rows.add(it) }
                }
            }
            event = parser.next()
        }
        return rows
    }
}
