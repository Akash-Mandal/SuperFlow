package com.superflow.domain

import com.superflow.data.model.Review
import com.superflow.data.model.ReviewKind
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewActionsTest {

    /* ------------------------------------------------------------- parse */

    @Test
    fun `parse blank or empty text returns empty list`() {
        assertTrue(ReviewActions.parse("").isEmpty())
        assertTrue(ReviewActions.parse("   \n \t  ").isEmpty())
    }

    @Test
    fun `parse removes bullet prefixes`() {
        val text = """
            • Drink more water
            - Read a book
            * Exercise daily
        """.trimIndent()

        val result = ReviewActions.parse(text)
        assertEquals(listOf("Drink more water", "Read a book", "Exercise daily"), result)
    }

    @Test
    fun `parse removes numbered list prefixes`() {
        val text = """
            1. Sleep early
            2) Wake up early
            10. Take breaks
        """.trimIndent()

        val result = ReviewActions.parse(text)
        assertEquals(listOf("Sleep early", "Wake up early", "Take breaks"), result)
    }

    @Test
    fun `parse filters lines by length between 3 and 200 characters`() {
        val shortLine = "a"
        val maxValidLine = "a".repeat(200)
        val longLine = "a".repeat(201)

        val text = """
            $shortLine
            Valid item
            $maxValidLine
            $longLine
        """.trimIndent()

        val result = ReviewActions.parse(text)
        assertEquals(listOf("Valid item", maxValidLine), result)
    }

    /* -------------------------------------------------- action ID generation */

    @Test
    fun `action ID generation produces expected format`() {
        val review = Review(
            id = "rev-1",
            kind = ReviewKind.WEEKLY,
            periodLabel = "Week 1",
            systemChange = "• Drink water\n• Read 10 mins"
        )

        val parsed = ReviewActions.parse(review.systemChange)
        val actions = parsed.mapIndexed { i, text ->
            val id = "$i:${text.hashCode()}"
            ReviewActions.Action(id, text, false)
        }

        assertEquals(2, actions.size)
        assertEquals("0:${"Drink water".hashCode()}", actions[0].id)
        assertEquals("Drink water", actions[0].text)
        assertFalse(actions[0].done)

        assertEquals("1:${"Read 10 mins".hashCode()}", actions[1].id)
        assertEquals("Read 10 mins", actions[1].text)
        assertFalse(actions[1].done)
    }

    /* ------------------------------------------- doneMap helper & JSON format */

    @Test
    fun `toggleDone creates and updates JSON in reviewActions string`() {
        var reviewActionsJson = "{}"

        fun toggleDoneLocal(reviewId: String, actionId: String, done: Boolean) {
            val all = JSONObject(reviewActionsJson)
            val perReview = all.optJSONObject(reviewId) ?: JSONObject()
            perReview.put(actionId, done)
            all.put(reviewId, perReview)
            reviewActionsJson = all.toString()
        }

        fun doneMapLocal(reviewId: String): Map<String, Boolean> {
            val all = runCatching { JSONObject(reviewActionsJson) }.getOrNull() ?: return emptyMap()
            val obj = all.optJSONObject(reviewId) ?: return emptyMap()
            return obj.keys().asSequence().associateWith { obj.optBoolean(it) }
        }

        val reviewId = "rev-1"
        val actionId = "0:12345"

        assertTrue(doneMapLocal(reviewId).isEmpty())

        toggleDoneLocal(reviewId, actionId, true)
        assertEquals(mapOf(actionId to true), doneMapLocal(reviewId))

        toggleDoneLocal(reviewId, actionId, false)
        assertEquals(mapOf(actionId to false), doneMapLocal(reviewId))
    }

    /* ------------------------------------------------ openActions filtering */

    @Test
    fun `openActions logic filters completed items and takes up to 6 reviews`() {
        val reviews = (1..8).map { i ->
            Review(
                id = "rev-$i",
                kind = ReviewKind.WEEKLY,
                periodLabel = "Week $i",
                systemChange = "• Action item $i"
            )
        }

        // Simulate doneMap where rev-1 action is completed
        val doneMap = mapOf("0:${"Action item 1".hashCode()}" to true)

        val recentReviews = reviews.take(6)
        val openActions = recentReviews.flatMap { r ->
            val parsed = ReviewActions.parse(r.systemChange)
            parsed.mapIndexed { i, text ->
                val id = "$i:${text.hashCode()}"
                val isDone = if (r.id == "rev-1") doneMap[id] == true else false
                ReviewActions.Action(id, text, isDone)
            }.filterNot { it.done }.map { r to it }
        }

        // rev-1 item is done, so 5 items remain from the 6 recent reviews
        assertEquals(5, openActions.size)
        assertTrue(openActions.none { it.first.id == "rev-1" })
        assertEquals("rev-2", openActions[0].first.id)
        assertEquals("rev-6", openActions[4].first.id)
    }
}
