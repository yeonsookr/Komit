package com.a501.service.domain.chat.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.chat.ChatErrorCode;

/**
 * 대화를 가리키는 이름표. {@code "room-92-chat"} 처럼 생겼습니다.
 *
 * <p><b>방과 세션이 한 이름 공간을 씁니다.</b> 둘 다 식별자가 1부터 매겨져 숫자만으로는 어느 쪽인지
 * 가릴 수 없습니다. 앞자리가 그것을 가릅니다.
 *
 * @param prefix 무엇에 붙은 대화인지. {@code room} 또는 {@code session}
 * @param targetId 그 방이나 세션의 식별자
 */
public record ChatReference(String prefix, Integer targetId) {
    private static final String SUFFIX = "-chat";

    /**
     * 이름표를 읽습니다.
     *
     * @throws BusinessException {@link ChatErrorCode#CHAT_NOT_FOUND} 모양이 맞지 않을 때. 없는 대화와
     *                           같게 다뤄 어떤 이름표가 있는지 더듬어 볼 수 없게 합니다
     */
    public static ChatReference parse(String chatRefId) {
        if (chatRefId == null || !chatRefId.endsWith(SUFFIX)) {
            throw new BusinessException(ChatErrorCode.CHAT_NOT_FOUND);
        }
        String body = chatRefId.substring(0, chatRefId.length() - SUFFIX.length());
        int separator = body.indexOf('-');
        if (separator <= 0) {
            throw new BusinessException(ChatErrorCode.CHAT_NOT_FOUND);
        }
        try {
            return new ChatReference(
                    body.substring(0, separator), Integer.valueOf(body.substring(separator + 1)));
        } catch (NumberFormatException exception) {
            throw new BusinessException(ChatErrorCode.CHAT_NOT_FOUND);
        }
    }

    public static String of(String prefix, Integer targetId) {
        return prefix + "-" + targetId + SUFFIX;
    }

    public String value() {
        return of(prefix, targetId);
    }
}
