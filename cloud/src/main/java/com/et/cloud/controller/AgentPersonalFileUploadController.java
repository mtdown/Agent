package com.et.cloud.controller;
import com.et.cloud.commen.BaseResponse;
import com.et.cloud.commen.ResultUtils;
import com.et.cloud.service.UserService;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
@RestController
@RequestMapping("/agent/personal-file-uploads")
public class AgentPersonalFileUploadController {
 private final UserService users; private final AgentPersonalFileUpload uploads;
 public AgentPersonalFileUploadController(UserService users, AgentPersonalFileUpload uploads) { this.users=users; this.uploads=uploads; }
 public static class Begin { public String path; public String mode; public long size; }
 @PostMapping public BaseResponse<Map<String,String>> begin(@RequestBody Begin body, HttpServletRequest request) {
  long user=users.getLoginUser(request).getId();
  if (body == null) throw new IllegalArgumentException("Invalid upload");
  return ResultUtils.success(Map.of("uploadId",uploads.begin(user,body.path,body.mode,body.size)));
 }
 @PutMapping(value="/{id}/segments", consumes="application/octet-stream") public BaseResponse<Boolean> part(@PathVariable String id, @RequestParam long offset, HttpServletRequest request) throws IOException {
  long user=users.getLoginUser(request).getId(); uploads.part(user,id,offset,request.getInputStream()); return ResultUtils.success(true);
 }
 @PostMapping("/{id}/commit") public BaseResponse<Map<String,Object>> commit(@PathVariable String id,HttpServletRequest request) {
  long user=users.getLoginUser(request).getId(); AgentPersonalFileStore.WriteResult result=uploads.commit(user,id); return ResultUtils.success(Map.of("bytes",result.bytes,"verified",result.verified));
 }
 @DeleteMapping("/{id}") public BaseResponse<Boolean> cancel(@PathVariable String id,HttpServletRequest request) {
  long user=users.getLoginUser(request).getId(); uploads.cancel(user,id); return ResultUtils.success(true);
 }
}
