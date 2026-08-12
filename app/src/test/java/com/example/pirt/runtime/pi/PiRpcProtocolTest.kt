package com.example.pirt.runtime.pi

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PiRpcProtocolTest {
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
            PiStreamEvent.Failed("Codex error: The usage limit has been reached"),
            parseStreamEvent(JSONObject("""{
                "type":"message_end",
                "message":{"role":"assistant","stopReason":"error","errorMessage":"Codex error: The usage limit has been reached"}
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
}
