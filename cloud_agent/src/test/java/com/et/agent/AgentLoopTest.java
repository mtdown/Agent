package com.et.agent;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AgentLoopTest {
    @Test void fileEvidenceIsKeptOutOfModelHistoryAndRecordedAsInternalEvent() {
        AtomicInteger turns = new AtomicInteger();
        AgentLoop loop = new AgentLoop((history, delta, cancelled) -> {
            if (turns.getAndIncrement() == 0) return new AgentLoop.Turn(
                    Collections.singletonList(new AgentLoop.ToolCall("1", "read_file_segment", "{}")), null);
            assertFalse(history.get(history.size() - 1).content.contains("secret-evidence"));
            return new AgentLoop.Turn(Collections.emptyList(), null);
        }, Collections.singletonMap("read_file_segment", args -> "{\"text\":\"hello\",\"referenceEvidence\":[\"secret-evidence\"]}"), 3, 10000, 1000);
        AgentLoop.Result result = loop.run("q", text -> {}, () -> false);
        assertTrue(result.wikiEvents.stream().anyMatch(event -> "personal_file_reference".equals(event.name)
                && event.result.contains("secret-evidence")));
    }
    @Test void sendsOnlySuppliedHistoryAndCurrentEvidence() {
        AgentLoop loop = new AgentLoop((history, delta, cancelled) -> {
            assertEquals("user", history.get(1).role);
            assertEquals("first question", history.get(1).content);
            assertEquals("assistant", history.get(2).role);
            assertTrue(history.get(3).content.contains("[1] source"));
            assertEquals("follow up", history.get(4).content);
            return new AgentLoop.Turn(Collections.emptyList(), null);
        }, Collections.emptyMap(), 2, 10000, 1000);
        loop.run("follow up", Arrays.asList(
                new AgentLoop.Message("user", "first question", null, null),
                new AgentLoop.Message("assistant", "first answer", null, null)),
                "[1] source", text -> {}, () -> false);
    }
    @Test void streamsFinalAnswerAndUsage() {
        List<String> chunks = new ArrayList<>();
        AgentLoop loop = new AgentLoop((history, delta, cancelled) -> {
            delta.accept("你"); delta.accept("好");
            return new AgentLoop.Turn(Collections.emptyList(), new AgentLoop.Usage(3, 2));
        }, Collections.emptyMap(), 3, 10000, 1000);
        AgentLoop.Result result = loop.run("问候", chunks::add, () -> false);
        assertEquals(Arrays.asList("你", "好"), chunks);
        assertEquals(5, result.usage.totalTokens);
        assertNotNull(result.firstAnswerMs);
    }

    @Test void returnsToolResultToNextTurn() {
        AtomicInteger turns = new AtomicInteger();
        AgentLoop loop = new AgentLoop((history, delta, cancelled) -> {
            if (turns.getAndIncrement() == 0)
                return new AgentLoop.Turn(Collections.singletonList(new AgentLoop.ToolCall("1", "read_file", "{}")), new AgentLoop.Usage(2, 1));
            assertTrue(history.stream().anyMatch(m -> "tool".equals(m.role) && "found".equals(m.content)));
            delta.accept("done");
            return new AgentLoop.Turn(Collections.emptyList(), new AgentLoop.Usage(3, 2));
        }, Collections.singletonMap("read_file", json -> "found"), 3, 10000, 1000);
        AgentLoop.Result result = loop.run("q", text -> {}, () -> false);
        assertEquals(2, turns.get());
        assertEquals(8, result.usage.totalTokens);
    }

    @Test void rejectsUnknownTool() {
        AgentLoop loop = new AgentLoop((history, delta, cancelled) -> new AgentLoop.Turn(
                Collections.singletonList(new AgentLoop.ToolCall("1", "missing", "{}")), null),
                Collections.emptyMap(), 3, 10000, 1000);
        assertThrows(IllegalArgumentException.class, () -> loop.run("q", text -> {}, () -> false));
    }

    @Test void rejectsInjectedUnregisteredToolWithoutExecutingIt() {
        AtomicInteger invoked = new AtomicInteger();
        AgentLoop loop = new AgentLoop((history, delta, cancelled) -> new AgentLoop.Turn(
                Collections.singletonList(new AgentLoop.ToolCall("1", "unregistered", "{}")), null),
                Collections.singletonMap("unregistered", json -> { invoked.incrementAndGet(); return "ok"; }),
                3, 10000, 1000);
        assertThrows(IllegalArgumentException.class, () -> loop.run("q", text -> {}, () -> false));
        assertEquals(0, invoked.get());
    }

    @Test void stopsAtTurnLimitBeforeAnotherTool() {
        AtomicInteger invoked = new AtomicInteger();
        AgentLoop loop = new AgentLoop((history, delta, cancelled) -> new AgentLoop.Turn(
                Collections.singletonList(new AgentLoop.ToolCall("1", "read_file", "{}")), null),
                Collections.singletonMap("read_file", json -> { invoked.incrementAndGet(); return "ok"; }), 1, 10000, 1000);
        assertThrows(IllegalStateException.class, () -> loop.run("q", text -> {}, () -> false));
        assertEquals(0, invoked.get());
    }

    @Test void cancellationStopsDeltas() {
        AtomicInteger chunks = new AtomicInteger();
        AgentLoop loop = new AgentLoop((history, delta, cancelled) -> {
            delta.accept("one"); delta.accept("two");
            return new AgentLoop.Turn(Collections.emptyList(), null);
        }, Collections.emptyMap(), 2, 10000, 1000);
        assertThrows(AgentLoop.Cancelled.class, () -> loop.run("q", text -> chunks.incrementAndGet(), () -> chunks.get() > 0));
        assertEquals(1, chunks.get());
    }

    @Test void rejectsMalformedToolArgumentsWithoutExecution() {
        AtomicInteger invoked = new AtomicInteger();
        AtomicInteger turns = new AtomicInteger();
        AgentLoop loop = new AgentLoop((history, delta, cancelled) -> {
            if (turns.getAndIncrement() == 0) return new AgentLoop.Turn(
                Collections.singletonList(new AgentLoop.ToolCall("1", "read_file", "{broken")), null);
            assertTrue(history.get(history.size() - 1).content.contains("\"code\":40000"));
            return new AgentLoop.Turn(Collections.emptyList(), null);
        },
                Collections.singletonMap("read_file", json -> { invoked.incrementAndGet(); return "ok"; }), 2, 10000, 1000);
        loop.run("q", text -> {}, () -> false);
        assertEquals(0, invoked.get());
    }

    @Test void expiresAfterModelTurn() {
        AgentLoop loop = new AgentLoop((history, delta, cancelled) -> {
            try { Thread.sleep(5); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return new AgentLoop.Turn(Collections.emptyList(), null);
        }, Collections.emptyMap(), 2, 1, 1000);
        assertThrows(IllegalStateException.class, () -> loop.run("q", text -> {}, () -> false));
    }

    @Test void loadsSelectedSkillOnlyAfterToolCall() {
        AtomicInteger turns = new AtomicInteger();
        AgentLoop.ToolAwareModel model = new AgentLoop.ToolAwareModel() {
            @Override public AgentLoop.Turn stream(List<AgentLoop.Message> history, java.util.Set<String> toolNames,
                                                   java.util.function.Consumer<String> delta,
                                                   java.util.function.BooleanSupplier cancelled) {
                assertTrue(toolNames.contains("get_skill"));
                if (turns.getAndIncrement() == 0) {
                    assertTrue(history.get(0).content.contains("skill_system"));
                    assertTrue(history.get(1).content.contains("Example"));
                    assertFalse(history.get(1).content.contains("secret step"));
                    return new AgentLoop.Turn(Collections.singletonList(
                            new AgentLoop.ToolCall("call-1", "get_skill", "{\"id\":\"9007199254740993\",\"version\":1}")), null);
                }
                assertTrue(history.stream().anyMatch(item -> "tool".equals(item.role) && item.content.contains("secret step")));
                return new AgentLoop.Turn(Collections.emptyList(), null);
            }
        };
        AgentLoop loop = new AgentLoop(model, Collections.emptyMap(), 3, 10000, 1000);
        loop.run("use example", Collections.emptyList(), null, "Example", Collections.singletonMap(
                "get_skill", args -> "secret step"), chunk -> {}, () -> false);
        assertEquals(2, turns.get());
    }

    @Test void failedLibraryWriteIsReportedToModelWithoutClaimingSuccess() {
        AtomicInteger turns = new AtomicInteger();
        AgentLoop loop = new AgentLoop((history, delta, cancelled) -> {
            if (turns.getAndIncrement() == 0)
                return new AgentLoop.Turn(Collections.singletonList(
                        new AgentLoop.ToolCall("write", "create_skill", "{}")), null);
            assertTrue(history.stream().anyMatch(item -> "tool".equals(item.role)
                    && item.content.contains("Code version or entrypoint does not exist")
                    && item.content.contains("\"saved\":false")));
            delta.accept("The draft was not saved");
            return new AgentLoop.Turn(Collections.emptyList(), null);
        }, Collections.singletonMap("create_skill", json -> {
            throw new IllegalArgumentException("Code version or entrypoint does not exist");
        }), 3, 10000, 1000);
        List<String> output = new ArrayList<>();
        loop.run("save draft", output::add, () -> false);
        assertEquals(Collections.singletonList("The draft was not saved"), output);
    }

    @Test void unknownBlocksOtherWritesInSameBatchOnlyForThatUserRequest() {
        AtomicInteger writes = new AtomicInteger();
        AtomicInteger otherWrites = new AtomicInteger();
        java.util.Map<String, AgentLoop.Tool> tools = new HashMap<>();
        tools.put("write_file", args -> { writes.incrementAndGet(); throw new AgentLibraryHttpGateway.CloudFailure(50001, "unknown", true); });
        tools.put("create_wiki_folder", args -> { otherWrites.incrementAndGet(); return "{}"; });
        AgentLoop loop = new AgentLoop((history, delta, cancelled) -> {
            if (history.stream().noneMatch(m -> "tool".equals(m.role))) return new AgentLoop.Turn(Arrays.asList(
                    new AgentLoop.ToolCall("1", "write_file", "{}"), new AgentLoop.ToolCall("2", "create_wiki_folder", "{}")), null);
            assertTrue(history.stream().anyMatch(m -> "2".equals(m.toolCallId) && m.content.contains("blocked")));
            return new AgentLoop.Turn(Collections.emptyList(), null);
        }, tools, 3, 10000, 2000);
        loop.run("first request", text -> {}, () -> false);
        loop.run("new request", text -> {}, () -> false);
        assertEquals(2, writes.get());
        assertEquals(0, otherWrites.get());
    }
    @Test void unknownWriteResultBlocksFurtherWritesButAllowsReads() {
        AtomicInteger reads = new AtomicInteger(), writes = new AtomicInteger(), turns = new AtomicInteger();
        Map<String, AgentLoop.Tool> tools = Map.of(
                "write_file", a -> "{\"status\":\"unknown\"}",
                "read_file", a -> { reads.incrementAndGet(); return "read"; },
                "create_code", a -> { writes.incrementAndGet(); return "written"; });
        AgentLoop loop = new AgentLoop((history, delta, cancelled) -> {
            if (turns.getAndIncrement() == 0) return new AgentLoop.Turn(List.of(
                    new AgentLoop.ToolCall("1", "write_file", "{}"),
                    new AgentLoop.ToolCall("2", "read_file", "{}"),
                    new AgentLoop.ToolCall("3", "create_code", "{}")), null);
            assertTrue(history.stream().anyMatch(m -> "3".equals(m.toolCallId) && m.content.contains("blocked")));
            return new AgentLoop.Turn(List.of(), null);
        }, tools, 3, 10000, 2000);
        loop.run("q", x -> {}, () -> false);
        assertEquals(1, reads.get());
        assertEquals(0, writes.get());
    }

    @Test void wikiReadAliasesPreservePaginationAndHideIdsAndPersonalNames() throws Exception {
        String space="123456789012345678", folder="876543210987654321"; AtomicInteger turns=new AtomicInteger();
        List<String> output=new ArrayList<>(); var json=new com.fasterxml.jackson.databind.ObjectMapper();
        AgentLoop.Model model=(history,delta,cancelled)->{
            int n=turns.getAndIncrement();
            if(n==0) return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("1","list_writable_wiki_locations","{}")),null);
            try {
                String response=history.get(history.size()-1).content;
                assertFalse(response.contains(space)); assertFalse(response.contains(folder)); assertFalse(response.contains("旧个人空间秘密"));
                var node=json.readTree(response); String alias=node.path("spaces").get(0).path("id").asText();
                if(n==1) return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("2","list_writable_wiki_locations",json.writeValueAsString(Map.of("spaceId",alias)))),null);
                String cursor=node.path("nextCursor").asText();
                if(n==2) return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("3","list_writable_wiki_locations",json.writeValueAsString(Map.of("spaceId",alias,"cursor",cursor)))),null);
                delta.accept("查询完成，可选择保存位置。"); return new AgentLoop.Turn(List.of(),null);
            } catch(Exception e){throw new RuntimeException(e);}
        };
        AtomicInteger calls=new AtomicInteger(); AgentLoop.Tool list=args->{
            try { var n=json.readTree(args); int k=calls.getAndIncrement();
                if(k>0) assertEquals(space,n.path("spaceId").asText()); if(k>1) assertEquals(folder,n.path("cursor").asText());
                return "{\"spaces\":[{\"id\":\""+space+"\",\"name\":\"旧个人空间秘密\",\"type\":\"personal\"}],\"nextCursor\":\""+folder+"\"}";
            }catch(Exception e){throw new RuntimeException(e);}
        };
        var tools=Map.<String,AgentLoop.Tool>of("list_writable_wiki_locations",list,"plan_wiki_destination",a->"{}");
        new AgentLoop(model,tools,5,10000,10000).run("list",output::add,()->false);
        assertEquals("查询完成，可选择保存位置。",String.join("",output)); assertEquals(3,calls.get());
    }
    @Test void wikiAliasWritesUseBoundValuesAndRejectForgedOrExpiredReferences() throws Exception {
        String space="123456789012345678", folder="876543210987654321";
        var json=new com.fasterxml.jackson.databind.ObjectMapper(); var issued=new java.util.concurrent.atomic.AtomicReference<String>();
        AtomicInteger turns=new AtomicInteger(), calls=new AtomicInteger(); List<String> output=new ArrayList<>();
        AgentLoop.Model model=(history,delta,cancelled)->{
            int n=turns.getAndIncrement();
            try {
                if(n==0) return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("1","plan_wiki_destination","{}")),null);
                if(n==1) {String ref=json.readTree(history.get(history.size()-1).content).path("spaceId").asText(); issued.set(ref);
                    return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("2","create_wiki_folder",json.writeValueAsString(Map.of("spaceId",ref)))),null);}
                if(n==2) {String ref=json.readTree(history.get(history.size()-1).content).path("folderId").asText();
                    return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("3","create_wiki_document",json.writeValueAsString(Map.of("spaceId",issued.get(),"folderId",ref)))),null);}
                delta.accept("must be hidden"); return new AgentLoop.Turn(List.of(),null);
            }catch(Exception e){throw new RuntimeException(e);}
        };
        var tools=Map.<String,AgentLoop.Tool>of("plan_wiki_destination",a->"{\"choiceRequired\":false,\"spaceId\":\""+space+"\"}",
         "create_wiki_folder",a->{try {assertEquals(space,json.readTree(a).path("spaceId").asText()); calls.incrementAndGet(); return "{\"folderId\":\""+folder+"\"}";}catch(Exception e){throw new RuntimeException(e);}},
         "create_wiki_document",a->{try {assertEquals(folder,json.readTree(a).path("folderId").asText()); assertEquals(space,json.readTree(a).path("spaceId").asText()); calls.incrementAndGet(); return "{\"documentId\":\"111222333444555666\"}";}catch(Exception e){throw new RuntimeException(e);}});
        var result=new AgentLoop(model,tools,5,10000,10000).run("q",output::add,()->false);
        assertEquals(2,calls.get()); assertTrue(output.isEmpty()); assertTrue(result.wikiEvents.get(1).result.contains(folder));
        for(String ref:List.of(issued.get(),"wiki_forged")) {
            AtomicInteger n=new AtomicInteger();
            AgentLoop.Model replay=(h,d,c)->{if(n.getAndIncrement()==0) return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("x","create_wiki_folder","{\"spaceId\":\""+ref+"\"}")),null);
                assertTrue(h.get(h.size()-1).content.contains("Invalid or expired")); return new AgentLoop.Turn(List.of(),null);};
            new AgentLoop(replay,tools,3,10000,10000).run("q",x->{},()->false);
        }
        assertEquals(2,calls.get());
    }
    @Test void wikiReadFailureDeliversFinalAnswer() {
        AtomicInteger turns=new AtomicInteger(); List<String> out=new ArrayList<>();
        AgentLoop.Model model=(h,d,c)->{if(turns.getAndIncrement()==0) return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("x","list_writable_wiki_locations","{}")),null);
            d.accept("Read failed. Please check access."); return new AgentLoop.Turn(List.of(),null);};
        new AgentLoop(model,Map.of("list_writable_wiki_locations", a->"{\"code\":40101,\"message\":\"request rejected\"}"),3,10000,10000).run("q",out::add,()->false);
        assertEquals("Read failed. Please check access.",String.join("",out));
    }
    @Test void selectedDestinationNeverEntersModelHistoryAndRemainsBoundOnWrites() throws Exception {
        String selected="123456789012345678"; AtomicInteger step=new AtomicInteger(); var json=new com.fasterxml.jackson.databind.ObjectMapper();
        AgentLoop.Model model=(history,delta,cancelled)->{
            assertTrue(history.stream().noneMatch(m->m.content!=null && m.content.contains(selected)));
            int n=step.getAndIncrement();
            if(n==0) return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("1","plan_wiki_destination","{\"destinationType\":\"personal\"}")),null);
            if(n==1) try {String ref=json.readTree(history.get(history.size()-1).content).path("spaceId").asText();
                return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("2","create_wiki_folder",json.writeValueAsString(Map.of("spaceId",ref,"taskName","任务")))),null);
            } catch(Exception e){throw new RuntimeException(e);}
            return new AgentLoop.Turn(List.of(),null);
        };
        AtomicInteger writes=new AtomicInteger();
        AgentLibraryGateway gateway=new AgentLibraryGateway() {
            public String getSkill(String u,String id,int v){return "{}";} public String createSkill(String u,String d){return "{}";}
            public String createCode(String u,String d){return "{}";} public String writeFile(String u,String p,String c,String m){return "{}";}
            public String readFile(String u,String p){return "{}";} public String writeWikiDocument(String u,String sp,String title,String content){return "{}";}
            public String createWikiFolder(String u,String sp,String task){assertEquals(selected,sp);writes.incrementAndGet();return "{\"spaceId\":\""+sp+"\",\"folderId\":\"456789123456789123\",\"name\":\"任务\"}";}
        };
        var controller=new AgentController(new AgentLoop(model,Map.of(),4,10000,10000),"secret",gateway);
        var ask=new AgentController.Ask("保存"); ask.userId="7"; ask.selectedSpaceId=selected;
        var out=new java.io.ByteArrayOutputStream(); controller.stream("secret",ask).writeTo(out);
        assertEquals(1,writes.get()); assertTrue(out.toString(java.nio.charset.StandardCharsets.UTF_8).contains("event: done"));
    }
    @Test void fixedWikiSkillLocationsBecomeTurnAliasesWithoutChangingOtherSteps() throws Exception {
        var json=new com.fasterxml.jackson.databind.ObjectMapper(); String space="123456789012345678", folder="234567890123456789";
        String definition="{\"name\":\"Example\",\"steps\":[{\"type\":\"tool\",\"tool\":\"create_wiki_document\",\"args\":{\"spaceId\":\""+space+"\",\"folderId\":\""+folder+"\",\"content\":\"plain 123456789012345678\"}},{\"type\":\"tool\",\"tool\":\"read_file\",\"args\":{\"spaceId\":\""+space+"\",\"path\":\"note\"}},{\"type\":\"code\",\"code_item_id\":\""+space+"\",\"code_version\":1,\"entrypoint\":\"run\",\"args\":{}}]}";
        for(boolean serialized : new boolean[]{false,true}) {
            AtomicInteger n=new AtomicInteger(), writes=new AtomicInteger();
            AgentLoop.Model model=(history,delta,cancelled)->{try {
                if(n.getAndIncrement()==0) return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("1","get_skill","{}")),null);
                if(n.get()==2) {
                    var envelope=json.readTree(history.get(history.size()-1).content); assertEquals(7,envelope.path("version").asInt());
                    var parsed=serialized?json.readTree(envelope.path("definitionJson").asText()):envelope.path("definition");
                    var steps=parsed.path("steps"); var args=steps.get(0).path("args");
                    assertTrue(args.path("spaceId").asText().startsWith("wiki_")); assertTrue(args.path("folderId").asText().startsWith("wiki_"));
                    assertEquals("plain "+space,args.path("content").asText()); assertEquals(space,steps.get(1).path("args").path("spaceId").asText());
                    assertEquals(space,steps.get(2).path("code_item_id").asText());
                    return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("2","create_wiki_document",args.toString())),null);
                }
                return new AgentLoop.Turn(List.of(),null);
            }catch(Exception e){throw new RuntimeException(e);}};
            String envelope=serialized?json.writeValueAsString(Map.of("version",7,"definitionJson",definition)):
                    "{\"version\":7,\"definition\":"+definition+"}";
            var tools=Map.<String,AgentLoop.Tool>of("get_skill",a->envelope,"create_wiki_document",a->{try {
                var args=json.readTree(a); assertEquals(space,args.path("spaceId").asText()); assertEquals(folder,args.path("folderId").asText());
                writes.incrementAndGet(); return "{\"saved\":true}";
            }catch(Exception e){throw new RuntimeException(e);}});
            new AgentLoop(model,tools,4,10000,10000).run("run",x->{},()->false); assertEquals(1,writes.get());
            assertEquals(definition,serialized?json.readTree(envelope).path("definitionJson").asText():json.writeValueAsString(json.readTree(envelope).path("definition")));
        }
    }
    @Test void skillIssuedReferencesCannotBeReusedInAnotherTurn() throws Exception {
        AtomicInteger n=new AtomicInteger(), writes=new AtomicInteger(); var issued=new java.util.concurrent.atomic.AtomicReference<String>();
        AgentLoop.Model model=(h,d,c)->{try {
            if(n.getAndIncrement()==0) return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("1","get_skill","{}")),null);
            issued.set(new com.fasterxml.jackson.databind.ObjectMapper().readTree(h.get(h.size()-1).content).path("definition").path("steps").get(0).path("args").path("spaceId").asText());
            return new AgentLoop.Turn(List.of(),null);
        }catch(Exception e){throw new RuntimeException(e);}};
        String skill="{\"version\":1,\"definition\":{\"steps\":[{\"type\":\"tool\",\"tool\":\"write_wiki_document\",\"args\":{\"spaceId\":\"123456789\"}}]}}";
        var tools=Map.<String,AgentLoop.Tool>of("get_skill",a->skill,"write_wiki_document",a->{writes.incrementAndGet();return "{}";});
        new AgentLoop(model,tools,3,10000,10000).run("first",x->{},()->false);
        assertTrue(issued.get().startsWith("wiki_"));
        for(String value:List.of(issued.get(),"wiki_forged")) {
            AtomicInteger calls=new AtomicInteger(); AgentLoop.Model replay=(h,d,c)->{
                if(calls.getAndIncrement()==0) return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("2","write_wiki_document","{\"spaceId\":\""+value+"\"}")),null);
                assertTrue(h.get(h.size()-1).content.contains("Invalid or expired")); return new AgentLoop.Turn(List.of(),null);};
            new AgentLoop(replay,tools,3,10000,10000).run("replay",x->{},()->false);
        }
        assertEquals(0,writes.get());
    }
    @Test void skillWithoutFixedWikiLocationsRemainsByteForByteUnchanged() {
        String response="{\"id\":\"builtin:wiki-write\", \"version\":1, \"definitionJson\":\"{\\\"steps\\\":[{\\\"type\\\":\\\"response\\\",\\\"template\\\":\\\"test\\\"}]}\"}";
        AtomicInteger n=new AtomicInteger(); AgentLoop.Model model=(h,d,c)->{
            if(n.getAndIncrement()==0) return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("1","get_skill","{}")),null);
            assertEquals(response,h.get(h.size()-1).content); return new AgentLoop.Turn(List.of(),null);
        };
        new AgentLoop(model,Map.of("get_skill",a->response),3,10000,10000).run("skill",x->{},()->false);
    }
    @Test void legacyWikiSkillSpaceAliasExecutesOriginalWrite() {
        var json=new com.fasterxml.jackson.databind.ObjectMapper(); AtomicInteger n=new AtomicInteger(), writes=new AtomicInteger();
        String definition="{\"steps\":[{\"type\":\"tool\",\"tool\":\"write_wiki_document\",\"args\":{\"spaceId\":\"123456789\",\"title\":\"T\",\"content\":\"B\"}}]}";
        AgentLoop.Model model=(h,d,c)->{try {
            int step=n.getAndIncrement(); if(step==0) return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("1","get_skill","{}")),null);
            if(step==1) {var args=json.readTree(h.get(h.size()-1).content).path("definition").path("steps").get(0).path("args");
                assertTrue(args.path("spaceId").asText().startsWith("wiki_")); return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("2","write_wiki_document",args.toString())),null);}
            return new AgentLoop.Turn(List.of(),null);
        }catch(Exception e){throw new RuntimeException(e);}};
        var tools=Map.<String,AgentLoop.Tool>of("get_skill",a->"{\"id\":\"7\",\"version\":1,\"definition\":"+definition+"}","write_wiki_document",a->{try {
            assertEquals("123456789",json.readTree(a).path("spaceId").asText()); writes.incrementAndGet(); return "{\"saved\":true}";
        }catch(Exception e){throw new RuntimeException(e);}});
        var result=new AgentLoop(model,tools,4,10000,10000).run("use skill",x->{},()->false);
        assertEquals(1,writes.get()); assertEquals("write_wiki_document",result.wikiEvents.get(0).name);
    }
    @Test void skillReferenceCannotOverrideCurrentUsersSelectedSpace() throws Exception {
        AtomicInteger turns=new AtomicInteger(), writes=new AtomicInteger(); var json=new com.fasterxml.jackson.databind.ObjectMapper();
        String definition="{\"steps\":[{\"type\":\"tool\",\"tool\":\"write_wiki_document\",\"args\":{\"spaceId\":\"111\",\"title\":\"T\",\"content\":\"B\"}}]}";
        AgentLibraryGateway gateway=new AgentLibraryGateway() {
            public String getSkill(String u,String id,int v){try{return json.writeValueAsString(Map.of("id",id,"version",v,"definitionJson",definition));}catch(Exception e){throw new RuntimeException(e);}}
            public String createSkill(String u,String d){return "{}";} public String createCode(String u,String d){return "{}";}
            public String writeFile(String u,String p,String c,String m){return "{}";} public String readFile(String u,String p){return "{}";}
            public String writeWikiDocument(String u,String space,String title,String content){writes.incrementAndGet();return "{}";}
        };
        AgentLoop.Model model=(h,d,c)->{try {
            int step=turns.getAndIncrement(); if(step==0) return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("1","get_skill","{\"id\":\"1\",\"version\":1}")),null);
            if(step==1) {var skill=json.readTree(json.readTree(h.get(h.size()-1).content).path("definitionJson").asText());
                return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("2","write_wiki_document",skill.path("steps").get(0).path("args").toString())),null);}
            assertTrue(h.get(h.size()-1).content.contains("Selected Wiki destination differs")); return new AgentLoop.Turn(List.of(),null);
        }catch(Exception e){throw new RuntimeException(e);}};
        new AgentLoop(model,Map.of(),4,10000,10000).run("use skill",List.of(),null,null,AgentToolRegistry.bind("7",gateway,"222"),x->{},()->false);
        assertEquals(0,writes.get());
    }
}
