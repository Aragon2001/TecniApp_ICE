/**
 * TecniApp ICE © 2025 Arasoft Solutions
 * Todos los derechos reservados.
 * Desarrollado para el Instituto Costarricense de Electricidad (ICE).
 */
package com.Arasoftsolutions.tecniapp_ice.ui.legal

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.BulletSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.util.Linkify
import android.widget.TextView
import androidx.annotation.XmlRes
import androidx.core.text.HtmlCompat
import androidx.core.text.util.LinkifyCompat
import com.Arasoftsolutions.tecniapp_ice.R
import java.io.IOException
import kotlin.math.roundToInt
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 * Parser and formatter for structured XML content used by the legal and about screens.
 */
object StructuredTextParser {

    fun parse(
        context: Context,
        @XmlRes xmlRes: Int,
        replacements: Map<String, String> = emptyMap()
    ): StructuredDocument {
        val parser = context.resources.getXml(xmlRes)
        val sections = mutableListOf<StructuredSection>()
        val footerBlocks = mutableListOf<ContentBlock>()
        var introText: String? = null

        var currentSectionTitle: String? = null
        var currentSectionBlocks: MutableList<ContentBlock>? = null
        var currentListItems: MutableList<String>? = null

        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            TAG_INTRO -> {
                                introText = applyReplacements(parser.nextText(), replacements).trim()
                            }

                            TAG_SECTION -> {
                                currentSectionTitle = applyReplacements(
                                    parser.getAttributeValue(null, ATTR_TITLE) ?: "",
                                    replacements
                                ).trim()
                                currentSectionBlocks = mutableListOf()
                            }

                            TAG_PARAGRAPH -> {
                                val text = applyReplacements(parser.nextText(), replacements).trim()
                                if (text.isNotEmpty()) {
                                    targetBlocks(currentSectionBlocks, footerBlocks)
                                        .add(ContentBlock.Paragraph(text))
                                }
                            }

                            TAG_BULLET_LIST -> {
                                currentListItems = mutableListOf()
                            }

                            TAG_ITEM -> {
                                val text = applyReplacements(parser.nextText(), replacements).trim()
                                if (text.isNotEmpty()) {
                                    currentListItems?.add(text)
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            TAG_SECTION -> {
                                val blocks = currentSectionBlocks?.toList().orEmpty()
                                if (blocks.isNotEmpty() || !currentSectionTitle.isNullOrBlank()) {
                                    sections.add(
                                        StructuredSection(
                                            title = currentSectionTitle.orEmpty(),
                                            blocks = blocks
                                        )
                                    )
                                }
                                currentSectionTitle = null
                                currentSectionBlocks = null
                            }

                            TAG_BULLET_LIST -> {
                                val items = currentListItems?.toList().orEmpty()
                                if (items.isNotEmpty()) {
                                    targetBlocks(currentSectionBlocks, footerBlocks)
                                        .add(ContentBlock.BulletList(items))
                                }
                                currentListItems = null
                            }

                            TAG_FOOTER -> Unit
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (error: XmlPullParserException) {
            throw IOException(context.getString(R.string.structured_text_parse_error), error)
        }

        return StructuredDocument(
            intro = introText?.takeIf { it.isNotBlank() },
            sections = sections,
            footer = footerBlocks
        )
    }

    private fun targetBlocks(
        currentSectionBlocks: MutableList<ContentBlock>?,
        footerBlocks: MutableList<ContentBlock>
    ): MutableList<ContentBlock> {
        return currentSectionBlocks ?: footerBlocks
    }

    private fun applyReplacements(
        source: String,
        replacements: Map<String, String>
    ): String {
        var text = source
        replacements.forEach { (key, value) ->
            text = text.replace("{{$key}}", value)
        }
        return text
    }

    private const val TAG_INTRO = "intro"
    private const val TAG_SECTION = "section"
    private const val TAG_FOOTER = "footer"
    private const val TAG_PARAGRAPH = "paragraph"
    private const val TAG_BULLET_LIST = "bulletList"
    private const val TAG_ITEM = "item"

    private const val ATTR_TITLE = "title"
}

data class StructuredDocument(
    val intro: String?,
    val sections: List<StructuredSection>,
    val footer: List<ContentBlock>
)

data class StructuredSection(
    val title: String,
    val blocks: List<ContentBlock>
)

sealed interface ContentBlock {
    data class Paragraph(val text: String) : ContentBlock
    data class BulletList(val items: List<String>) : ContentBlock
}

object StructuredTextFormatter {

    fun buildDocument(
        context: Context,
        document: StructuredDocument,
        includeIntro: Boolean = true,
        includeFooter: Boolean = true
    ): CharSequence {
        val builder = SpannableStringBuilder()

        if (includeIntro) {
            document.intro?.let {
                builder.append(it.trim())
                builder.append("\n\n")
            }
        }

        document.sections.forEachIndexed { index, section ->
            if (section.title.isNotBlank()) {
                if (builder.isNotEmpty() && builder.last() != '\n') {
                    builder.append('\n')
                }
                val titleStart = builder.length
                builder.append(section.title)
                builder.setSpan(
                    StyleSpan(Typeface.BOLD),
                    titleStart,
                    builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.setSpan(
                    RelativeSizeSpan(1.05f),
                    titleStart,
                    builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.append('\n')
            }

            appendBlocks(context, builder, section.blocks)

            if (index < document.sections.lastIndex) {
                builder.append('\n')
            }
        }

        if (includeFooter) {
            val footerContent = buildBlocks(context, document.footer)
            if (footerContent.isNotEmpty()) {
                if (builder.isNotEmpty()) {
                    builder.append('\n')
                }
                builder.append(footerContent)
            }
        }

        return builder.trimTrailingNewLines()
    }

    fun buildBlocks(context: Context, blocks: List<ContentBlock>): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        appendBlocks(context, builder, blocks)
        return builder.trimTrailingNewLines()
    }

    fun buildSectionBody(context: Context, section: StructuredSection): CharSequence {
        return buildBlocks(context, section.blocks)
    }

    fun buildDocumentHtml(
        document: StructuredDocument,
        includeIntro: Boolean = true,
        includeFooter: Boolean = true
    ): CharSequence {
        val html = StringBuilder()

        if (includeIntro) {
            html.appendParagraphHtml(document.intro)
        }

        document.sections.forEach { section ->
            val hasContent = section.title.isNotBlank() || section.blocks.isNotEmpty()
            if (!hasContent) return@forEach
            html.append("<div style=\"margin:0 0 20px 0;\">")
            if (section.title.isNotBlank()) {
                html.append(
                    "<p style=\"text-align:justify; font-weight:600; margin:0 0 8px 0;\">"
                )
                html.append(TextUtils.htmlEncode(section.title.trim()))
                html.append("</p>")
            }
            html.appendBlocksHtml(section.blocks)
            html.append("</div>")
        }

        if (includeFooter) {
            html.appendBlocksHtml(document.footer)
        }

        return html.toStyledText()
    }

    fun buildBlocksHtml(blocks: List<ContentBlock>): CharSequence {
        val html = StringBuilder()
        html.appendBlocksHtml(blocks)
        return html.toStyledText()
    }

    fun buildSectionBodyHtml(section: StructuredSection): CharSequence {
        return buildBlocksHtml(section.blocks)
    }

    fun buildParagraphHtml(text: String?): CharSequence? {
        val trimmed = text?.trim() ?: return null
        if (trimmed.isEmpty()) return null
        val html = StringBuilder()
        html.appendParagraphHtml(trimmed)
        val result = html.toStyledText()
        return if (result.isNotEmpty()) result else null
    }

    private fun appendBlocks(
        context: Context,
        builder: SpannableStringBuilder,
        blocks: List<ContentBlock>
    ) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is ContentBlock.Paragraph -> {
                    if (builder.isNotEmpty() && !builder.endsWithNewLine()) {
                        builder.append('\n')
                    }
                    builder.append(block.text)
                    if (index < blocks.lastIndex) {
                        builder.append("\n\n")
                    }
                }

                is ContentBlock.BulletList -> {
                    if (builder.isNotEmpty() && builder.last() != '\n') {
                        builder.append('\n')
                    }
                    appendBulletList(context, builder, block.items)
                    if (index < blocks.lastIndex) {
                        builder.append('\n')
                    }
                }
            }
        }
    }

    private fun appendBulletList(
        context: Context,
        builder: SpannableStringBuilder,
        items: List<String>
    ) {
        val density = context.resources.displayMetrics.density
        val gapWidth = (density * 12f).roundToInt().coerceAtLeast(6)

        items.forEach { rawItem ->
            val item = rawItem.trim()
            if (item.isEmpty()) return@forEach
            val start = builder.length
            builder.append(item)
            val end = builder.length
            builder.setSpan(
                BulletSpan(gapWidth),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder.append('\n')
        }
    }

    private fun SpannableStringBuilder.trimTrailingNewLines(): SpannableStringBuilder {
        var i = length - 1
        while (i >= 0 && this[i].isWhitespace()) {
            if (this[i] == '\n' || this[i] == ' ') {
                delete(i, i + 1)
                i--
            } else {
                break
            }
        }
        return this
    }

    private fun SpannableStringBuilder.endsWithNewLine(): Boolean {
        return isNotEmpty() && this[length - 1] == '\n'
    }

    private fun StringBuilder.appendParagraphHtml(text: String?) {
        val content = text?.trim().orEmpty()
        if (content.isEmpty()) return
        append("<p style=\"text-align:justify; margin:0 0 12px 0;\">")
        append(TextUtils.htmlEncode(content))
        append("</p>")
    }

    private fun StringBuilder.appendBulletListHtml(items: List<String>) {
        val sanitized = items.map { it.trim() }.filter { it.isNotEmpty() }
        if (sanitized.isEmpty()) return
        append("<ul style=\"margin:0 0 12px 20px; padding-left:12px;\">")
        sanitized.forEach { item ->
            append("<li style=\"text-align:justify; margin-bottom:8px;\">")
            append(TextUtils.htmlEncode(item))
            append("</li>")
        }
        append("</ul>")
    }

    private fun StringBuilder.appendBlocksHtml(blocks: List<ContentBlock>) {
        blocks.forEach { block ->
            when (block) {
                is ContentBlock.Paragraph -> appendParagraphHtml(block.text)
                is ContentBlock.BulletList -> appendBulletListHtml(block.items)
            }
        }
    }

    private fun StringBuilder.toStyledText(): CharSequence {
        if (isEmpty()) {
            return ""
        }
        return HtmlCompat.fromHtml(toString(), HtmlCompat.FROM_HTML_MODE_COMPACT)
    }
}

fun TextView.renderStructuredContent(content: CharSequence?) {
    if (content == null) {
        text = null
        return
    }
    text = content
    movementMethod = LinkMovementMethod.getInstance()
    LinkifyCompat.addLinks(this, Linkify.EMAIL_ADDRESSES or Linkify.WEB_URLS)
}
