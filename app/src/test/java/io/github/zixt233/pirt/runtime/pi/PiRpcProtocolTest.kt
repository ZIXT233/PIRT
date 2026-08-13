package io.github.zixt233.pirt.runtime.pi

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PiRpcProtocolTest {
    @Test
    fun modelSwitchDecodesClampedThinkingState() {
        val selection = PiRequest.SetModel("deepseek", "deepseek-v4-pro").decode(JSONObject(
            """{
                "model":{"provider":"deepseek","id":"deepseek-v4-pro","name":"DeepSeek V4 Pro","reasoning":true},
                "thinkingLevel":"medium",
                "thinkingLevels":["off","minimal","low","medium","high"]
            }""".trimIndent()
        ))

        assertEquals("deepseek-v4-pro", selection.model.id)
        assertTrue(selection.model.reasoning)
        assertEquals("medium", selection.thinkingLevel)
        assertEquals(listOf("off", "minimal", "low", "medium", "high"), selection.thinkingLevels)
    }

    @Test
    fun getStateDecodesTheOfficialRuntimeFields() {
        val data = JSONObject(
            """{
                "model":{"provider":"openai-codex","id":"gpt-5.6","name":"GPT-5.6"},
                "thinkingLevel":"high",
                "isStreaming":true,
                "isCompacting":false,
                "sessionFile":"/root/.pi/session.jsonl",
                "sessionId":"session-1",
                "pendingMessageCount":2,
                "autoCompactionEnabled":false
            }""".trimIndent()
        )

        val state = PiRequest.GetState.decode(data)

        assertEquals("openai-codex", state.provider)
        assertEquals("gpt-5.6", state.modelId)
        assertEquals("high", state.thinkingLevel)
        assertTrue(state.streaming)
        assertFalse(state.compacting)
        assertEquals("session-1", state.sessionId)
        assertEquals(2, state.pendingMessageCount)
        assertFalse(state.autoCompactionEnabled)
    }

    @Test
    fun messagesDecodeTextAndImagesWithoutOwningPiHistory() {
        val data = JSONObject(
            """{"messages":[
                {"entryId":"entry-user","role":"user","content":[
                    {"type":"text","text":"inspect"},
                    {"type":"image","data":"abc","mimeType":"image/png"}
                ]},
                {"role":"toolResult","content":"internal"},
                {"role":"assistant","content":"done"}
            ]}"""
        )

        val messages = PiRequest.GetMessages.decode(data)

        assertEquals(2, messages.size)
        assertEquals(PiMessageRole.USER, messages[0].role)
        assertEquals("inspect", messages[0].text)
        assertEquals("entry-user", messages[0].entryId)
        assertEquals("image/png", messages[0].images.single().mimeType)
        assertEquals(PiMessageRole.ASSISTANT, messages[1].role)
    }

    @Test
    fun messageHistoryPromotesOnlyExplicitlySentImages() {
        val messages = PiRequest.GetMessages.decode(JSONObject("""{"messages":[
            {"entryId":"read-image-entry","role":"toolResult","toolName":"read","content":[
                {"type":"text","text":"Read image file [image/png]"},
                {"type":"image","data":"aGlkZGVu","mimeType":"image/png"}
            ]},
            {"entryId":"tool-image-entry","role":"toolResult","toolName":"send_image","content":[
                {"type":"text","text":"Read image file [image/png]"},
                {"type":"image","data":"cG5n","mimeType":"image/png"}
            ]},
            {"entryId":"tool-text-entry","role":"toolResult","content":[
                {"type":"text","text":"ordinary command output"}
            ]}
        ]}"""))

        assertEquals(1, messages.size)
        assertEquals(PiMessageRole.ASSISTANT, messages.single().role)
        assertEquals("tool-image-entry", messages.single().entryId)
        assertEquals("", messages.single().text)
        assertEquals("cG5n", messages.single().images.single().data)
        assertEquals("image/png", messages.single().images.single().mimeType)
    }

    @Test
    fun sdkSessionOpenCarriesOptionalPiPath() {
        val open = PiRequest.OpenSession("/root/.pi/pirt-sessions/session.jsonl")

        assertEquals("session_open", open.command)
        assertEquals("/root/.pi/pirt-sessions/session.jsonl", open.payload().getString("sessionPath"))
    }

    @Test
    fun nativeForkCloneAndSteerUseTypedPayloads() {
        assertEquals("entry-1", PiRequest.Fork("entry-1").payload().getString("entryId"))
        assertEquals("clone", PiRequest.Clone.command)
        val steer = PiRequest.Steer("change direction", listOf(PiImage("abc", "image/png"))).payload()
        assertEquals("change direction", steer.getString("message"))
        assertEquals("image/png", steer.getJSONArray("images").getJSONObject(0).getString("mimeType"))

        val replacement = PiRequest.Fork("entry-1").decode(JSONObject("""{
            "cancelled":false,
            "selectedText":"original prompt",
            "sessionKey":"session-2",
            "state":{"sessionId":"session-2","sessionFile":"/sessions/2.jsonl"},
            "messages":[{"entryId":"entry-0","role":"user","content":"before"}]
        }"""))

        assertFalse(replacement.cancelled)
        assertEquals("original prompt", replacement.selectedText)
        assertEquals("session-2", replacement.sessionKey)
        assertEquals("/sessions/2.jsonl", replacement.agent?.sessionFile)
        assertEquals("entry-0", replacement.messages.single().entryId)
    }

    @Test
    fun streamParserSeparatesKnownIgnoredAndUnknownEvents() {
        assertEquals(
            PiStreamEvent.TextDelta("hello"),
            parseStreamEvent(JSONObject("""{"type":"message_update","assistantMessageEvent":{"type":"text_delta","delta":"hello"}}""")),
        )
        assertEquals(PiStreamEvent.Ignored, parseStreamEvent(JSONObject("""{"type":"message_end"}""")))
        assertEquals(
            PiStreamEvent.AssistantError("Codex error: The usage limit has been reached"),
            parseStreamEvent(JSONObject("""{
                "type":"message_end",
                "message":{"role":"assistant","stopReason":"error","errorMessage":"Codex error: The usage limit has been reached"}
            }""")),
        )
        assertEquals(
            PiStreamEvent.AutoRetryStarted(attempt = 1, maxAttempts = 3, delayMs = 2_000),
            parseStreamEvent(JSONObject("""{
                "type":"auto_retry_start","attempt":1,"maxAttempts":3,"delayMs":2000,"errorMessage":"fetch failed"
            }""")),
        )
        assertEquals(
            PiStreamEvent.AutoRetryEnded(success = false, attempt = 3, finalError = "fetch failed"),
            parseStreamEvent(JSONObject("""{
                "type":"auto_retry_end","success":false,"attempt":3,"finalError":"fetch failed"
            }""")),
        )
        assertEquals(
            PiStreamEvent.CompactionStarted("manual"),
            parseStreamEvent(JSONObject("""{"type":"compaction_start","reason":"manual"}""")),
        )
        assertEquals(
            PiStreamEvent.CompactionEnded("manual", aborted = false, willRetry = false, errorMessage = null),
            parseStreamEvent(JSONObject("""{
                "type":"compaction_end","reason":"manual","aborted":false,"willRetry":false,
                "result":{"summary":"done"}
            }""")),
        )
        assertEquals(
            PiStreamEvent.QueueUpdated(listOf("use the new API")),
            parseStreamEvent(JSONObject("""{"type":"queue_update","steering":["use the new API"],"followUp":[]}""")),
        )
        assertEquals(
            PiStreamEvent.UserMessageStarted("change direction"),
            parseStreamEvent(JSONObject("""{"type":"message_start","message":{"role":"user","content":[{"type":"text","text":"change direction"}]}}""")),
        )
        assertEquals(
            PiStreamEvent.Ignored,
            parseStreamEvent(JSONObject("""{"type":"message_start","message":{"role":"assistant","content":[]}}""")),
        )
        assertNull(parseStreamEvent(JSONObject("""{"type":"future_event"}""")))
    }

    @Test
    fun messageHistoryPreservesAssistantErrors() {
        val messages = PiRequest.GetMessages.decode(JSONObject("""{"messages":[{
            "role":"assistant",
            "content":[],
            "stopReason":"error",
            "errorMessage":"Codex error: The usage limit has been reached"
        }]}"""))

        assertEquals(1, messages.size)
        assertEquals(PiMessageRole.SYSTEM, messages.single().role)
        assertEquals("Codex error: The usage limit has been reached", messages.single().text)
    }

    @Test
    fun streamParserPreservesOrderedThinkingAndCompleteToolInput() {
        assertEquals(
            PiStreamEvent.ThinkingStarted,
            parseStreamEvent(JSONObject("""{"type":"message_update","assistantMessageEvent":{"type":"thinking_start"}}""")),
        )
        assertEquals(
            PiStreamEvent.ThinkingEnded,
            parseStreamEvent(JSONObject("""{"type":"message_update","assistantMessageEvent":{"type":"thinking_end"}}""")),
        )

        val event = parseStreamEvent(JSONObject("""{
            "type":"tool_execution_start",
            "toolCallId":"call-1",
            "toolName":"bash",
            "args":{"command":"ls -la","timeout":30}
        }""")) as PiStreamEvent.ToolStarted

        assertEquals("call-1", event.id)
        assertEquals("ls -la", event.summary)
        assertTrue(event.input.contains("\"timeout\": 30"))
    }

    @Test
    fun streamParserPreservesImagesReturnedByTools() {
        val event = parseStreamEvent(JSONObject("""{
            "type":"tool_execution_end",
            "toolCallId":"call-image",
            "toolName":"view_image",
            "result":{"content":[
                {"type":"text","text":"Screenshot"},
                {"type":"image","data":"cG5n","mimeType":"image/png"}
            ]},
            "isError":false
        }""")) as PiStreamEvent.ToolEnded

        assertEquals("Screenshot", event.output)
        assertEquals("cG5n", event.images.single().data)
        assertEquals("image/png", event.images.single().mimeType)
        assertFalse(event.failed)
    }

    @Test
    fun sessionStatsDecodeTokensAndNullableContextUsageWithoutCost() {
        val stats = PiRequest.GetSessionStats.decode(JSONObject("""{
            "tokens":{"input":1200,"output":300,"cacheRead":5000,"cacheWrite":50,"total":6550},
            "cost":12.34,
            "contextUsage":{"tokens":42000,"contextWindow":128000,"percent":32.8125}
        }"""))

        assertEquals(6550L, stats.tokens.total)
        assertEquals(42000L, stats.contextUsage?.tokens)
        assertEquals(128000L, stats.contextUsage?.contextWindow)
        assertEquals(32.8125, stats.contextUsage?.percent ?: 0.0, 0.0001)
        assertEquals("export_html", PiRequest.ExportHtml.command)
        assertEquals("/workspace/export.html", PiRequest.ExportHtml.decode(JSONObject("""{"path":"/workspace/export.html"}""")))
    }

    @Test
    fun extensionUiProtocolPreservesDialogsAndResponses() {
        val request = parseStreamEvent(JSONObject("""{
            "type":"extension_ui_request",
            "sessionKey":"session-1",
            "id":"dialog-1",
            "method":"select",
            "title":"Choose target",
            "options":["staging","production"]
        }""")) as PiStreamEvent.ExtensionUiRequested

        assertEquals("dialog-1", request.request.id)
        assertEquals("Choose target", request.request.title)
        assertEquals(listOf("staging", "production"), request.request.options)

        val response = PiRequest.ExtensionUiResponse("dialog-1", value = "staging").payload()
        assertEquals("dialog-1", response.getString("requestId"))
        assertEquals("staging", response.getString("value"))
        assertFalse(response.has("confirmed"))

        assertEquals(
            PiStreamEvent.ExtensionUiCancelled("dialog-1"),
            parseStreamEvent(JSONObject("""{"type":"extension_ui_cancel","id":"dialog-1"}""")),
        )
    }
}
