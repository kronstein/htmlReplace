package com.github.kronstein.html

import kotlinx.html.Entities
import kotlinx.html.HTMLTag
import kotlinx.html.HtmlInlineTag
import kotlinx.html.HtmlTagMarker
import kotlinx.html.Tag
import kotlinx.html.TagConsumer
import kotlinx.html.Unsafe
import kotlinx.html.consumers.delayed
import kotlinx.html.org.w3c.dom.events.Event
import kotlinx.html.stream.appendHTML
import kotlinx.html.unsafe
import kotlinx.html.visitAndFinalize
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Безопасно заменяет один или несколько тэгов в HTML.
 * @param html исходный HTML
 * @param cssQuery селекторы и замены
 */
fun htmlReplace(
    html: String,
    cssQuery: Map<String, TagConsumer<StringBuilder>.(Element) -> Unit>,
): String {
    val document = Jsoup.parse(html)
    val elements = document.body().children()

    val elementsToReplace = cssQuery.flatMap { (selector, function) ->
        document.select(selector).map { it to function }
    }.toMap()

    return buildString {
        with(appendHTML(prettyPrint = false).replacing(elementsToReplace)) {
            elements.forEach {
                jsoupElement(it)
            }
        }
    }.trim()
}

/**
 * Безопасно заменяет один или несколько тэгов в HTML.
 * @param html исходный HTML
 * @param cssQuery селектор и замена
 */
fun htmlReplace(
    html: String,
    cssQuery: Pair<String, TagConsumer<StringBuilder>.(Element) -> Unit>,
) = htmlReplace(
    html = html,
    cssQuery = mapOf(cssQuery)
)

private class JsoupElement(
    val element: Element,
    consumer: TagConsumer<*>,
): HTMLTag(
    tagName = element.tag().normalName(),
    consumer = consumer,
    emptyTag = false,
    inlineTag = true,
    initialAttributes = element.attributes().associate {
        it.key to it.value
    }
), HtmlInlineTag {}

@HtmlTagMarker
private fun <T> TagConsumer<T>.jsoupElement(
    element: Element,
): T {
    return JsoupElement(
        element = element,
        consumer = this,
    ).visitAndFinalize(
        consumer = this,
        block = {
            element.childNodes().forEach {
                when (it) {
                    is Element -> jsoupElement(it)
                    else -> unsafe {
                        raw(it.outerHtml())
                    }
                }
            }
        }
    )
}

private fun <T> TagConsumer<T>.replacing(
    elementsToReplace: Map<Element, TagConsumer<T>.(Element) -> Unit>,
): TagConsumer<T> = JsoupReplaceConsumer(
    downstream = this,
    elementsToReplace = elementsToReplace,
).delayed()

private class JsoupReplaceConsumer<T>(
    private val downstream: TagConsumer<T>,
    private val elementsToReplace: Map<Element, TagConsumer<T>.(Element) -> Unit>,
): TagConsumer<T> {
    private var currentLevel = 0
    private var skippedLevels = HashSet<Int>()
    private var dropLevel: Int? = null

    override fun onTagStart(tag: Tag) {
        currentLevel++

        if (dropLevel == null) {
            when (tag) {
                is JsoupElement -> elementsToReplace[tag.element]?.let { block ->
                    downstream.block(tag.element)
                    dropLevel = currentLevel
                } ?: downstream.onTagStart(tag)
                else -> downstream.onTagStart(tag)
            }
        }
    }

    override fun onTagAttributeChange(tag: Tag, attribute: String, value: String?) {
        throw UnsupportedOperationException("this filter doesn't support onTagAttributeChange")
    }

    override fun onTagEvent(tag: Tag, event: String, value: (Event) -> Unit) {
        throw UnsupportedOperationException("this filter doesn't support onTagEvent")
    }

    override fun onTagEnd(tag: Tag) {
        if (canPassCurrentLevel()) {
            downstream.onTagEnd(tag)
        }

        skippedLevels.remove(currentLevel)
        if (dropLevel == currentLevel) {
            dropLevel = null
        }

        currentLevel--
    }

    override fun onTagContent(content: CharSequence) {
        if (canPassCurrentLevel()) {
            downstream.onTagContent(content)
        }
    }

    override fun onTagContentEntity(entity: Entities) {
        if (canPassCurrentLevel()) {
            downstream.onTagContentEntity(entity)
        }
    }

    override fun onTagContentUnsafe(block: Unsafe.() -> Unit) {
        if (canPassCurrentLevel()) {
            downstream.onTagContentUnsafe(block)
        }
    }

    private fun canPassCurrentLevel() = dropLevel == null && currentLevel !in skippedLevels

    override fun onTagComment(content: CharSequence) {
        if (canPassCurrentLevel()) {
            downstream.onTagComment(content)
        }
    }

    override fun finalize(): T = downstream.finalize()
}
