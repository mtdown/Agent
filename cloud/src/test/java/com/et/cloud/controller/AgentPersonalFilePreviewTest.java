package com.et.cloud.controller;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
class AgentPersonalFilePreviewTest {
 @TempDir Path root;
 @Test void positionedSegmentsAreBoundedUtf8AndVersionBound() {
  var files = new AgentPersonalFileStore(root);
  files.write(1, "a.txt", "甲乙丙丁戊己", "create");
  String version = files.version(1, "a.txt");
  var segment = files.previewSegment(1, "a.txt", version, 6, 6);
  assertEquals("丙丁", segment.text); assertEquals(6, segment.start);
  assertThrows(IllegalArgumentException.class, () -> files.previewSegment(1, "a.txt", version, 7, 6));
  files.write(1, "a.txt", "changed", "overwrite");
  assertThrows(IllegalStateException.class, () -> files.previewSegment(1, "a.txt", version, 6, 6));
 }
}
