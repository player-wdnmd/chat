package com.example.chat.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.chat.entity.ChatUserSkill;
import com.example.chat.mapper.ChatUserSkillMapper;
import com.example.chat.model.ChatMessage;
import com.example.chat.model.ChatSkillDefinition;
import com.example.chat.model.ChatSkillOption;
import com.example.chat.model.SaveSkillRequest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 数据库版 skill 服务。
 *
 * <p>当前版本中，skill 不再从本地文件目录读取，而是直接从数据库表 `chat_user_skill`
 * 按用户查询。这样每个用户都可以拥有自己独立的 skills 集合，管理员预制 skill 也通过
 * SQL 初始化注入数据库。</p>
 */
@Service
@RequiredArgsConstructor
public class ChatSkillService {

    private static final Logger log = LoggerFactory.getLogger(ChatSkillService.class);

    private final ChatUserSkillMapper chatUserSkillMapper;

    /**
     * 返回当前用户可见的 skill 列表，供聊天页下拉框和技能管理页列表共用。
     */
    public List<ChatSkillOption> listOptions(Long userId) {
        List<ChatUserSkill> skills = loadUserSkills(userId);
        log.info("整理用户技能列表：userId={}，count={}", userId, skills.size());
        return skills.stream()
                .map(this::toOption)
                .toList();
    }

    /**
     * 新增一个自定义 skill，并绑定到当前用户。
     */
    @Transactional
    public ChatSkillOption createSkill(Long userId, SaveSkillRequest request) {
        ChatUserSkill skill = new ChatUserSkill();
        skill.setUserId(userId);
        skill.setSkillName(request.skillName().trim());
        skill.setSkillDescription(StringUtils.hasText(request.skillDescription()) ? request.skillDescription().trim() : null);
        skill.setSystemPrompt(request.systemPrompt().trim());
        chatUserSkillMapper.insert(skill);
        log.info("创建用户技能成功：userId={}，skillId={}，skillName={}", userId, skill.getId(), skill.getSkillName());
        return toOption(skill);
    }

    /**
     * 更新当前用户名下的一个 skill。
     */
    @Transactional
    public ChatSkillOption updateSkill(Long userId, Long skillId, SaveSkillRequest request) {
        ChatUserSkill skill = chatUserSkillMapper.selectById(skillId);
        if (skill == null || !userId.equals(skill.getUserId())) {
            throw new IllegalArgumentException("要更新的 skill 不存在。");
        }

        skill.setSkillName(request.skillName().trim());
        skill.setSkillDescription(StringUtils.hasText(request.skillDescription()) ? request.skillDescription().trim() : null);
        skill.setSystemPrompt(request.systemPrompt().trim());
        chatUserSkillMapper.updateById(skill);
        log.info("更新用户技能成功：userId={}，skillId={}，skillName={}", userId, skill.getId(), skill.getSkillName());
        return toOption(skill);
    }

    /**
     * 删除当前用户名下的一个 skill。
     *
     * <p>只允许删除自己的 skill，删除不存在或不属于自己的 skill 会直接忽略。</p>
     */
    @Transactional
    public void deleteSkill(Long userId, Long skillId) {
        int deleted = chatUserSkillMapper.delete(
                Wrappers.<ChatUserSkill>lambdaQuery()
                        .eq(ChatUserSkill::getId, skillId)
                        .eq(ChatUserSkill::getUserId, userId)
        );
        log.info("删除用户技能：userId={}，skillId={}，deletedRows={}", userId, skillId, deleted);
    }

    /**
     * 把前端传来的 skill id 列表归一化成“当前用户名下最多一个合法 skill id”。
     */
    public List<String> normalizeSkillIds(Long userId, List<String> requestedSkillIds) {
        if (requestedSkillIds == null || requestedSkillIds.isEmpty()) {
            return List.of();
        }

        Map<String, ChatUserSkill> skillIndex = loadUserSkills(userId).stream()
                .collect(Collectors.toMap(skill -> String.valueOf(skill.getId()), skill -> skill));

        List<String> normalized = requestedSkillIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .map(skillIndex::get)
                .filter(Objects::nonNull)
                .map(skill -> String.valueOf(skill.getId()))
                .toList();
        return normalized.isEmpty() ? List.of() : List.of(normalized.getFirst());
    }

    /**
     * 把选中的 skill 展开成一条注入模型上下文的 system 消息。
     */
    public List<ChatMessage> buildSkillMessages(Long userId, List<String> requestedSkillIds) {
        Map<String, ChatUserSkill> skillIndex = loadUserSkills(userId).stream()
                .collect(Collectors.toMap(skill -> String.valueOf(skill.getId()), skill -> skill));
        List<String> normalizedSkillIds = normalizeSkillIds(userId, requestedSkillIds);
        if (!normalizedSkillIds.isEmpty()) {
            log.info("构造技能系统提示：userId={}，skillIds={}", userId, normalizedSkillIds);
        }
        return normalizedSkillIds.stream()
                .map(skillIndex::get)
                .filter(Objects::nonNull)
                .map(skill -> new ChatMessage(
                        "system",
                        "当前启用技能《" + skill.getSkillName() + "》：\n" + skill.getSystemPrompt()
                ))
                .toList();
    }

    private List<ChatUserSkill> loadUserSkills(Long userId) {
        return chatUserSkillMapper.selectList(
                Wrappers.<ChatUserSkill>lambdaQuery()
                        .eq(ChatUserSkill::getUserId, userId)
                        .orderByDesc(ChatUserSkill::getUpdatedAt)
                        .orderByDesc(ChatUserSkill::getId)
        );
    }

    private ChatSkillOption toOption(ChatUserSkill skill) {
        return new ChatSkillOption(
                String.valueOf(skill.getId()),
                skill.getSkillName(),
                StringUtils.hasText(skill.getSkillDescription()) ? skill.getSkillDescription() : "自定义 Skill"
        );
    }

    /**
     * 返回一个 skill 的完整定义，供前端查看和编辑。
     */
    public ChatSkillDefinition getSkillDefinition(Long userId, Long skillId) {
        if (skillId == null) {
            return null;
        }
        ChatUserSkill skill = chatUserSkillMapper.selectById(skillId);
        if (skill == null || !userId.equals(skill.getUserId())) {
            return null;
        }
        return new ChatSkillDefinition(
                String.valueOf(skill.getId()),
                skill.getSkillName(),
                skill.getSkillDescription(),
                skill.getSystemPrompt()
        );
    }
}
