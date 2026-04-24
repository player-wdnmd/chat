package com.example.chat.controller;

import com.example.chat.model.AuthenticatedUser;
import com.example.chat.model.ChatSkillDefinition;
import com.example.chat.model.ChatSkillOption;
import com.example.chat.model.SaveSkillRequest;
import com.example.chat.service.AuthService;
import com.example.chat.service.ChatSkillService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Log4j2
@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
public class ChatSkillController {

    private final AuthService authService;
    private final ChatSkillService chatSkillService;

    /**
     * 返回当前后端已识别到的 skill 列表，供前端下拉框渲染使用。
     *
     * <p>这里不做任何业务拼装，单纯把本地 skill 目录扫描后的结果透传给前端。
     * 入口日志主要用于确认：
     * 当前到底扫到了多少个 skill，
     * 以及前端“为什么下拉框是空的”这类问题到底发生在扫描前还是扫描后。</p>
     */
    @GetMapping
    public List<ChatSkillOption> listSkills(HttpServletRequest request) {
        AuthenticatedUser user = authService.requireAuthenticatedUser(request);
        List<ChatSkillOption> skills = chatSkillService.listOptions(user.id());
        log.info("返回技能列表：userId={}，count={}", user.id(), skills.size());
        return skills;
    }

    @PostMapping
    public ChatSkillOption createSkill(HttpServletRequest request, @Valid @RequestBody SaveSkillRequest saveSkillRequest) {
        AuthenticatedUser user = authService.requireAuthenticatedUser(request);
        log.info("收到技能创建请求：userId={}，skillName={}", user.id(), saveSkillRequest.skillName());
        return chatSkillService.createSkill(user.id(), saveSkillRequest);
    }

    @GetMapping("/{skillId}")
    public ChatSkillDefinition getSkill(HttpServletRequest request, @PathVariable Long skillId) {
        AuthenticatedUser user = authService.requireAuthenticatedUser(request);
        log.info("读取技能详情：userId={}，skillId={}", user.id(), skillId);
        ChatSkillDefinition skillDefinition = chatSkillService.getSkillDefinition(user.id(), skillId);
        if (skillDefinition == null) {
            throw new IllegalArgumentException("要查看的 skill 不存在。");
        }
        return skillDefinition;
    }

    @PutMapping("/{skillId}")
    public ChatSkillOption updateSkill(
            HttpServletRequest request,
            @PathVariable Long skillId,
            @Valid @RequestBody SaveSkillRequest saveSkillRequest
    ) {
        AuthenticatedUser user = authService.requireAuthenticatedUser(request);
        log.info("收到技能更新请求：userId={}，skillId={}，skillName={}", user.id(), skillId, saveSkillRequest.skillName());
        return chatSkillService.updateSkill(user.id(), skillId, saveSkillRequest);
    }

    @DeleteMapping("/{skillId}")
    public void deleteSkill(HttpServletRequest request, @PathVariable Long skillId) {
        AuthenticatedUser user = authService.requireAuthenticatedUser(request);
        log.info("收到技能删除请求：userId={}，skillId={}", user.id(), skillId);
        chatSkillService.deleteSkill(user.id(), skillId);
    }
}
